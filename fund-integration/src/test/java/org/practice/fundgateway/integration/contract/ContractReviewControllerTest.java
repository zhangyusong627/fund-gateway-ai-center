package org.practice.fundgateway.integration.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** 验证审核 REST 编排层的状态码和核心交互。 */
class ContractReviewControllerTest {

    private final ContractReviewController controller = new ContractReviewController(
            new ContractReviewService(new ContractCandidateValidator(),
                    Clock.fixed(Instant.parse("2026-09-10T00:00:00Z"), ZoneOffset.UTC)));

    /** 验证提交、通过和已发布查询的 REST 编排结果。 */
    @Test
    void shouldSubmitApproveAndQueryPublishedVersion() {
        ResponseEntity<ContractReviewResult> submitted = controller.submit(candidate());
        String candidateId = submitted.getBody().candidate().candidateId();

        ResponseEntity<ContractReviewResult> approved = controller.approve(candidateId,
                new ContractReviewController.ReviewActionRequest("owner", null));
        ResponseEntity<PublishedContractVersion> published = controller.published(
                "synthetic-provider", "credit-application");

        assertEquals(200, submitted.getStatusCode().value());
        assertEquals("v1", approved.getBody().publishedVersion().version());
        assertEquals(200, published.getStatusCode().value());
    }

    /** 验证不存在的已发布版本返回 404。 */
    @Test
    void shouldReturnNotFoundWhenPublishedVersionDoesNotExist() {
        ResponseEntity<PublishedContractVersion> response = controller.published("unknown", "unknown");

        assertEquals(404, response.getStatusCode().value());
    }

    /** 创建最小合成候选请求。 */
    private ContractCandidate candidate() {
        ContractEvidenceCitation citation = new ContractEvidenceCitation(
                "doc:v1:117", "synthetic-doc", "v1", "4.2 授信申请#117-117", "applyAmt | BigDecimal | 必填");
        CandidateFieldDefinition field = new CandidateFieldDefinition(
                "applyAmt", "BigDecimal", FieldRequirement.REQUIRED, null, "申请金额", citation);
        return new ContractCandidate("candidate-rest-001", "synthetic-provider", "credit-application",
                "提交授信申请", "/credit/apply", "POST", List.of(field), List.of(citation), "v1",
                "deepseek-v4-flash", Instant.parse("2026-09-10T00:00:00Z"), ContractCandidateStatus.DRAFT);
    }
}
