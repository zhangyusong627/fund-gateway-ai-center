package org.practice.fundgateway.knowledge.embedding;

/** 描述向量生成所使用的提供方、模型、维度和归一化配置。 */
public record EmbeddingDescriptor(
        String provider,
        String model,
        int dimension,
        boolean normalized) {

    /** 校验向量配置的基本信息。 */
    public EmbeddingDescriptor {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Embedding 提供方不能为空");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Embedding 模型不能为空");
        }
        if (dimension <= 0) {
            throw new IllegalArgumentException("Embedding 维度必须大于零");
        }
    }
}
