package org.practice.fundgateway.guardian.audit;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** 提供模型调用审计写入、条件查询和按币种成本汇总用例。 */
public class ModelAuditApplicationService {

    private final ModelCallAuditRepository repository;

    /** 注入审计仓储创建应用服务。 */
    public ModelAuditApplicationService(ModelCallAuditRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("模型调用审计仓储不能为空");
        }
        this.repository = repository;
    }

    /** 幂等记录一次已经完成或失败的模型调用。 */
    public boolean record(ModelCallAudit audit) {
        return repository.saveIfAbsent(audit);
    }

    /** 按调用标识查询完整审计证据。 */
    public Optional<ModelCallAudit> findByCallId(String callId) {
        return repository.findByCallId(callId);
    }

    /** 按领域和阶段筛选审计，空条件表示不过滤。 */
    public List<ModelCallAudit> findAll(String domain, String stage) {
        return repository.findAll().stream()
                .filter(audit -> isBlank(domain) || audit.domain().equals(domain))
                .filter(audit -> isBlank(stage) || audit.stage().equals(stage))
                .toList();
    }

    /** 按币种汇总筛选结果，防止不同币种金额被直接相加。 */
    public List<ModelCostSummary> summarizeByCurrency(String domain, String stage) {
        Map<String, List<ModelCallAudit>> groups = findAll(domain, stage).stream()
                .collect(Collectors.groupingBy(ModelCallAudit::currency));
        return groups.entrySet().stream().map(entry -> summarize(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ModelCostSummary::currency)).toList();
    }

    /** 汇总同一币种下的调用数、token 和估算成本。 */
    private ModelCostSummary summarize(String currency, List<ModelCallAudit> audits) {
        long inputTokens = audits.stream().mapToLong(ModelCallAudit::inputTokens).sum();
        long outputTokens = audits.stream().mapToLong(ModelCallAudit::outputTokens).sum();
        BigDecimal cost = audits.stream().map(ModelCallAudit::estimatedCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ModelCostSummary(currency, audits.size(), inputTokens, outputTokens,
                inputTokens + outputTokens, cost);
    }

    /** 判断查询条件是否为空。 */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 表示同一币种下可直接展示的模型调用成本汇总。 */
    public record ModelCostSummary(String currency, long callCount, long inputTokens,
                                   long outputTokens, long totalTokens, BigDecimal estimatedCost) {
    }
}
