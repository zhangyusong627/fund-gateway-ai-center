package org.practice.fundgateway.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.practice.fundgateway.console.ConsoleModels.RagAnswerRequest;
import org.practice.fundgateway.console.ConsoleModels.RagAnswerResponse;
import org.practice.fundgateway.console.ConsoleModels.RagCandidate;
import org.practice.fundgateway.console.ConsoleModels.RagCitation;
import org.practice.fundgateway.console.ConsoleModels.RagQueryRequest;
import org.practice.fundgateway.console.ConsoleModels.RagQueryResponse;
import org.practice.fundgateway.guardian.ai.ModelGateway;
import org.practice.fundgateway.guardian.ai.DeepSeekPricingPolicy;
import org.practice.fundgateway.guardian.audit.ModelAuditApplicationService;
import org.practice.fundgateway.guardian.audit.ModelCallAudit;
import org.practice.fundgateway.guardian.audit.ModelCallStatus;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 在纯检索之上提供证据约束的结构化 RAG 答案生成。 */
@Service
public class RagAnswerService {

    private static final Pattern INTERFACE_CODE = Pattern.compile("交易码\\s*[：:]\\s*([A-Za-z][A-Za-z0-9]+XJ)");

    /**
     * 证据门禁在模型调用之前拒绝时使用的模型字段值。
     * 该值表示本次没有发起模型调用，避免把“当前配置的模型”误当成“本次调用的模型”。
     */
    public static final String NOT_INVOKED_MODEL = "NOT_INVOKED";

    private final ConsoleRagService retrieval;
    private final ModelGateway modelGateway;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbcTemplate;
    private final RagModelConfiguration modelConfiguration;
    private final ModelAuditApplicationService auditService;

    public RagAnswerService(ConsoleRagService retrieval, ModelGateway modelGateway, ObjectMapper mapper,
                            JdbcTemplate jdbcTemplate, RagModelConfiguration modelConfiguration,
                            ModelAuditApplicationService auditService) {
        this.retrieval = retrieval;
        this.modelGateway = modelGateway;
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
        this.modelConfiguration = modelConfiguration;
        this.auditService = auditService;
    }

    /** 先完成检索和证据门禁，再让模型仅基于召回片段生成答案。 */
    public RagAnswerResponse answer(RagAnswerRequest request) throws Exception {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        String traceId = "rag-answer-" + UUID.randomUUID();
        String model = modelConfiguration.current().model();
        // 用户显式指定的 Top-K 同时作为在线回答的召回预算；未指定时使用默认值 8。
        int answerTopK = request.topK() == null ? 8 : request.topK();
        RagQueryResponse retrieved = retrieval.query(new RagQueryRequest(request.collectionName(), request.documentId(),
                request.documentVersion(), request.question(), request.keywords(), answerTopK, request.providerId()));
        if (retrieved.truncated()) {
            RagAnswerResponse response = new RagAnswerResponse("INSUFFICIENT_EVIDENCE", request.question(),
                    "匹配结果超过系统安全上限，当前仅展示部分结果，无法保证列表完整。请缩小文档、章节或接口范围后重试。",
                    false, NOT_INVOKED_MODEL, traceId, List.of(), retrieved);
            saveAudit(response);
            return response;
        }
        if (!"ACCEPTED".equals(retrieved.status())) {
            RagAnswerResponse response = new RagAnswerResponse("INSUFFICIENT_EVIDENCE", request.question(),
                    "当前资方知识库没有足够证据回答该问题。", false, NOT_INVOKED_MODEL, traceId, List.of(), retrieved);
            saveAudit(response);
            return response;
        }
        if (!modelGateway.available()) {
            throw new IllegalStateException("DeepSeek 模型适配器不可用");
        }
        boolean exhaustiveList = isExhaustiveListQuestion(request.question());
        int maxTokens = exhaustiveList ? 2400 : 800;
        String prompt = buildPrompt(request.question(), retrieved.candidates(), exhaustiveList);
        String rawRequest = mapper.writeValueAsString(Map.of(
                "model", model, "stream", false, "max_tokens", maxTokens,
                "traceId", traceId, "question", request.question()));
        long started = System.nanoTime();
        String rawResponse = "";
        ModelCallStatus auditStatus = ModelCallStatus.FAILED;
        try {
            ModelGateway.ModelCompletion completion = modelGateway.complete(
                    new ModelGateway.ModelRequest(model, prompt, maxTokens, rawRequest));
            rawResponse = completion.rawResponse();
            JsonNode root = parseJson(completion.content());
            String answer = requiredText(root, "answer");
            List<RagCitation> citations = parseCitations(root.get("citations"), retrieved.candidates());
            if (citations.isEmpty()) {
                auditStatus = ModelCallStatus.REJECTED_BY_GATE;
                throw new IllegalStateException("模型答案缺少有效引用");
            }
            if (exhaustiveList) {
                answer = completeInterfaceListAnswer(answer, retrieved.candidates());
            }
            auditStatus = ModelCallStatus.SUCCEEDED;
            RagAnswerResponse response = new RagAnswerResponse("ANSWERED", request.question(), answer, true, model,
                    traceId, citations, retrieved);
            saveAudit(response);
            return response;
        } finally {
            recordModelAudit(rawRequest, rawResponse, traceId, model, prompt, started, auditStatus);
        }
    }

