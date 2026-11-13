package com.company.common.dto;

import java.math.BigDecimal;
import java.util.List;

public final class OperatingStandardsContracts {
    private OperatingStandardsContracts() {}

    public record Item(String standardCode, String standardName, String value, String unit,
        String effectiveMonth, boolean closed, boolean immediate) {}
    public record Result(List<Item> items) {}
    public record Change(String standardCode, String value, String effectiveMonth, String action) {}
    public record Input(List<Change> items) {}
    public record Mutation(String standardCode, String month, BigDecimal value,
        String standardName, String unit, String description) {}
}
