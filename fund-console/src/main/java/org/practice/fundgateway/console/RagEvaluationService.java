package org.practice.fundgateway.console;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.practice.fundgateway.console.ConsoleModels.CreateEvaluationCaseRequest;
import org.practice.fundgateway.console.ConsoleModels.CreateEvaluationSetRequest;
import org.practice.fundgateway.console.ConsoleModels.EvaluationRunSummary;
import org.practice.fundgateway.console.ConsoleModels.EvaluationSetSummary;
import org.practice.fundgateway.console.ConsoleModels.RagCandidate;
import org.practice.fundgateway.console.ConsoleModels.RagEvaluationCase;
import org.practice.fundgateway.console.ConsoleModels.RagEvaluationResponse;
import org.practice.fundgateway.console.ConsoleModels.RagQueryRequest;
import org.practice.fundgateway.console.ConsoleModels.RagQueryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** 管理与文档版本绑定的不可变 RAG 评测集和可追溯评测运行。 */
@Service
public class RagEvaluationService {

    private final JdbcTemplate jdbcTemplate;
    private final ConsoleRagService ragService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    /** 注入真实检索服务，并建立短数据库事务模板。 */
    public RagEvaluationService(JdbcTemplate jdbcTemplate, ConsoleRagService ragService,
                                ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.ragService = ragService;
        this.objectMapper = objectMapper;
        if (jdbcTemplate.getDataSource() == null) {
            throw new IllegalArgumentException("JdbcTemplate 必须配置 DataSource");
        }
        this.transactionTemplate = new TransactionTemplate(
                new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
    }

    /** 查询全部评测集及其题目数量。 */
    public List<EvaluationSetSummary> findSets() {
        return jdbcTemplate.query("select s.*, count(c.case_id) case_count "
                        + "from knowledge.rag_evaluation_sets s left join knowledge.rag_evaluation_cases c "
                        + "on c.set_id=s.set_id group by s.set_id order by s.created_at desc",
                (resultSet, rowNumber) -> new EvaluationSetSummary(
                        resultSet.getObject("set_id", UUID.class), resultSet.getString("name"),
                        resultSet.getString("collection_name"), resultSet.getString("document_id"),
                        resultSet.getString("document_version"), resultSet.getInt("top_k"),
                        resultSet.getInt("case_count"), resultSet.getTimestamp("created_at").toInstant()));
    }

    /** 创建评测集新版本；创建后题目不可静默修改。 */
    public EvaluationSetSummary createSet(CreateEvaluationSetRequest request) {
        validateCreateRequest(request);
        int documentExists = count("select count(*) from knowledge.knowledge_chunks c "
                        + "join knowledge.rag_collections r on r.collection_name=c.collection_name "
                        + "where r.status='PUBLISHED' and c.collection_name=? and c.document_id=? "
                        + "and c.document_version=?",
                request.collectionName(), request.documentId(), request.documentVersion());
        if (documentExists == 0) {
            throw new IllegalArgumentException("评测集只能绑定已发布的文档版本");
        }
        UUID setId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update("insert into knowledge.rag_evaluation_sets "
                            + "(set_id,name,collection_name,document_id,document_version,top_k,created_at) "
                            + "values (?,?,?,?,?,?,?)", setId, request.name().trim(), request.collectionName(),
                    request.documentId(), request.documentVersion(), request.topK(), Timestamp.from(createdAt));
            for (int index = 0; index < request.cases().size(); index++) {
                CreateEvaluationCaseRequest item = request.cases().get(index);
                jdbcTemplate.update("insert into knowledge.rag_evaluation_cases "
                                + "(case_id,set_id,question,expected_keyword,expected_chunk_id,expected_locator,"
                                + "refusal_expected,case_order) values (?,?,?,?,?,?,?,?)",
                        UUID.randomUUID(), setId, item.question().trim(), trimToNull(item.expectedKeyword()),
                        trimToNull(item.expectedChunkId()), trimToNull(item.expectedLocator()),
                        Boolean.TRUE.equals(item.refusalExpected()), index + 1);
            }
        });
        return new EvaluationSetSummary(setId, request.name().trim(), request.collectionName(),
                request.documentId(), request.documentVersion(), request.topK(), request.cases().size(), createdAt);
    }

    /** 对指定评测集执行真实检索，并持久化汇总和逐题证据。 */
    public RagEvaluationResponse run(UUID setId) throws Exception {
        EvaluationSet set = loadSet(setId);
        List<EvaluationCaseDefinition> definitions = loadCases(setId);
        if (definitions.isEmpty()) {
            throw new IllegalStateException("评测集没有题目");
        }
        UUID runId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        jdbcTemplate.update("insert into knowledge.rag_evaluation_runs "
                        + "(run_id,set_id,status,started_at) values (?,?,'RUNNING',?)",
                runId, setId, Timestamp.from(startedAt));
        try {
            List<RagEvaluationCase> results = new ArrayList<>();
            int positiveCount = 0;
            int hitCount = 0;
            double reciprocalRank = 0D;
            int citationMatches = 0;
            int refusalCount = 0;
            int correctRefusals = 0;
            for (EvaluationCaseDefinition definition : definitions) {
                RagQueryResponse response = ragService.query(new RagQueryRequest(set.collectionName(),
                        set.documentId(), set.documentVersion(), definition.question(),
                        List.of(definition.expectedKeyword()), set.topK()));
                int rank = expectedRank(definition, response.candidates());
                boolean refusalActual = "INSUFFICIENT_EVIDENCE".equals(response.status());
                boolean hit = !definition.refusalExpected() && rank > 0;
                boolean citationMatched = hit;
                if (definition.refusalExpected()) {
                    refusalCount++;
                    if (refusalActual) {
                        correctRefusals++;
                    }
                } else {
                    positiveCount++;
                    if (hit) {
                        hitCount++;
                        reciprocalRank += 1D / rank;
                        citationMatches++;
                    }
                }
                RagEvaluationCase result = new RagEvaluationCase(definition.caseId(), definition.question(),
                        definition.expectedKeyword(), definition.expectedChunkId(), definition.expectedLocator(),
                        definition.refusalExpected(), hit, rank, citationMatched, response.status());
                results.add(result);
                jdbcTemplate.update("insert into knowledge.rag_evaluation_results "
                                + "(run_id,case_id,hit,actual_rank,citation_matched,retrieval_status,candidates_json) "
                                + "values (?,?,?,?,?,?,?::jsonb)", runId, definition.caseId(), hit, rank,
                        citationMatched, response.status(), objectMapper.writeValueAsString(response.candidates()));
            }
            double recall = ratio(hitCount, positiveCount);
            double mrr = positiveCount == 0 ? 0D : reciprocalRank / positiveCount;
            double citationAccuracy = ratio(citationMatches, positiveCount);
            double refusalAccuracy = ratio(correctRefusals, refusalCount);
            Instant completedAt = Instant.now();
            jdbcTemplate.update("update knowledge.rag_evaluation_runs set status='COMPLETED', "
                            + "recall_at_k=?,mean_reciprocal_rank=?,citation_accuracy=?,refusal_accuracy=?,"
                            + "completed_at=? where run_id=?", recall, mrr, citationAccuracy, refusalAccuracy,
                    Timestamp.from(completedAt), runId);
            return new RagEvaluationResponse(runId, setId, set.name(), set.topK(), definitions.size(),
                    recall, mrr, citationAccuracy, refusalAccuracy, results);
        } catch (Exception exception) {
            jdbcTemplate.update("update knowledge.rag_evaluation_runs set status='FAILED',error_message=?,"
                            + "completed_at=? where run_id=?", safeMessage(exception), Timestamp.from(Instant.now()), runId);
            throw exception;
        }
    }

    /** 查询最近五十次评测运行，支持按评测集过滤。 */
    public List<EvaluationRunSummary> findRuns(UUID setId) {
        String base = "select r.*,s.name,s.top_k from knowledge.rag_evaluation_runs r "
                + "join knowledge.rag_evaluation_sets s on s.set_id=r.set_id ";
        String sql = setId == null ? base + "order by r.started_at desc limit 50"
                : base + "where r.set_id=? order by r.started_at desc limit 50";
        Object[] arguments = setId == null ? new Object[0] : new Object[] {setId};
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> new EvaluationRunSummary(
                resultSet.getObject("run_id", UUID.class), resultSet.getObject("set_id", UUID.class),
                resultSet.getString("name"), resultSet.getString("status"), resultSet.getInt("top_k"),
                nullableDouble(resultSet, "recall_at_k"), nullableDouble(resultSet, "mean_reciprocal_rank"),
                nullableDouble(resultSet, "citation_accuracy"), nullableDouble(resultSet, "refusal_accuracy"),
                resultSet.getTimestamp("started_at").toInstant(),
                resultSet.getTimestamp("completed_at") == null ? null
                        : resultSet.getTimestamp("completed_at").toInstant()), arguments);
    }

    /** 校验评测集和题目均具备可复算的标准答案。 */
    private void validateCreateRequest(CreateEvaluationSetRequest request) {
        if (request == null || blank(request.name()) || blank(request.collectionName())
                || blank(request.documentId()) || blank(request.documentVersion())) {
            throw new IllegalArgumentException("评测集名称、集合和文档版本不能为空");
        }
        if (request.topK() == null || request.topK() < 1 || request.topK() > 50) {
            throw new IllegalArgumentException("Top-K 必须在 1 到 50 之间");
        }
        if (request.cases() == null || request.cases().isEmpty()) {
            throw new IllegalArgumentException("评测集至少包含一道题");
        }
        for (CreateEvaluationCaseRequest item : request.cases()) {
            if (item == null || blank(item.question()) || blank(item.expectedKeyword())) {
                throw new IllegalArgumentException("每道题必须填写问题和证据关键词");
            }
            if (!Boolean.TRUE.equals(item.refusalExpected())
                    && blank(item.expectedChunkId()) && blank(item.expectedLocator())) {
                throw new IllegalArgumentException("非拒答题必须填写标准 chunkId 或标准 locator");
            }
        }
    }

    /** 读取一个评测集的固定运行配置。 */
    private EvaluationSet loadSet(UUID setId) {
        return jdbcTemplate.query("select * from knowledge.rag_evaluation_sets where set_id=?",
                (resultSet, rowNumber) -> new EvaluationSet(resultSet.getObject("set_id", UUID.class),
                        resultSet.getString("name"), resultSet.getString("collection_name"),
                        resultSet.getString("document_id"), resultSet.getString("document_version"),
                        resultSet.getInt("top_k")), setId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("评测集不存在：" + setId));
    }

    /** 按稳定顺序读取评测题定义。 */
    private List<EvaluationCaseDefinition> loadCases(UUID setId) {
        return jdbcTemplate.query("select * from knowledge.rag_evaluation_cases where set_id=? order by case_order",
                (resultSet, rowNumber) -> new EvaluationCaseDefinition(
                        resultSet.getObject("case_id", UUID.class), resultSet.getString("question"),
                        resultSet.getString("expected_keyword"), resultSet.getString("expected_chunk_id"),
                        resultSet.getString("expected_locator"), resultSet.getBoolean("refusal_expected")), setId);
    }

    /** 在候选列表中查找标准证据首次出现的排名。 */
    private int expectedRank(EvaluationCaseDefinition definition, List<RagCandidate> candidates) {
        for (RagCandidate candidate : candidates) {
            boolean chunkMatches = definition.expectedChunkId() != null
                    && definition.expectedChunkId().equals(candidate.chunkId());
            boolean locatorMatches = definition.expectedLocator() != null
                    && definition.expectedLocator().equals(candidate.locator());
            if (chunkMatches || locatorMatches) {
                return candidate.rank();
            }
        }
        return 0;
    }

    /** 执行带参数的计数查询。 */
    private int count(String sql, Object... arguments) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, arguments);
        return value == null ? 0 : value;
    }

    /** 避免零分母，并保持指标含义明确。 */
    private double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0D : (double) numerator / denominator;
    }

    /** 读取可能为空的数值指标。 */
    private Double nullableDouble(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    /** 将空白字符串统一转换为空值。 */
    private String trimToNull(String value) {
        return blank(value) ? null : value.trim();
    }

    /** 判断业务字符串是否为空白。 */
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** 生成可持久化的有限长度错误信息。 */
    private String safeMessage(Exception exception) {
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }

    /** 内部使用的评测集固定配置。 */
    private record EvaluationSet(UUID setId, String name, String collectionName,
                                 String documentId, String documentVersion, int topK) {
    }

    /** 内部使用的评测题标准答案。 */
    private record EvaluationCaseDefinition(UUID caseId, String question, String expectedKeyword,
                                            String expectedChunkId, String expectedLocator,
                                            boolean refusalExpected) {
    }
}
