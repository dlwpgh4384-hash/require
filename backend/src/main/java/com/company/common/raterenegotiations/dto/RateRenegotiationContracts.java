package com.company.common.raterenegotiations.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class RateRenegotiationContracts {
    private RateRenegotiationContracts() {}

    public record Filter(Integer renewalMonths, BigDecimal profitRateThreshold,
        String employeeTypeCode, String projectId, BigDecimal increaseRate) {}
    public record Row(String assignmentId, String developerId, String developerName,
        String employeeTypeName, String projectName, LocalDate renewalDate, int daysUntilRenewal,
        BigDecimal monthlyMm, BigDecimal currentUnitPrice, BigDecimal monthlyCost,
        BigDecimal profitRate, BigDecimal targetUnitPrice, BigDecimal requiredIncreaseRate,
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
