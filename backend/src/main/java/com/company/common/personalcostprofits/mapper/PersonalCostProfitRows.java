package com.company.common.personalcostprofits.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PersonalCostProfitRows {
    private PersonalCostProfitRows() {}
    public record Person(String developerId, String developerName, String affiliationId,
        String affiliationName, String positionCode, String positionName, String employeeTypeCode,
        String employeeTypeName, LocalDate joinDate, LocalDate retireDate, String jobCode,
        String jobName, String gradeCode, String gradeName, String salesPartId, String directClassCode,
        // 시안 pname(): 이름 아래 관리번호(TB_HR_DEV.MNG_NO), 정렬: 영업 파트 이름 → 이름
        String managementNumber, String salesPartName) {}
    public record Pay(String yearMonth, String developerId, String employeeTypeCode,
        BigDecimal salary, BigDecimal allowance, BigDecimal companyInsurance,
        BigDecimal retirementReserve, BigDecimal personalInsurance, BigDecimal withholdingTax,
        BigDecimal incomeTax, BigDecimal netPay, BigDecimal vendorSupply) {}
    public record Leave(String developerId, LocalDate startDate, LocalDate endDate) {}
    public record Cost(String yearMonth, String developerId, String projectId, String assignmentId,
        String projectName, String jobName, String contractTypeCode, BigDecimal mm,
        BigDecimal revenue, BigDecimal labor, BigDecimal team, BigDecimal expense,
        // 투입 구분(FREE=무상지원). 펼친 줄의 「무상지원」 표시에 쓴다. 대기(IDLE)는 null
        String inputTypeCode) {}
    public record Assignment(String yearMonth, String developerId, String projectId,
        String assignmentId, String projectName, String jobName, String contractTypeCode,
        String inputTypeCode, BigDecimal mm, BigDecimal revenue, BigDecimal team,
        BigDecimal expense, BigDecimal contractAmount, BigDecimal budget,
        BigDecimal distribution) {}
    public record Code(String groupCode, String code, String name, BigDecimal value) {}
}
