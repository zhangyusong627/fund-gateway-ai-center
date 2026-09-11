package org.practice.fundgateway.console;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jakarta.annotation.PreDestroy;
import org.practice.fundgateway.console.ConsoleModels.RagCandidate;
import org.practice.fundgateway.console.ConsoleModels.RagQueryRequest;
import org.practice.fundgateway.console.ConsoleModels.RagQueryResponse;
import org.practice.fundgateway.console.ConsoleModels.PublishedCollection;
import org.practice.fundgateway.console.ConsoleModels.RagEvaluationCase;
import org.practice.fundgateway.console.ConsoleModels.RagEvaluationResponse;
import org.practice.fundgateway.knowledge.embedding.LocalBgeEmbeddingModel;
import org.practice.fundgateway.knowledge.search.EvidenceAcceptanceGate;
import org.practice.fundgateway.knowledge.search.HybridPgvectorRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 为可视化控制台提供真实 BGE 向量和 pgvector 混合检索。 */
@Service
public class ConsoleRagService {

    private final JdbcTemplate jdbcTemplate;
    private final Path modelPath;
    private volatile LocalBgeEmbeddingModel embeddingModel;

    /** 使用环境配置创建数据库连接和模型路径。 */
    public ConsoleRagService(JdbcTemplate jdbcTemplate,
                             @Value("${console.embedding.model-path}") String configuredModelPath) {
        this.jdbcTemplate = jdbcTemplate;
        this.modelPath = resolveModelPath(configuredModelPath);
    }

    /** 执行一次真实向量检索并返回各阶段可解释分数。 */
    public RagQueryResponse query(RagQueryRequest request) throws Exception {
        validate(request);
        long startedAt = System.nanoTime();
        String collectionName = resolveCollection(request.collectionName());
        int topK = request.topK() == null ? 3 : request.topK();
        Set<String> keywords = new TreeSet<>(request.keywords());
        float[] queryVector = model().embed(request.question());
        List<HybridPgvectorRetriever.HybridRetrievedChunk> candidates =
                new HybridPgvectorRetriever(jdbcTemplate).search(collectionName, queryVector, keywords, topK);
        EvidenceAcceptanceGate.AcceptanceResult acceptance =
                new EvidenceAcceptanceGate().evaluate(candidates, keywords);
        List<RagCandidate> mapped = java.util.stream.IntStream.range(0, candidates.size())
                .mapToObj(index -> {
                    HybridPgvectorRetriever.HybridRetrievedChunk candidate = candidates.get(index);
                    var chunk = candidate.chunk();
                    return new RagCandidate(index + 1, chunk.chunkId(), chunk.content(), chunk.score(),
                            candidate.keywordScore(), candidate.finalScore(), chunk.documentId(),
                            chunk.documentVersion(), chunk.locator());
                }).toList();
        int indexedChunks = jdbcTemplate.queryForObject(
                "select count(*) from knowledge.knowledge_chunks where collection_name=?", Integer.class, collectionName);
        return new RagQueryResponse(acceptance.accepted() ? "ACCEPTED" : "INSUFFICIENT_EVIDENCE",
                collectionName, request.question(), topK, Duration.ofNanos(System.nanoTime() - startedAt).toMillis(),
                indexedChunks, acceptance.missingKeywords(), mapped);
    }

    /** 查询全部已发布集合，页面只能从这些不可见半成品之外的集合中选择。 */
    public List<PublishedCollection> publishedCollections() {
        return jdbcTemplate.query("select r.collection_name, min(c.document_id) document_id, "
                        + "min(c.document_version) document_version, count(c.chunk_id) chunk_count, "
                        + "r.embedding_model, r.embedding_dimension from knowledge.rag_collections r "
                        + "join knowledge.knowledge_chunks c on c.collection_name=r.collection_name "
                        + "where r.status='PUBLISHED' group by r.collection_name, r.embedding_model, "
                        + "r.embedding_dimension order by r.updated_at desc",
                (resultSet, rowNumber) -> new PublishedCollection(resultSet.getString("collection_name"),
                        resultSet.getString("document_id"), resultSet.getString("document_version"),
                        resultSet.getInt("chunk_count"), resultSet.getString("embedding_model"),
                        resultSet.getInt("embedding_dimension")));
    }

