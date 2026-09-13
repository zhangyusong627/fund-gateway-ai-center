package org.practice.fundgateway.guardian.tool;

import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

/** 统一校验合成诊断工具的 JSON 入参，避免字符串包含判断绕过边界。 */
final class SyntheticToolInput {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private SyntheticToolInput() {
    }

    /** 只接受精确的合成资方和授信申请标识。 */
    static void require(String toolInput, String errorMessage) {
        try {
            Map<?, ?> request = MAPPER.readValue(toolInput, Map.class);
            if (!"synthetic-provider".equals(request.get("provider"))
                    || !"credit-apply".equals(request.get("interface"))) {
                throw new IllegalArgumentException(errorMessage);
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(errorMessage, exception);
        }
    }
}
