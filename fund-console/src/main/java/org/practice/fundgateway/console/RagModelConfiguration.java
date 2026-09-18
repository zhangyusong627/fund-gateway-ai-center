package org.practice.fundgateway.console;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** RAG 答案模型的运行时选择；只暴露已声明的供应商和模型。 */
@Service
public class RagModelConfiguration {
    private static final Map<String, List<String>> MODELS = Map.of(
            "deepseek", List.of("deepseek-flash", "deepseek-v4-pro", "deepseek-v4-flash",
                    "deepseek-v4-flash-vision-exp"),
            "openai-compatible", List.of("未接入"));
    private final AtomicReference<String> provider;
    private final AtomicReference<String> model;

    public RagModelConfiguration(@Value("${console.rag.provider:deepseek}") String provider,
                                 @Value("${console.rag.answer-model:deepseek-flash}") String model) {
        this.provider = new AtomicReference<>(provider);
        this.model = new AtomicReference<>(model);
        validate(provider, model);
    }

    public Selection current() {
        return new Selection(provider.get(), model.get(), MODELS, "deepseek".equals(provider.get()));
    }

    public Selection select(String requestedProvider, String requestedModel) {
        validate(requestedProvider, requestedModel);
        provider.set(requestedProvider);
        model.set(requestedModel);
        return current();
    }

    private void validate(String requestedProvider, String requestedModel) {
        if (!MODELS.containsKey(requestedProvider) || !MODELS.get(requestedProvider).contains(requestedModel)) {
            throw new IllegalArgumentException("不支持的 RAG 模型供应商或模型");
        }
        if (!"deepseek".equals(requestedProvider)) {
            throw new IllegalStateException("该模型供应商尚未接入：" + requestedProvider);
        }
    }

    public record Selection(String provider, String model, Map<String, List<String>> availableModels,
                            boolean callable) { }
    public record UpdateRequest(String provider, String model) { }
}
