package org.practice.fundgateway.guardian.metrics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import tools.jackson.databind.json.JsonMapper;

/** 编排规则命中、风险指纹、冷却和诊断任务落库，不调用大模型。 */
public class RiskDiagnosisCoordinator {

    private final RiskRuleEvaluator ruleEvaluator;
    private final RiskCooldownGate cooldownGate;
    private final GuardianRiskRepository repository;
    private final JsonMapper mapper;
    private final Clock clock;

    /** 使用一分钟冷却和系统时钟创建编排器。 */
    public RiskDiagnosisCoordinator(GuardianRiskRepository repository) {
        this(new RiskRuleEvaluator(), new RiskCooldownGate(Duration.ofMinutes(1)), repository,
                JsonMapper.builder().build(), Clock.systemUTC());
    }

    /** 注入规则、冷却、仓储和时钟，便于测试与回放。 */
    public RiskDiagnosisCoordinator(RiskRuleEvaluator ruleEvaluator, RiskCooldownGate cooldownGate,
                                    GuardianRiskRepository repository, JsonMapper mapper, Clock clock) {
        this.ruleEvaluator = ruleEvaluator;
        this.cooldownGate = cooldownGate;
        this.repository = repository;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** 评估一个窗口并返回是否创建诊断任务。 */
    public CoordinationResult process(MetricWindowAggregate aggregate) throws Exception {
        List<RiskRuleHit> hits = ruleEvaluator.evaluate(aggregate);
        if (hits.isEmpty()) {
            return new CoordinationResult(false, false, null, List.of());
        }
        String fingerprint = RiskFingerprint.of(aggregate, hits);
        Instant now = Instant.now(clock);
        String severity = hits.stream().map(RiskRuleHit::severity).max(String::compareTo).orElse("LOW");
        String payload = mapper.writeValueAsString(aggregate);
        repository.saveRiskEvent(new GuardianRiskRepository.RiskEventRecord(fingerprint,
                aggregate.serviceName(), aggregate.interfacePath(), severity,
                hits.stream().map(RiskRuleHit::ruleId).toList(), aggregate.windowStart(), aggregate.windowEnd(), now,
                payload));
        boolean admitted = cooldownGate.tryAcquire(fingerprint, now);
        boolean taskCreated = admitted && repository.saveDiagnosticTask(fingerprint, aggregate.windowStart(), "PENDING");
        return new CoordinationResult(true, taskCreated, fingerprint, hits);
    }

    /** 表示一次风险编排结果。 */
    public record CoordinationResult(boolean riskDetected, boolean taskCreated,
                                     String fingerprint, List<RiskRuleHit> ruleHits) {
    }
}
