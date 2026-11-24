package com.company.common.salesopportunities;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.dto.CalculationRuleContracts.Standard;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.*;
import com.company.common.salesopportunities.dto.SalesOpportunityContracts.Need;
import com.company.common.salesopportunities.mapper.SalesOpportunityMapper;
import com.company.common.salesopportunities.service.SalesOpportunityCalculations;
import com.company.common.support.service.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesOpportunityCalculationsTest {
    final SalesPipelineDefinitions definitions = mock(SalesPipelineDefinitions.class);
    final SalesPipelineApprovalStandards approvals = mock(SalesPipelineApprovalStandards.class);
    final CalculationRuleService standards = mock(CalculationRuleService.class);
    final SalesOpportunityMapper mapper = mock(SalesOpportunityMapper.class);
    final BusinessCalendar calendar = mock(BusinessCalendar.class);
    final SalesOpportunityCalculations calculations = new SalesOpportunityCalculations(definitions, approvals, standards, mapper, calendar);
    final YearMonth month = YearMonth.of(2026, 11);
    @BeforeEach void setup() {
        when(calendar.currentMonth()).thenReturn(month);
        when(definitions.definitions()).thenReturn(List.of(
            new Definition("식-83", "같은 직무·등급", "만 원"),
            new Definition("식-84", "인원 × 월 MM × 개월, 예상 매출 − 예상 원가 × 100", "계산"),
            new Definition("식-86", "반려 AUTO_REL", "계산")));
        when(approvals.effective(month)).thenReturn(new SalesPipelineApprovalStandards.Values(new BigDecimal("15"), new BigDecimal("0.5")));
        when(mapper.monthlyCost("BE", null)).thenReturn(new BigDecimal("8500000"));
        when(mapper.defaultPrice("BE")).thenReturn(new BigDecimal("10000000"));
    }
    Need need() { return new Need(null, "BE", null, 2, "2026-11", 6, BigDecimal.ONE, null, List.of()); }
    @Test void storedDefinitionAndDatabaseCostsProduceExactBoundaryProfit() {
        var result = calculations.evaluate(List.of(need()));
        assertThat(result.profitRate()).isEqualByComparingTo("15");
        verify(mapper).monthlyCost("BE", null);
        verify(approvals).effective(month);
    }
    @Test void missingPayrollCostIsFieldErrorNotZeroOrFallback() {
        when(mapper.monthlyCost("BE", null)).thenReturn(null);
        assertThatThrownBy(() -> calculations.evaluate(List.of(need()))).isInstanceOfSatisfying(CodeFailure.class,
            e -> assertThat(e.getField()).isEqualTo("needs[0].jobCode"));
    }
    @Test void missingJobPriceIsFieldErrorNotSampleRate() {
        when(mapper.defaultPrice("BE")).thenReturn(null);
        assertThatThrownBy(() -> calculations.evaluate(List.of(need()))).isInstanceOf(CodeFailure.class);
    }
    @Test void unsupportedDefinitionIsRejectedBeforeCostRead() {
        when(definitions.definitions()).thenReturn(List.of(new Definition("식-83", "지원되지 않는 식", "자료")));
        assertThatThrownBy(() -> calculations.evaluate(List.of(need()))).isInstanceOf(CodeFailure.class);
        verify(mapper, never()).monthlyCost(anyString(), any());
    }
    @Test void stageProbabilityUsesCurrentMonthEffectiveStandard() {
        when(standards.effectiveStandard("STEP_PRBL_2", month)).thenReturn(new Standard("STEP_PRBL_2", month, new BigDecimal("37")));
        assertThat(calculations.stageProbability("PROP")).isEqualByComparingTo("37");
    }
}
