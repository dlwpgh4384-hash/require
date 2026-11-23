package com.company.common.cashflowstatements;

import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CashFlowStatementPeriodTest {
    @Test void missingBaselineExposesDatabaseKoreanMonthWithoutInventingPeriodBasis() {
        var mapper = mock(UserPreferencePeriodSourceMapper.class);
        when(mapper.selectClosedMonths()).thenReturn(List.of());
        when(mapper.selectCurrentKoreanMonth()).thenReturn("202611");
        var meta = new UserPreferencePeriodBasis(mapper).metadata();
        assertThat(meta).containsEntry("periodBasis", null).containsEntry("currentYearMonth", "202611");
        assertThat(meta).containsKey("periodBasisError");
    }
    @Test void existingPeriodDefaultsAndLocksAreUnchangedWhenBaselineExists() {
        var mapper = mock(UserPreferencePeriodSourceMapper.class);
        when(mapper.selectCashBaseYm()).thenReturn(new BigDecimal("202501"));
        when(mapper.selectClosedMonths()).thenReturn(List.of("202609", "202610"));
        var meta = new UserPreferencePeriodBasis(mapper).metadata();
        assertThat(meta).containsOnlyKeys("periodBasis");
        var basis = (UserPreferencePeriodBasis.Basis) meta.get("periodBasis");
        assertThat(basis.latestYm()).isEqualTo("202611");
        assertThat(basis.cashBaseYm()).isEqualTo("202501");
        assertThat(basis.closedMonths()).containsExactly("202609", "202610");
        verify(mapper, never()).selectCurrentKoreanMonth();
    }
}
