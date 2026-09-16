package org.practice.fundgateway.console;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import org.practice.fundgateway.knowledge.chunk.KnowledgeChunk;
import org.practice.fundgateway.knowledge.document.DocumentElement;
import org.practice.fundgateway.knowledge.document.DocumentFormat;
import org.practice.fundgateway.knowledge.document.DocumentVersionRecord;
import org.practice.fundgateway.knowledge.document.IndexTask;
import org.practice.fundgateway.knowledge.document.KnowledgeDocumentApplicationService;
import org.practice.fundgateway.knowledge.document.DocumentIndexApplicationService;
import org.practice.fundgateway.knowledge.document.IndexTaskStatus;
import org.practice.fundgateway.knowledge.embedding.EmbeddingDescriptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 提供多格式文档上传、解析预览和索引任务创建接口。 */
@RestController
@RequestMapping("/api/console/knowledge")
public class KnowledgeDocumentController {

    private static final int PREVIEW_LIMIT = 50;
    private final KnowledgeDocumentApplicationService applicationService;
    private final DocumentIndexApplicationService indexApplicationService;
    private final Path storagePath;
    private final Executor indexExecutor;

    /** 注入知识库应用服务和原始文档保存目录。 */
    public KnowledgeDocumentController(KnowledgeDocumentApplicationService applicationService,
                                       @Value("${console.document.storage-path}") String storagePath) {
        this(applicationService, null, storagePath, Runnable::run);
    }

    /** 注入文档服务、正式索引服务和原始文档保存目录。 */
    public KnowledgeDocumentController(KnowledgeDocumentApplicationService applicationService,
                                       DocumentIndexApplicationService indexApplicationService,
                                       @Value("${console.document.storage-path}") String storagePath) {
        this(applicationService, indexApplicationService, storagePath, Runnable::run);
    }

    /** 注入有界索引执行器，隔离文档索引任务与公共线程池。 */
    @Autowired
    public KnowledgeDocumentController(KnowledgeDocumentApplicationService applicationService,
                                       DocumentIndexApplicationService indexApplicationService,
                                       @Value("${console.document.storage-path}") String storagePath,
                                       Executor indexExecutor) {
        this.applicationService = applicationService;
        this.indexApplicationService = indexApplicationService;
        this.storagePath = Path.of(storagePath).toAbsolutePath().normalize();
        this.indexExecutor = indexExecutor;
    }

