package org.practice.fundgateway.integration.credit;

import java.math.BigDecimal;

/** 升恒消费金融授信申请接口的结构化结果模型。 */
public record CreditApplication(
        String billNo,
        String inputSrc,
        String custName,
        String idNo,
        String indivMobile,
        String idCtry,
        String idTermBgn,
        String idTermEnd,
        String idType,
        String indivDegree,
        String indivOccup,
        String apptStartDt,
        String indivSex,
        String indivAddr,
        String indivArea,
        String indivCity,
        String indivPro,
        String indivRegAddr,
        String indivRegArea,
        String indivRegCity,
        String indivRegProvince,
        String applyDt,
        BigDecimal applyAmt,
        BigDecimal intRate,
        String workunit,
        String workEmail,
        String maritalStatus,
        String indivRelName,
        String indivRelation,
        String indivRelMobile,
        String indivOthName,
        String indivOthRel,
        String indivOthMobile) {
}
