package com.company.common.monthcloses.dto;

import java.time.Instant;
import java.util.List;

public final class MonthCloseContracts {
    private MonthCloseContracts() {}
    public record Check(String name, String status, String description, String targetScreenId) {}
    /** reopenable: the most recent TB_CL_CLOSE close, never a month implied closed before CASH_BASE_YM. */
    public record MonthClose(String yearMonth, boolean isClosed, boolean reopenable, Instant modifiedAt, Instant closedAt,
        String closedBy, int totalCount, int attentionCount, List<Check> checks) {}
    /** One TB_CL_CLOSE_CHK row: CHK_ITEM / CHK_STAT codes, count, one-line text and target screen. */
    public record StoredCheck(String itemCode, String statusCode, int count, String description, String screenId) {}
    public record ConfirmInput(Instant modifiedAt) {}
    public record ReopenInput(Instant modifiedAt, String reason) {}
    public record ReopenImpact(String yearMonth, int previousAdjustmentCount) {}
    public record Header(String state, Instant modifiedAt, Instant closedAt, String closedBy) {}
    public record Counts(int payroll, int invoices, int finance) {}
    public record Mutation(String month, String state, String actor, Instant version) {}
    public record History(String month, String action, String reason, String remaining, String actor) {}
}
