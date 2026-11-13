package com.company.common.mapper;

import com.company.common.dto.CalculationRuleContracts.Definition;

public class CalculationRuleRow {
    public String calcId;
    public String formula;
    public String details;

    public Definition value() { return new Definition(calcId, formula, details); }
}
