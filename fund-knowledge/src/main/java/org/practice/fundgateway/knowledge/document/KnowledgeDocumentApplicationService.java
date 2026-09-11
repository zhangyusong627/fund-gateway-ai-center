package org.practice.fundgateway.knowledge.document;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.practice.fundgateway.knowledge.chunk.DocumentChunker;
import org.practice.fundgateway.knowledge.chunk.KnowledgeChunk;

/** 编排文档登记、解析、分片预览和索引任务状态流转。 */
public class KnowledgeDocumentApplicationService {

    private final DocumentVersionRepository documentRepository;
    private final IndexTaskRepository taskRepository;
    private final DocxDocumentParser parser;
    private final DocumentChunker chunker;

    /** 使用默认 DOCX 解析器和分片器创建应用服务。 */
    public KnowledgeDocumentApplicationService(DocumentVersionRepository documentRepository,
                                               IndexTaskRepository taskRepository) {
        this(documentRepository, taskRepository, new DocxDocumentParser(), new DocumentChunker());
    }

    /** 注入解析和分片组件，方便测试替换具体实现。 */
    public KnowledgeDocumentApplicationService(DocumentVersionRepository documentRepository,
                                               IndexTaskRepository taskRepository,
                                               DocxDocumentParser parser, DocumentChunker chunker) {
        this.documentRepository = documentRepository;
        this.taskRepository = taskRepository;
        this.parser = parser;
        this.chunker = chunker;
    }

    /** 解析 DOCX 后登记文档版本，并返回可供审核的分片预览。 */
    public DocumentVersionRecord registerAndParse(Path file, String documentId, String version)
            throws java.io.IOException {
        ParsedDocument parsed = parser.parse(file, documentId, version);
        if (documentRepository.find(documentId, version).isPresent()) {
            throw new IllegalStateException("文档版本已存在，不允许覆盖：" + documentId + "@" + version);
        }
        documentRepository.findBySha256(parsed.source().fileSha256()).ifPresent(existing -> {
            throw new IllegalStateException("文档内容哈希重复：" + existing.source().documentId()
                    + "@" + existing.source().version());
        });
        List<KnowledgeChunk> chunks = chunker.chunk(parsed);
        DocumentVersionRecord record = new DocumentVersionRecord(parsed.source(), DocumentIndexStatus.PARSED,
                parsed.elements(), chunks);
        return documentRepository.save(record);
    }

    /** 登记已有哈希的文档版本，状态保持为 REGISTERED。 */
    public DocumentVersionRecord register(DocumentSource source) {
        if (documentRepository.find(source.documentId(), source.version()).isPresent()) {
            throw new IllegalStateException("文档版本已存在，不允许覆盖：" + source.documentId() + "@" + source.version());
        }
        documentRepository.findBySha256(source.fileSha256()).ifPresent(existing -> {
            throw new IllegalStateException("文档内容哈希重复：" + existing.source().documentId()
                    + "@" + existing.source().version());
        });
        return documentRepository.save(new DocumentVersionRecord(source, DocumentIndexStatus.REGISTERED,
                List.of(), List.of()));
    }

    /** 创建索引任务，任务初始状态为 CREATED。 */
    public IndexTask createIndexTask(String documentId, String version) {
        DocumentVersionRecord record = documentRepository.find(documentId, version)
                .orElseThrow(() -> new IllegalArgumentException("文档版本未登记：" + documentId + "@" + version));
        if (record.status() != DocumentIndexStatus.PARSED) {
            throw new IllegalStateException("文档尚未解析成功，不能创建索引任务");
        }
        return taskRepository.save(IndexTask.created(documentId, version));
    }

    /** 将任务推进到下一个合法状态。 */
    public IndexTask transition(UUID taskId, IndexTaskStatus target, String errorMessage) {
        IndexTask current = taskRepository.find(taskId).orElseThrow(() -> new IllegalArgumentException("索引任务不存在"));
        boolean legal = switch (current.status()) {
            case CREATED -> target == IndexTaskStatus.PARSING;
            case PARSING -> target == IndexTaskStatus.PARSED || target == IndexTaskStatus.FAILED;
            case PARSED -> target == IndexTaskStatus.INDEXING;
            case INDEXING -> target == IndexTaskStatus.INDEXED || target == IndexTaskStatus.FAILED;
            case INDEXED, FAILED -> false;
        };
        if (!legal) {
            throw new IllegalStateException("索引任务状态不能从 " + current.status() + " 流转到 " + target);
        }
        return taskRepository.save(new IndexTask(current.taskId(), current.documentId(), current.version(),
                target, target == IndexTaskStatus.FAILED ? errorMessage : null, java.time.Instant.now()));
    }

    /** 查询全部文档版本，供管理后台展示。 */
    public List<DocumentVersionRecord> findAllDocuments() {
        return documentRepository.findAll();
    }

    /** 查询一份文档版本的解析元素和分片预览。 */
    public DocumentVersionRecord findDocument(String documentId, String version) {
        return documentRepository.find(documentId, version)
                .orElseThrow(() -> new IllegalArgumentException("文档版本不存在：" + documentId + "@" + version));
    }

    /** 查询全部索引任务。 */
    public List<IndexTask> findAllIndexTasks() {
        return taskRepository.findAll();
    }

    /** 查询一个索引任务详情。 */
    public IndexTask findIndexTask(UUID taskId) {
        return taskRepository.find(taskId).orElseThrow(() -> new IllegalArgumentException("索引任务不存在：" + taskId));
    }
}
