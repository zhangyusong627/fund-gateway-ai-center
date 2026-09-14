package org.practice.fundgateway.guardian.memory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 验证 Prompt 可以按名称和版本从资源读取。 */
class ClasspathPromptTemplateRepositoryTest {

    @Test
    void shouldLoadVersionedPrompt() {
        PromptTemplate template = new ClasspathPromptTemplateRepository()
                .load("diagnosis-system", "v1");

        assertTrue(template.content().contains("固定 JSON"));
    }
}
