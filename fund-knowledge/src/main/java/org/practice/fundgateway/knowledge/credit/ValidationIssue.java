package org.practice.fundgateway.knowledge.credit;

/** 描述一个可定位到字段和校验层的输入问题。 */
public record ValidationIssue(String field, String message) {
}
