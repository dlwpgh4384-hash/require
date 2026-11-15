package com.company.common.developers.service;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeveloperManMonthRulesTest {
    private CalculationRuleService metadata;
    private DeveloperManMonthRules rules;
    @BeforeEach void setup() {
        metadata=mock(CalculationRuleService.class);rules=new DeveloperManMonthRules(metadata);
        when(metadata.definition("식-00")).thenReturn(definition("식-00","원 단위 정수, 소수 반올림"));
        when(metadata.definition("식-01")).thenReturn(definition("식-01","월 MM = 계약 MM × (그 달 투입일수 ÷ 그 달 달력일수), 소수 둘째 자리 반올림"));
        when(metadata.definition("식-77")).thenReturn(definition("식-77","철수 예정 = 0 ≤ D-n 이고 철수일이 속한 달 ≤ 이번 달 + 2개월"));
        when(metadata.definition("식-95")).thenReturn(definition("식-95","경고 = 한 달 MM 합 > 1.0."));
    }
    private Definition definition(String id,String formula) { return new Definition(id,formula,"상세"); }
    @Test void includesBothEndpointsAndRoundsToTwoPlaces() {
        assertThat(rules.prorate(new BigDecimal("15"),30)).isEqualByComparingTo("0.50");
        assertThat(rules.prorate(new BigDecimal("1"),30)).isEqualByComparingTo("0.03");
        verify(metadata,times(2)).definition("식-00");verify(metadata,times(2)).definition("식-01");
    }
    @Test void exactOneDoesNotWarnAndStrictlyMoreDoes() { assertThat(rules.over(BigDecimal.ONE)).isFalse();assertThat(rules.over(new BigDecimal("1.01"))).isTrue(); }
    @Test void changedDefinitionChangesThresholdWithoutCodeExecution() {
        when(metadata.definition("식-95")).thenReturn(definition("식-95","경고 = 한 달 MM 합 > 1.5."));
        assertThat(rules.over(new BigDecimal("1.2"))).isFalse();assertThat(rules.over(new BigDecimal("1.51"))).isTrue();
    }
    @Test void horizonIsCalendarMonthEndNotNinetyDays() {
        LocalDate today=LocalDate.of(2026,11,1);
        assertThat(rules.withdrawalSoon(LocalDate.of(2027,1,31),today)).isTrue();
        assertThat(rules.withdrawalSoon(LocalDate.of(2027,2,1),today)).isFalse();
        assertThat(rules.withdrawalSoon(today.minusDays(1),today)).isFalse();
    }
    @Test void unsupportedDefinitionFailsClosed() {
        when(metadata.definition("식-01")).thenReturn(definition("식-01","arbitrary executable expression"));
        assertThatThrownBy(() -> rules.prorate(BigDecimal.ONE,30)).isInstanceOf(IllegalStateException.class);
    }
}
