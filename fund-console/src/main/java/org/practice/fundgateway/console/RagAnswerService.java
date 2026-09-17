package org.practice.fundgateway.console;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.practice.fundgateway.console.ConsoleModels.RagAnswerRequest;
import org.practice.fundgateway.console.ConsoleModels.RagAnswerResponse;
import org.practice.fundgateway.console.ConsoleModels.RagCandidate;
import org.practice.fundgateway.console.ConsoleModels.RagCitation;
import org.practice.fundgateway.console.ConsoleModels.RagQueryRequest;
import org.practice.fundgateway.console.ConsoleModels.RagQueryResponse;
import org.practice.fundgateway.guardian.ai.ModelGateway;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 在纯检索之上提供证据约束的结构化 RAG 答案生成。 */
@Service
public class RagAnswerService {

    private static final String MODEL = "deepseek-chat";
    private final ConsoleRagService retrieval;
    private final ModelGateway modelGateway;
    private final ObjectMapper mapper;
    private final JdbcTemplate jdbcTemplate;

    public RagAnswerService(ConsoleRagService retrieval, ModelGateway modelGateway, ObjectMapper mapper,
                            JdbcTemplate jdbcTemplate) {
        this.retrieval = retrieval;
        this.modelGateway = modelGateway;
        this.mapper = mapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 先完成检索和证据门禁，再让模型仅基于召回片段生成答案。 */
    public RagAnswerResponse answer(RagAnswerRequest request) throws Exception {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        String traceId = "rag-answer-" + UUID.randomUUID();
        RagQueryResponse retrieved = retrieval.query(new RagQueryRequest(request.collectionName(), request.documentId(),
                request.documentVersion(), request.question(), request.keywords(), request.topK()));
        if (!"ACCEPTED".equals(retrieved.status())) {
            RagAnswerResponse response = new RagAnswerResponse("INSUFFICIENT_EVIDENCE", request.question(),
                    "当前资方知识库没有足够证据回答该问题。", false, MODEL, traceId, List.of(), retrieved);
            saveAudit(response);
            return response;
        }
        if (!modelGateway.available()) {
            throw new IllegalStateException("DeepSeek 模型适配器不可用");
        }
        String prompt = buildPrompt(request.question(), retrieved.candidates());
        ModelGateway.ModelCompletion completion = modelGateway.complete(
                new ModelGateway.ModelRequest(MODEL, prompt, 800,
                        mapper.writeValueAsString(java.util.Map.of("traceId", traceId, "question", request.question()))));
        JsonNode root = parseJson(completion.content());
        String answer = requiredText(root, "answer");
        List<RagCitation> citations = parseCitations(root.get("citations"), retrieved.candidates());
        if (citations.isEmpty()) {
            throw new IllegalStateException("模型答案缺少有效引用");
        }
        RagAnswerResponse response = new RagAnswerResponse("ANSWERED", request.question(), answer, true, MODEL,
                traceId, citations, retrieved);
        saveAudit(response);
        return response;
    }

    private void saveAudit(RagAnswerResponse response) throws Exception {
        jdbcTemplate.update("insert into knowledge.rag_answer_audits "
                        + "(answer_id,trace_id,question,status,model,answer,citations_json,answered_at) "
                        + "values (?,?,?,?,?,?,CAST(? AS jsonb),now())",
                UUID.randomUUID(), response.traceId(), response.question(), response.status(), response.model(),
                response.answer(), mapper.writeValueAsString(response.citations()));
    }

    private String buildPrompt(String question, List<RagCandidate> candidates) throws Exception {
        StringBuilder evidence = new StringBuilder();
        for (RagCandidate candidate : candidates) {
            evidence.append("[chunkId=").append(candidate.chunkId()).append(", locator=")
                    .append(candidate.locator()).append("]\n").append(candidate.content()).append("\n\n");
        }
        return "你是资方接口知识库问答助手。只能依据下列证据回答，禁止补充证据之外的事实。"
                + "必须返回严格 JSON，不要 Markdown，字段为 answer 和 citations；citations 内含 chunkId、quote。"
                + "每个 citation 的 chunkId 必须来自证据。若证据不足，answer 明确说明无法回答，citations 返回空数组。\n"
                + "问题：" + question + "\n证据：\n" + evidence;
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
