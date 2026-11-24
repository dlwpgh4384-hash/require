package com.company.common.manpowerforecasts;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.manpowerforecasts.service.CommonManpowerForecastService;
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
        service = new CommonManpowerForecastService(shared, metadata, calendar);
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
    @Test void thresholdsComeFromOperatingStandardsOfTheCurrentMonth() {
        var result = service.getCommonManpowerForecasts(2026);
        assertThat(result.thresholds().shortageThreshold()).isEqualByComparingTo("0.4");
        assertThat(result.thresholds().surplusThreshold()).isEqualByComparingTo("1");
        verify(metadata).effectiveStandard("SURPLUS_PERSON", YearMonth.of(2026, 11));
    }
    @Test void onlyThisYearAndNextYearAreAllowed() {
        assertThatThrownBy(() -> service.getCommonManpowerForecasts(null)).isInstanceOf(CodeFailure.class);
        // 지난해(이전 기준 2024년까지 허용하던 값 포함)와 내후년은 같은 문구의 400.
        for (Integer year : new Integer[]{2023, 2024, 2025, 2028})
            assertThatThrownBy(() -> service.getCommonManpowerForecasts(year)).isInstanceOf(CodeFailure.class)
                .hasMessageContaining("선택할 수 있는 조회 연도를 확인해 주세요.");
        verifyNoInteractions(mapper);
    }
    @Test void missingFormulaDefinitionStopsTheForecastLikeTheSalesPipeline() {
        when(calculations.definitions()).thenReturn(List.of());
        assertThatThrownBy(() -> service.getCommonManpowerForecasts(2026)).isInstanceOf(CodeFailure.class);
        verify(mapper, never()).supply(anyList());
    }
    @Test void lastMonthKpisAndWholePeriodShortageAreDifferentAndTieUsesJobOrderAcrossMonths() {
        when(mapper.opportunityDemand(anyList())).thenReturn(List.of(
            new MonthOpportunityDemand("202611", "FE", new BigDecimal("3"), BigDecimal.ONE, new BigDecimal("100")),
            new MonthOpportunityDemand("202612", "BE", new BigDecimal("3"), BigDecimal.ONE, new BigDecimal("100"))));
        var result = service.getCommonManpowerForecasts(2026);
        assertThat(result.summary().lastMonthDemand()).isEqualTo(result.months().get(1).demand());
        assertThat(result.summary().mostShortage().jobName()).isEqualTo("백엔드");
        assertThat(result.summary().mostShortage().maximumShortage()).isEqualTo(new BigDecimal("3.0"));
    }
    @Test void shortageInAnEarlierMonthIsNotReplacedByTheLastMonth() {
        when(mapper.opportunityDemand(anyList())).thenReturn(List.of(
            new MonthOpportunityDemand("202611", "FE", new BigDecimal("3"), BigDecimal.ONE, new BigDecimal("100"))));
        var result = service.getCommonManpowerForecasts(2026);
        assertThat(result.summary().lastMonthDemand()).isZero();
        assertThat(result.summary().mostShortage().jobName()).isEqualTo("프론트엔드");
    }
    // 시안 worst: 직무·달마다 한 자리로 반올림한 가용 − 필요가 −0.05 미만일 때만 부족 직무(0.05 → 0.1명 부족).
    @ParameterizedTest @CsvSource({"0.0499,false,", "0.05,true,0.1", "0.0501,true,0.1", "0.149,true,0.1", "0.15,true,0.2"})
    void shortageEligibilityUsesRoundedOneDecimalDifference(String demand, boolean expected, String shortage) {
        when(mapper.opportunityDemand(anyList())).thenReturn(List.of(
            new MonthOpportunityDemand("202611", "BE", BigDecimal.ONE, new BigDecimal(demand), new BigDecimal("100"))));
        var most = service.getCommonManpowerForecasts(2026).summary().mostShortage();
        assertThat(most != null).isEqualTo(expected);
        if (expected) assertThat(most.maximumShortage()).isEqualTo(new BigDecimal(shortage));
    }
    @Test void worstJobUsesMinimumRoundedDifferenceOverThePeriod() {
        // FE: 11월 −0.04(→ 반올림 0.0), BE: 12월 −0.36(→ −0.4). 가장 부족한 직무는 BE 0.4명.
        when(mapper.opportunityDemand(anyList())).thenReturn(List.of(
            new MonthOpportunityDemand("202611", "FE", BigDecimal.ONE, new BigDecimal("0.04"), new BigDecimal("100")),
            new MonthOpportunityDemand("202612", "BE", BigDecimal.ONE, new BigDecimal("0.36"), new BigDecimal("100"))));
        var most = service.getCommonManpowerForecasts(2026).summary().mostShortage();
        assertThat(most.jobName()).isEqualTo("백엔드");
        assertThat(most.maximumShortage()).isEqualTo(new BigDecimal("0.4"));
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
        // 차이 = 한 자리 가용(1.00 → 1.0) − 한 자리 필요(0.46 → 0.5). 반올림 전 34자리 값이 아니다.
        assertThat(result.difference()).isEqualTo(new BigDecimal("0.5"));
    }
    @Test void decemberCurrentYearReturnsOnlyDecember() {
        var december = new BusinessCalendar(Clock.fixed(Instant.parse("2026-12-15T00:00:00Z"), ZoneOffset.UTC));
        var core = new SalesPipelineForecastService(mapper, new WithdrawalCalculationRules(metadata), calculations, december);
        var query = new CommonManpowerForecastService(core, metadata, december);
        assertThat(query.getCommonManpowerForecasts(2026).months()).extracting(m -> m.month()).containsExactly("2026-12");
        assertThat(query.getCommonManpowerForecasts(2027).months()).hasSize(12);
    }
    @Test void koreanYearBoundaryChangesThePermittedYearAndProjectionNotUtcYear() {
        var korean = new BusinessCalendar(Clock.fixed(Instant.parse("2026-12-31T15:01:00Z"), ZoneOffset.UTC));
        assertThat(korean.currentMonth()).isEqualTo(YearMonth.of(2027, 1));
        var core = new SalesPipelineForecastService(mapper, new WithdrawalCalculationRules(metadata), calculations, korean);
        var query = new CommonManpowerForecastService(core, metadata, korean);
        // 한국 시간으로 2027년 1월이면 2026년은 지난해라 고를 수 없다(UTC 연도 2026이 아님).
        assertThatThrownBy(() -> query.getCommonManpowerForecasts(2026)).isInstanceOf(CodeFailure.class);
        assertThat(query.getCommonManpowerForecasts(2027).months()).hasSize(12);
        assertThat(query.getCommonManpowerForecasts(2028).months()).hasSize(12);
        verify(mapper, times(2)).extensions(anyList(), eq(LocalDate.of(2027, 1, 1)));
        verify(mapper, times(2)).projectDemand(anyList(), eq(LocalDate.of(2027, 2, 1)));
    }
}
