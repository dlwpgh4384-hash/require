package com.company.common.projectcostanalyses.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

public final class ProjectCostAnalysisContracts {
    private ProjectCostAnalysisContracts() {}
    public record Filter(Integer year, Integer baseMonth, String projectId,
        String contractTypeCode, String projectStatus, Boolean alertOnly) {}
    public record Period(Integer year, Integer baseMonth) {}
    public record SimulationInput(Integer year, Integer baseMonth, BigDecimal headcountChange,
        BigDecimal monthlyCostPerPerson, Integer months, BigDecimal monthlyPricePerPerson) {}
    public record CommonProjectCostAnalysisSummary(BigDecimal cumulativeRevenue, BigDecimal cumulativeProfit,
        int lowMarginCount, int budgetExceededCount, BigDecimal researchCost) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CommonProjectCostAnalysisRow(String projectId, String projectName,
        String contractTypeCode, String contractTypeName, String projectStatus,
        BigDecimal originalContractAmount, BigDecimal contractAmount, BigDecimal plannedMm,
        BigDecimal actualMm, BigDecimal cumulativeRevenue, BigDecimal totalCost,
        BigDecimal cumulativeProfit, BigDecimal cumulativeMarginRate, BigDecimal expectedFinalMarginRate,
        BigDecimal monthlyMarginRate, BigDecimal targetDifference, BigDecimal budgetUsageRate,
        BigDecimal unitPricePerMm, BigDecimal costPerMm, BigDecimal profitPerMm,
        BigDecimal freeSupportMm, BigDecimal freeSupportCost, BigDecimal researchCost,
        boolean lowMargin, boolean budgetExceeded, int alertCount, List<String> alerts,
        String budgetColor, boolean missingMonthlyCost, String missingMonthlyCostReason,
        String costComposition) {}
    public record ProjectOption(String projectId, String projectName) {}
    public record CommonProjectCostAnalysisResult(CommonProjectCostAnalysisSummary summary,
        List<CommonProjectCostAnalysisRow> items, int totalCount, List<ProjectOption> projectOptions) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CommonProjectCostAnalysisMonthlyCost(String yearMonth, BigDecimal cost,
        BigDecimal distributionRevenue) {}
    public record CommonProjectCostAnalysisMonthlyCosts(List<CommonProjectCostAnalysisMonthlyCost> items) {}
    public record CommonProjectCostAnalysisWorkforceRow(String developerId, String developerName,
        String jobName, BigDecimal monthlyMm) {}
    public record CommonProjectCostAnalysisWorkforce(List<CommonProjectCostAnalysisWorkforceRow> items) {}
    public record CommonProjectCostAnalysisSimulation(boolean virtual, BigDecimal marginRateBefore,
        BigDecimal marginRateAfter, BigDecimal marginRateChange, String calculationBasis) {}
    public record CommonProjectCostAnalysisCause(String causeName, BigDecimal amount, String task,
        String targetScreenId, String navigationContext) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CommonProjectCostAnalysisCauses(List<CommonProjectCostAnalysisCause> items,
        BigDecimal progressExcessAmount, String remainderExplanation, String primaryBarColor,
        String referenceNotice) {}
    public record ExcelFile(byte[] bytes, String fileName, int count) {}
}
