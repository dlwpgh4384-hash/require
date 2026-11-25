package com.company.common.manpowerforecasts.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ManpowerForecastContracts {
    private ManpowerForecastContracts() {}

    public record Month(String month, BigDecimal supply, BigDecimal demand,
        BigDecimal unfilledDemand, BigDecimal difference) {}
    public record JobShortage(String jobName, BigDecimal maximumShortage) {}
    public record Summary(BigDecimal lastMonthSupply, BigDecimal lastMonthDemand,
        JobShortage mostShortage) {}
    public record Thresholds(BigDecimal shortageThreshold, BigDecimal surplusThreshold) {}
    public record Result(List<Month> months, Summary summary, Thresholds thresholds) {}
    public record Workbook(byte[] bytes, String fileName, int count) {}
}
