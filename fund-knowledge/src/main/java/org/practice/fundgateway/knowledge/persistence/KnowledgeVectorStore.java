package org.practice.fundgateway.knowledge.persistence;

import org.practice.fundgateway.knowledge.chunk.KnowledgeChunk;
import org.practice.fundgateway.knowledge.embedding.EmbeddingDescriptor;

/** 定义知识分块向量持久化能力，不暴露具体数据库实现。 */
public interface KnowledgeVectorStore {

    /** 注册或校验向量集合的模型配置。 */
    void ensureCollection(String collectionName, String description, EmbeddingDescriptor descriptor);

    /** 幂等保存一个知识分块及其向量。 */
    void save(String collectionName, KnowledgeChunk chunk, float[] vector,
              EmbeddingDescriptor descriptor);

    /** 创建不可查询的暂存集合，供索引完成后发布。 */
    default void beginStagingCollection(String collectionName, String description,
                                         EmbeddingDescriptor descriptor) {
        ensureCollection(collectionName, description, descriptor);
    }

    /** 将暂存集合发布为可查询集合。 */
    default void publishStagingCollection(String collectionName) {
    }

    /** 删除失败的暂存集合及其分片。 */
    default void discardStagingCollection(String collectionName) {
    }
}
