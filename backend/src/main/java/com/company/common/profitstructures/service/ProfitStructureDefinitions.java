package com.company.common.profitstructures.service;

import com.company.common.calculation.service.CalculationRuleService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ProfitStructureDefinitions {
    public static final List<String> IDS = List.of("식-00", "식-01", "식-02", "식-04", "식-08", "식-09",
        "식-10", "식-11", "식-12", "식-13", "식-14", "식-15", "식-17", "식-22", "식-25", "식-28",
        "식-29", "식-30", "식-31", "식-32", "식-33", "식-34", "식-35", "식-57", "식-58", "식-59");
    private final CalculationRuleService calculation;
    public ProfitStructureDefinitions(CalculationRuleService calculation) { this.calculation = calculation; }
    public void require() { IDS.forEach(calculation::definition); }
}
