package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 단가 재협상 계산의 DB 정의 목록. 설명문은 실행하거나 복제하지 않는다. */
@Component
public class RateRenegotiationDefinitions {
    private final CalculationRuleService metadata;

    public RateRenegotiationDefinitions(CalculationRuleService metadata) {
        this.metadata = metadata;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Definition> definitions() {
        return List.of("식-00", "식-57", "식-85", "식-86", "식-90")
            .stream().map(metadata::definition).toList();
    }
}
