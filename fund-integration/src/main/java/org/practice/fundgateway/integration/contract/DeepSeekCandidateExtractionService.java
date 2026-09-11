package org.practice.fundgateway.integration.contract;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.practice.fundgateway.knowledge.rag.RagEvidenceQueryService;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionMessage;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest;

import tools.jackson.databind.json.JsonMapper;

/** 调用 DeepSeek 从已接受 RAG 证据中抽取候选接口规范，并立即做 Java 门禁。 */
public class DeepSeekCandidateExtractionService {

    private final DeepSeekApi api;
    private final JsonMapper mapper;
    private final ContractCandidateValidator validator;
    private final Clock clock;
    private final String model;

    /** 使用默认模型和系统时钟创建抽取服务。 */
    public DeepSeekCandidateExtractionService(DeepSeekApi api) {
        this(api, JsonMapper.builder().build(), new ContractCandidateValidator(), Clock.systemUTC(),
                "deepseek-v4-flash");
    }

    /** 注入客户端、映射器和时钟，便于测试和复现。 */
    public DeepSeekCandidateExtractionService(DeepSeekApi api, JsonMapper mapper,
                                              ContractCandidateValidator validator,
                                              Clock clock, String model) {
        this.api = api;
        this.mapper = mapper;
        this.validator = validator;
        this.clock = clock;
        this.model = model;
    }

    /** 调用模型并把固定 JSON 映射成候选规范，任何门禁失败都直接拒绝。 */
    public ExtractionResult extract(RagEvidenceQueryService.RagEvidenceResponse response,
                                    String providerId, String interfaceId) throws Exception {
        CandidateContractPromptBuilder.CandidateContractPrompt prompt =
                new CandidateContractPromptBuilder().build(response, providerId, interfaceId);
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(model)
                .messages(List.of(
                        new ChatCompletionMessage(prompt.systemInstruction(), ChatCompletionMessage.Role.SYSTEM),
                        new ChatCompletionMessage(prompt.userInput(), ChatCompletionMessage.Role.USER)))
                .stream(false)
                .maxTokens(1200)
                .thinking(ChatCompletionRequest.Thinking.DISABLED)
                .build();
        String rawRequest = mapper.writeValueAsString(request);
        var entity = api.chatCompletionEntity(request);
        if (entity.getBody() == null || entity.getBody().choices() == null
                || entity.getBody().choices().isEmpty()) {
            throw new ExtractionRejectedException("模型响应没有 choices", rawRequest, "");
        }
        String rawResponse = mapper.writeValueAsString(entity.getBody());
        String raw = entity.getBody().choices().getFirst().message().content();
        ModelCandidatePayload payload;
        try {
            payload = mapper.readValue(raw, ModelCandidatePayload.class);
        } catch (Exception exception) {
            throw new ExtractionRejectedException("模型返回无法映射为候选 JSON", rawRequest, rawResponse, exception);
        }
        if (payload.error() != null) {
            throw new ExtractionRejectedException("模型返回证据不足", rawRequest, rawResponse);
        }
        ContractCandidate candidate = new ContractCandidate(
                "candidate-" + UUID.randomUUID(), providerId, interfaceId, payload.purpose(),
                payload.endpoint(), payload.httpMethod(), payload.fields(), payload.evidence(),
                response.evidence().getFirst().documentVersion(), model, Instant.now(clock),
                ContractCandidateStatus.DRAFT);
        ContractValidationResult structure = validator.validateStructure(candidate);
        ContractValidationResult business = validator.validateBusiness(candidate);
        ContractValidationResult source = validator.validateSource(candidate);
        if (!structure.valid() || !business.valid() || !source.valid()) {
            throw new ExtractionRejectedException("模型候选未通过 Java 三层校验", rawRequest, rawResponse);
        }
        return new ExtractionResult(rawRequest, rawResponse, candidate);
    }

    /** 保存模型输出映射成功后的候选结果。 */
    public record ExtractionResult(String rawRequest, String rawResponse, ContractCandidate candidate) {
    }

    /** 保存被 Java 门禁拒绝的原始请求和响应，便于复盘且不泄露密钥。 */
    public static class ExtractionRejectedException extends IllegalStateException {
        private final String rawRequest;
        private final String rawResponse;

        /** 创建带原始报文的拒绝异常。 */
        public ExtractionRejectedException(String message, String rawRequest, String rawResponse) {
            super(message);
            this.rawRequest = rawRequest;
            this.rawResponse = rawResponse;
        }

        /** 创建带原始报文和底层原因的拒绝异常。 */
        public ExtractionRejectedException(String message, String rawRequest, String rawResponse,
                                           Throwable cause) {
            super(message, cause);
            this.rawRequest = rawRequest;
            this.rawResponse = rawResponse;
        }

        /** 返回原始请求报文。 */
        public String rawRequest() {
            return rawRequest;
        }

        /** 返回原始响应报文。 */
        public String rawResponse() {
            return rawResponse;
        }
    }

    /** 限制模型只能返回任务定义的候选字段和证据。 */
    private record ModelCandidatePayload(
            String purpose,
            String endpoint,
            String httpMethod,
            List<CandidateFieldDefinition> fields,
            List<ContractEvidenceCitation> evidence,
            String error) {
    }
}
