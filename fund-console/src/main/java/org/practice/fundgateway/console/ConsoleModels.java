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

    /** RAG 查询参数。 */
    public record RagQueryRequest(String collectionName, String question, List<String> keywords, Integer topK) {
    }

    /** RAG 查询结果。 */
    public record RagQueryResponse(String status, String collectionName, String question, int topK, long durationMs,
                                   int indexedChunks, Set<String> missingKeywords,
                                   List<RagCandidate> candidates) {
    }

    /** 可供在线检索选择的已发布知识集合。 */
    public record PublishedCollection(String collectionName, String documentId, String documentVersion,
                                      int chunkCount, String embeddingModel, int embeddingDimension) {
    }

    /** 固定问题集评测结果，指标由每次检索结果复算。 */
    public record RagEvaluationResponse(int caseCount, double recallAt3, double meanReciprocalRank,
                                        double citationHitRate, double refusalAccuracy,
                                        List<RagEvaluationCase> cases) {
    }

    /** 一道评测题的预期关键词和实际召回结果。 */
    public record RagEvaluationCase(String question, String expectedKeyword, int expectedRank,
                                    boolean hit, int actualRank, String status) {
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
