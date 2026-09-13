package org.practice.fundgateway.guardian.ai;

import java.util.concurrent.TimeoutException;

/** 定义模型基础设施的最小出站端口，业务层不依赖具体模型 SDK。 */
public interface ModelGateway {

    /** 返回当前是否具备发起模型调用的必要配置。 */
    boolean available();

    /** 执行一次模型请求，适配器负责把响应转换为原始报文和文本内容。 */
    ModelCompletion complete(ModelRequest request) throws Exception;

    /** 判断基础设施异常是否值得进行一次有限重试。 */
    default boolean retryable(Throwable exception) {
        return exception instanceof TimeoutException || exception instanceof java.io.IOException;
    }

    /** 模型请求的脱离 SDK 的稳定表示。 */
    record ModelRequest(String model, String prompt, int maxOutputTokens, String rawRequest) {

        /** 校验模型请求，避免把不完整请求交给基础设施。 */
        public ModelRequest {
            if (model == null || model.isBlank() || prompt == null || prompt.isBlank()
                    || maxOutputTokens <= 0 || rawRequest == null || rawRequest.isBlank()) {
                throw new IllegalArgumentException("模型请求缺少必要字段");
            }
        }
    }

    /** 模型适配器返回的原始响应和候选文本。 */
    record ModelCompletion(String content, String rawResponse) {

        /** 校验响应，避免以空内容进入结构化输出门禁。 */
        public ModelCompletion {
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("模型响应内容为空");
            }
            rawResponse = rawResponse == null ? "" : rawResponse;
        }
    }

    /** 创建不可用适配器，供没有密钥的本地模式和单元测试使用。 */
    static ModelGateway unavailable() {
        return new ModelGateway() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public ModelCompletion complete(ModelRequest request) {
                throw new IllegalStateException("模型适配器不可用");
            }
        };
    }
}
