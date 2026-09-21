package org.practice.fundgateway.experiments.console;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisGate;
import org.practice.fundgateway.guardian.diagnosis.ModelDiagnosisReport;
import org.practice.fundgateway.guardian.diagnosis.RuleFinding;
import org.practice.fundgateway.guardian.metrics.MetricWindowAggregate;
import org.practice.fundgateway.guardian.metrics.RiskRuleHit;

/** 集中定义演示控制台的请求和响应结构。 */
public final class ConsoleModels {

    /** 禁止实例化只承载数据结构的工具类。 */
    private ConsoleModels() {
    }

    /** 控制台运行状态。 */
    public record ConsoleStatus(String status, String ragEngine, String embeddingModel,
                                int embeddingDimension, String guardianMode, boolean deepSeekAvailable) {
    }

    /** RAG 查询参数。 */
    public record RagQueryRequest(String question, List<String> keywords, Integer topK) {
    }

    /** RAG 查询结果。 */
    public record RagQueryResponse(String status, String question, int topK, long durationMs,
                                   int indexedChunks, Set<String> missingKeywords,
                                   List<RagCandidate> candidates) {
    }

    /** 一条带来源和分数的 RAG 候选证据。 */
    public record RagCandidate(int rank, String chunkId, String content, double vectorScore,
                               double keywordScore, double finalScore, String documentId,
                               String documentVersion, String locator) {
    }

    /** 智能守护模拟参数。 */
    public record GuardianSimulationRequest(String scenario, Integer messageCount, Boolean invokeModel) {
    }

    /** 智能守护模拟结果。 */
    public record GuardianSimulationResponse(String scenario, int metricMessages, int aggregateWindows,
                                             int riskWindows, int diagnosticTasks, int suppressedTasks,
                                             int actualModelCalls, long durationMs,
                                             MetricWindowAggregate representativeWindow,
                                             List<RiskRuleHit> ruleHits, List<RuleFinding> deterministicFindings,
                                             String riskFingerprint, String modelStatus,
                                             String modelPrompt, String modelRawResponse,
                                             ModelDiagnosisReport modelReport,
                                             ModelDiagnosisGate.GateDecision gateDecision,
                                             Instant executedAt) {
    }
}
