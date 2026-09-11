package org.practice.fundgateway.guardian.audit;

import java.util.List;
import java.util.Optional;

/** 定义模型调用审计的保存和查询端口。 */
public interface ModelCallAuditRepository {

    /** 按调用标识幂等保存一条审计记录。 */
    boolean saveIfAbsent(ModelCallAudit audit);

    /** 按调用标识查询审计记录。 */
    Optional<ModelCallAudit> findByCallId(String callId);

    /** 按调用时间倒序查询全部审计记录。 */
    List<ModelCallAudit> findAll();
}
