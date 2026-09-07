package org.practice.fundgateway.knowledge.credit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.json.JsonMapper;

/** 演示授信申请输出的映射、结构校验和业务校验三层边界。 */
public class CreditApplicationStructuredOutputService {

    private final JsonMapper jsonMapper;

    /** 使用默认 JSON 映射器创建服务。 */
    public CreditApplicationStructuredOutputService() {
        this(JsonMapper.builder().build());
    }

    /** 使用指定 JSON 映射器创建服务，便于测试和后续接入 Spring 配置。 */
    public CreditApplicationStructuredOutputService(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /** 将 JSON 映射为 Java 结果；映射失败时返回结构化错误，不进入业务校验。 */
    public MappingResult map(String json) {
        try {
            return MappingResult.success(jsonMapper.readValue(json, CreditApplication.class));
        } catch (RuntimeException exception) {
            return MappingResult.failure(new ValidationIssue("$", "JSON 无法映射为授信申请结构"));
        }
    }

    /** 校验文档声明的必填字段、格式和类型边界。 */
    public ValidationResult validateStructure(CreditApplication application) {
        List<ValidationIssue> issues = new ArrayList<>();
        require(issues, "billNo", application.billNo());
        require(issues, "inputSrc", application.inputSrc());
        require(issues, "custName", application.custName());
        require(issues, "idNo", application.idNo());
        require(issues, "indivMobile", application.indivMobile());
        require(issues, "idCtry", application.idCtry());
        require(issues, "idTermBgn", application.idTermBgn());
        require(issues, "idTermEnd", application.idTermEnd());
        require(issues, "idType", application.idType());
        require(issues, "indivOccup", application.indivOccup());
        require(issues, "indivSex", application.indivSex());
        require(issues, "indivAddr", application.indivAddr());
        require(issues, "indivArea", application.indivArea());
        require(issues, "indivCity", application.indivCity());
        require(issues, "indivPro", application.indivPro());
        require(issues, "indivRegAddr", application.indivRegAddr());
        require(issues, "indivRegArea", application.indivRegArea());
        require(issues, "indivRegCity", application.indivRegCity());
        require(issues, "indivRegProvince", application.indivRegProvince());
        require(issues, "applyDt", application.applyDt());
        require(issues, "applyAmt", application.applyAmt());
        require(issues, "intRate", application.intRate());
        require(issues, "indivRelName", application.indivRelName());
        require(issues, "indivRelation", application.indivRelation());
        require(issues, "indivRelMobile", application.indivRelMobile());
        return issues.isEmpty() ? ValidationResult.passed() : ValidationResult.failed(issues);
    }

    /** 校验金额、利率、日期和文档枚举等业务规则。 */
    public ValidationResult validateBusiness(CreditApplication application) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (application.applyAmt() != null && application.applyAmt().signum() <= 0) {
            issues.add(new ValidationIssue("applyAmt", "授信申请金额必须大于 0"));
        }
        if (application.intRate() != null
                && (application.intRate().signum() < 0 || application.intRate().compareTo(BigDecimal.ONE) > 0)) {
            issues.add(new ValidationIssue("intRate", "授信申请利率必须在 0 到 1 之间"));
        }
        checkDate(issues, "applyDt", application.applyDt());
        LocalDate start = parseDate(issues, "idTermBgn", application.idTermBgn());
        LocalDate end = "9999-12-31".equals(application.idTermEnd())
                ? LocalDate.of(9999, 12, 31)
                : parseDate(issues, "idTermEnd", application.idTermEnd());
        if (start != null && end != null && start.isAfter(end)) {
            issues.add(new ValidationIssue("idTermEnd", "证件有效期结束日期不能早于开始日期"));
        }
        if (application.indivSex() != null && !List.of("1", "2").contains(application.indivSex())) {
            issues.add(new ValidationIssue("indivSex", "性别只能是 1 或 2"));
        }
        return issues.isEmpty() ? ValidationResult.passed() : ValidationResult.failed(issues);
    }

    /** 校验字符串或对象是否存在。 */
    private static void require(List<ValidationIssue> issues, String field, Object value) {
        if (value == null || value instanceof String text && text.isBlank()) {
            issues.add(new ValidationIssue(field, "文档规定的必填字段缺失"));
        }
    }

    /** 校验日期字段是否符合 ISO 日期格式。 */
    private static void checkDate(List<ValidationIssue> issues, String field, String value) {
        parseDate(issues, field, value);
    }

    /** 解析日期并把格式问题转为可定位的业务校验错误。 */
    private static LocalDate parseDate(List<ValidationIssue> issues, String field, String value) {
        if (value == null || "9999-12-31".equals(value)) {
            return "9999-12-31".equals(value) ? LocalDate.of(9999, 12, 31) : null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            issues.add(new ValidationIssue(field, "日期必须使用 yyyy-MM-dd 格式"));
            return null;
        }
    }

    /** 保存映射阶段的成功对象或失败问题。 */
    public record MappingResult(CreditApplication application, ValidationIssue issue) {

        /** 创建映射成功结果。 */
        public static MappingResult success(CreditApplication application) {
            return new MappingResult(application, null);
        }

        /** 创建映射失败结果。 */
        public static MappingResult failure(ValidationIssue issue) {
            return new MappingResult(null, issue);
        }

        /** 判断映射是否成功。 */
        public boolean successful() {
            return application != null;
        }
    }
}
