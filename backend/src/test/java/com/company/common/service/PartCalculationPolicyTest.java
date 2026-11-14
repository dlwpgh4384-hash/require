package com.company.common.service;

import com.company.common.dto.CalculationRuleContracts.*;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartCalculationPolicyTest {
    private final CalculationRuleService calculations = mock(CalculationRuleService.class);
    private final PartCalculationPolicy policy = new PartCalculationPolicy(calculations);

    @Test void readsOriginalPartFormulaFromCalculationService() {
        var definition = new Definition("식-45", "DB 원문", "DB 설명");
        when(calculations.definition("식-45")).thenReturn(definition);
        assertThat(policy.definition()).isSameAs(definition);
        verify(calculations).definition("식-45");
        verifyNoMoreInteractions(calculations);
    }
    @Test void readsOriginalCommonPrinciplesFromCalculationService() {
        var definition = new Definition("식-00", "공통 원칙", "DB 원문");
        when(calculations.definition("식-00")).thenReturn(definition);
        assertThat(policy.commonPrinciples()).isSameAs(definition);
        verify(calculations).definition("식-00");
        verifyNoMoreInteractions(calculations);
    }
    @Test void effectiveValueUsesExistingClosedAndOverrideResolutionAfterReadingBothRules() {
        var request = new ValueRequest("PART_DSTRB_RT", YearMonth.of(2026, 11), null, new BigDecimal("37.25"));
        var value = new EffectiveValue(request.closedResult(), Source.CLOSED_RESULT);
        when(calculations.resolve(request)).thenReturn(value);
        assertThat(policy.resolve(request)).isSameAs(value);
        var order = inOrder(calculations);
        order.verify(calculations).definition("식-00");
        order.verify(calculations).definition("식-45");
        order.verify(calculations).resolve(request);
        verifyNoMoreInteractions(calculations);
    }
    @Test void missingOriginalRuleDoesNotFallBackToInventedFormula() {
        when(calculations.definition("식-00")).thenThrow(CodeFailure.invalid("calcId", "정의 없음"));
        assertThatThrownBy(() -> policy.resolve(new ValueRequest("PART_DSTRB_RT", YearMonth.of(2026, 11), null, null)))
            .isInstanceOf(CodeFailure.class);
        verify(calculations, never()).resolve(any());
    }
}
