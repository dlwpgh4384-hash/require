package com.company.common.manpowercomparisons.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ManpowerComparisonContracts {
    private ManpowerComparisonContracts() {}

    public record Filter(Integer year, String projectId, String differenceStatus) {}
    public record Row(String projectId, String projectName, String jobCode, String jobName,
        BigDecimal plannedMm, BigDecimal actualMm, BigDecimal differenceMm, String differenceStatus) {}
    public record Totals(BigDecimal plannedMm, BigDecimal actualMm, BigDecimal differenceMm) {}
    public record Result(List<Row> items, Totals totals) {}
}
