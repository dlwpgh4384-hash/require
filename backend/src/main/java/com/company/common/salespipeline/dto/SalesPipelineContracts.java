package com.company.common.salespipeline.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SalesPipelineContracts {
    private SalesPipelineContracts() {}

    public record Items<T>(List<T> items) {}
    public record ClosedFilter(String result, boolean includeStopped) {}
    public record Summary(int ongoingCount, int staleCount, BigDecimal expectedAmount,
        BigDecimal weightedAmount, BigDecimal weightedManpower, int closedCount) {}
    public record Card(String opportunityId, String opportunityName, String customerId,
        String customerName, String stageCode, BigDecimal winningProbability, BigDecimal expectedAmount,
        String startMonth, String nextAction, LocalDate nextActionDueDate, List<String> requiredJobs,
        boolean stale, String approvalStatusCode, Instant modifiedAt, List<JobHeadcount> requiredJobHeadcounts) {}
    /** 카드 직무 칩 하나(시안 「백엔드 개발 2」): 직무 코드·이름과 그 직무 필요 인원 합. */
    public record JobHeadcount(String jobCode, String jobName, int headcount) {}
    public record Stage(String stageCode, int count, BigDecimal amount, List<Card> items,
        String stageName, BigDecimal defaultProbability) {}
    public record Pipeline(Summary summary, List<Stage> stages) {}
    public record Closed(String opportunityId, String opportunityName, String customerName,
        String result, BigDecimal expectedAmount, LocalDate resultDate, String projectId,
        String lossReason) {}
    public record NeedQuote(int rowIndex, BigDecimal minimumUnitPrice, BigDecimal targetUnitPrice) {}
    public record Quote(BigDecimal expectedRevenue, BigDecimal expectedCost, BigDecimal profitRate,
        BigDecimal minimumUnitPrice, BigDecimal targetUnitPrice, BigDecimal breakEvenAmount,
        BigDecimal targetAmount, BigDecimal approvalRate, BigDecimal reapprovalGap, String profitBand,
        List<NeedQuote> needQuotes) {}
    public record ForecastMonth(String month, BigDecimal projectRevenue,
        BigDecimal weightedOpportunityRevenue, BigDecimal allWonRevenue, BigDecimal demand,
        BigDecimal supply) {}
    public record HiringReview(String jobCode, String jobName, String shortageStartMonth,
        BigDecimal maximumShortage, String recommendedHiringMonth, int candidateCount) {}
    /** 월별 예상 매출의 기회 한 줄(시안 「└ 기회명 n%」). months는 예상 기간 안의 달만, 금액은 원 단위. */
    public record ForecastOpportunity(String opportunityId, String opportunityName, BigDecimal winningProbability,
        List<ForecastOpportunityMonth> months) {}
    public record ForecastOpportunityMonth(String month, BigDecimal weightedRevenue) {}
    public record Forecast(List<ForecastMonth> months, List<HiringReview> hiringReviews,
        List<ForecastOpportunity> opportunityRevenues) {}
    public record Workbook(byte[] bytes, String fileName, int count) {}
    public record WorkforceJob(String jobCode, String jobName, BigDecimal supply,
        BigDecimal demand, BigDecimal unfilledDemand) {}
    public record WorkforceMonth(String month, BigDecimal supply, BigDecimal demand,
        BigDecimal unfilledDemand, List<WorkforceJob> jobs) {}
}
