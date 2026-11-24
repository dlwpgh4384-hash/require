package com.company.common.manpowerforecasts;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.manpowerforecasts.service.CommonManpowerForecastService;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.salespipeline.mapper.SalesPipelineMapper;
import com.company.common.salespipeline.mapper.SalesPipelineRows.*;
import com.company.common.salespipeline.service.*;
import com.company.common.support.service.*;
import com.company.common.withdrawals.service.WithdrawalCalculationRules;
import java.math.BigDecimal;

import java.time.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManpowerForecastServiceTest {
    SalesPipelineMapper mapper;
    CalculationRuleService metadata;
    SalesPipelineCalculationRules calculations;
    SalesPipelineForecastService shared;
    CommonManpowerForecastService service;
    BusinessCalendar calendar;

    @BeforeEach void setup() throws Exception {
        mapper = mock(SalesPipelineMapper.class);
        metadata = mock(CalculationRuleService.class);
        calculations = mock(SalesPipelineCalculationRules.class);
        ManpowerForecastTestData.definitions(metadata, calculations);
        calendar = new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC));
        shared = new SalesPipelineForecastService(mapper, new WithdrawalCalculationRules(metadata), calculations, calendar);
        var periods = mock(UserPreferencePeriodBasis.class);
        when(periods.metadata()).thenReturn(Map.of("periodBasis", new UserPreferencePeriodBasis.Basis("202401", "202401", List.of())));
        service = new CommonManpowerForecastService(shared, metadata, periods, calendar);
        when(mapper.jobs()).thenReturn(List.of(new Job("BE", "백엔드"), new Job("FE", "프론트엔드")));
    }

    @Test void currentYearIsCurrentMonthThroughDecemberAndNextYearHasTwelveMonths() {
        assertThat(service.getCommonManpowerForecasts(2026).months()).extracting(m -> m.month()).containsExactly("2026-11", "2026-12");
        var next = service.getCommonManpowerForecasts(2027).months();
        assertThat(next).hasSize(12);
        assertThat(next.get(0).month()).isEqualTo("2027-01");
        assertThat(next.get(11).month()).isEqualTo("2027-12");
        verify(mapper, times(2)).extensions(anyList(), eq(LocalDate.of(2026, 11, 1)));
        verify(mapper, times(2)).projectDemand(anyList(), eq(LocalDate.of(2026, 12, 1)));
        verify(mapper, never()).revenues(anyList(), any());
        verify(mapper, never()).opportunityRevenues(anyList());
        verify(mapper, never()).candidates();
    }
    @Test void allowedPastYearIsEmptyButHasDbThresholdsAndDoesNotInvokeEmptySql() {
        var result = service.getCommonManpowerForecasts(2024);
        assertThat(result.months()).isEmpty();
        assertThat(result.summary().lastMonthDemand()).isZero();
        assertThat(result.summary().lastMonthSupply()).isZero();
        assertThat(result.summary().mostShortage()).isNull();
        assertThat(result.thresholds().shortageThreshold()).isEqualByComparingTo("0.4");
        verify(mapper, never()).supply(anyList());
        verify(metadata).effectiveStandard("SURPLUS_PERSON", YearMonth.of(2026, 11));
    }
    @Test void yearsBeforeCashBaseAndAfterNextYearAreInvalid() {
        for (Integer year : new Integer[]{null, 2023, 2028})
            assertThatThrownBy(() -> service.getCommonManpowerForecasts(year)).isInstanceOf(CodeFailure.class);
        verifyNoInteractions(mapper);
    }
    @Test void lastMonthKpisAndWholePeriodShortageAreDifferentAndTieUsesJobOrderAcrossMonths() {
        when(mapper.opportunityDemand(anyList())).thenReturn(List.of(
            new MonthOpportunityDemand("202611", "FE", new BigDecimal("3"), BigDecimal.ONE, new BigDecimal("100")),
            new MonthOpportunityDemand("202612", "BE", new BigDecimal("3"), BigDecimal.ONE, new BigDecimal("100"))));
        var result = service.getCommonManpowerForecasts(2026);
        assertThat(result.summary().lastMonthDemand()).isEqualTo(result.months().get(1).demand());
        assertThat(result.summary().mostShortage().jobName()).isEqualTo("백엔드");
        assertThat(result.summary().mostShortage().maximumShortage()).isEqualByComparingTo("3");
    }
    @Test void shortageInAnEarlierMonthIsNotReplacedByTheLastMonth() {
        when(mapper.opportunityDemand(anyList())).thenReturn(List.of(
            new MonthOpportunityDemand("202611", "FE", new BigDecimal("3"), BigDecimal.ONE, new BigDecimal("100"))));
        var result = service.getCommonManpowerForecasts(2026);
        assertThat(result.summary().lastMonthDemand()).isZero();
        assertThat(result.summary().mostShortage().jobName()).isEqualTo("프론트엔드");
    }
    @ParameterizedTest @CsvSource({"0.0499,false", "0.05,false", "0.0501,true"})
    void shortageEligibilityUsesRawStrictBoundary(String demand, boolean expected) {
        when(mapper.opportunityDemand(anyList())).thenReturn(List.of(
            new MonthOpportunityDemand("202611", "BE", BigDecimal.ONE, new BigDecimal(demand), new BigDecimal("100"))));
        assertThat(service.getCommonManpowerForecasts(2026).summary().mostShortage() != null).isEqualTo(expected);
    }
    @Test void unfilledContributionIsIncludedOnceAndLegacyOverloadsKeepTheSameMonthValues() {
        when(mapper.supply(anyList())).thenReturn(List.of(new MonthSupply("202612", "BE", "available", new BigDecimal("0.5")),
            new MonthSupply("202612", "BE", "extension", BigDecimal.ZERO)));
        when(mapper.extensions(anyList(), any())).thenReturn(List.of(new MonthExtension("202612", "BE", "extension", BigDecimal.ONE, null)));
        when(mapper.opportunityDemand(anyList())).thenReturn(List.of(new MonthOpportunityDemand("202612", "BE", BigDecimal.ONE, new BigDecimal("0.125"), new BigDecimal("100"))));
        when(mapper.projectDemand(anyList(), any())).thenReturn(List.of(new MonthProjectDemand("202612", "BE", BigDecimal.ONE, BigDecimal.ZERO, 3)));
        var result = service.getCommonManpowerForecasts(2026).months().get(1);
        var old = shared.getCommonSalesPipelineForecast().months().get(0);
        var explicit = shared.getCommonSalesPipelineForecast(null).months().get(0);
        var board = shared.getCommonSalesPipelineForecast("2026-11").months().get(1);
        assertThat(result.supply()).isEqualTo(old.supply()).isEqualTo(explicit.supply()).isEqualTo(board.supply());
        assertThat(result.demand()).isEqualTo(old.demand()).isEqualTo(board.demand());
        assertThat(result.unfilledDemand()).isEqualByComparingTo("0.33");
        assertThat(result.demand()).isEqualByComparingTo("0.46");
        assertThat(result.difference()).isEqualByComparingTo(BigDecimal.ONE.subtract(new BigDecimal("0.125").add(BigDecimal.ONE.divide(new BigDecimal("3"), java.math.MathContext.DECIMAL128))));
    }
    @Test void decemberCurrentYearReturnsOnlyDecember() {
        var december = new BusinessCalendar(Clock.fixed(Instant.parse("2026-12-15T00:00:00Z"), ZoneOffset.UTC));
        var periods = mock(UserPreferencePeriodBasis.class);
        when(periods.metadata()).thenReturn(Map.of("periodBasis", new UserPreferencePeriodBasis.Basis("202401", "202401", List.of())));
        var core = new SalesPipelineForecastService(mapper, new WithdrawalCalculationRules(metadata), calculations, december);
        var query = new CommonManpowerForecastService(core, metadata, periods, december);
        assertThat(query.getCommonManpowerForecasts(2026).months()).extracting(m -> m.month()).containsExactly("2026-12");
        assertThat(query.getCommonManpowerForecasts(2027).months()).hasSize(12);
    }
    @Test void koreanYearBoundaryChangesThePermittedYearAndProjectionNotUtcYear() {
        var korean = new BusinessCalendar(Clock.fixed(Instant.parse("2026-12-31T15:01:00Z"), ZoneOffset.UTC));
        assertThat(korean.currentMonth()).isEqualTo(YearMonth.of(2027, 1));
        var periods = mock(UserPreferencePeriodBasis.class);
        when(periods.metadata()).thenReturn(Map.of("periodBasis", new UserPreferencePeriodBasis.Basis("202401", "202401", List.of())));
        var core = new SalesPipelineForecastService(mapper, new WithdrawalCalculationRules(metadata), calculations, korean);
        var query = new CommonManpowerForecastService(core, metadata, periods, korean);
        assertThat(query.getCommonManpowerForecasts(2026).months()).isEmpty();
        assertThat(query.getCommonManpowerForecasts(2027).months()).hasSize(12);
        assertThat(query.getCommonManpowerForecasts(2028).months()).hasSize(12);
        verify(mapper, times(2)).extensions(anyList(), eq(LocalDate.of(2027, 1, 1)));
        verify(mapper, times(2)).projectDemand(anyList(), eq(LocalDate.of(2027, 2, 1)));
    }
}
