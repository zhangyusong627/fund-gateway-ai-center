package org.practice.fundgateway.knowledge.persistence;

import org.practice.fundgateway.knowledge.chunk.KnowledgeChunk;
import org.practice.fundgateway.knowledge.embedding.EmbeddingDescriptor;

import java.util.List;

/** 定义知识分块向量持久化能力，不暴露具体数据库实现。 */
public interface KnowledgeVectorStore {

    /** 注册或校验向量集合的模型配置。 */
    void ensureCollection(String collectionName, String description, EmbeddingDescriptor descriptor);

    /** 幂等保存一个知识分块及其向量。 */
    void save(String collectionName, KnowledgeChunk chunk, float[] vector,
              EmbeddingDescriptor descriptor);

    /** 原子发布一份文档版本的全部分块，失败时不得影响集合内其他文档。 */
    default void publishDocument(String collectionName, String description,
                                 List<KnowledgeChunk> chunks, List<float[]> vectors,
                                 EmbeddingDescriptor descriptor) {
        if (chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("分块数量与向量数量不一致");
        }
        ensureCollection(collectionName, description, descriptor);
        for (int index = 0; index < chunks.size(); index++) {
            save(collectionName, chunks.get(index), vectors.get(index), descriptor);
        }
    }
}
