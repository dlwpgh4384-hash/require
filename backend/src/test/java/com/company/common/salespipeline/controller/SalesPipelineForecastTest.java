package com.company.common.salespipeline.controller;

import com.company.common.calculation.dto.CalculationRuleContracts.Standard;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.salespipeline.mapper.SalesPipelineMapper;
import com.company.common.salespipeline.mapper.SalesPipelineRows.*;
import com.company.common.salespipeline.service.*;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.withdrawals.service.WithdrawalCalculationRules;
import java.math.BigDecimal;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesPipelineForecastTest {
    static final List<String> MONTHS = List.of("202612", "202701", "202702", "202703", "202704", "202705");
    SalesPipelineMapper mapper;
    CalculationRuleService metadata;
    SalesPipelineCalculationRules calculations;
    SalesPipelineForecastService service;
    static <T> List<T> everyMonth(Function<String, T> row) { var rows = new ArrayList<T>(); MONTHS.forEach(m -> rows.add(row.apply(m))); return rows; }
    static <T> List<T> everyMonthAll(Function<String, List<T>> rows) { var all = new ArrayList<T>(); MONTHS.forEach(m -> all.addAll(rows.apply(m))); return all; }
    @BeforeEach void setup() throws Exception {
        mapper = mock(SalesPipelineMapper.class);
        metadata = mock(CalculationRuleService.class); calculations = mock(SalesPipelineCalculationRules.class);
        String seed = new ClassPathResource("db/migration/V8__calculation_rule_seed.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        when(metadata.definition(anyString())).thenAnswer(call -> CommonSalesPipelineControllerTest.definition(seed, call.getArgument(0)));
        when(calculations.definitions()).thenReturn(List.of("식-60", "식-61", "식-92", "식-93").stream()
            .map(id -> CommonSalesPipelineControllerTest.definition(seed, id)).toList());
        when(calculations.standard("SHORT_PERSON")).thenReturn(new BigDecimal("0.4"));
        when(calculations.standard("HIRE_LEAD_MON")).thenReturn(new BigDecimal("2"));
        when(metadata.effectiveStandard("EXT_PRBL_H", YearMonth.of(2026, 11)))
            .thenReturn(new Standard("EXT_PRBL_H", YearMonth.of(2026, 1), new BigDecimal("80")));
        when(mapper.jobs()).thenReturn(List.of(new Job("BE", "백엔드")));
        when(mapper.revenues(anyList(), any())).thenReturn(everyMonth(m -> new MonthRevenue(m, new BigDecimal("30000000"),
            new BigDecimal("15000000"), new BigDecimal("50000000"))));
        when(mapper.supply(anyList())).thenReturn(everyMonthAll(m -> List.of(new MonthSupply(m, "BE", "part", new BigDecimal("0.5")),
            new MonthSupply(m, "BE", "idle", BigDecimal.ZERO), new MonthSupply(m, "BE", "extend", BigDecimal.ZERO))));
        when(mapper.extensions(anyList(), any())).thenReturn(everyMonth(m -> new MonthExtension(m, "BE", "extend", BigDecimal.ONE, "H")));
        when(mapper.opportunityDemand(anyList())).thenReturn(everyMonthAll(m -> List.of(
            new MonthOpportunityDemand(m, "BE", new BigDecimal("3"), BigDecimal.ONE, new BigDecimal("60")),
            new MonthOpportunityDemand(m, "BE", new BigDecimal("2"), BigDecimal.ONE, new BigDecimal("30")))));
        when(mapper.projectDemand(anyList(), any())).thenReturn(everyMonth(m -> new MonthProjectDemand(m, "BE", new BigDecimal("12"), new BigDecimal("9"), 3)));
        when(mapper.candidates()).thenReturn(List.of(new CandidateCount("BE", 4)));
        var calendar = new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC));
        service = new SalesPipelineForecastService(mapper, new WithdrawalCalculationRules(metadata), calculations, calendar);
    }
    @Test void sixMonthsStartNextMonthAndIncludeUnfilledProjectsInDemand() {
        var result = service.getCommonSalesPipelineForecast();
        assertThat(result.months()).extracting(m -> m.month()).containsExactly("2026-12", "2027-01", "2027-02", "2027-03", "2027-04", "2027-05");
        var first = result.months().get(0);
        assertThat(first.demand()).isEqualByComparingTo("3.4");
        assertThat(first.supply()).isEqualByComparingTo("1.7");
        assertThat(first.projectRevenue()).isEqualByComparingTo("30000000");
        assertThat(first.weightedOpportunityRevenue()).isEqualByComparingTo("15000000");
        assertThat(first.allWonRevenue()).isEqualByComparingTo("80000000");
        var hiring = result.hiringReviews().get(0);
        assertThat(hiring.shortageStartMonth()).isEqualTo("2026-12");
        assertThat(hiring.maximumShortage()).isEqualByComparingTo("1.7");
        assertThat(hiring.recommendedHiringMonth()).isEqualTo("지금");
        assertThat(hiring.candidateCount()).isEqualTo(4);
        verify(metadata, times(6)).effectiveStandard("EXT_PRBL_H", YearMonth.of(2026, 11));
    }
    @Test void sixMonthsAndEveryJobAreReadWithOneGroupedQueryEach() {
        when(mapper.jobs()).thenReturn(List.of(new Job("BE", "백엔드"), new Job("FE", "프론트엔드"), new Job("PM", "PM")));
        service.getCommonSalesPipelineForecast();
        verify(mapper, times(1)).revenues(anyList(), any()); verify(mapper, times(1)).supply(anyList());
        verify(mapper, times(1)).extensions(anyList(), any()); verify(mapper, times(1)).opportunityDemand(anyList());
        verify(mapper, times(1)).projectDemand(anyList(), any()); verify(mapper, times(1)).candidates();
        verify(mapper).revenues(argThat(months -> months.size() == 6 && months.get(0).month().equals("202612")
            && months.get(0).firstDay().equals(LocalDate.of(2026, 12, 1)) && months.get(5).lastDay().equals(LocalDate.of(2027, 5, 31))),
            eq(LocalDate.of(2026, 12, 1)));
    }
    @Test void rowsOfOtherMonthsAndJobsDoNotLeakIntoAMonth() {
        when(mapper.opportunityDemand(anyList())).thenReturn(List.of(
            new MonthOpportunityDemand("202701", "BE", new BigDecimal("10"), BigDecimal.ONE, new BigDecimal("100")),
            new MonthOpportunityDemand("202612", "FE", new BigDecimal("10"), BigDecimal.ONE, new BigDecimal("100"))));
        when(mapper.projectDemand(anyList(), any())).thenReturn(List.of());
        var months = service.getCommonSalesPipelineForecast().months();
        assertThat(months.get(0).demand()).isEqualByComparingTo("0");
        assertThat(months.get(1).demand()).isEqualByComparingTo("10");
    }
    @Test void revenueAndManpowerAreRoundedForDisplay() {
        when(mapper.revenues(anyList(), any())).thenReturn(everyMonth(m -> new MonthRevenue(m, new BigDecimal("33333333.3333"),
            new BigDecimal("14999999.5"), new BigDecimal("0.4"))));
        when(mapper.projectDemand(anyList(), any())).thenReturn(everyMonth(m -> new MonthProjectDemand(m, "BE", new BigDecimal("10"), BigDecimal.ZERO, 3)));
        var first = service.getCommonSalesPipelineForecast().months().get(0);
        assertThat(first.projectRevenue()).isEqualTo(new BigDecimal("33333333"));
        assertThat(first.weightedOpportunityRevenue()).isEqualTo(new BigDecimal("15000000"));
        assertThat(first.allWonRevenue()).isEqualTo(new BigDecimal("33333334"));
        assertThat(first.demand()).isEqualTo(new BigDecimal("5.73"));
    }
    @Test void opportunityLinesSplitTheWeightedTotalPerOpportunityAndSumToItInWon() {
        when(mapper.opportunityRevenues(anyList())).thenReturn(List.of(
            new OpportunityMonthRevenue("O-1", "고객 포털", new BigDecimal("10"), "202612", new BigDecimal("3333333.3333")),
            new OpportunityMonthRevenue("O-1", "고객 포털", new BigDecimal("10"), "202701", new BigDecimal("3333333.3333")),
            new OpportunityMonthRevenue("O-2", "물류 개선", new BigDecimal("30"), "202612", new BigDecimal("3333333.3333")),
            new OpportunityMonthRevenue("O-3", "보험 청약", new BigDecimal("60"), "202612", new BigDecimal("8333333.3334"))));
        var result = service.getCommonSalesPipelineForecast();
        verify(mapper, times(1)).opportunityRevenues(argThat(months -> months.size() == 6 && months.get(0).month().equals("202612")));
        var lines = result.opportunityRevenues();
        assertThat(lines).extracting(l -> l.opportunityId()).containsExactly("O-1", "O-2", "O-3");
        assertThat(lines.get(0).opportunityName()).isEqualTo("고객 포털");
        assertThat(lines.get(0).winningProbability()).isEqualByComparingTo("10");
        assertThat(lines.get(0).months()).extracting(m -> m.month()).containsExactly("2026-12", "2027-01");
        // 12월: 버림 합 14,999,999원 → 남은 1원은 소수 부분이 가장 큰 줄(O-3)에. 줄 합 = 그 달 영업 기회 합계.
        assertThat(lines.get(0).months().get(0).weightedRevenue()).isEqualTo(new BigDecimal("3333333"));
        assertThat(lines.get(1).months().get(0).weightedRevenue()).isEqualTo(new BigDecimal("3333333"));
        assertThat(lines.get(2).months().get(0).weightedRevenue()).isEqualTo(new BigDecimal("8333334"));
        assertThat(lines.stream().flatMap(l -> l.months().stream()).filter(m -> m.month().equals("2026-12"))
            .map(m -> m.weightedRevenue()).reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo(result.months().get(0).weightedOpportunityRevenue());
    }
    @Test void noOpportunitiesGiveNoOpportunityLines() {
        assertThat(service.getCommonSalesPipelineForecast().opportunityRevenues()).isEmpty();
    }
    @Test void exactlyShortageThresholdDoesNotRecommendHiring() {
        when(mapper.supply(anyList())).thenReturn(everyMonth(m -> new MonthSupply(m, "BE", "idle", BigDecimal.ZERO)));
        when(mapper.extensions(anyList(), any())).thenReturn(List.of());
        when(mapper.opportunityDemand(anyList())).thenReturn(everyMonth(m -> new MonthOpportunityDemand(m, "BE", new BigDecimal("2"), BigDecimal.ONE, new BigDecimal("70"))));
        when(mapper.projectDemand(anyList(), any())).thenReturn(List.of());
        assertThat(service.getCommonSalesPipelineForecast().hiringReviews()).isEmpty();
    }
    @Test void noWorkforceOrOpportunitiesStillReturnsSixMonths() {
        when(mapper.supply(anyList())).thenReturn(List.of()); when(mapper.extensions(anyList(), any())).thenReturn(List.of());
        when(mapper.opportunityDemand(anyList())).thenReturn(List.of()); when(mapper.projectDemand(anyList(), any())).thenReturn(List.of());
        when(mapper.revenues(anyList(), any())).thenReturn(everyMonth(m -> new MonthRevenue(m, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
        var result = service.getCommonSalesPipelineForecast();
        assertThat(result.months()).hasSize(6).allSatisfy(m -> {
            assertThat(m.demand()).isZero(); assertThat(m.supply()).isZero(); assertThat(m.allWonRevenue()).isZero();
        });
        assertThat(result.hiringReviews()).isEmpty();
    }
    @Test void startMonthDefaultsToNextMonthAndKeepsDivisorsFromNextMonth() {
        service.getCommonSalesPipelineForecast(null);
        verify(mapper).supply(argThat(months -> months.get(0).month().equals("202612")));
        verify(mapper).extensions(anyList(), eq(LocalDate.of(2026, 11, 1)));
        verify(mapper).projectDemand(anyList(), eq(LocalDate.of(2026, 12, 1)));
    }
    @Test void currentStartMonthReadsSixMonthsFromThisMonthButDivisorsStillStartNextMonth() {
        var result = service.getCommonSalesPipelineForecast("2026-11");
        assertThat(result.months()).extracting(m -> m.month()).containsExactly("2026-11", "2026-12", "2027-01", "2027-02", "2027-03", "2027-04");
        verify(mapper).revenues(argThat(months -> months.size() == 6 && months.get(0).month().equals("202611")
            && months.get(0).firstDay().equals(LocalDate.of(2026, 11, 1))), eq(LocalDate.of(2026, 12, 1)));
        verify(mapper).extensions(anyList(), eq(LocalDate.of(2026, 11, 1)));
        verify(mapper).projectDemand(anyList(), eq(LocalDate.of(2026, 12, 1)));
        assertThat(service.getCommonSalesPipelineForecast("2026-12").months().get(0).month()).isEqualTo("2026-12");
    }
    @Test void invalidOrOutOfRangeStartMonthIsRejected() {
        for (String bad : List.of("2026-1", "202611", "2026-13", "abc", "2026-10", "2027-01")) {
            assertThatThrownBy(() -> service.getCommonSalesPipelineForecast(bad))
                .isInstanceOf(com.company.common.support.service.CodeFailure.class)
                .hasMessageContaining("시작 달");
        }
        verifyNoInteractions(mapper);
    }
}
