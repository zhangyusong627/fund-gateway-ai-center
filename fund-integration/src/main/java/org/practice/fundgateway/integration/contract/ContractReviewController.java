package org.practice.fundgateway.integration.contract;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** 提供候选接口规范审核和已发布版本查询的最小 REST 接口。 */
@RestController
@RequestMapping("/api/integration/contracts")
public class ContractReviewController {

    private final ContractReviewService reviewService;

    /** 注入审核服务。 */
    public ContractReviewController(ContractReviewService reviewService) {
        this.reviewService = reviewService;
    }

    /** 提交候选规范进入人工审核。 */
    @PostMapping("/candidates")
    public ResponseEntity<ContractReviewResult> submit(@RequestBody ContractCandidate candidate) {
        return ResponseEntity.ok(reviewService.submitForReview(candidate));
    }

    /** 审核通过并发布新的不可变版本。 */
    @PostMapping("/candidates/{candidateId}/approve")
    public ResponseEntity<ContractReviewResult> approve(@PathVariable String candidateId,
                                                        @RequestBody ReviewActionRequest request) {
        return ResponseEntity.ok(reviewService.approve(candidateId, request.reviewer()));
    }

    /** 退回候选规范并保留原因。 */
    @PostMapping("/candidates/{candidateId}/return")
    public ResponseEntity<ContractReviewResult> returnForRevision(@PathVariable String candidateId,
                                                                  @RequestBody ReviewActionRequest request) {
        return ResponseEntity.ok(reviewService.returnForRevision(candidateId, request.reason()));
    }

    /** 拒绝候选规范并保留原因。 */
    @PostMapping("/candidates/{candidateId}/reject")
    public ResponseEntity<ContractReviewResult> reject(@PathVariable String candidateId,
                                                       @RequestBody ReviewActionRequest request) {
        return ResponseEntity.ok(reviewService.reject(candidateId, request.reason()));
    }

    /** 查询审核页面需要展示的候选当前状态。 */
    @GetMapping("/candidates/{candidateId}")
    public ResponseEntity<ContractCandidate> candidate(@PathVariable String candidateId) {
        ContractCandidate candidate = reviewService.findCandidate(candidateId);
        return candidate == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(candidate);
    }

    /** 查询指定资方接口的最新已发布版本。 */
    @GetMapping("/published")
    public ResponseEntity<PublishedContractVersion> published(@RequestParam String providerId,
                                                              @RequestParam String interfaceId) {
        PublishedContractVersion version = reviewService.findPublished(providerId, interfaceId);
        return version == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(version);
    }

    /** 查询指定资方接口的契约发布审计事件。 */
    @GetMapping("/published/events")
    public ResponseEntity<List<ContractPublishedEvent>> publishedEvents(@RequestParam String providerId,
                                                                          @RequestParam String interfaceId) {
        return ResponseEntity.ok(reviewService.findPublishedEvents(providerId, interfaceId));
    }

    /** 把请求参数错误映射为客户端错误。 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", exception.getMessage()));
    }

    /** 把非法状态操作映射为冲突错误。 */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> conflict(IllegalStateException exception) {
        return ResponseEntity.status(409).body(new ApiError("INVALID_STATE", exception.getMessage()));
    }

    /** 审核动作的操作者和原因请求。 */
    public record ReviewActionRequest(String reviewer, String reason) {
    }

    /** REST 错误响应。 */
    public record ApiError(String code, String message) {
    }
}
