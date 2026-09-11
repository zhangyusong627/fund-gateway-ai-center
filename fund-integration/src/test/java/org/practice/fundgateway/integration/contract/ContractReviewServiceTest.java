package org.practice.fundgateway.integration.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 验证候选审核、拒绝和不可变发布版本的状态边界。 */
class ContractReviewServiceTest {

    private final ContractReviewService service = new ContractReviewService(
            new ContractCandidateValidator(),
            Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneOffset.UTC));

    /** 审核通过后生成 V1，已发布版本可以只读查询。 */
    @Test
    void shouldApproveAndPublishImmutableV1() {
        ContractReviewResult pending = service.submitForReview(candidate("candidate-001"));
        ContractReviewResult approved = service.approve(pending.candidate().candidateId(), "owner");

        assertEquals(ContractCandidateStatus.PENDING_REVIEW, pending.candidate().status());
        assertEquals(ContractCandidateStatus.APPROVED, approved.candidate().status());
        assertEquals("v1", approved.publishedVersion().version());
        assertNotNull(service.findPublished("synthetic-provider", "credit-application"));
        assertEquals(1, service.findPublishedEvents("synthetic-provider", "credit-application").size());
        assertEquals("v1", service.findPublishedEvents("synthetic-provider", "credit-application").get(0).version());
    }

    /** 已审核候选不能重复批准或被拒绝。 */
    @Test
    void shouldRejectRepeatedReview() {
        ContractReviewResult pending = service.submitForReview(candidate("candidate-002"));
        service.approve(pending.candidate().candidateId(), "owner");

        assertThrows(IllegalStateException.class,
                () -> service.approve(pending.candidate().candidateId(), "owner"));
        assertThrows(IllegalStateException.class,
                () -> service.reject(pending.candidate().candidateId(), "重复操作"));
    }

    /** 退回和拒绝都保留非发布状态。 */
    @Test
    void shouldKeepReturnedAndRejectedCandidatesOutOfPublishedView() {
        ContractReviewResult returned = service.submitForReview(candidate("candidate-003"));
        ContractReviewResult rejected = service.submitForReview(candidate("candidate-004"));

        assertEquals(ContractCandidateStatus.RETURNED,
                service.returnForRevision(returned.candidate().candidateId(), "字段条件不清楚")
                        .candidate().status());
        assertEquals(ContractCandidateStatus.REJECTED,
                service.reject(rejected.candidate().candidateId(), "来源不足")
                        .candidate().status());
        assertEquals(null, service.findPublished("synthetic-provider", "credit-application"));
    }

    /** 创建通过三层校验的合成候选。 */
    private ContractCandidate candidate(String candidateId) {
        ContractEvidenceCitation citation = new ContractEvidenceCitation(
                "doc:v1:117", "synthetic-doc", "v1", "4.2 授信申请#117-117", "applyAmt | BigDecimal | 必填");
        CandidateFieldDefinition field = new CandidateFieldDefinition(
                "applyAmt", "BigDecimal", FieldRequirement.REQUIRED, null, "申请金额", citation);
        return new ContractCandidate(candidateId, "synthetic-provider", "credit-application", "提交授信申请",
                "/credit/apply", "POST", List.of(field), List.of(citation), "v1", "deepseek-v4-flash",
                Instant.parse("2026-09-10T00:00:00Z"), ContractCandidateStatus.DRAFT);
    }
}
