package org.practice.fundgateway.knowledge.document;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.practice.fundgateway.knowledge.chunk.KnowledgeChunk;
import org.practice.fundgateway.knowledge.embedding.EmbeddingDescriptor;
import org.practice.fundgateway.knowledge.embedding.EmbeddingGenerator;
import org.practice.fundgateway.knowledge.persistence.KnowledgeVectorStore;

/** 验证正式索引服务的成功和失败边界。 */
class DocumentIndexApplicationServiceTest {

    /** 验证全部向量生成后写入，并发布 INDEXED。 */
    @Test
    void shouldIndexParsedDocument() {
        InMemoryDocumentVersionRepository documents = new InMemoryDocumentVersionRepository();
        InMemoryIndexTaskRepository tasks = new InMemoryIndexTaskRepository();
        DocumentSource source = new DocumentSource("doc-1", "v1", Path.of("sample.docx"), "hash-1");
        KnowledgeChunk chunk = new KnowledgeChunk("doc-1:v1:1", source, "授信申请", "申请金额", 1, 1, -1, -1);
        documents.save(new DocumentVersionRecord(source, DocumentIndexStatus.PARSED, List.of(), List.of(chunk)));
        IndexTask task = tasks.save(IndexTask.created("doc-1", "v1"));
        RecordingStore store = new RecordingStore();
        IndexTask result = new DocumentIndexApplicationService(documents, tasks, text -> new float[] {1F, 0F}, store)
                .index(task.taskId(), "contracts", new EmbeddingDescriptor("test", "test-model", 2, true));
        assertEquals(IndexTaskStatus.INDEXED, result.status());
        assertEquals(DocumentIndexStatus.INDEXED, documents.find("doc-1", "v1").orElseThrow().status());
        assertEquals(1, store.saved.size());
    }

    /** 验证向量生成失败时不写入分片，并保留 FAILED 状态。 */
    @Test
    void shouldFailBeforeWritingWhenEmbeddingFails() {
        InMemoryDocumentVersionRepository documents = new InMemoryDocumentVersionRepository();
        InMemoryIndexTaskRepository tasks = new InMemoryIndexTaskRepository();
        DocumentSource source = new DocumentSource("doc-1", "v1", Path.of("sample.docx"), "hash-1");
        KnowledgeChunk chunk = new KnowledgeChunk("doc-1:v1:1", source, "授信申请", "申请金额", 1, 1, -1, -1);
        documents.save(new DocumentVersionRecord(source, DocumentIndexStatus.PARSED, List.of(), List.of(chunk)));
        IndexTask task = tasks.save(new IndexTask(java.util.UUID.randomUUID(), "doc-1", "v1", IndexTaskStatus.PARSED, null, java.time.Instant.now()));
        RecordingStore store = new RecordingStore();
        IndexTask result = new DocumentIndexApplicationService(documents, tasks, text -> { throw new IllegalStateException("模型失败"); }, store)
                .index(task.taskId(), "contracts", new EmbeddingDescriptor("test", "test-model", 2, true));
        assertEquals(IndexTaskStatus.FAILED, result.status());
        assertEquals(DocumentIndexStatus.INDEX_FAILED, documents.find("doc-1", "v1").orElseThrow().status());
        assertEquals(0, store.saved.size());
        assertEquals(1, store.discarded);
    }

    /** 记录向量写入调用的测试替身。 */
    private static class RecordingStore implements KnowledgeVectorStore {
        private final List<KnowledgeChunk> saved = new ArrayList<>();
        private int discarded;

        /** 接受集合配置。 */
        @Override
        public void ensureCollection(String collectionName, String description, EmbeddingDescriptor descriptor) {
        }

        /** 记录写入分片。 */
        @Override
        public void save(String collectionName, KnowledgeChunk chunk, float[] vector, EmbeddingDescriptor descriptor) {
            saved.add(chunk);
        }

        /** 记录失败集合清理。 */
        @Override
        public void discardStagingCollection(String collectionName) {
            discarded++;
        }
    }
}
