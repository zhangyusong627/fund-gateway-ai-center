package org.practice.fundgateway.knowledge.embedding;

import java.nio.file.Path;

/** 将现有本地 BGE 模型适配为索引服务的向量生成端口。 */
public class LocalBgeEmbeddingGenerator implements EmbeddingGenerator, AutoCloseable {

    private final LocalBgeEmbeddingModel model;

    /** 从本地模型目录创建向量生成器。 */
    public LocalBgeEmbeddingGenerator(Path modelDirectory) throws java.io.IOException {
        this.model = new LocalBgeEmbeddingModel(modelDirectory);
    }

    /** 调用 BGE 模型生成归一化向量。 */
    @Override
    public float[] embed(String text) throws Exception {
        return model.embed(text);
    }

    /** 释放模型资源。 */
    @Override
    public void close() {
        model.close();
    }
}
