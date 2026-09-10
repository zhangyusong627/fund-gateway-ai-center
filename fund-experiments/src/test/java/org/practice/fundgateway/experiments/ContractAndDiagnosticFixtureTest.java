package org.practice.fundgateway.experiments;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.practice.fundgateway.integration.contract.PublishedContract;
import org.practice.fundgateway.guardian.diagnosis.DiagnosticCase;

/** 验证最小契约字段和诊断评测样例具备可追溯关联。 */
class ContractAndDiagnosticFixtureTest {

    /** 契约版本必须携带来源、版本和生效时间等共享字段。 */
    @Test
    void publishedContractContainsTraceableFacts() {
        PublishedContract contract = contract();

        assertEquals("synthetic-provider", contract.providerId());
        assertEquals("v1", contract.version());
        assertEquals("synthetic-doc-section-2", contract.source());
        assertTrue(contract.effectiveAt().isBefore(Instant.parse("2026-09-09T00:00:00Z")));
    }

    /** 诊断样例必须引用同一资方和接口，并声明所需证据。 */
    @Test
    void diagnosticCaseReferencesContractAndEvidence() {
        DiagnosticCase diagnosticCase = new DiagnosticCase(
                "case-001", "synthetic-provider", "credit-apply",
                List.of("timeout spike", "provider response delayed"),
                "资方接口响应超时风险",
                List.of("contract.timeoutMs", "runtime.p95"));

        assertEquals(contract().providerId(), diagnosticCase.providerId());
        assertEquals(contract().interfaceId(), diagnosticCase.interfaceId());
        assertTrue(diagnosticCase.requiredEvidence().contains("contract.timeoutMs"));
    }

    /** 构造最小合成契约。 */
    private static PublishedContract contract() {
        return new PublishedContract("synthetic-provider", "credit-apply",
                "synthetic-doc-section-2", "v1", Instant.parse("2026-09-08T00:00:00Z"),
                20, 3000, true);
    }
}
