package org.practice.fundgateway.console;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import jakarta.annotation.PreDestroy;
import org.practice.fundgateway.console.ConsoleModels.RagCandidate;
import org.practice.fundgateway.console.ConsoleModels.RagQueryRequest;
import org.practice.fundgateway.console.ConsoleModels.RagQueryResponse;
import org.practice.fundgateway.console.ConsoleModels.PublishedCollection;
import org.practice.fundgateway.console.ConsoleModels.PublishedDocument;
import org.practice.fundgateway.console.ConsoleModels.KnowledgeChunkPage;
import org.practice.fundgateway.console.ConsoleModels.KnowledgeChunkPreview;
import org.practice.fundgateway.console.ConsoleModels.RagQueryAudit;
import org.practice.fundgateway.console.ConsoleModels.GuardianProvider;
import org.practice.fundgateway.common.permission.PermissionAuditRecorder;
import org.practice.fundgateway.common.permission.PermissionContext;
import org.practice.fundgateway.common.permission.PermissionGuard;
import org.practice.fundgateway.knowledge.embedding.LocalBgeEmbeddingModel;
import org.practice.fundgateway.knowledge.search.EvidenceAcceptanceGate;
import org.practice.fundgateway.knowledge.search.HybridPgvectorRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 为可视化控制台提供真实 BGE 向量和 pgvector 混合检索。 */
@Service
public class ConsoleRagService {

    private static final int MAX_EXHAUSTIVE_CANDIDATES = 100;

    private final JdbcTemplate jdbcTemplate;
    private final Path modelPath;
    private final ObjectMapper objectMapper;
    private final PermissionGuard permissionGuard;
    private final PermissionContext defaultPermissionContext;
    private volatile LocalBgeEmbeddingModel embeddingModel;

    /** 使用环境配置创建数据库连接和模型路径。 */
    public ConsoleRagService(JdbcTemplate jdbcTemplate,
                             @Value("${console.embedding.model-path}") String configuredModelPath,
                             ObjectMapper objectMapper) {
        this(jdbcTemplate, configuredModelPath, objectMapper,
                new PermissionGuard(PermissionAuditRecorder.noop()), PermissionContext.syntheticConsole());
    }

