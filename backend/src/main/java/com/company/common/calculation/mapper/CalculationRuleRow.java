package com.company.common.calculation.mapper;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;

public class CalculationRuleRow {
    public String calcId;
    public String formula;
    public String details;

    public Definition value() { return new Definition(calcId, formula, details); }
}
