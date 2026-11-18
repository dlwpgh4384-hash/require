package com.company.common.manpowercomparisons.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ManpowerComparisonRows {
    private ManpowerComparisonRows() {}

    public record Source(String projectId, String projectName, String jobCode, String jobName,
        LocalDate startDate, LocalDate endDate, BigDecimal plannedMm, BigDecimal actualMm) {}
}
