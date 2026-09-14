package org.practice.fundgateway.console;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.practice.fundgateway.knowledge.document.InMemoryDocumentVersionRepository;
import org.practice.fundgateway.knowledge.document.InMemoryIndexTaskRepository;
import org.practice.fundgateway.knowledge.document.KnowledgeDocumentApplicationService;
import org.practice.fundgateway.knowledge.document.DocumentIndexApplicationService;
import org.practice.fundgateway.knowledge.embedding.EmbeddingDescriptor;
import org.practice.fundgateway.knowledge.embedding.EmbeddingGenerator;
import org.practice.fundgateway.knowledge.chunk.KnowledgeChunk;
import org.practice.fundgateway.knowledge.persistence.KnowledgeVectorStore;
import org.springframework.mock.web.MockMultipartFile;

/** 验证控制台能够上传 DOCX、解析分片并创建索引任务。 */
class KnowledgeDocumentControllerTest {

    @TempDir
    private Path storagePath;

    /** 一份有效 DOCX 应返回解析统计并生成不可覆盖的索引任务。 */
    @Test
    void shouldUploadParseAndCreateIndexTask() throws Exception {
        var documentRepository = new InMemoryDocumentVersionRepository();
        var taskRepository = new InMemoryIndexTaskRepository();
        var service = new KnowledgeDocumentApplicationService(documentRepository, taskRepository);
        var controller = new KnowledgeDocumentController(service, storagePath.toString());

        MockMultipartFile file = new MockMultipartFile("file", "demo.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes());
        var response = controller.upload(file, "demo-document", "v1");
        var task = controller.createIndexTask("demo-document", "v1");

        assertThat(response.status()).isEqualTo("PARSED");
        assertThat(response.format()).isEqualTo("DOCX");
        assertThat(response.chunkCount()).isPositive();
        assertThat(response.previews()).isNotEmpty();
        assertThat(response.previews().getFirst().locator()).contains("序号");
        assertThat(task.documentId()).isEqualTo("demo-document");
    }

    /** 验证执行接口异步索引指定版本，并可查询最终状态。 */
    @Test
    void shouldExecuteAndQueryIndexTask() throws Exception {
        var documentRepository = new InMemoryDocumentVersionRepository();
        var taskRepository = new InMemoryIndexTaskRepository();
        var documentService = new KnowledgeDocumentApplicationService(documentRepository, taskRepository);
        EmbeddingGenerator embedding = text -> new float[] {1F, 0F};
        KnowledgeVectorStore store = new KnowledgeVectorStore() {
            /** 接受测试集合。 */
            public void ensureCollection(String name, String description, EmbeddingDescriptor descriptor) { }
            /** 记录测试分片写入。 */
            public void save(String name, KnowledgeChunk chunk, float[] vector, EmbeddingDescriptor descriptor) { }
        };
        var indexService = new DocumentIndexApplicationService(documentRepository, taskRepository, embedding, store);
        var controller = new KnowledgeDocumentController(documentService, indexService, storagePath.toString());
        MockMultipartFile file = new MockMultipartFile("file", "demo.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes());
        controller.upload(file, "demo-document", "v1");
        var created = controller.createIndexTask("demo-document", "v1");
        var accepted = controller.executeIndexTask(created.taskId());
        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        for (int attempt = 0; attempt < 20; attempt++) {
            var current = controller.findIndexTask(created.taskId());
            if ("INDEXED".equals(current.status()) || "FAILED".equals(current.status())) {
                assertThat(current.status()).isEqualTo("INDEXED");
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("索引任务未在测试窗口内完成");
    }

    /** 创建一份同时包含标题、段落和表格的最小 DOCX。 */
    private byte[] docxBytes() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("授信申请接口");
            document.createParagraph().createRun().setText("申请金额 applyAmt 为必填字段。");
            var table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("字段");
            table.getRow(0).getCell(1).setText("说明");
            document.write(output);
            return output.toByteArray();
        }
    }
}
