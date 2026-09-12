package org.practice.fundgateway.knowledge.document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import org.practice.fundgateway.knowledge.embedding.EmbeddingDescriptor;
import org.practice.fundgateway.knowledge.embedding.EmbeddingGenerator;
import org.practice.fundgateway.knowledge.persistence.KnowledgeVectorStore;

/** 编排已解析文档的向量生成、批量写入和最终状态发布。 */
public class DocumentIndexApplicationService {

    private final DocumentVersionRepository documentRepository;
    private final IndexTaskRepository taskRepository;
    private final EmbeddingGenerator embeddingGenerator;
    private final KnowledgeVectorStore vectorStore;

    /** 注入文档仓储、任务仓储、Embedding 和向量存储端口。 */
    public DocumentIndexApplicationService(DocumentVersionRepository documentRepository,
                                           IndexTaskRepository taskRepository,
                                           EmbeddingGenerator embeddingGenerator,
                                           KnowledgeVectorStore vectorStore) {
        this.documentRepository = documentRepository;
        this.taskRepository = taskRepository;
        this.embeddingGenerator = embeddingGenerator;
        this.vectorStore = vectorStore;
    }

    /** 生成全部向量后再写入，Embedding 失败时不会写入半成品。 */
    public IndexTask index(UUID taskId, String collectionName, EmbeddingDescriptor descriptor) {
        return index(taskId, collectionName, descriptor, 50);
    }

    /** 按指定批大小生成并写入向量，批内生成失败时不写入该批。 */
    public IndexTask index(UUID taskId, String collectionName, EmbeddingDescriptor descriptor, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("索引批大小必须大于零");
        }
        IndexTask task = claim(taskId).orElseThrow(() -> new IllegalStateException("索引任务已被执行或不可重复执行"));
        return indexClaimed(task, collectionName, descriptor, batchSize);
    }

    /** 原子抢占索引任务，供控制器在启动异步线程前完成并发防重。 */
    public Optional<IndexTask> claim(UUID taskId) {
        if (!taskRepository.find(taskId).isPresent()) {
            throw new IllegalArgumentException("索引任务不存在");
        }
        return taskRepository.claim(taskId);
    }

    /** 执行已经抢占成功的任务，向量全部生成后再原子发布文档版本。 */
    public IndexTask indexClaimed(IndexTask task, String collectionName,
                                  EmbeddingDescriptor descriptor, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("索引批大小必须大于零");
        }
        try {
            DocumentVersionRecord document = documentRepository.find(task.documentId(), task.version())
                    .orElseThrow(() -> new IllegalArgumentException("文档版本不存在"));
            if (document.status() != DocumentIndexStatus.PARSED
                    || (task.status() != IndexTaskStatus.PARSING && task.status() != IndexTaskStatus.INDEXING)) {
                throw new IllegalStateException("只有已解析文档和已抢占任务可以索引");
            }
            if (task.status() == IndexTaskStatus.PARSING) {
                task = transition(task, IndexTaskStatus.PARSED, null);
                task = transition(task, IndexTaskStatus.INDEXING, null);
            }
            List<float[]> allVectors = new ArrayList<>(document.chunks().size());
            for (int start = 0; start < document.chunks().size(); start += batchSize) {
                int end = Math.min(start + batchSize, document.chunks().size());
                for (int index = start; index < end; index++) {
                    allVectors.add(embeddingGenerator.embed(document.chunks().get(index).text()));
                }
            }
            vectorStore.publishDocument(collectionName, "资方接口文档知识集合",
                    document.chunks(), allVectors, descriptor);
            documentRepository.updateStatus(task.documentId(), task.version(), DocumentIndexStatus.INDEXED);
            return transition(task, IndexTaskStatus.INDEXED, null);
        } catch (Exception exception) {
            String failureMessage = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            try {
                documentRepository.updateStatus(task.documentId(), task.version(), DocumentIndexStatus.INDEX_FAILED);
            } finally {
                return transition(task, IndexTaskStatus.FAILED, failureMessage);
            }
        }
    }

    /** 按索引任务状态机推进状态。 */
    private IndexTask transition(IndexTask current, IndexTaskStatus target, String errorMessage) {
        boolean legal = current.status() == IndexTaskStatus.CREATED && target == IndexTaskStatus.PARSING
                || current.status() == IndexTaskStatus.PARSING
                && (target == IndexTaskStatus.PARSED || target == IndexTaskStatus.FAILED)
                || current.status() == IndexTaskStatus.PARSED && target == IndexTaskStatus.INDEXING
                || current.status() == IndexTaskStatus.INDEXING
                && (target == IndexTaskStatus.INDEXED || target == IndexTaskStatus.FAILED);
        if (!legal) {
            throw new IllegalStateException("索引任务状态不能从 " + current.status() + " 流转到 " + target);
        }
        return taskRepository.save(new IndexTask(current.taskId(), current.documentId(), current.version(), target,
                target == IndexTaskStatus.FAILED ? errorMessage : null, java.time.Instant.now()));
    }
}