    /** 保存并解析一份支持格式的文档，返回文档版本及首批分片预览。 */
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentUploadResponse upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam String documentId,
                                         @RequestParam String version) throws IOException {
        validate(file, documentId, version);
        String extension = extension(file.getOriginalFilename());
        Path documentDirectory = storagePath.resolve(safeSegment(documentId)).resolve(safeSegment(version));
        Files.createDirectories(documentDirectory);
        Path savedFile = documentDirectory.resolve("source." + extension);
        if (Files.exists(savedFile)) {
            throw new IllegalStateException("原始文档版本已存在，不允许覆盖：" + documentId + "@" + version);
        }
        Files.copy(file.getInputStream(), savedFile);
        DocumentVersionRecord record = applicationService.registerAndParse(savedFile, documentId, version);
        return response(record);
    }

    /** 为已经解析成功的文档版本创建索引任务。 */
    @PostMapping("/index-tasks")
    public IndexTaskResponse createIndexTask(@RequestParam String documentId,
                                             @RequestParam String version) {
        IndexTask task = applicationService.createIndexTask(documentId, version);
        return new IndexTaskResponse(task.taskId(), task.documentId(), task.version(),
                task.status().name(), task.errorMessage(), task.updatedAt().toString());
    }

    /** 查询全部文档版本的摘要信息。 */
    @GetMapping("/documents")
    public List<DocumentSummary> findAllDocuments() {
        return applicationService.findAllDocuments().stream().map(this::summary).toList();
    }

    /** 查询一份文档版本的完整解析预览。 */
    @GetMapping("/documents/{documentId}/versions/{version}")
    public DocumentUploadResponse findDocument(@PathVariable String documentId, @PathVariable String version) {
        return response(applicationService.findDocument(documentId, version));
    }

    /** 查询全部索引任务。 */
    @GetMapping("/index-tasks")
    public List<IndexTaskResponse> findAllIndexTasks() {
        return applicationService.findAllIndexTasks().stream().map(task -> new IndexTaskResponse(
                task.taskId(), task.documentId(), task.version(), task.status().name(),
                task.errorMessage(), task.updatedAt().toString())).toList();
    }

    /** 异步执行指定版本索引，接口立即返回任务当前状态。 */
    @PostMapping("/index-tasks/{taskId}/execute")
    public IndexTaskResponse executeIndexTask(@PathVariable UUID taskId) {
        if (indexApplicationService == null) {
            throw new IllegalStateException("正式索引服务尚未装配");
        }
        java.util.Optional<IndexTask> claimed = indexApplicationService.claim(taskId);
        if (!claimed.isPresent()) {
            return taskResponse(applicationService.findIndexTask(taskId));
        }
        IndexTask task = claimed.get();
        indexExecutor.execute(() -> indexApplicationService.indexClaimed(task, "fund-gateway-contracts",
                new EmbeddingDescriptor("local", "BAAI/bge-small-zh-v1.5", 512, true), 50));
        return new IndexTaskResponse(task.taskId(), task.documentId(), task.version(), "ACCEPTED",
                null, task.updatedAt().toString());
    }

    /** 查询指定索引任务的实时状态和失败信息。 */
    @GetMapping("/index-tasks/{taskId}")
    public IndexTaskResponse findIndexTask(@PathVariable UUID taskId) {
        return taskResponse(applicationService.findIndexTask(taskId));
    }

    /** 映射索引任务响应。 */
    private IndexTaskResponse taskResponse(IndexTask task) {
        return new IndexTaskResponse(task.taskId(), task.documentId(), task.version(), task.status().name(),
                task.errorMessage(), task.updatedAt().toString());
    }

    /** 校验上传参数和当前支持的五种文档格式。 */
    private void validate(MultipartFile file, String documentId, String version) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        DocumentFormat.from(filename);
        safeSegment(documentId);
        safeSegment(version);
    }

    /** 提取已经通过格式白名单校验的扩展名。 */
    private String extension(String filename) {
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
    }

    /** 把业务标识转换为安全的目录片段，阻止路径穿越。 */
    private String safeSegment(String value) {
        if (value == null || value.isBlank() || !value.matches("[\\p{L}\\p{N}._-]{1,80}")) {
            throw new IllegalArgumentException("文档标识和版本只能包含文字、数字、点、下划线或短横线");
        }
        return value;
    }

    /** 将领域记录映射为控制台上传响应。 */
    private DocumentUploadResponse response(DocumentVersionRecord record) {
        List<ChunkPreview> previews = record.chunks().stream().limit(PREVIEW_LIMIT).map(this::preview).toList();
        long paragraphCount = record.elements().stream()
                .filter(element -> element.type() == DocumentElement.ElementType.PARAGRAPH).count();
        long tableRowCount = record.elements().stream()
                .filter(element -> element.type() == DocumentElement.ElementType.TABLE_ROW).count();
        return new DocumentUploadResponse(record.source().documentId(), record.source().version(),
                format(record), record.source().fileSha256(), record.status().name(), record.elements().size(),
                paragraphCount, tableRowCount, record.chunks().size(), previews);
    }

    /** 将一个知识分片映射为页面预览结构。 */
    private ChunkPreview preview(KnowledgeChunk chunk) {
        return new ChunkPreview(chunk.chunkId(), chunk.sectionPath(), chunk.text(),
                chunk.firstSequence(), chunk.lastSequence(), chunk.tableIndex(), chunk.rowIndex(), chunk.locator());
    }

    /** 将文档领域记录映射为列表摘要。 */
    private DocumentSummary summary(DocumentVersionRecord record) {
        return new DocumentSummary(record.source().documentId(), record.source().version(),
                format(record), record.source().fileSha256(), record.status().name(), record.elements().size(),
                record.chunks().size());
    }

    /** 从原始文件名提取文档格式，确保列表和详情口径一致。 */
    private String format(DocumentVersionRecord record) {
        return DocumentFormat.from(record.source().file().getFileName().toString()).name();
    }

    /** 文档上传和解析结果。 */
    public record DocumentUploadResponse(String documentId, String version, String format, String sha256, String status,
                                         int elementCount, long paragraphCount, long tableRowCount,
                                         int chunkCount, List<ChunkPreview> previews) {
    }

    /** 页面展示的一条分片预览。 */
    public record ChunkPreview(String chunkId, String sectionPath, String text, int firstSequence,
                               int lastSequence, int tableIndex, int rowIndex, String locator) {
    }

    /** 文档版本列表摘要。 */
    public record DocumentSummary(String documentId, String version, String format, String sha256, String status,
                                  int elementCount, int chunkCount) {
    }

    /** 索引任务创建结果。 */
    public record IndexTaskResponse(UUID taskId, String documentId, String version, String status,
                                    String errorMessage, String updatedAt) {
    }
}
