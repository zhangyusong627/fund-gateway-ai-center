package org.practice.fundgateway.guardian.memory;

/** 表示可追踪的 Prompt 模板版本。 */
public record PromptTemplate(String name, String version, String content) {

    /** 校验模板标识和正文。 */
    public PromptTemplate {
        if (name == null || name.isBlank() || version == null || version.isBlank()
                || content == null || content.isBlank()) {
            throw new IllegalArgumentException("Prompt 模板字段不完整");
        }
    }
}