    /** 将 RAG 生成调用写入统一模型审计，失败也保留状态和成本估算。 */
    private void recordModelAudit(String rawRequest, String rawResponse, String traceId, String model,
                                  String prompt, long started, ModelCallStatus status) {
        try {
            long inputTokens = jsonUsage(rawResponse, "prompt_tokens", Math.max(1, prompt.length() / 2L));
            long outputTokens = jsonUsage(rawResponse, "completion_tokens",
                    rawResponse == null ? 0 : rawResponse.length() / 2L);
            DeepSeekPricingPolicy.PriceSnapshot pricing = DeepSeekPricingPolicy.snapshot(model);
            auditService.record(ModelCallAudit.priced("rag-model-call-" + UUID.randomUUID(), traceId,
                    "rag", "answer", "deepseek", model, "rag-answer-v2", inputTokens, outputTokens,
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), status, 0, pricing.version(),
                    pricing.inputCacheMissPerMillion(), pricing.outputPerMillion(), pricing.currency(),
                    rawRequest, rawResponse == null ? "" : rawResponse, Instant.now()));
        } catch (Exception auditException) {
            System.err.println("RAG 模型调用审计写入失败：" + auditException.getMessage());
        }
    }

    /** 优先读取供应商返回的 token 用量，缺失时使用可解释的字符估算。 */
    private long jsonUsage(String rawResponse, String field, long fallback) {
        try {
            JsonNode usage = mapper.readTree(rawResponse).get("usage");
            JsonNode value = usage == null ? null : usage.get(field);
            return value != null && value.isNumber() ? value.asLong() : fallback;
        } catch (Exception exception) {
            return fallback;
        }
    }

    public RagModelConfiguration.Selection modelConfiguration() {
        return modelConfiguration.current();
    }

    public RagModelConfiguration.Selection selectModel(RagModelConfiguration.UpdateRequest request) {
        if (request == null) throw new IllegalArgumentException("模型选择不能为空");
        return modelConfiguration.select(request.provider(), request.model());
    }

    private void saveAudit(RagAnswerResponse response) throws Exception {
        jdbcTemplate.update("insert into knowledge.rag_answer_audits "
                        + "(answer_id,trace_id,question,status,model,answer,citations_json,answered_at) "
                        + "values (?,?,?,?,?,?,CAST(? AS jsonb),now())",
                UUID.randomUUID(), response.traceId(), response.question(), response.status(), response.model(),
                response.answer(), mapper.writeValueAsString(response.citations()));
    }

    private String buildPrompt(String question, List<RagCandidate> candidates, boolean exhaustiveList) throws Exception {
        StringBuilder evidence = new StringBuilder();
        for (RagCandidate candidate : candidates) {
            evidence.append("[chunkId=").append(candidate.chunkId()).append(", locator=")
                    .append(candidate.locator()).append("]\n").append(candidate.content()).append("\n\n");
        }
        String listInstruction = exhaustiveList
                ? "这是一个完整列表问题，必须逐项列出证据中出现的全部交易码，不能只列举部分；若无法确认完整性，明确说明证据不足。"
                : "";
        return "你是资方接口知识库问答助手。只能依据下列证据回答，禁止补充证据之外的事实。"
                + "必须返回严格 JSON，不要 Markdown，字段为 answer 和 citations；citations 内含 chunkId、quote。"
                + "每个 citation 的 chunkId 必须来自证据。若证据不足，answer 明确说明无法回答，citations 返回空数组。\n"
                + listInstruction + "\n"
                + "问题：" + question + "\n证据：\n" + evidence;
    }

    /**
     * 列表问题的交易码清单由证据确定性补齐；模型负责组织解释，不因漏写一个交易码而把整次回答变成 500。
     * 证据本身若被有界查询截断，调用方已在前面拒答，这里只处理未截断证据中的生成遗漏。
     */
    private String completeInterfaceListAnswer(String answer, List<RagCandidate> candidates) {
        Set<String> expected = new LinkedHashSet<>();
        for (RagCandidate candidate : candidates) {
            Matcher matcher = INTERFACE_CODE.matcher(candidate.content());
            while (matcher.find()) {
                expected.add(matcher.group(1));
            }
        }
        String normalizedAnswer = answer.toLowerCase(Locale.ROOT);
        Set<String> missing = expected.stream()
                .filter(code -> !normalizedAnswer.contains(code.toLowerCase(Locale.ROOT)))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (missing.isEmpty()) {
            return answer;
        }
        StringBuilder supplement = new StringBuilder(answer.trim())
                .append("\n\n【证据校正】模型输出未逐项写出全部交易码，以下清单由本次检索证据确定性补齐：\n");
        int index = 1;
        for (String code : missing) {
            supplement.append(index++).append(". ").append(code).append('\n');
        }
        return supplement.toString().trim();
    }

    private boolean isExhaustiveListQuestion(String question) {
        String normalized = question == null ? "" : question.replaceAll("\\s+", "");
        return normalized.matches(".*(包含哪些|有哪些|列出.*(接口|交易码)|完整.*(接口|交易码)|全部.*(接口|交易码)|接口.*列表|交易码.*列表).*");
    }

    private JsonNode parseJson(String content) throws Exception {
        String normalized = content.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        return mapper.readTree(normalized);
    }

    private String requiredText(JsonNode root, String field) {
        JsonNode value = root == null ? null : root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException("模型结构化输出缺少字段: " + field);
        }
        return value.asText();
    }

    private List<RagCitation> parseCitations(JsonNode node, List<RagCandidate> candidates) {
        List<RagCitation> result = new ArrayList<>();
        if (node == null || !node.isArray()) return result;
        for (JsonNode item : node) {
            String chunkId = text(item, "chunkId");
            String quote = text(item, "quote");
            RagCandidate matched = candidates.stream().filter(c -> c.chunkId().equals(chunkId)).findFirst().orElse(null);
            if (matched != null && !quote.isBlank()) {
                result.add(new RagCitation(chunkId, matched.locator(), quote));
            }
        }
        return result;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.asText() : "";
    }
}
