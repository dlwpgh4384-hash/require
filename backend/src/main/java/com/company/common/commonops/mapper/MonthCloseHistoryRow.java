package com.company.common.commonops.mapper;

import java.time.Instant;

public record MonthCloseHistoryRow(String month, String actionCode, String processor,
    Instant occurredAt, String reason, String remainingNames) {}
