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
        parse(toolInput, errorMessage);
    }

    /** 解析并校验工具目标，供权限检查在工具回调前复用。 */
    static Target parse(String toolInput, String errorMessage) {
        try {
            Map<?, ?> request = MAPPER.readValue(toolInput, Map.class);
            Object provider = request.get("provider");
            Object interfaceName = request.get("interface");
            if (!(provider instanceof String providerValue)
                    || !(interfaceName instanceof String interfaceValue)
                    || providerValue.isBlank() || interfaceValue.isBlank()) {
                throw new IllegalArgumentException(errorMessage);
            }
            if (!("NYXJ".equals(providerValue) || "synthetic-provider".equals(providerValue))
                    || !"credit-apply".equals(interfaceValue)) {
                throw new IllegalArgumentException(errorMessage);
            }
            return new Target("NYXJ", interfaceValue);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(errorMessage, exception);
        }
    }

    /** 工具请求中经过结构校验的资源目标。 */
    record Target(String provider, String interfaceName) {
    }
}
