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
    // 사람별 급여·퇴직금·비용·이익·이익금은 개인별 원가로 셀 수 있는 민감 칸: 민감 체크가 없으면 응답에서 뺀다(명세 ⑨-1 UI-19).
    // 파트 합계(Summary)는 그대로 둔다.
    public record Workforce(String developerId, String developerName, String employeeTypeCode,
        List<String> projectNames, BigDecimal monthlyMm, BigDecimal unitPrice, @com.fasterxml.jackson.annotation.JsonInclude(value = com.fasterxml.jackson.annotation.JsonInclude.Include.CUSTOM, valueFilter = com.company.common.support.auth.SensitiveValueFilter.class) BigDecimal salary,
        BigDecimal salesExpense, @com.fasterxml.jackson.annotation.JsonInclude(value = com.fasterxml.jackson.annotation.JsonInclude.Include.CUSTOM, valueFilter = com.company.common.support.auth.SensitiveValueFilter.class) BigDecimal retirementAmount, @com.fasterxml.jackson.annotation.JsonInclude(value = com.fasterxml.jackson.annotation.JsonInclude.Include.CUSTOM, valueFilter = com.company.common.support.auth.SensitiveValueFilter.class) BigDecimal totalCost,
        @com.fasterxml.jackson.annotation.JsonInclude(value = com.fasterxml.jackson.annotation.JsonInclude.Include.CUSTOM, valueFilter = com.company.common.support.auth.SensitiveValueFilter.class) BigDecimal profit,
        @com.fasterxml.jackson.annotation.JsonInclude(value = com.fasterxml.jackson.annotation.JsonInclude.Include.CUSTOM, valueFilter = com.company.common.support.auth.SensitiveValueFilter.class) BigDecimal profitShare, List<String> notes, boolean missingMonthlySalary) {}
    public record Settlement(String partId, String partName, String yearMonth, Summary summary,
        List<Workforce> items, BigDecimal corpCardAmount, boolean confirmed, boolean closed,
        String confirmedBy, String confirmedByName, Instant confirmedAt, String confirmationNote, Instant modifiedAt,
        List<String> turnkeyDeveloperIds) {}
    public record TrendMonth(String yearMonth, BigDecimal monthlyMm, BigDecimal unitPrice,
        @com.fasterxml.jackson.annotation.JsonInclude(value = com.fasterxml.jackson.annotation.JsonInclude.Include.CUSTOM, valueFilter = com.company.common.support.auth.SensitiveValueFilter.class) BigDecimal totalCost, @com.fasterxml.jackson.annotation.JsonInclude(value = com.fasterxml.jackson.annotation.JsonInclude.Include.CUSTOM, valueFilter = com.company.common.support.auth.SensitiveValueFilter.class) BigDecimal profit,
        @com.fasterxml.jackson.annotation.JsonInclude(value = com.fasterxml.jackson.annotation.JsonInclude.Include.CUSTOM, valueFilter = com.company.common.support.auth.SensitiveValueFilter.class) BigDecimal profitShare) {}
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
