package org.practice.fundgateway.experiments.console;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import jakarta.annotation.PreDestroy;
import org.postgresql.ds.PGSimpleDataSource;
import org.practice.fundgateway.experiments.console.ConsoleModels.RagCandidate;
import org.practice.fundgateway.experiments.console.ConsoleModels.RagQueryRequest;
import org.practice.fundgateway.experiments.console.ConsoleModels.RagQueryResponse;
import org.practice.fundgateway.knowledge.chunk.KnowledgeChunk;
import org.practice.fundgateway.knowledge.document.DocumentSource;
import org.practice.fundgateway.knowledge.embedding.EmbeddingDescriptor;
import org.practice.fundgateway.knowledge.embedding.LocalBgeEmbeddingModel;
import org.practice.fundgateway.knowledge.persistence.PgvectorKnowledgeVectorStore;
import org.practice.fundgateway.knowledge.search.EvidenceAcceptanceGate;
import org.practice.fundgateway.knowledge.search.HybridPgvectorRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 为可视化控制台提供真实 BGE 向量和 pgvector 混合检索。 */
@Service
public class ConsoleRagService {

    private static final String COLLECTION = "console_credit_application_v1";
    private static final EmbeddingDescriptor DESCRIPTOR =
            new EmbeddingDescriptor("local", "BAAI/bge-small-zh-v1.5", 512, true);
    private final JdbcTemplate jdbcTemplate;
    private final Path modelPath;
    private volatile LocalBgeEmbeddingModel embeddingModel;

    /** 使用环境配置创建数据库连接和模型路径。 */
    public ConsoleRagService(@Value("${console.jdbc.url}") String jdbcUrl,
                             @Value("${console.jdbc.user}") String jdbcUser,
                             @Value("${console.jdbc.password}") String jdbcPassword,
                             @Value("${console.embedding.model-path}") String configuredModelPath) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(jdbcUrl);
        dataSource.setUser(jdbcUser);
        dataSource.setPassword(jdbcPassword);
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.modelPath = resolveModelPath(configuredModelPath);
    }

    /** 执行一次真实向量检索并返回各阶段可解释分数。 */
    public RagQueryResponse query(RagQueryRequest request) throws Exception {
        validate(request);
        long startedAt = System.nanoTime();
        ensureSchemaAndSeed();
        int topK = request.topK() == null ? 3 : request.topK();
        Set<String> keywords = new TreeSet<>(request.keywords());
        float[] queryVector = model().embed(request.question());
        List<HybridPgvectorRetriever.HybridRetrievedChunk> candidates =
                new HybridPgvectorRetriever(jdbcTemplate).search(COLLECTION, queryVector, keywords, topK);
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
                "select count(*) from knowledge_chunks where collection_name=?", Integer.class, COLLECTION);
        return new RagQueryResponse(acceptance.accepted() ? "ACCEPTED" : "INSUFFICIENT_EVIDENCE",
                request.question(), topK, Duration.ofNanos(System.nanoTime() - startedAt).toMillis(),
                indexedChunks, acceptance.missingKeywords(), mapped);
    }

    /** 检查数据库和本地模型是否已具备运行条件。 */
    public boolean ready() {
        try {
            return Files.isRegularFile(modelPath.resolve("model.onnx"))
                    && Boolean.TRUE.equals(jdbcTemplate.queryForObject("select true", Boolean.class));
        } catch (RuntimeException exception) {
            return false;
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

    /** 幂等创建 M2 已定义的数据结构，并写入一组可解释演示分片。 */
    private synchronized void ensureSchemaAndSeed() throws Exception {
        jdbcTemplate.execute("create extension if not exists vector");
        jdbcTemplate.execute("create table if not exists rag_collections ("
                + "collection_name varchar(128) primary key, description text not null,"
                + "embedding_provider varchar(64) not null, embedding_model varchar(256) not null,"
                + "embedding_dimension integer not null, embedding_normalize boolean not null,"
                + "created_at timestamptz not null default now(), updated_at timestamptz not null default now())");
        jdbcTemplate.execute("create table if not exists knowledge_chunks ("
                + "chunk_id varchar(256) primary key, collection_name varchar(128) not null references rag_collections(collection_name),"
                + "document text not null, content text not null, embedding vector(512) not null, metadata jsonb not null,"
                + "document_id varchar(128) not null, document_version varchar(64) not null, institution varchar(128),"
                + "product_code varchar(128), operation varchar(256), content_type varchar(64), source_type varchar(64),"
                + "block_type varchar(64), locator varchar(512), embedding_provider varchar(64) not null,"
                + "embedding_model varchar(256) not null, embedding_dimension integer not null,"
                + "embedding_normalize boolean not null, created_at timestamptz not null default now(),"
                + "updated_at timestamptz not null default now())");
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from knowledge_chunks where collection_name=?", Integer.class, COLLECTION);
        if (count != null && count > 0) {
            return;
        }
        PgvectorKnowledgeVectorStore store = new PgvectorKnowledgeVectorStore(jdbcTemplate);
        store.ensureCollection(COLLECTION, "授信申请接口演示知识", DESCRIPTOR);
        DocumentSource source = new DocumentSource("credit-application-guide", "v1",
                Path.of("synthetic-credit-application.md"), "synthetic-console-v1");
        List<String> texts = List.of(
                "授信申请接口用于渠道向资金方提交客户授信申请，请求地址为 /credit/apply，HTTP 方法为 POST。",
                "字段 applyAmt 的数据类型为 BigDecimal，属于必填字段，表示授信申请金额，金额必须大于零。",
                "字段 intRate 的数据类型为 BigDecimal，属于必填字段，表示授信申请年利率，例如 23.76% 应传 0.2376。",
                "字段 term 的数据类型为 Integer，属于必填字段，表示申请期限，必须使用资金方支持的期限枚举。",
                "字段 spouseName 为条件必填字段；婚姻状态为已婚时必须填写，其他情况可以不填写。",
                "发起授信申请前必须先上传已完成电子签名的授信协议，否则返回协议文件不存在错误。",
                "响应字段 applyCode 为字符串，是资金方返回的授信申请唯一编号，可用于后续状态查询。",
                "同一业务流水号重复提交时应返回原处理结果，调用方不得因超时直接创建新的业务流水。",
                "接口超时时间为 1000 毫秒，约定 QPS 上限为 100；超过运行基线时进入智能守护诊断。",
                "当证据无法覆盖问题中的字段、条件或错误码时，系统必须返回证据不足，不能由模型补造答案。"
        );
        for (int index = 0; index < texts.size(); index++) {
            KnowledgeChunk chunk = new KnowledgeChunk("console-credit-" + (index + 1), source,
                    "授信申请", texts.get(index), 80 + index, 80 + index, index == 0 ? -1 : 1, index);
            store.save(COLLECTION, chunk, model().embed(chunk.text()), DESCRIPTOR);
        }
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
