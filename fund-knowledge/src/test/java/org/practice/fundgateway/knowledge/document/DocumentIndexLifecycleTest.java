package org.practice.fundgateway.knowledge.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** 验证文档登记、解析和索引状态只能按顺序流转。 */
class DocumentIndexLifecycleTest {

    /** 验证正常状态链路。 */
    @Test
    void shouldFollowRegisteredParsedIndexed() {
        DocumentIndexLifecycle lifecycle = new DocumentIndexLifecycle();
        lifecycle.markParsed();
        lifecycle.markIndexed();
        assertEquals(DocumentIndexStatus.INDEXED, lifecycle.status());
    }

    /** 验证未解析文档不能直接进入索引状态。 */
    @Test
    void shouldRejectIndexBeforeParse() {
        DocumentIndexLifecycle lifecycle = new DocumentIndexLifecycle();
        assertThrows(IllegalStateException.class, lifecycle::markIndexed);
    }
}
