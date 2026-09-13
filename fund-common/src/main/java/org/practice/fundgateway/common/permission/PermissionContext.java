package org.practice.fundgateway.common.permission;

import java.util.Set;

/** 描述一次调用可访问的资方、知识、工具和审批范围，不承担真实认证职责。 */
public record PermissionContext(
        String subject,
        Set<String> providerScope,
        Set<KnowledgeScope> knowledgeScope,
        Set<String> allowedTools,
        boolean approvalAllowed) {

    /** 固定的当前合成守护工具名称，避免默认实验上下文放开未知工具。 */
    public static final Set<String> SYNTHETIC_DIAGNOSTIC_TOOLS = Set.of(
            "querySyntheticContract", "querySyntheticMetrics", "querySyntheticIncidentHistory");

    /** 校验并复制所有权限集合，防止调用方在运行中修改权限。 */
    public PermissionContext {
        if (subject == null || subject.isBlank() || providerScope == null || knowledgeScope == null
                || allowedTools == null) {
            throw new IllegalArgumentException("权限上下文字段不完整");
        }
        providerScope = immutableNonBlankSet(providerScope, "资方权限范围不能为空");
        knowledgeScope = Set.copyOf(knowledgeScope);
        allowedTools = immutableNonBlankSet(allowedTools, "工具白名单不能为空");
    }

    /** 创建当前控制台使用的合成演示权限，不代表真实用户身份。 */
    public static PermissionContext syntheticConsole() {
        return new PermissionContext("synthetic-console-demo", Set.of("synthetic-provider"),
                Set.of(new KnowledgeScope("*", "*", "*")), SYNTHETIC_DIAGNOSTIC_TOOLS, true);
    }

    /** 创建旧工具实验使用的显式合成权限上下文。 */
    public static PermissionContext syntheticDiagnostic() {
        return new PermissionContext("synthetic-diagnostic-experiment", Set.of("synthetic-provider"),
                Set.of(new KnowledgeScope("*", "*", "*")), SYNTHETIC_DIAGNOSTIC_TOOLS, false);
    }

    /** 判断是否允许访问一个资方。 */
    public boolean allowsProvider(String provider) {
        return matches(providerScope, provider);
    }

    /** 判断是否允许访问一个集合中的文档版本。 */
    public boolean allowsKnowledge(String collectionName, String documentId, String documentVersion) {
        return knowledgeScope.stream().anyMatch(scope -> scope.includes(collectionName, documentId, documentVersion));
    }

    /** 判断是否允许调用一个工具。 */
    public boolean allowsTool(String toolName) {
        return matches(allowedTools, toolName);
    }

    /** 判断集合中是否有指定值或通配符。 */
    private boolean matches(Set<String> values, String candidate) {
        return candidate != null && (values.contains("*") || values.contains(candidate));
    }

    /** 复制并校验字符串集合。 */
    private static Set<String> immutableNonBlankSet(Set<String> values, String errorMessage) {
        if (values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(errorMessage);
        }
        return Set.copyOf(values);
    }

    /** 表示集合、文档和版本的精确或通配知识范围。 */
    public record KnowledgeScope(String collectionName, String documentId, String documentVersion) {

        /** 校验知识范围，并拒绝空字符串形式的隐式全量授权。 */
        public KnowledgeScope {
            if (isBlank(collectionName) || isBlank(documentId) || isBlank(documentVersion)) {
                throw new IllegalArgumentException("知识权限范围必须显式填写集合、文档和版本");
            }
        }

        /** 判断一个知识资源是否落入当前范围。 */
        public boolean includes(String collection, String document, String version) {
            return matches(collectionName, collection) && matches(documentId, document)
                    && matches(documentVersion, version);
        }

        /** 判断单个范围值是否匹配。 */
        private static boolean matches(String expected, String actual) {
            return actual != null && ("*".equals(expected) || expected.equals(actual));
        }

        /** 判断字符串是否缺少有效内容。 */
        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }
}
