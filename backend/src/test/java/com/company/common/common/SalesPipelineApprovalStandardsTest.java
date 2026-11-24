package com.company.common.common;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.mapper.CalculationStandardRow;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesPipelineApprovalStandardsTest {
    private final CalculationRuleMapper mapper = mock(CalculationRuleMapper.class);
    private final UserPreferencePeriodSourceMapper periods = mock(UserPreferencePeriodSourceMapper.class);
    private final SalesPipelineApprovalStandards standards =
        new SalesPipelineApprovalStandards(new CalculationRuleService(mapper, periods));

    private CalculationStandardRow row(String code, String month, String value) {
        var row = new CalculationStandardRow();
        row.standardCode = code;
        row.effectiveMonth = month;
        row.value = value == null ? null : new BigDecimal(value);
        return row;
    }

    private void values(String month, String rate, String gap) {
        when(mapper.selectEffectiveStandard("QUOTE_APRV_RT", month))
            .thenReturn(row("QUOTE_APRV_RT", month, rate));
        when(mapper.selectEffectiveStandard("QUOTE_REAPRV_GAP", month))
            .thenReturn(row("QUOTE_REAPRV_GAP", month, gap));
    }

    @Test
    void bothValuesUseRequestedMonthAndStoredPrecisionWithoutDefaults() {
        values("202611", "18.1250", "0.7500");
        var value = standards.effective(YearMonth.of(2026, 11));
        assertThat(value.approvalRate()).isEqualByComparingTo("18.1250");
        assertThat(value.reapprovalGap()).isEqualByComparingTo("0.7500");
        verify(mapper).selectEffectiveStandard("QUOTE_APRV_RT", "202611");
        verify(mapper).selectEffectiveStandard("QUOTE_REAPRV_GAP", "202611");
        verifyNoMoreInteractions(mapper);
        verifyNoInteractions(periods);
    }

    @Test
    void changingMonthChangesBothThresholds() {
        values("202611", "18", "0.75");
        values("202612", "21", "1.25");
        var november = standards.effective(YearMonth.of(2026, 11));
        var december = standards.effective(YearMonth.of(2026, 12));
        assertThat(december.approvalRate()).isNotEqualByComparingTo(november.approvalRate());
        assertThat(december.reapprovalGap()).isNotEqualByComparingTo(november.reapprovalGap());
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUOTE_APRV_RT", "QUOTE_REAPRV_GAP"})
    void missingStandardIsFieldErrorRatherThanFallback(String code) {
        values("202611", "18", "0.75");
        when(mapper.selectEffectiveStandard(code, "202611")).thenReturn(null);
        assertThatThrownBy(() -> standards.effective(YearMonth.of(2026, 11)))
            .isInstanceOfSatisfying(CodeFailure.class, error -> {
                assertThat(error.getField()).isEqualTo("standardCode");
                assertThat(error.getStatus()).isEqualTo(400);
            });
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUOTE_APRV_RT", "QUOTE_REAPRV_GAP"})
    void nullValueIsNotZero(String code) {
        values("202611", "18", "0.75");
        when(mapper.selectEffectiveStandard(code, "202611")).thenReturn(row(code, "202611", null));
        assertThatThrownBy(() -> standards.effective(YearMonth.of(2026, 11))).isInstanceOf(CodeFailure.class);
    }

    @Test
    void futureStandardCannotApplyEarly() {
        values("202611", "18", "0.75");
        when(mapper.selectEffectiveStandard("QUOTE_APRV_RT", "202611"))
            .thenReturn(row("QUOTE_APRV_RT", "202612", "21"));
        assertThatThrownBy(() -> standards.effective(YearMonth.of(2026, 11))).isInstanceOf(CodeFailure.class);
    }

    @Test
    void wrongStandardIdentityIsRejected() {
        values("202611", "18", "0.75");
        when(mapper.selectEffectiveStandard("QUOTE_APRV_RT", "202611"))
            .thenReturn(row("QUOTE_REAPRV_GAP", "202611", "18"));
        assertThatThrownBy(() -> standards.effective(YearMonth.of(2026, 11))).isInstanceOf(CodeFailure.class);
    }

    @Test
    void monthIsRequiredBeforeDatabaseRead() {
        assertThatThrownBy(() -> standards.effective(null))
            .isInstanceOfSatisfying(CodeFailure.class, error -> assertThat(error.getField()).isEqualTo("effectiveMonth"));
        verifyNoInteractions(mapper, periods);
    }
}
