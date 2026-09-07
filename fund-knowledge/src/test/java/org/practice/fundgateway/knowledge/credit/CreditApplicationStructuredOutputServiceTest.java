package org.practice.fundgateway.knowledge.credit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** 验证授信申请结构化映射、结构校验和业务校验的分层行为。 */
class CreditApplicationStructuredOutputServiceTest {

    private final CreditApplicationStructuredOutputService service = new CreditApplicationStructuredOutputService();

    /** 合法样例必须完成映射并通过两层校验。 */
    @Test
    void validSamplePassesBothValidationLayers() {
        CreditApplication application = mapFixture(validJson());

        assertTrue(service.validateStructure(application).valid());
        assertTrue(service.validateBusiness(application).valid());
        assertEquals(new BigDecimal("1200.00"), application.applyAmt());
    }

    /** 缺少文档规定的必填字段时，结构校验失败且不应进入业务结论。 */
    @Test
    void missingRequiredFieldFailsStructureValidation() {
        CreditApplication application = mapFixture(validJson().replace(",\"applyAmt\":1200.00", ""));

        ValidationResult result = service.validateStructure(application);

        assertFalse(result.valid());
        assertEquals("applyAmt", result.issues().getFirst().field());
    }

    /** 字段完整但金额、利率和日期关系非法时，只能在业务校验层失败。 */
    @Test
    void businessInvalidSamplePassesStructureButFailsBusinessValidation() {
        CreditApplication application = mapFixture(validJson()
                .replace("\"applyAmt\":1200.00", "\"applyAmt\":-100.00")
                .replace("\"intRate\":0.2376", "\"intRate\":1.50")
                .replace("\"idTermBgn\":\"2026-01-01\"", "\"idTermBgn\":\"2029-01-01\""));

        assertTrue(service.validateStructure(application).valid());
        ValidationResult result = service.validateBusiness(application);
        assertFalse(result.valid());
        assertTrue(result.issues().stream().anyMatch(issue -> issue.field().equals("applyAmt")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.field().equals("intRate")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.field().equals("idTermEnd")));
    }

    /** 非 JSON 输入必须在映射层失败。 */
    @Test
    void malformedJsonFailsMapping() {
        CreditApplicationStructuredOutputService.MappingResult result = service.map("{not-json}");

        assertFalse(result.successful());
        assertNotNull(result.issue());
    }

    /** 构造来源于升恒授信申请字段的最小完整合成样例。 */
    private static String validJson() {
        return "{" +
                "\"billNo\":\"SYN-BY-20260908-0001\"," +
                "\"inputSrc\":\"20\"," +
                "\"custName\":\"合成客户甲\"," +
                "\"idNo\":\"SYNTHETIC-ID-0001\"," +
                "\"indivMobile\":\"13800000000\"," +
                "\"idCtry\":\"CHN\"," +
                "\"idTermBgn\":\"2026-01-01\"," +
                "\"idTermEnd\":\"2028-01-01\"," +
                "\"idType\":\"20\"," +
                "\"indivOccup\":\"技术人员\"," +
                "\"indivSex\":\"1\"," +
                "\"indivAddr\":\"合成省合成市合成区合成路1号\"," +
                "\"indivArea\":\"合成区\"," +
                "\"indivCity\":\"合成市\"," +
                "\"indivPro\":\"合成省\"," +
                "\"indivRegAddr\":\"合成省合成市合成区户籍路2号\"," +
                "\"indivRegArea\":\"合成区\"," +
                "\"indivRegCity\":\"合成市\"," +
                "\"indivRegProvince\":\"合成省\"," +
                "\"applyDt\":\"2026-09-08\"," +
                "\"applyAmt\":1200.00," +
                "\"intRate\":0.2376," +
                "\"indivRelName\":\"合成联系人\"," +
                "\"indivRelation\":\"朋友\"," +
                "\"indivRelMobile\":\"13900000000\"," +
                "\"workunit\":\"合成单位\"," +
                "\"workEmail\":\"synthetic@example.invalid\"" +
                "}";
    }

    /** 将测试 JSON 映射为结构化对象，失败则让测试直接失败。 */
    private CreditApplication mapFixture(String json) {
        CreditApplicationStructuredOutputService.MappingResult result = service.map(json);
        assertTrue(result.successful(), "测试样例必须先完成结构化映射");
        return result.application();
    }
}
