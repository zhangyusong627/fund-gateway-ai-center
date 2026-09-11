package org.practice.fundgateway.guardian.audit;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 使用线程安全内存映射保存模型调用审计，供无数据库模式和测试使用。 */
public class InMemoryModelCallAuditRepository implements ModelCallAuditRepository {

    private final ConcurrentMap<String, ModelCallAudit> audits = new ConcurrentHashMap<>();

    /** 按调用标识原子写入审计，重复调用号不覆盖首次事实。 */
    @Override
    public boolean saveIfAbsent(ModelCallAudit audit) {
        if (audit == null) {
            throw new IllegalArgumentException("模型调用审计不能为空");
        }
        return audits.putIfAbsent(audit.callId(), audit) == null;
    }

    /** 按调用标识查询审计记录。 */
    @Override
    public Optional<ModelCallAudit> findByCallId(String callId) {
        return Optional.ofNullable(audits.get(callId));
    }

    /** 返回按调用时间倒序排列的稳定审计快照。 */
    @Override
    public List<ModelCallAudit> findAll() {
        return audits.values().stream()
                .sorted(Comparator.comparing(ModelCallAudit::calledAt).reversed()
                        .thenComparing(ModelCallAudit::callId))
                .toList();
    }
}
