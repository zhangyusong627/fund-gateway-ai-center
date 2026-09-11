package org.practice.fundgateway.integration.contract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 验证候选接口规范的三层 Java 确定性校验。 */
class ContractCandidateValidatorTest {

    private final ContractCandidateValidator validator = new ContractCandidateValidator();

    /** 完整候选通过结构、业务和来源校验。 */
    @Test
    void shouldAcceptCompleteCandidate() {
        ContractCandidate candidate = candidate(FieldRequirement.REQUIRED, null, citation("v1"));

        assertTrue(validator.validateStructure(candidate).valid());
        assertTrue(validator.validateBusiness(candidate).valid());
        assertTrue(validator.validateSource(candidate).valid());
    }

    /** 条件必填缺少触发条件时必须拒绝。 */
    @Test
    void shouldRejectConditionalFieldWithoutCondition() {
        ContractCandidate candidate = candidate(FieldRequirement.CONDITIONAL, "", citation("v1"));

        assertFalse(validator.validateBusiness(candidate).valid());
    }

    /** 字段引用缺失或版本不一致时必须拒绝。 */
    @Test
    void shouldRejectMissingOrCrossVersionEvidence() {
        ContractCandidate missingCitation = candidate(FieldRequirement.REQUIRED, null, null);
        ContractCandidate crossVersion = candidate(FieldRequirement.REQUIRED, null, citation("v2"));

        assertFalse(validator.validateSource(missingCitation).valid());
        assertFalse(validator.validateSource(crossVersion).valid());
    }

    /** 创建最小合成候选。 */
    private ContractCandidate candidate(FieldRequirement requirement, String condition,
                                       ContractEvidenceCitation fieldCitation) {
        ContractEvidenceCitation generalCitation = citation("v1");
        CandidateFieldDefinition field = new CandidateFieldDefinition(
                "applyAmt", "BigDecimal", requirement, condition, "申请金额", fieldCitation);
        return new ContractCandidate("candidate-001", "synthetic-provider", "credit-application",
                "提交授信申请", "/credit/apply", "POST", List.of(field), List.of(generalCitation),
                "v1", "deepseek-v4-flash", Instant.parse("2026-09-10T00:00:00Z"),
                ContractCandidateStatus.PENDING_REVIEW);
    }

    /** 创建可定位的合成引用。 */
    private ContractEvidenceCitation citation(String version) {
        return new ContractEvidenceCitation("doc:v1:117", "synthetic-doc", version,
                "4.2 授信申请#117-117", "applyAmt | BigDecimal | 必填");
    }
}
