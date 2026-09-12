package org.practice.fundgateway.console;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;
import org.practice.fundgateway.guardian.diagnosis.RuleFinding;
import org.practice.fundgateway.guardian.metrics.MetricWindowAggregate;
import org.practice.fundgateway.guardian.metrics.RiskRuleHit;
import org.practice.fundgateway.guardian.workflow.DiagnosticTaskView;

/** 集中定义演示控制台的请求和响应结构。 */
public final class ConsoleModels {

    /** 禁止实例化只承载数据结构的工具类。 */
    private ConsoleModels() {
    }

    /** 控制台运行状态。 */
    public record ConsoleStatus(String status, String ragEngine, String embeddingModel,
                                int embeddingDimension, String guardianMode, boolean deepSeekAvailable,
                                String persistenceMode, int publishedCollections) {
    }

    /** 运行总览的真实业务计数和依赖状态。 */
    public record ConsoleOverview(int documentVersions, int indexedDocumentVersions, int indexedChunks,
                                  int diagnosticTasksToday, int pendingApprovals, int modelCallsToday,
                                  DependencyStatus postgres, DependencyStatus redpanda,
                                  DependencyStatus embedding, DependencyStatus deepSeek) {
    }

    /** 单项运行依赖的探测结果。 */
    public record DependencyStatus(boolean available, String label) {
    }

    /** RAG 查询参数。 */
    public record RagQueryRequest(String collectionName, String documentId, String documentVersion,
                                  String question, List<String> keywords, Integer topK) {
    }

    /** RAG 查询结果。 */
    public record RagQueryResponse(String status, String collectionName, String question, int topK, long durationMs,
                                   int indexedChunks, Set<String> missingKeywords,
                                   List<RagCandidate> candidates) {
    }

    /** 在线检索审计记录，保存一次查询的输入、门禁和候选证据。 */
    public record RagQueryAudit(java.util.UUID queryId, String collectionName, String documentId,
                                String documentVersion, String question, List<String> keywords, int topK,
                                String status, int indexedChunks, long durationMs, Set<String> missingKeywords,
                                List<RagCandidate> candidates, java.time.Instant queriedAt) {
    }

    /** 可供在线检索选择的已发布知识集合。 */
    public record PublishedCollection(String collectionName, int documentCount, int chunkCount,
                                      String embeddingModel, int embeddingDimension,
                                      List<PublishedDocument> documents) {
    }

    /** 已发布集合内可供限定检索范围的文档版本。 */
    public record PublishedDocument(String documentId, String documentVersion, int chunkCount) {
    }

    /** 一次可追溯评测运行的汇总指标。 */
    public record RagEvaluationResponse(java.util.UUID runId, java.util.UUID setId, String setName,
                                        int topK, int caseCount, double recallAtK, double meanReciprocalRank,
                                        double citationAccuracy, double refusalAccuracy,
                                        List<RagEvaluationCase> cases) {
    }

    /** 一道评测题的标准证据和真实召回结果。 */
    public record RagEvaluationCase(java.util.UUID caseId, String question, String expectedKeyword,
                                    String expectedChunkId, String expectedLocator, boolean refusalExpected,
                                    boolean hit, int actualRank, boolean citationMatched, String status) {
    }

    /** 创建不可变评测集的请求。 */
    public record CreateEvaluationSetRequest(String name, String collectionName, String documentId,
                                             String documentVersion, Integer topK,
                                             List<CreateEvaluationCaseRequest> cases) {
    }

    /** 创建一道带标准证据的评测题。 */
    public record CreateEvaluationCaseRequest(String question, String expectedKeyword,
                                              String expectedChunkId, String expectedLocator,
                                              Boolean refusalExpected) {
    }

    /** 可供页面选择的评测集摘要。 */
    public record EvaluationSetSummary(java.util.UUID setId, String name, String collectionName,
                                       String documentId, String documentVersion, int topK,
                                       int caseCount, Instant createdAt) {
    }

    /** 历史评测运行摘要。 */
    public record EvaluationRunSummary(java.util.UUID runId, java.util.UUID setId, String setName,
                                       String status, int topK, Double recallAtK,
                                       Double meanReciprocalRank, Double citationAccuracy,
                                       Double refusalAccuracy, Instant startedAt, Instant completedAt) {
    }

    /** 一条带来源和分数的 RAG 候选证据。 */
    public record RagCandidate(int rank, String chunkId, String content, double vectorScore,
                               double keywordScore, double finalScore, String documentId,
                               String documentVersion, String locator) {
    }

    /** 智能守护回放参数。 */
    public record GuardianSimulationRequest(String scenario, Integer messageCount, Boolean invokeModel) {
    }

    /** 智能守护回放结果。 */
    public record GuardianSimulationResponse(String scenario, int metricMessages, int aggregateWindows,
                                             int riskWindows, int diagnosticTasks, int suppressedTasks,
                                             int actualModelCalls, long durationMs,
                                             MetricWindowAggregate representativeWindow,
                                             List<RiskRuleHit> ruleHits, List<RuleFinding> deterministicFindings,
                                             String riskFingerprint, String modelStatus,
                                             String modelRequest, String modelPrompt, String modelRawResponse,
                                             ModelDiagnosisReport modelReport,
                                             ModelDiagnosisGate.GateDecision gateDecision,
                                             DiagnosticTaskView diagnosticTask,
                                             Instant executedAt) {
    }
}
