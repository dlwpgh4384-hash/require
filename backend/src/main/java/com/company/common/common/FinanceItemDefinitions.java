package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FinanceItemDefinitions {
    private final CalculationRuleService metadata;

    public FinanceItemDefinitions(CalculationRuleService metadata) {
        this.metadata = metadata;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Definition> definitions() {
        return List.of(
            "식-00", "식-29", "식-31", "식-47", "식-48", "식-50", "식-57", "식-59"
        ).stream().map(metadata::definition).toList();
    }
}
