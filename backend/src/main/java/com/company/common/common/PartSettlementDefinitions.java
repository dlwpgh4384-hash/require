package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 영업 파트 정산의 DB 정의 목록. 형식화된 계산과 응답 meta.calculationRules가 같은 원천을 사용한다. */
@Component
public class PartSettlementDefinitions {
    private final CalculationRuleService metadata;

    public PartSettlementDefinitions(CalculationRuleService metadata) {
        this.metadata = metadata;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Definition> definitions() {
        return List.of(
            "식-00", "식-01", "식-35", "식-36", "식-37", "식-39", "식-40",
            "식-41", "식-42", "식-43", "식-44", "식-45", "식-57", "식-58"
        ).stream().map(metadata::definition).toList();
    }
}
