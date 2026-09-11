package org.practice.fundgateway.knowledge.embedding;

/** 定义索引服务需要的文本向量生成端口。 */
public interface EmbeddingGenerator {

    /** 为一段文本生成向量。 */
    float[] embed(String text) throws Exception;
}
