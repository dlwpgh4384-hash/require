package com.company.common.commonops.dto;

import java.time.Instant;
import java.util.List;

public final class MonthCloseReadContracts {
    private MonthCloseReadContracts() {}

    public record Badge(boolean needsAttention) {}
    public record History(List<HistoryItem> items) {}
    public record HistoryItem(String yearMonth, String action, String processor,
        Instant occurredAt, String reason, List<String> remainingChecks) {}
}
