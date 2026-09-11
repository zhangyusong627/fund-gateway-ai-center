package org.practice.fundgateway.console;

import java.util.List;

import org.practice.fundgateway.guardian.audit.ModelAuditApplicationService;
import org.practice.fundgateway.guardian.audit.ModelCallAudit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供模型调用明细和可复算成本汇总接口。 */
@RestController
@RequestMapping("/api/console/audit")
public class AuditConsoleController {

    private final ModelAuditApplicationService auditService;

    /** 注入模型审计应用服务。 */
    public AuditConsoleController(ModelAuditApplicationService auditService) {
        this.auditService = auditService;
    }

    /** 查询模型调用完整审计记录，原始请求和响应只在本地受控页面展示。 */
    @GetMapping("/model-calls")
    public List<ModelCallAudit> modelCalls(@RequestParam(required = false) String domain,
                                           @RequestParam(required = false) String stage) {
        return auditService.findAll(domain, stage);
    }

    /** 按币种汇总调用次数、Token 和估算费用。 */
    @GetMapping("/model-costs")
    public List<ModelAuditApplicationService.ModelCostSummary> modelCosts(
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String stage) {
        return auditService.summarizeByCurrency(domain, stage);
    }
}
