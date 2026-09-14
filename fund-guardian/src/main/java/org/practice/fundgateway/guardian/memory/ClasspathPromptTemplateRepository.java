package org.practice.fundgateway.guardian.memory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 从 classpath 读取版本化 Prompt，避免模型约束散落在业务方法中。 */
public class ClasspathPromptTemplateRepository {

    /** 读取指定名称和版本的模板。 */
    public PromptTemplate load(String name, String version) {
        String resource = "prompts/guardian/" + name + "-" + version + ".txt";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalArgumentException("Prompt 模板不存在：" + resource);
            return new PromptTemplate(name, version, new String(input.readAllBytes(), StandardCharsets.UTF_8).trim());
        } catch (IOException exception) {
            throw new IllegalStateException("Prompt 模板读取失败：" + resource, exception);
        }
    }
}
