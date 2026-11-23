package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Source;
import com.company.common.calculation.dto.CalculationRuleContracts.ValueRequest;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.support.service.BusinessCalendar;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MonthClosePeriodBasisTest {
    private final UserPreferencePeriodSourceMapper mapper = mock(UserPreferencePeriodSourceMapper.class);
    private final UserPreferencePeriodBasis service = new UserPreferencePeriodBasis(mapper);

    @Test
    void firstOpenMonthCannotPrecedeCashBasisEvenWhenOlderCloseRowsExist() {
        when(mapper.selectCashBaseYm()).thenReturn(new BigDecimal("202605.0000"));
        when(mapper.selectClosedMonths()).thenReturn(List.of("202601", "202602"));
        assertThat(service.metadata().get("periodBasis"))
            .isEqualTo(new UserPreferencePeriodBasis.Basis("202605", "202605", List.of("202601", "202602")));
    }

    @Test
    void everyReadObservesConfirmationAndReopeningWithoutCachingPeriodBasis() {
        when(mapper.selectCashBaseYm()).thenReturn(new BigDecimal("202601.0000"));
        when(mapper.selectClosedMonths()).thenReturn(List.of("202601"), List.of("202601", "202602"), List.of("202601"));
        assertThat(service.metadata().get("periodBasis"))
            .isEqualTo(new UserPreferencePeriodBasis.Basis("202602", "202601", List.of("202601")));
        assertThat(service.metadata().get("periodBasis"))
            .isEqualTo(new UserPreferencePeriodBasis.Basis("202603", "202601", List.of("202601", "202602")));
        assertThat(service.metadata().get("periodBasis"))
            .isEqualTo(new UserPreferencePeriodBasis.Basis("202602", "202601", List.of("202601")));
        verify(mapper, times(3)).selectClosedMonths();
    }

    @Test
    void beforeBaseMonthStillUsesClosedResultAfterAllRecordedMonthsAreReopened() {
        when(mapper.selectCashBaseYm()).thenReturn(new BigDecimal("202605"));
        var calculations = mock(CalculationRuleMapper.class);
        var rules = new CalculationRuleService(calculations, mapper);
        var value = rules.resolve(new ValueRequest("MONTH_COST", YearMonth.of(2026, 4),
            new BigDecimal("17"), new BigDecimal("13")));
        assertThat(value.source()).isEqualTo(Source.CLOSED_RESULT);
        assertThat(value.value()).isEqualByComparingTo("13");
        verifyNoInteractions(calculations);
    }

    @Test
    void latestClosedDecemberAdvancesAcrossYearBoundaryUsingSharedCalendarRule() {
        when(mapper.selectCashBaseYm()).thenReturn(new BigDecimal("202601"));
        when(mapper.selectClosedMonths()).thenReturn(List.of("202612", "202610"));
        var basis = (UserPreferencePeriodBasis.Basis) service.metadata().get("periodBasis");
        var calendar = new BusinessCalendar();
        assertThat(basis.latestYm()).isEqualTo(calendar.toDatabaseMonth(
            calendar.firstOpenMonth(YearMonth.of(2026, 12), YearMonth.of(2026, 1))));
        assertThat(basis.latestYm()).isEqualTo("202701");
    }
}
