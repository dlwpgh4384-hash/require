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
    /**
     * 목록 한 줄. 내부 프로젝트도 누적 원가(totalCost)를 주고 화면은 「연구개발비」를 붙인다.
     * remainingCost·remainingCostBasis는 진행 중인 턴키의 남은 예상 원가(식-20)와 마우스 근거,
     * primaryCause*는 경보 칸 아래 「주원인」(식-91), *Months는 상세 창 경과 칸에 쓴다.
     */
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
        BigDecimal remainingCost, String remainingCostBasis,
        String primaryCauseName, BigDecimal primaryCauseAmount,
        int elapsedMonths, int totalMonths, int remainingMonths, String startDate, String endDate) {}
    public record ProjectOption(String projectId, String projectName) {}
    /** monthlyMarginNotice: 작업중 달에 급여가 없을 때 월간 마진을 계산하지 않는다는 안내(없으면 null). */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CommonProjectCostAnalysisResult(CommonProjectCostAnalysisSummary summary,
        List<CommonProjectCostAnalysisRow> items, int totalCount, List<ProjectOption> projectOptions,
        BigDecimal targetMarginRate, String monthlyMarginNotice) {}
    /** 월별 원가 한 줄: 매출은 월단위·파견, 배분 기준 매출(참고)은 턴키(식-25), 내부는 둘 다 null. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CommonProjectCostAnalysisMonthlyCost(String yearMonth, BigDecimal mm, BigDecimal cost,
        BigDecimal revenue, BigDecimal distributionRevenue, BigDecimal marginRate) {}
    public record CommonProjectCostAnalysisMonthlyCosts(List<CommonProjectCostAnalysisMonthlyCost> items) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CommonProjectCostAnalysisWorkforceRow(String developerId, String developerName,
        String jobName, BigDecimal monthlyMm, BigDecimal allocatedCost, BigDecimal revenue,
        String inputTypeCode, String inputTypeName) {}
    public record CommonProjectCostAnalysisWorkforce(List<CommonProjectCostAnalysisWorkforceRow> items) {}
    public record CommonProjectCostAnalysisSimulation(boolean virtual, BigDecimal marginRateBefore,
        BigDecimal marginRateAfter, BigDecimal marginRateChange, BigDecimal addedRevenue,
        BigDecimal addedCost, BigDecimal targetMarginRate) {}
    /** 원가 초과 원인 한 줄(식-91). 이동 단추가 없는 원인은 targetScreenId·targetLabel·navigationContext가 null. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CommonProjectCostAnalysisCause(String causeKey, String causeName, BigDecimal amount,
        String description, String owner, String task, String targetScreenId, String targetLabel,
        String navigationContext) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record CommonProjectCostAnalysisCauses(List<CommonProjectCostAnalysisCause> items,
        BigDecimal actualProgressRate, BigDecimal expectedCostAtProgress, BigDecimal cumulativeCost,
        BigDecimal progressExcessAmount, BigDecimal remainderAmount, String primaryBarColor,
        String referenceNotice, BigDecimal costOverRate) {}
    public record ExcelFile(byte[] bytes, String fileName, int count) {}
}
