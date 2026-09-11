package org.practice.fundgateway.integration.contract;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;

/** 编排候选审核状态和不可变发布版本，当前使用内存端口验证流程。 */
@Service
public class ContractReviewService {

    private final ContractCandidateValidator validator;
    private final Clock clock;
    private final Map<String, ContractCandidate> candidates = new ConcurrentHashMap<>();
    private final Map<String, PublishedContractVersion> publishedVersions = new ConcurrentHashMap<>();
    private final List<ContractPublishedEvent> publishedEvents = new CopyOnWriteArrayList<>();

    /** 使用系统时钟创建审核服务。 */
    public ContractReviewService() {
        this(new ContractCandidateValidator(), Clock.systemUTC());
    }

    /** 注入校验器和时钟，便于测试审核时间和规则。 */
    public ContractReviewService(ContractCandidateValidator validator, Clock clock) {
        this.validator = validator;
        this.clock = clock;
    }

    /** 校验候选并提交人工审核，校验失败的候选不能进入待审核状态。 */
    public ContractReviewResult submitForReview(ContractCandidate candidate) {
        requireCandidate(candidate);
        ContractValidationResult structure = validator.validateStructure(candidate);
        ContractValidationResult business = validator.validateBusiness(candidate);
        ContractValidationResult source = validator.validateSource(candidate);
        if (!structure.valid() || !business.valid() || !source.valid()) {
            throw new IllegalArgumentException("候选规范未通过 Java 三层校验");
        }
        ContractCandidate pending = withStatus(candidate, ContractCandidateStatus.PENDING_REVIEW);
        candidates.put(candidate.candidateId(), pending);
        return new ContractReviewResult(pending, null, "候选规范已进入人工审核");
    }

    /** 通过待审核候选并生成新的不可变发布版本。 */
    public ContractReviewResult approve(String candidateId, String reviewer) {
        ContractCandidate candidate = requirePending(candidateId);
        requireText(reviewer, "审核人");
        int nextVersion = nextVersion(candidate.providerId(), candidate.interfaceId());
        PublishedContractVersion published = new PublishedContractVersion(
                candidate.providerId(), candidate.interfaceId(), "v" + nextVersion,
                candidate.sourceDocumentVersion(), candidate.endpoint(), candidate.httpMethod(),
                candidate.fields(), candidate.evidence(), reviewer, Instant.now(clock));
        candidates.put(candidateId, withStatus(candidate, ContractCandidateStatus.APPROVED));
        publishedVersions.put(versionKey(candidate, published.version()), published);
        publishedEvents.add(new ContractPublishedEvent(UUID.randomUUID().toString(), candidate.candidateId(),
                candidate.providerId(), candidate.interfaceId(), published.version(), reviewer, Instant.now(clock)));
        return new ContractReviewResult(candidates.get(candidateId), published, "候选规范已发布");
    }

    /** 退回待审核候选并保留退回原因，允许后续重新提交。 */
    public ContractReviewResult returnForRevision(String candidateId, String reason) {
        ContractCandidate candidate = requirePending(candidateId);
        requireText(reason, "退回原因");
        ContractCandidate returned = withStatus(candidate, ContractCandidateStatus.RETURNED);
        candidates.put(candidateId, returned);
        return new ContractReviewResult(returned, null, reason);
    }

    /** 拒绝待审核候选并保留拒绝状态。 */
    public ContractReviewResult reject(String candidateId, String reason) {
        ContractCandidate candidate = requirePending(candidateId);
        requireText(reason, "拒绝原因");
        ContractCandidate rejected = withStatus(candidate, ContractCandidateStatus.REJECTED);
        candidates.put(candidateId, rejected);
        return new ContractReviewResult(rejected, null, reason);
    }

    /** 按资方和接口读取最新已发布版本，候选状态不会出现在查询结果中。 */
    public PublishedContractVersion findPublished(String providerId, String interfaceId) {
        return publishedVersions.values().stream()
                .filter(version -> version.providerId().equals(providerId)
                        && version.interfaceId().equals(interfaceId))
                .max((left, right) -> left.version().compareTo(right.version()))
                .orElse(null);
    }

    /** 读取候选当前状态，供审核页面显示。 */
    public ContractCandidate findCandidate(String candidateId) {
        return candidates.get(candidateId);
    }

    /** 查询指定资方接口的发布事件，按发生时间倒序返回。 */
    public List<ContractPublishedEvent> findPublishedEvents(String providerId, String interfaceId) {
        return publishedEvents.stream()
                .filter(event -> event.providerId().equals(providerId) && event.interfaceId().equals(interfaceId))
                .sorted((left, right) -> right.occurredAt().compareTo(left.occurredAt()))
                .toList();
    }

    private ContractCandidate requirePending(String candidateId) {
        ContractCandidate candidate = candidates.get(candidateId);
        if (candidate == null) {
            throw new IllegalArgumentException("候选规范不存在：" + candidateId);
        }
        if (candidate.status() != ContractCandidateStatus.PENDING_REVIEW) {
            throw new IllegalStateException("只有待审核候选才能执行审核操作");
        }
        return candidate;
    }

    private int nextVersion(String providerId, String interfaceId) {
        return (int) publishedVersions.values().stream()
                .filter(version -> version.providerId().equals(providerId)
                        && version.interfaceId().equals(interfaceId))
                .count() + 1;
    }

    private String versionKey(ContractCandidate candidate, String version) {
        return candidate.providerId() + ":" + candidate.interfaceId() + ":" + version;
    }

    private ContractCandidate withStatus(ContractCandidate candidate, ContractCandidateStatus status) {
        return new ContractCandidate(candidate.candidateId(), candidate.providerId(), candidate.interfaceId(),
                candidate.purpose(), candidate.endpoint(), candidate.httpMethod(), candidate.fields(),
                candidate.evidence(), candidate.sourceDocumentVersion(), candidate.extractionModel(),
                candidate.createdAt(), status);
    }

    private void requireCandidate(ContractCandidate candidate) {
        if (candidate == null || candidate.candidateId() == null || candidate.candidateId().isBlank()) {
            throw new IllegalArgumentException("候选规范标识不能为空");
        }
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
    }
}