    /** 执行固定三题评测，验证字段、条件和证据不足三类行为。 */
    public RagEvaluationResponse evaluate() throws Exception {
        List<EvaluationCase> definitions = List.of(
                new EvaluationCase("授信申请金额字段是什么类型，是否必填？", "applyAmt", false),
                new EvaluationCase("公共请求参数 requestNo 是什么？", "requestNo", false),
                new EvaluationCase("不存在的火星字段有什么含义？", "火星字段", true));
        List<RagEvaluationCase> results = new java.util.ArrayList<>();
        int hitCount = 0;
        int reciprocalRankSum = 0;
        int citationHits = 0;
        int refusalCorrect = 0;
        for (EvaluationCase definition : definitions) {
            RagQueryResponse response = query(new RagQueryRequest(null, definition.question(),
                    List.of(definition.expectedKeyword()), 3));
            int rank = 0;
            for (RagCandidate candidate : response.candidates()) {
                if (candidate.content().contains(definition.expectedKeyword())) {
                    rank = candidate.rank();
                    break;
                }
            }
            boolean hit = rank > 0;
            boolean refusalExpected = definition.refusalExpected();
            boolean refusalActual = "INSUFFICIENT_EVIDENCE".equals(response.status());
            if (hit) {
                hitCount++;
                reciprocalRankSum += 1_000 / rank;
                citationHits++;
            }
            if (refusalExpected == refusalActual) {
                refusalCorrect++;
            }
            results.add(new RagEvaluationCase(definition.question(), definition.expectedKeyword(),
                    1, hit, rank, response.status()));
        }
        int count = definitions.size();
        return new RagEvaluationResponse(count, (double) hitCount / count,
                (double) reciprocalRankSum / (1000 * count),
                (double) citationHits / count, (double) refusalCorrect / count, results);
    }

    /** 检查数据库和本地模型是否已具备运行条件。 */
    public boolean ready() {
        try {
            return Files.isRegularFile(modelPath.resolve("model.onnx"))
                    && !publishedCollections().isEmpty();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** 返回当前可检索的已发布集合数量，依赖未初始化时返回零。 */
    public int publishedCollectionCount() {
        try {
            return publishedCollections().size();
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    /** 关闭常驻的本地 Embedding 模型。 */
    @PreDestroy
    public void close() {
        LocalBgeEmbeddingModel current = embeddingModel;
        if (current != null) {
            current.close();
        }
    }

    /** 解析页面指定集合；未指定时选择最近发布的集合。 */
    private String resolveCollection(String requestedCollection) {
        List<PublishedCollection> collections = publishedCollections();
        if (collections.isEmpty()) {
            throw new IllegalStateException("当前没有已发布的知识集合，请先完成离线索引");
        }
        if (requestedCollection == null || requestedCollection.isBlank()) {
            return collections.getFirst().collectionName();
        }
        return collections.stream().filter(item -> item.collectionName().equals(requestedCollection))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "知识集合不存在或尚未发布：" + requestedCollection)).collectionName();
    }

    /** 固定问题集定义。 */
    private record EvaluationCase(String question, String expectedKeyword, boolean refusalExpected) {
    }

    /** 延迟加载模型，避免应用启动阶段占用大量内存。 */
    private LocalBgeEmbeddingModel model() throws IOException {
        LocalBgeEmbeddingModel current = embeddingModel;
        if (current == null) {
            synchronized (this) {
                current = embeddingModel;
                if (current == null) {
                    current = new LocalBgeEmbeddingModel(modelPath);
                    embeddingModel = current;
                }
            }
        }
        return current;
    }

    /** 校验查询问题、关键词和 Top-K。 */
    private void validate(RagQueryRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("检索问题不能为空");
        }
        if (request.keywords() == null || request.keywords().stream().noneMatch(value -> value != null && !value.isBlank())) {
            throw new IllegalArgumentException("至少提供一个证据关键词");
        }
        if (request.topK() != null && (request.topK() < 1 || request.topK() > 10)) {
            throw new IllegalArgumentException("Top-K 必须在 1 到 10 之间");
        }
    }

    /** 兼容从仓库根目录、模块目录和 Docker 镜像启动。 */
    private Path resolveModelPath(String configuredPath) {
        Path configured = Path.of(configuredPath);
        if (Files.isRegularFile(configured.resolve("model.onnx"))) {
            return configured;
        }
        Path parentRelative = Path.of("..", configuredPath);
        return Files.isRegularFile(parentRelative.resolve("model.onnx")) ? parentRelative : configured;
    }
}
