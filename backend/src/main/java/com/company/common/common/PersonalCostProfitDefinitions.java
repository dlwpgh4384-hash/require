package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 개인 원가·수익 계산에 쓰는 DB 정의. 설명문을 실행하거나 식 내용을 복사하지 않는다. */
@Component
public class PersonalCostProfitDefinitions {
    private final CalculationRuleService metadata;

    public PersonalCostProfitDefinitions(CalculationRuleService metadata) {
        this.metadata = metadata;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Definition> definitions() {
        return List.of(
            "식-00", "식-01", "식-02", "식-07", "식-08", "식-09", "식-10", "식-11",
            "식-12", "식-13", "식-14", "식-15", "식-16", "식-17", "식-19", "식-20",
            "식-21", "식-23", "식-24", "식-25", "식-27", "식-30", "식-34", "식-35",
            "식-37", "식-38", "식-39", "식-40", "식-53", "식-55", "식-57", "식-66",
            "식-95", "식-96"
        ).stream().map(metadata::definition).toList();
    }
}
