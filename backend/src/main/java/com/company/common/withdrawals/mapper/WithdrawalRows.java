package com.company.common.withdrawals.mapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class WithdrawalRows {
    private WithdrawalRows() {}
    public record Source(String assignmentId, String developerId, String name, String projectName,
        String jobCode, String jobName, LocalDate endDate, String extensionPossibilityCode,
        int extensionCount, Instant modifiedAt, String statusCode, String primaryJobCode,
        String gradeCode, String customerId) {}
    public record ListFilter(int year, LocalDate from, LocalDate through,
        String extensionPossibilityCode, String jobCode) {}
    public record SharedFilter(String developerId, String opportunityId) {}
    public record Need(String opportunityId, int needSn, String opportunityName, String jobCode,
        String jobName, String gradeCode, String gradeName, String startMonth, int durationMonths,
        int requiredCount, int occupiedCount, String statusCode, String customerId,
        BigDecimal winningProbability, BigDecimal monthlyMm) {}
    public record Reservation(String tentativeId, String developerId, String opportunityId,
        String opportunityName, int needSn, String startMonth, String statusCode) {}
    public record ReservationWrite(String developerId, String opportunityId, int needSn,
        String startMonth, String actor) {}
    public record MonthFilter(String jobCode, String month, LocalDate firstDay, LocalDate lastDay) {}
    public record SupplyRow(String developerId, BigDecimal confirmedMm) {}
    public record ExtensionRow(String developerId, BigDecimal monthlyMm, String extensionPossibilityCode) {}
    public record OpportunityDemandRow(BigDecimal requiredCount, BigDecimal monthlyMm, BigDecimal winningProbability) {}
    public record ProjectDemandRow(BigDecimal plannedMm, BigDecimal confirmedMm, int remainingMonths) {}
}
