package com.company.common.raterenegotiations.dto;

import com.company.common.support.auth.SensitiveValueFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class RateRenegotiationContracts {
    private RateRenegotiationContracts() {}

    public record Filter(Integer renewalMonths, BigDecimal profitRateThreshold,
        String employeeTypeCode, String projectId, BigDecimal increaseRate) {}
    public record Row(String assignmentId, String developerId, String developerName,
        String employeeTypeName, String projectName, LocalDate renewalDate, int daysUntilRenewal,
        // 민감 칸(명세 ⑨-1, 시안 canRate·canSens, 파트장 예외 없음): 현재 단가(월 단가)·월 원가(개인별 원가)·목표 단가(월 원가로
        // 셀 수 있는 금액)는 민감 체크가 없으면 응답에서 뺀다. 이익률·필요 인상률·연 이익은 보인다(SFR-132-05).
        BigDecimal monthlyMm,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SensitiveValueFilter.class) BigDecimal currentUnitPrice,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SensitiveValueFilter.class) BigDecimal monthlyCost,
        BigDecimal profitRate,
        @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = SensitiveValueFilter.class) BigDecimal targetUnitPrice,
        BigDecimal requiredIncreaseRate,
        BigDecimal annualProfitIncrease, String projectId, String managementNumber) {}
    /** targetCount: 재협상 대상 줄 수(투입 단위, 시안 KPI, 2026-11-24 사용자 결정). */
    public record Summary(int targetCount, BigDecimal averageProfitRate, BigDecimal targetProfitRate,
        BigDecimal assumedAnnualProfitIncrease, BigDecimal targetAnnualProfitIncrease) {}
    public record FormulaHelp(String renewalDate, String monthlyCost, String profitRate,
        String targetUnitPrice, String requiredIncreaseRate, String annualProfitIncrease) {}
    public record Result(String baseYearMonth, BigDecimal appliedIncreaseRate, List<Row> items,
        Summary summary, BigDecimal totalAnnualProfitIncrease, FormulaHelp formulaHelp,
        List<String> salaryEstimatedDeveloperIds, List<ProjectOption> projectOptions) {}
    /** 조건 「프로젝트」 목록: 재협상 대상이 될 수 있는 프로젝트만(월단위·파견 진행 중, 무상지원 아닌 투입, 종료일 ≥ 오늘. 시안·SFR-132-02). */
    public record ProjectOption(String projectId, String projectName) {}
    public record ExcelFile(byte[] bytes, String fileName, int count) {}
}
