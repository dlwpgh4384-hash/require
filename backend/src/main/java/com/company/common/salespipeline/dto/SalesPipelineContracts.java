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
        BigDecimal weightedAmount, BigDecimal weightedManpower) {}
    public record Card(String opportunityId, String opportunityName, String customerId,
        String customerName, String stageCode, BigDecimal winningProbability, BigDecimal expectedAmount,
        String startMonth, String nextAction, LocalDate nextActionDueDate, List<String> requiredJobs,
        boolean stale, String approvalStatusCode, Instant modifiedAt) {}
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
    public record Forecast(List<ForecastMonth> months, List<HiringReview> hiringReviews) {}
    public record Workbook(byte[] bytes, String fileName, int count) {}
}
