package com.company.common.calculation.dto;

import java.math.BigDecimal;
import java.time.YearMonth;

public final class CalculationRuleContracts {
    private CalculationRuleContracts() {}

    public record Definition(String calcId, String formula, String details) {}
    public record Standard(String standardCode, YearMonth effectiveMonth, BigDecimal value) {}
    public record TextResolution(String groupId, String code, String codeName, String warning) {}
    public record ValueRequest(String standardCode, YearMonth month, BigDecimal projectOverride,
                               BigDecimal closedResult) {}
    public record EffectiveValue(BigDecimal value, Source source) {}
    public enum Source { CLOSED_RESULT, PROJECT_OVERRIDE, OPERATING_STANDARD }
}