    /** 注入正式权限检查器和控制台演示上下文。 */
    @org.springframework.beans.factory.annotation.Autowired
    public ConsoleRagService(JdbcTemplate jdbcTemplate,
                             @Value("${console.embedding.model-path}") String configuredModelPath,
                             ObjectMapper objectMapper, PermissionGuard permissionGuard,
                             PermissionContext defaultPermissionContext) {
        if (permissionGuard == null || defaultPermissionContext == null) {
            throw new IllegalArgumentException("RAG 权限依赖不能为空");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.modelPath = resolveModelPath(configuredModelPath);
        this.objectMapper = objectMapper;
        this.permissionGuard = permissionGuard;
        this.defaultPermissionContext = defaultPermissionContext;
    }

    /** 执行一次真实向量检索并返回各阶段可解释分数。 */
    public RagQueryResponse query(RagQueryRequest request) throws Exception {
        return query(request, defaultPermissionContext);
    }

    /** 在真正读取向量前检查调用方的知识集合、文档和版本范围。 */
    public RagQueryResponse query(RagQueryRequest request, PermissionContext permissionContext) throws Exception {
        validate(request);
        long startedAt = System.nanoTime();
        String traceId = "rag-query-" + UUID.randomUUID();
        if (request.collectionName() != null && !request.collectionName().isBlank()) {
            permissionGuard.requireKnowledge(permissionContext, request.collectionName(),
                    request.documentId() == null ? "*" : request.documentId(),
                    request.documentVersion() == null ? "*" : request.documentVersion(),
                    "KNOWLEDGE_QUERY", traceId);
        }
        String collectionName = resolveCollection(request.collectionName(), permissionContext);
        permissionGuard.requireKnowledge(permissionContext, collectionName,
                request.documentId() == null ? "*" : request.documentId(),
                request.documentVersion() == null ? "*" : request.documentVersion(),
                "KNOWLEDGE_QUERY", traceId);
        int topK = request.topK() == null ? 3 : request.topK();
        Set<String> keywords = new TreeSet<>(request.keywords());
        float[] queryVector = model().embed(request.question());
        HybridPgvectorRetriever retriever = new HybridPgvectorRetriever(jdbcTemplate);
        boolean exhaustiveList = isExhaustiveListQuestion(request.question());
        String retrievalMode = "TOP_K";
        int matchedCandidateCount;
        boolean truncated = false;
        List<HybridPgvectorRetriever.HybridRetrievedChunk> candidates;
        if (exhaustiveList) {
            HybridPgvectorRetriever.BoundedInterfaceSearchResult bounded =
                    retriever.searchBoundedInterfaceChunks(collectionName, request.documentId(),
                            request.documentVersion(), request.providerId(), MAX_EXHAUSTIVE_CANDIDATES);
            candidates = bounded.candidates();
            matchedCandidateCount = bounded.matchedCount();
            truncated = bounded.truncated();
            retrievalMode = "EXHAUSTIVE_BOUNDED";
        } else {
            candidates = retriever.search(collectionName, request.documentId(), request.documentVersion(),
                    request.providerId(), queryVector, keywords, topK);
            matchedCandidateCount = candidates.size();
        }
        if (candidates.isEmpty() && exhaustiveList) {
            candidates = retriever.search(collectionName, request.documentId(), request.documentVersion(),
                    request.providerId(), queryVector, keywords, topK);
            exhaustiveList = false;
            retrievalMode = "TOP_K";
            matchedCandidateCount = candidates.size();
            truncated = false;
        }
        if (!exhaustiveList && isInterfaceDetailQuestion(request.question())) {
            List<HybridPgvectorRetriever.HybridRetrievedChunk> exactMatches =
                    retriever.searchKeywordInterfaceChunks(collectionName, request.documentId(),
                            request.documentVersion(), request.providerId(), keywords);
            candidates = mergeAndLimit(candidates, exactMatches, topK);
        }
        EvidenceAcceptanceGate.AcceptanceResult acceptance =
                new EvidenceAcceptanceGate().evaluate(candidates, keywords);
        List<HybridPgvectorRetriever.HybridRetrievedChunk> selectedCandidates = candidates;
        List<RagCandidate> mapped = java.util.stream.IntStream.range(0, selectedCandidates.size())
                .mapToObj(index -> {
                    HybridPgvectorRetriever.HybridRetrievedChunk candidate = selectedCandidates.get(index);
                    var chunk = candidate.chunk();
                    return new RagCandidate(index + 1, chunk.chunkId(), chunk.content(), chunk.score(),
                            candidate.keywordScore(), candidate.finalScore(), chunk.documentId(),
                            chunk.documentVersion(), chunk.locator());
                }).toList();
        int indexedChunks = countIndexedChunks(collectionName, request.documentId(), request.documentVersion(),
                request.providerId());
        String status = truncated || !acceptance.accepted() ? "INSUFFICIENT_EVIDENCE" : "ACCEPTED";
        RagQueryResponse response = new RagQueryResponse(status,
                collectionName, request.question(), topK, mapped.size(), retrievalMode, matchedCandidateCount, truncated,
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis(),
                indexedChunks, acceptance.missingKeywords(), mapped);
        saveAudit(request, response, keywords);
        return response;
    }

    /** 识别需要文档级穷举的列表问题，避免把局部 Top-K 当成完整清单。 */
    private boolean isExhaustiveListQuestion(String question) {
        String normalized = question == null ? "" : question.replaceAll("\\s+", "");
        return normalized.matches(".*(包含哪些|有哪些|列出.*(接口|交易码)|完整.*(接口|交易码)|全部.*(接口|交易码)|接口.*列表|交易码.*列表).* ".trim());
    }

    /** 识别具体接口细节问题，触发关键词精确补召回。 */
    private boolean isInterfaceDetailQuestion(String question) {
        String normalized = question == null ? "" : question.replaceAll("\\s+", "");
        return normalized.contains("接口") && !normalized.matches(".*(包含哪些|有哪些|列出.*(接口|交易码)|完整.*(接口|交易码)|全部.*(接口|交易码)|接口.*列表|交易码.*列表).*");
    }

    /** 将精确关键词命中与向量候选去重合并，仍保持请求 Top-K 上限。 */
    private List<HybridPgvectorRetriever.HybridRetrievedChunk> mergeAndLimit(
            List<HybridPgvectorRetriever.HybridRetrievedChunk> ranked,
            List<HybridPgvectorRetriever.HybridRetrievedChunk> exactMatches,
            int topK) {
        Map<String, HybridPgvectorRetriever.HybridRetrievedChunk> merged = new LinkedHashMap<>();
        ranked.forEach(item -> merged.put(item.chunk().chunkId(), item));
        exactMatches.forEach(item -> merged.put(item.chunk().chunkId(), item));
        return merged.values().stream()
                .sorted((left, right) -> Double.compare(right.finalScore(), left.finalScore()))
                .limit(topK)
                .toList();
    }

    /** 将在线检索输入、结果和证据写入审计表；审计失败不改变检索结论。 */
    private void saveAudit(RagQueryRequest request, RagQueryResponse response, Set<String> keywords) {
        try {
            jdbcTemplate.update("insert into knowledge.rag_query_audits "
                            + "(query_id,collection_name,document_id,document_version,question,keywords,top_k,status,"
                            + "actual_candidate_count,retrieval_mode,matched_candidate_count,truncated,indexed_chunks,duration_ms,missing_keywords,candidates_json,queried_at) "
                            + "values (?,?,?,?,?,CAST(? AS jsonb),?,?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),now())", UUID.randomUUID(),
                    response.collectionName(), request.documentId(), request.documentVersion(), request.question(),
                    objectMapper.writeValueAsString(keywords), auditTopK(request), response.status(), response.actualCandidateCount(),
                    response.retrievalMode(), response.matchedCandidateCount(), response.truncated(),
                    response.indexedChunks(),
                    response.durationMs(), objectMapper.writeValueAsString(response.missingKeywords()),
                    objectMapper.writeValueAsString(response.candidates()));
        } catch (Exception exception) {
            System.err.println("在线检索审计写入失败：" + exception);
            exception.printStackTrace(System.err);
        }
    }

    /**
     * 审计表的 top_k 表示用户请求的 Top-K（1~50）；完整列表模式的实际候选数单独记录。
     */
    private int auditTopK(RagQueryRequest request) {
        int requested = request.topK() == null ? 3 : request.topK();
        return Math.max(1, Math.min(50, requested));
    }

    /** 查询最近五十条在线检索审计记录。 */
    public List<RagQueryAudit> queryAudits() {
        return jdbcTemplate.query("select * from knowledge.rag_query_audits order by queried_at desc limit 50",
                (resultSet, rowNumber) -> {
                    try {
                        List<RagCandidate> candidates = objectMapper.readValue(resultSet.getString("candidates_json"),
                                objectMapper.getTypeFactory().constructCollectionType(List.class, RagCandidate.class));
                        List<String> keywords = objectMapper.readValue(resultSet.getString("keywords"),
                                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                        Set<String> missing = objectMapper.readValue(resultSet.getString("missing_keywords"),
                                objectMapper.getTypeFactory().constructCollectionType(java.util.TreeSet.class, String.class));
                        return new RagQueryAudit(resultSet.getObject("query_id", UUID.class),
                                resultSet.getString("collection_name"), resultSet.getString("document_id"),
                                resultSet.getString("document_version"), resultSet.getString("question"), keywords,
                                resultSet.getInt("top_k"), resultSet.getInt("actual_candidate_count"), resultSet.getString("retrieval_mode"),
                                resultSet.getInt("matched_candidate_count"), resultSet.getBoolean("truncated"), resultSet.getString("status"), resultSet.getInt("indexed_chunks"),
                                resultSet.getLong("duration_ms"), missing, candidates,
                                resultSet.getTimestamp("queried_at").toInstant());
                    } catch (Exception exception) {
                        throw new IllegalStateException("检索审计 JSON 解析失败", exception);
                    }
                });
    }

    /** 查询全部已发布集合，页面只能从这些不可见半成品之外的集合中选择。 */
    public List<PublishedCollection> publishedCollections() {
        return publishedCollections(defaultPermissionContext);
    }

    /** 返回调用方知识范围内可见的已发布集合和文档。 */
    public List<PublishedCollection> publishedCollections(PermissionContext permissionContext) {
        List<PublishedCollection> collections = jdbcTemplate.query("select r.collection_name, "
                        + "count(distinct (c.document_id, c.document_version)) document_count, "
                        + "count(c.chunk_id) chunk_count, "
                        + "r.embedding_model, r.embedding_dimension from knowledge.rag_collections r "
                        + "join knowledge.knowledge_chunks c on c.collection_name=r.collection_name "
                        + "where r.status='PUBLISHED' group by r.collection_name, r.embedding_model, "
                        + "r.embedding_dimension order by r.updated_at desc",
                (resultSet, rowNumber) -> new PublishedCollection(resultSet.getString("collection_name"),
                        resultSet.getInt("document_count"), resultSet.getInt("chunk_count"),
                        resultSet.getString("embedding_model"), resultSet.getInt("embedding_dimension"),
                        List.of()));
        return collections.stream().map(collection -> {
            List<PublishedDocument> visibleDocuments = publishedDocuments(collection.collectionName()).stream()
                    .filter(document -> permissionContext != null && permissionContext.allowsKnowledge(
                            collection.collectionName(), document.documentId(), document.documentVersion()))
                    .toList();
            return new PublishedCollection(collection.collectionName(), visibleDocuments.size(),
                    visibleDocuments.stream().mapToInt(PublishedDocument::chunkCount).sum(),
                    collection.embeddingModel(), collection.embeddingDimension(), visibleDocuments);
        }).filter(collection -> !collection.documents().isEmpty()).toList();
    }

    /** 返回当前权限范围内可用于智能守护的资方诊断上下文。 */
    public List<GuardianProvider> publishedGuardianProviders() {
        return publishedGuardianProviders(defaultPermissionContext);
    }

    /** 返回当前权限范围内可用于智能守护的资方诊断上下文。 */
    public List<GuardianProvider> publishedGuardianProviders(PermissionContext permissionContext) {
        List<String> providerIds = jdbcTemplate.query("select distinct "
                        + "case when institution in ('synthetic-source','synthetic-provider') then 'NYXJ' "
                        + "else nullif(institution,'') end as provider_id "
                        + "from knowledge.knowledge_chunks c join knowledge.rag_collections r "
                        + "on r.collection_name=c.collection_name where r.status='PUBLISHED' "
                        + "and (institution is not null and institution <> '') order by provider_id",
                (resultSet, rowNumber) -> resultSet.getString("provider_id"));
        return providerIds.stream().filter(providerId -> providerId != null && !providerId.isBlank())
                .filter(providerId -> permissionContext == null || permissionContext.allowsProvider(providerId))
                .map(providerId -> new GuardianProvider(providerId, providerId))
                .toList();
    }

    /** 分页浏览已发布文档分片，供评测人员选择标准证据。 */
    public KnowledgeChunkPage browseChunks(String collectionName, String documentId, String documentVersion,
                                           String keyword, Integer offset, Integer limit) {
        if (collectionName == null || collectionName.isBlank() || documentId == null || documentId.isBlank()
                || documentVersion == null || documentVersion.isBlank()) {
            throw new IllegalArgumentException("浏览分片必须指定集合、文档和版本");
        }
        permissionGuard.requireKnowledge(defaultPermissionContext, collectionName, documentId, documentVersion,
                "KNOWLEDGE_CHUNK_BROWSE", "rag-chunk-browser-" + UUID.randomUUID());
        int safeOffset = offset == null ? 0 : offset;
        int safeLimit = limit == null ? 20 : limit;
        if (safeOffset < 0 || safeLimit < 1 || safeLimit > 100) {
            throw new IllegalArgumentException("分片分页参数无效");
        }
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String condition = normalizedKeyword.isBlank() ? "" : " and (content ilike ? or locator ilike ? or chunk_id ilike ?)";
        List<Object> arguments = new java.util.ArrayList<>(List.of(collectionName, documentId, documentVersion));
        if (!normalizedKeyword.isBlank()) {
            String pattern = "%" + normalizedKeyword + "%";
            arguments.add(pattern);
            arguments.add(pattern);
            arguments.add(pattern);
        }
        int total = jdbcTemplate.queryForObject("select count(*) from knowledge.knowledge_chunks "
                + "where collection_name=? and document_id=? and document_version=?" + condition,
                Integer.class, arguments.toArray());
        arguments.add(safeLimit);
        arguments.add(safeOffset);
        List<KnowledgeChunkPreview> chunks = jdbcTemplate.query("select chunk_id,document_id,document_version,"
                + "coalesce(metadata->>'format','UNKNOWN') as format,locator,content "
                + "from knowledge.knowledge_chunks where collection_name=? and document_id=? and document_version=?" + condition
                + " order by locator,chunk_id limit ? offset ?", (resultSet, rowNumber) -> new KnowledgeChunkPreview(
                        resultSet.getString("chunk_id"), resultSet.getString("document_id"),
                        resultSet.getString("document_version"), resultSet.getString("format"),
                        resultSet.getString("locator"),
                        resultSet.getString("content")), arguments.toArray());
        return new KnowledgeChunkPage(total, safeOffset, safeLimit, chunks);
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

    /** 检查本地 Embedding 模型文件是否完整存在。 */
    public boolean embeddingAvailable() {
        return Files.isRegularFile(modelPath.resolve("model.onnx"));
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
    private String resolveCollection(String requestedCollection, PermissionContext permissionContext) {
        List<PublishedCollection> collections = publishedCollections(permissionContext);
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

    /** 查询集合内的已发布文档版本及各自分片数。 */
    private List<PublishedDocument> publishedDocuments(String collectionName) {
        return jdbcTemplate.query("select document_id, document_version, count(*) chunk_count "
                        + "from knowledge.knowledge_chunks where collection_name=? "
                        + "group by document_id, document_version order by document_id, document_version",
                (resultSet, rowNumber) -> new PublishedDocument(resultSet.getString("document_id"),
                        resultSet.getString("document_version"), resultSet.getInt("chunk_count")), collectionName);
    }

    /** 统计本次集合或文档范围内实际参与检索的向量分片。 */
    private int countIndexedChunks(String collectionName, String documentId, String documentVersion,
                                   String providerId) {
        boolean providerScoped = providerId != null && !providerId.isBlank();
        if (documentId == null || documentId.isBlank()) {
            return providerScoped
                    ? jdbcTemplate.queryForObject("select count(*) from knowledge.knowledge_chunks "
                    + "where collection_name=? and (institution=? or (?='NYXJ' and institution in ('synthetic-source','synthetic-provider')))",
                    Integer.class, collectionName, providerId, providerId)
                    : jdbcTemplate.queryForObject("select count(*) from knowledge.knowledge_chunks "
                    + "where collection_name=?", Integer.class, collectionName);
        }
        return providerScoped
                ? jdbcTemplate.queryForObject("select count(*) from knowledge.knowledge_chunks "
                + "where collection_name=? and document_id=? and document_version=? "
                + "and (institution=? or (?='NYXJ' and institution in ('synthetic-source','synthetic-provider')))",
                Integer.class, collectionName, documentId, documentVersion, providerId, providerId)
                : jdbcTemplate.queryForObject("select count(*) from knowledge.knowledge_chunks "
                + "where collection_name=? and document_id=? and document_version=?",
                Integer.class, collectionName, documentId, documentVersion);
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
        if (request.topK() != null && (request.topK() < 1 || request.topK() > 50)) {
            throw new IllegalArgumentException("Top-K 必须在 1 到 50 之间");
        }
        boolean hasDocumentId = request.documentId() != null && !request.documentId().isBlank();
        boolean hasDocumentVersion = request.documentVersion() != null && !request.documentVersion().isBlank();
        if (hasDocumentId != hasDocumentVersion) {
            throw new IllegalArgumentException("文档标识和版本必须同时提供");
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
