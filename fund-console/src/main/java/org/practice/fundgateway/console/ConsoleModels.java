package org.practice.fundgateway.console;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;
import org.practice.fundgateway.guardian.diagnosis.DiagnosticEvaluationService;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate.GateStatus;
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
    public record ConsoleStatus(String status, String environment, String ragEngine, String embeddingModel,
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

    /** 面向用户的证据约束 RAG 答案请求。 */
    public record RagAnswerRequest(String collectionName, String documentId, String documentVersion,
                                   String question, List<String> keywords, Integer topK) {
    }

    /** 面向用户的结构化 RAG 答案；引用必须来自本次召回证据。 */
    public record RagAnswerResponse(String status, String question, String answer, boolean evidenceSufficient,
                                    String model, String traceId, List<RagCitation> citations,
                                    RagQueryResponse retrieval) {
    }

    /** RAG 答案引用的原文证据。 */
    public record RagCitation(String chunkId, String locator, String quote) {
    }

    /** RAG 生成答案的审计记录。 */
    public record RagAnswerAudit(java.util.UUID answerId, String traceId, String question, String status,
                                 String model, String answer, List<RagCitation> citations,
                                 java.time.Instant answeredAt) {
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

    /** 文档分片浏览结果，供评测集选择标准证据。 */
    public record KnowledgeChunkPreview(String chunkId, String documentId, String documentVersion,
                                        String format, String locator, String content) {
    }

    /** 分页返回已发布文档分片。 */
    public record KnowledgeChunkPage(int total, int offset, int limit,
                                    List<KnowledgeChunkPreview> chunks) {
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

    /** 诊断评测请求，评测数据由调用方提供，控制台不新增持久化结构。 */
    public record DiagnosticEvaluationRequest(List<DiagnosticCaseRequest> cases,
                                              List<DiagnosticObservationRequest> observations) {
    }

    /** 一道诊断评测样例及其金标准。 */
    public record DiagnosticCaseRequest(String caseId, String providerId, String interfaceId,
                                       List<String> symptoms, String expectedConclusion,
                                       List<String> requiredEvidence) {
    }

    /** 一道诊断样例的实际观测结果。 */
    public record DiagnosticObservationRequest(String caseId, String actualConclusion,
                                              List<String> evidenceIds, boolean stoppedWithoutEvidence,
                                              GateStatus gateStatus) {
    }

    /** 诊断评测接口返回的可复算指标。 */
    public record DiagnosticEvaluationResponse(int totalCases, int conclusionHits, int evidenceHits,
                                               int evidenceStopHits, int humanReviews,
                                               double conclusionAccuracy, double evidenceSupportRate,
                                               double evidenceStopRate, double humanReviewRate) {

        /** 将 guardian 领域评测结果转换为控制台 API 模型。 */
        public static DiagnosticEvaluationResponse from(DiagnosticEvaluationService.EvaluationSummary summary) {
            return new DiagnosticEvaluationResponse(summary.totalCases(), summary.conclusionHits(),
                    summary.evidenceHits(), summary.evidenceStopHits(), summary.humanReviews(),
                    summary.conclusionAccuracy(), summary.evidenceSupportRate(), summary.evidenceStopRate(),
                    summary.humanReviewRate());
        }
    }
}
