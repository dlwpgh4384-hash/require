package com.company.common.calculation.mapper;

import com.company.common.calculation.dto.CalculationRuleContracts.Standard;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public class CalculationStandardRow {
    public String standardCode;
    public String effectiveMonth;
    public BigDecimal value;

    public Standard value() {
        return new Standard(standardCode, YearMonth.parse(effectiveMonth, DateTimeFormatter.ofPattern("uuuuMM")), value);
    }
}
