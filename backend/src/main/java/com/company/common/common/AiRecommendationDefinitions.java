package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AiRecommendationDefinitions {
    private static final List<String> IDS = List.of(
        "식-00", "식-68", "식-69", "식-70", "식-71", "식-79", "식-80"
    );
    private static final Map<String, String> STANDARDS = Map.of(
        "job", "AI_SCORE_JOB", "skill", "AI_SCORE_SKILL",
        "customerIndustry", "AI_SCORE_CUST", "grade", "AI_SCORE_GRADE",
        "performance", "AI_SCORE_PERF", "cost", "AI_SCORE_COST",
        "availability", "AI_SCORE_AVAIL", "workplace", "AI_SCORE_LOC"
    );
    private final CalculationRuleService metadata;
    private final BusinessCalendar calendar;

    public AiRecommendationDefinitions(CalculationRuleService metadata, BusinessCalendar calendar) {
        this.metadata = metadata;
        this.calendar = calendar;
    }

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

    public Map<String, BigDecimal> currentMaxima() {
        var month = calendar.currentMonth();
        var result = new LinkedHashMap<String, BigDecimal>();
        STANDARDS.forEach((dimension, code) -> {
            var standard = metadata.effectiveStandard(code, month);
            if (standard == null || !code.equals(standard.standardCode())
                || standard.effectiveMonth() == null || standard.effectiveMonth().isAfter(month)
                || standard.value() == null || standard.value().signum() < 0) {
                throw CodeFailure.invalid("standardCode", "AI 추천 배점을 확인해 주세요.");
            }
            result.put(dimension, standard.value());
        });
        if (result.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
            .compareTo(new BigDecimal("100")) != 0) {
            throw CodeFailure.invalid("standardCode", "AI 추천 배점 합계는 100이어야 합니다.");
        }
        return Map.copyOf(result);
    }
}
