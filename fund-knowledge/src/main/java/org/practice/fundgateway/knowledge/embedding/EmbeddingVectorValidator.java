package org.practice.fundgateway.knowledge.embedding;

/** 校验向量维度、有限值和归一化约束。 */
public final class EmbeddingVectorValidator {

    private EmbeddingVectorValidator() {
    }

    /** 校验向量是否符合固定模型配置。 */
    public static void validate(float[] vector, EmbeddingDescriptor descriptor) {
        if (vector == null || vector.length != descriptor.dimension()) {
            throw new IllegalArgumentException("向量维度不匹配，期望 " + descriptor.dimension());
        }
        double norm = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("向量包含非有限值");
            }
            norm += value * value;
        }
        if (descriptor.normalized() && Math.abs(Math.sqrt(norm) - 1D) > 0.001D) {
            throw new IllegalArgumentException("向量未通过 L2 归一化校验");
        }
    }
}
