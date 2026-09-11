package org.practice.fundgateway.guardian.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.practice.fundgateway.guardian.audit.ModelAuditApplicationService.ModelCostSummary;

/** 验证线程安全内存审计、条件查询和成本汇总。 */
class ModelAuditApplicationServiceTest {

    /** 并行写入不同调用号时应完整保留，重复调用号只接受一次。 */
    @Test
    void shouldStoreAuditsThreadSafelyAndIdempotently() {
        InMemoryModelCallAuditRepository repository = new InMemoryModelCallAuditRepository();

        IntStream.range(0, 100).parallel().forEach(index ->
                assertTrue(repository.saveIfAbsent(audit("call-" + index, "guardian", "CNY"))));

        assertEquals(100, repository.findAll().size());
        assertFalse(repository.saveIfAbsent(audit("call-1", "guardian", "CNY")));
        assertEquals(100, repository.findAll().size());
    }

    /** 应按领域筛选调用并按币种分别汇总 token 和成本。 */
    @Test
    void shouldQueryAndSummarizeCostByCurrency() {
        ModelAuditApplicationService service = new ModelAuditApplicationService(
                new InMemoryModelCallAuditRepository());
        service.record(audit("call-cny-1", "guardian", "CNY"));
        service.record(audit("call-cny-2", "guardian", "CNY"));
        service.record(audit("call-usd", "guardian", "USD"));
        service.record(audit("call-other", "knowledge", "CNY"));

        List<ModelCostSummary> summaries = service.summarizeByCurrency("guardian", "diagnosis");

        assertEquals(3, service.findAll("guardian", null).size());
        assertEquals(2, summaries.size());
        assertEquals("CNY", summaries.get(0).currency());
        assertEquals(2, summaries.get(0).callCount());
        assertEquals(3000, summaries.get(0).totalTokens());
        assertEquals(0, new BigDecimal("0.0040000000").compareTo(summaries.get(0).estimatedCost()));
    }

    /** 构造固定 token 和价格版本的模型调用审计。 */
    private ModelCallAudit audit(String callId, String domain, String currency) {
        return ModelCallAudit.priced(callId, "trace-1", domain, "diagnosis", "deepseek",
                "deepseek-v4-flash", "guardian-diagnosis-v1", 1000, 500, 1200,
                ModelCallStatus.SUCCEEDED, 0, "deepseek-price-2026-09",
                new BigDecimal("1.00"), new BigDecimal("2.00"), currency,
                "{\"prompt\":\"诊断\"}", "{\"summary\":\"风险\"}",
                Instant.parse("2026-09-20T00:00:00Z"));
    }
}
