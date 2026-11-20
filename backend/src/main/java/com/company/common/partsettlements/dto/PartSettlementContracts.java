package com.company.common.partsettlements.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PartSettlementContracts {
    private PartSettlementContracts() {}
    public record Summary(String leaderName, BigDecimal distributionRate, BigDecimal salesExpense,
        int personCount, BigDecimal profitTotal, BigDecimal partProfitAmount) {}
    public record Tab(String partId, String partName, Summary summary) {}
    public record Tabs(String yearMonth, List<Tab> items, String notice) {}
    public record Workforce(String developerId, String developerName, String employeeTypeCode,
        List<String> projectNames, BigDecimal monthlyMm, BigDecimal unitPrice, BigDecimal salary,
        BigDecimal salesExpense, BigDecimal retirementAmount, BigDecimal totalCost, BigDecimal profit,
        BigDecimal profitShare, List<String> notes, boolean missingMonthlySalary) {}
    public record Settlement(String partId, String partName, String yearMonth, Summary summary,
        List<Workforce> items, BigDecimal corpCardAmount, boolean confirmed, boolean closed,
        String confirmedBy, String confirmedByName, Instant confirmedAt, String confirmationNote, Instant modifiedAt,
        List<String> turnkeyDeveloperIds) {}
    public record TrendMonth(String yearMonth, BigDecimal monthlyMm, BigDecimal unitPrice,
        BigDecimal totalCost, BigDecimal profit, BigDecimal profitShare) {}
    public record TrendPerson(String developerId, String developerName, List<TrendMonth> months) {}
    public record TrendTotal(String yearMonth, BigDecimal partProfitAmount) {}
    public record Trends(String partId, String yearMonth, List<TrendPerson> items, List<TrendTotal> monthlyTotals) {}
    public record CardInput(BigDecimal corpCardAmount, Instant modifiedAt) {}
    public record CancelInput(String cancelReason, Instant modifiedAt) {}
    public record CloseItem(String partId, boolean confirmed, Instant modifiedAt) {}
    public record CloseCheck(String yearMonth, int totalCount, int unconfirmedCount, List<CloseItem> items) {}
    public record CloseInput(String yearMonth, List<CloseItem> items) {}
    public record CloseResult(String yearMonth, List<Settlement> items, int unconfirmedCount) {}
    public record Key(String partId, String month) {}
    public record Mutation(Key key, Instant version, BigDecimal card, BigDecimal profit, String state, String actor) {}
    public record Audit(String key, String item, String before, String after, String reason, String actor) {}
    public record Snapshot(Key key, Workforce row, String actor) {
        // MyBatis resolves record properties by their exact accessor method names.
        public String projectNames() { return String.join(", ", row.projectNames()); }
        public String notes() { return String.join(" · ", row.notes()); }
        public String getProjectNames() { return projectNames(); }
        public String getNotes() { return notes(); }
    }
    public record ExcelFile(byte[] bytes, String fileName) {}
}
