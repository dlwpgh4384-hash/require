package com.company.common.parts.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PartContracts {
    private PartContracts() {}

    public record Items<T>(List<T> items) {}
    public record Part(String partId, String partName, String leaderUserId, String leaderName,
        BigDecimal distributionRate, BigDecimal salesExpense, String effectiveMonth, String endMonth,
        String useYn, Instant modifiedAt, long personCount, long projectCount, String scheduledChange) {}
    public record Standard(String partId, String leaderUserId, String leaderName,
        BigDecimal distributionRate, BigDecimal salesExpense, String effectiveMonth, String endMonth,
        Instant modifiedAt, String state) {}
    public record Leader(String userId, String userName) {}
    public record Options(List<Leader> leaders, BigDecimal distributionRate, BigDecimal salesExpense,
        String closedMonth, String earliestMonth) {}
    public record Change(Instant changedAt, String fieldName, String beforeValue,
        String afterValue, String changedBy) {}
    public record Values(String effectiveMonth, String leaderUserId,
        BigDecimal distributionRate, BigDecimal salesExpense) {}
    public record Create(String partName, Values values) {}
    public record StandardInput(Values values, Instant modifiedAt) {}
    public record Rename(String partName, Instant modifiedAt) {}
    public record Version(Instant modifiedAt) {}
    public record PeriodMutation(String partId, String month, String endMonth, Values values) {}
}
