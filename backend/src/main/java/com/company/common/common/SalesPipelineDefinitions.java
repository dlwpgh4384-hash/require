package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.support.service.CodeFailure;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SalesPipelineDefinitions {
    private static final List<String> IDS = List.of(
        "식-00", "식-60", "식-61", "식-79", "식-81", "식-82",
        "식-83", "식-84", "식-85", "식-86", "식-92", "식-93");
    private final CalculationRuleService metadata;

    public SalesPipelineDefinitions(CalculationRuleService metadata) {
        this.metadata = metadata;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Definition> definitions() {
        return IDS.stream().map(id -> {
            var definition = metadata.definition(id);
            if (definition == null || !id.equals(definition.calcId())
                || definition.formula() == null || definition.formula().isBlank()
                || definition.details() == null || definition.details().isBlank()) {
                throw CodeFailure.invalid("calcId", id + " 계산 정의를 확인해 주세요.");
            }
            return definition;
        }).toList();
    }
}
