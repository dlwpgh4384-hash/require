package com.company.common.dto;

import java.math.BigDecimal;
import java.util.List;

public final class OperatingStandardsContracts {
    private OperatingStandardsContracts() {}

    /** changedBy: 이력 창 「처리자」(시안). 처음 값은 「처음 설정」. */
    public record Item(String standardCode, String standardName, String value, String unit,
        String effectiveMonth, boolean closed, boolean immediate, String changedBy) {}
    /** closedMonth: 마지막으로 마감한 달(YYYY-MM, 없으면 null). 저장 창 「마감한 달(2026-09)까지는…」(시안). */
    public record Result(List<Item> items, String closedMonth) {}
    public record Change(String standardCode, String value, String effectiveMonth, String action) {}
    public record Input(List<Change> items) {}
    public record Mutation(String standardCode, String month, BigDecimal value,
        String standardName, String unit, String description) {}
}
