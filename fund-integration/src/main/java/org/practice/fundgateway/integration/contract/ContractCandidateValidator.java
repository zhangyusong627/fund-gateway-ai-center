package org.practice.fundgateway.integration.contract;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 对候选接口规范执行结构、业务和来源三层确定性校验。 */
public class ContractCandidateValidator {

    /** 校验候选对象的基本结构和字段形状。 */
    public ContractValidationResult validateStructure(ContractCandidate candidate) {
        List<ContractValidationIssue> issues = new ArrayList<>();
        if (candidate == null) {
            return ContractValidationResult.failed(List.of(
                    new ContractValidationIssue("candidate", "候选接口规范不能为空")));
        }
        require(issues, "candidateId", candidate.candidateId());
        require(issues, "providerId", candidate.providerId());
        require(issues, "interfaceId", candidate.interfaceId());
        require(issues, "purpose", candidate.purpose());
        require(issues, "endpoint", candidate.endpoint());
        require(issues, "httpMethod", candidate.httpMethod());
        require(issues, "sourceDocumentVersion", candidate.sourceDocumentVersion());
        if (candidate.fields().isEmpty()) {
            issues.add(new ContractValidationIssue("fields", "候选规范至少要有一个字段"));
        }
        for (int index = 0; index < candidate.fields().size(); index++) {
            CandidateFieldDefinition field = candidate.fields().get(index);
            String path = "fields[" + index + "]";
            require(issues, path + ".name", field.name());
            require(issues, path + ".type", field.type());
            if (field.requirement() == null) {
                issues.add(new ContractValidationIssue(path + ".requirement", "字段必填级别不能为空"));
            }
            require(issues, path + ".description", field.description());
        }
        return issues.isEmpty() ? ContractValidationResult.passed() : ContractValidationResult.failed(issues);
    }

    /** 校验枚举、条件必填和重复字段等业务约束。 */
    public ContractValidationResult validateBusiness(ContractCandidate candidate) {
        List<ContractValidationIssue> issues = new ArrayList<>();
        if (candidate == null) {
            return ContractValidationResult.failed(List.of(
                    new ContractValidationIssue("candidate", "候选接口规范不能为空")));
        }
        String method = candidate.httpMethod() == null ? "" : candidate.httpMethod().toUpperCase();
        if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method)) {
            issues.add(new ContractValidationIssue("httpMethod", "HTTP 方法不在允许范围内"));
        }
        Set<String> fieldNames = new HashSet<>();
        for (int index = 0; index < candidate.fields().size(); index++) {
            CandidateFieldDefinition field = candidate.fields().get(index);
            String path = "fields[" + index + "]";
            if (field.name() != null && !fieldNames.add(field.name())) {
                issues.add(new ContractValidationIssue(path + ".name", "字段名称不能重复"));
            }
            if (field.requirement() == FieldRequirement.CONDITIONAL
                    && (field.condition() == null || field.condition().isBlank())) {
                issues.add(new ContractValidationIssue(path + ".condition", "条件必填字段必须说明触发条件"));
            }
            if (field.requirement() != FieldRequirement.CONDITIONAL
                    && field.condition() != null && !field.condition().isBlank()) {
                issues.add(new ContractValidationIssue(path + ".condition", "非条件必填字段不能携带触发条件"));
            }
        }
        return issues.isEmpty() ? ContractValidationResult.passed() : ContractValidationResult.failed(issues);
    }

    /** 校验证据是否完整且与候选文档版本一致。 */
    public ContractValidationResult validateSource(ContractCandidate candidate) {
        List<ContractValidationIssue> issues = new ArrayList<>();
        if (candidate == null) {
            return ContractValidationResult.failed(List.of(
                    new ContractValidationIssue("candidate", "候选接口规范不能为空")));
        }
        validateCitation(issues, "evidence", candidate.evidence(), candidate.sourceDocumentVersion());
        for (int index = 0; index < candidate.fields().size(); index++) {
            CandidateFieldDefinition field = candidate.fields().get(index);
            if (field.evidence() == null) {
                issues.add(new ContractValidationIssue("fields[" + index + "].evidence", "字段事实缺少来源引用"));
            } else {
                validateCitation(issues, "fields[" + index + ".evidence]",
                        List.of(field.evidence()), candidate.sourceDocumentVersion());
            }
        }
        return issues.isEmpty() ? ContractValidationResult.passed() : ContractValidationResult.failed(issues);
    }

    /** 校验引用字段、版本和原文定位。 */
    private void validateCitation(List<ContractValidationIssue> issues, String path,
                                  List<ContractEvidenceCitation> citations, String expectedVersion) {
        if (citations == null || citations.isEmpty()) {
            issues.add(new ContractValidationIssue(path, "候选事实至少需要一条来源引用"));
            return;
        }
        for (int index = 0; index < citations.size(); index++) {
            ContractEvidenceCitation citation = citations.get(index);
            String itemPath = path + "[" + index + "]";
            require(issues, itemPath + ".chunkId", citation.chunkId());
            require(issues, itemPath + ".documentId", citation.documentId());
            require(issues, itemPath + ".documentVersion", citation.documentVersion());
            require(issues, itemPath + ".locator", citation.locator());
            require(issues, itemPath + ".quote", citation.quote());
            if (expectedVersion != null && !expectedVersion.equals(citation.documentVersion())) {
                issues.add(new ContractValidationIssue(itemPath + ".documentVersion", "引用版本与候选版本不一致"));
            }
        }
    }

    /** 校验字符串字段不能为空。 */
    private void require(List<ContractValidationIssue> issues, String path, String value) {
        if (value == null || value.isBlank()) {
            issues.add(new ContractValidationIssue(path, "字段不能为空"));
        }
    }
}
