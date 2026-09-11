package org.practice.fundgateway.knowledge.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** 验证文档版本不可覆盖、哈希去重和索引任务状态机。 */
class KnowledgeDocumentApplicationServiceTest {

    /** 验证同一版本和相同内容不能重复登记。 */
    @Test
    void shouldRejectVersionOverwriteAndHashDuplicate() {
        InMemoryDocumentVersionRepository documents = new InMemoryDocumentVersionRepository();
        KnowledgeDocumentApplicationService service = new KnowledgeDocumentApplicationService(
                documents, new InMemoryIndexTaskRepository());
        DocumentSource source = new DocumentSource("doc-1", "v1", Path.of("sample.docx"), "hash-1");
        service.register(source);
        assertThrows(IllegalStateException.class, () -> service.register(source));
        assertThrows(IllegalStateException.class, () -> service.register(
                new DocumentSource("doc-2", "v1", Path.of("sample-2.docx"), "hash-1")));
    }

    /** 验证索引任务必须按 CREATED 到 INDEXED 的顺序推进。 */
    @Test
    void shouldFollowIndexTaskLifecycle() {
        InMemoryDocumentVersionRepository documents = new InMemoryDocumentVersionRepository();
        InMemoryIndexTaskRepository tasks = new InMemoryIndexTaskRepository();
        KnowledgeDocumentApplicationService service = new KnowledgeDocumentApplicationService(documents, tasks);
        DocumentSource source = new DocumentSource("doc-1", "v1", Path.of("sample.docx"), "hash-1");
        documents.save(new DocumentVersionRecord(source, DocumentIndexStatus.PARSED, java.util.List.of(), java.util.List.of()));
        IndexTask task = service.createIndexTask("doc-1", "v1");
        assertEquals(IndexTaskStatus.CREATED, task.status());
        service.transition(task.taskId(), IndexTaskStatus.PARSING, null);
        service.transition(task.taskId(), IndexTaskStatus.PARSED, null);
        service.transition(task.taskId(), IndexTaskStatus.INDEXING, null);
        assertEquals(IndexTaskStatus.INDEXED, service.transition(task.taskId(), IndexTaskStatus.INDEXED, null).status());
        assertThrows(IllegalStateException.class,
                () -> service.transition(task.taskId(), IndexTaskStatus.CREATED, null));
    }
}
