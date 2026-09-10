package org.practice.fundgateway.knowledge.embedding;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** 验证向量维度、归一化和非法数值门禁。 */
class EmbeddingVectorValidatorTest {

    /** 归一化向量通过校验。 */
    @Test
    void shouldAcceptNormalizedVector() {
        EmbeddingDescriptor descriptor = new EmbeddingDescriptor("local", "bge", 2, true);
        assertDoesNotThrow(() -> EmbeddingVectorValidator.validate(new float[]{1F, 0F}, descriptor));
    }

    /** 维度不一致时拒绝写入。 */
    @Test
    void shouldRejectDimensionMismatch() {
        EmbeddingDescriptor descriptor = new EmbeddingDescriptor("local", "bge", 2, true);
        assertThrows(IllegalArgumentException.class,
                () -> EmbeddingVectorValidator.validate(new float[]{1F}, descriptor));
    }

    /** 未归一化向量在固定配置下拒绝写入。 */
    @Test
    void shouldRejectUnnormalizedVector() {
        EmbeddingDescriptor descriptor = new EmbeddingDescriptor("local", "bge", 2, true);
        assertThrows(IllegalArgumentException.class,
                () -> EmbeddingVectorValidator.validate(new float[]{2F, 0F}, descriptor));
    }
}
