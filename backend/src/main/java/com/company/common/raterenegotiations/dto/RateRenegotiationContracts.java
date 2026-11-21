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
    public record Summary(int targetCount, BigDecimal averageProfitRate, BigDecimal targetProfitRate,
        BigDecimal assumedAnnualProfitIncrease, BigDecimal targetAnnualProfitIncrease) {}
    public record FormulaHelp(String renewalDate, String monthlyCost, String profitRate,
        String targetUnitPrice, String requiredIncreaseRate, String annualProfitIncrease) {}
    public record Result(String baseYearMonth, BigDecimal appliedIncreaseRate, List<Row> items,
        Summary summary, BigDecimal totalAnnualProfitIncrease, FormulaHelp formulaHelp,
        List<String> salaryEstimatedDeveloperIds) {}
    public record ExcelFile(byte[] bytes, String fileName, int count) {}
}
