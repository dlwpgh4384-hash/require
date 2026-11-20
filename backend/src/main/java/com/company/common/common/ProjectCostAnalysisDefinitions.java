package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 프로젝트 원가 분석의 정의 목록. 계산은 소비 측의 형식화된 업무 규칙이 수행한다. */
@Component
public class ProjectCostAnalysisDefinitions {
    private final CalculationRuleService metadata;

    public ProjectCostAnalysisDefinitions(CalculationRuleService metadata) {
        this.metadata = metadata;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Definition> definitions() {
        return List.of(
            "식-00", "식-02", "식-09", "식-10", "식-11", "식-13", "식-15", "식-16",
            "식-18", "식-19", "식-20", "식-21", "식-22", "식-23", "식-24", "식-25",
            "식-26", "식-27", "식-28", "식-29", "식-31", "식-53", "식-55", "식-56",
            "식-57", "식-75", "식-87", "식-88", "식-90", "식-91", "식-94"
        ).stream().map(metadata::definition).toList();
    }
}
