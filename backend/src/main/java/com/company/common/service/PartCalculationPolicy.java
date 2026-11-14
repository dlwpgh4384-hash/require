package com.company.common.service;

import com.company.common.dto.CalculationRuleContracts.Definition;
import com.company.common.dto.CalculationRuleContracts.EffectiveValue;
import com.company.common.dto.CalculationRuleContracts.ValueRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PartCalculationPolicy {
    private final CalculationRuleService calculations;

    public PartCalculationPolicy(CalculationRuleService calculations) { this.calculations = calculations; }

    public Definition commonPrinciples() { return calculations.definition("식-00"); }
    public Definition definition() { return calculations.definition("식-45"); }

    public EffectiveValue resolve(ValueRequest request) {
        commonPrinciples();
        definition();
        return calculations.resolve(request);
    }
}
