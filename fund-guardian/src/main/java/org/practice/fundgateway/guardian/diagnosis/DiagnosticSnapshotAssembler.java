package org.practice.fundgateway.guardian.diagnosis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.practice.fundgateway.guardian.metrics.GuardianRiskRepository;
import org.practice.fundgateway.guardian.metrics.MetricWindowAggregate;
import org.practice.fundgateway.guardian.metrics.RiskRuleEvaluator;

import tools.jackson.databind.json.JsonMapper;

/** 将数据库风险任务还原为模型可读的只读诊断快照。 */
public class DiagnosticSnapshotAssembler {

    private final GuardianRiskRepository repository;
    private final RiskRuleEvaluator ruleEvaluator;
    private final JsonMapper mapper;

    /** 创建任务快照组装器。 */
    public DiagnosticSnapshotAssembler(GuardianRiskRepository repository) {
        this(repository, new RiskRuleEvaluator(), JsonMapper.builder().build());
    }

    /** 注入依赖，便于测试不同规则和 JSON 映射行为。 */
    public DiagnosticSnapshotAssembler(GuardianRiskRepository repository,
                                       RiskRuleEvaluator ruleEvaluator, JsonMapper mapper) {
        this.repository = repository;
        this.ruleEvaluator = ruleEvaluator;
        this.mapper = mapper;
    }

    /** 读取最早待处理任务并组装快照；没有任务时返回空。 */
    public java.util.Optional<DiagnosisSnapshot> assemblePending() throws Exception {
        return repository.findPendingTask().map(task -> assemble(task));
    }

    private DiagnosisSnapshot assemble(GuardianRiskRepository.DiagnosticTaskRecord task) {
        try {
            MetricWindowAggregate aggregate = mapper.readValue(task.payloadJson(), MetricWindowAggregate.class);
            List<RuleFinding> findings = ruleEvaluator.evaluate(aggregate).stream()
                    .map(hit -> new RuleFinding(hit.ruleId(), true, hit.severity(), String.join("；", hit.evidence()),
                            "根据规则执行人工核查", true)).toList();
            MetricsEvidence metrics = new MetricsEvidence(aggregate.serviceName(), aggregate.interfacePath(),
                    (int) Math.round(aggregate.qps()), (int) aggregate.p95LatencyMs(), aggregate.timeoutRate(),
                    (int) aggregate.activeThreads(), (int) aggregate.maxThreads());
            return new DiagnosisSnapshot(task.taskId().toString(), Instant.now(), metrics, findings,
                    null, List.of(), task.fingerprint());
        } catch (Exception exception) {
            throw new IllegalStateException("风险任务快照解析失败: " + task.taskId(), exception);
        }
    }
}
