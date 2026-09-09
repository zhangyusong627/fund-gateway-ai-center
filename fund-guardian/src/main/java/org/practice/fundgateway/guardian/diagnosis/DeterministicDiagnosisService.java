package org.practice.fundgateway.guardian.diagnosis;

import java.util.ArrayList;
import java.util.List;

/** 用固定阈值评估证据，作为模型建议的可解释基线和安全兜底。 */
public class DeterministicDiagnosisService {

    /** 执行证据完整性检查和五条首版规则。 */
    public DiagnosisResult diagnose(DiagnosisEvidence evidence) {
        if (evidence == null || evidence.contract() == null || evidence.metrics() == null
                || evidence.incident() == null) {
            RuleFinding finding = new RuleFinding("R005", true, "HIGH", "契约、指标或历史故障证据缺失",
                    "补充完整证据后再做确定性判断", true);
            return new DiagnosisResult(List.of(finding), true, false);
        }
        ContractEvidence c = evidence.contract();
        MetricsEvidence m = evidence.metrics();
        List<RuleFinding> findings = new ArrayList<>();
        add(findings, "R001", m.qps() >= c.qpsLimit() * 0.9,
                "当前 QPS=" + m.qps() + "，契约上限=" + c.qpsLimit(), "降低调用并发或限流值", "HIGH");
        add(findings, "R002", m.timeoutRate() >= 0.05,
                "超时率=" + m.timeoutRate(), "检查上游超时和网络链路", "HIGH");
        add(findings, "R003", m.avgLatencyMs() >= c.timeoutMs() * 0.8,
                "平均响应=" + m.avgLatencyMs() + "ms，契约超时=" + c.timeoutMs() + "ms", "增大客户端超时时间前先核查上游", "MEDIUM");
        add(findings, "R004", m.maxThreads() <= 0 || (double) m.activeThreads() / m.maxThreads() >= 0.8,
                "线程池活跃数=" + m.activeThreads() + "/" + m.maxThreads(), "检查线程池容量", "HIGH");
        boolean review = findings.stream().anyMatch(RuleFinding::matched);
        return new DiagnosisResult(findings, review, true);
    }

    private void add(List<RuleFinding> findings, String id, boolean matched, String evidence,
                     String recommendation, String risk) {
        findings.add(new RuleFinding(id, matched, matched ? risk : "NONE",
                evidence, matched ? recommendation : "继续观察", matched));
    }
}
