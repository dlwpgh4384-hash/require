package com.company.common.salespipeline.controller;

import com.company.common.calculation.dto.CalculationRuleContracts.Standard;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.salespipeline.mapper.SalesPipelineMapper;
import com.company.common.salespipeline.mapper.SalesPipelineRows.*;
import com.company.common.salespipeline.service.*;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.company.common.withdrawals.mapper.WithdrawalRows.*;
import com.company.common.withdrawals.service.WithdrawalCalculationRules;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SalesPipelineForecastTest {
    SalesPipelineMapper mapper;
    WithdrawalMapper workforce;
    CalculationRuleService metadata;
    SalesPipelineCalculationRules calculations;
    SalesPipelineForecastService service;
    @BeforeEach void setup() throws Exception {
        mapper = mock(SalesPipelineMapper.class); workforce = mock(WithdrawalMapper.class);
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
        when(mapper.revenue(any())).thenReturn(new Revenue(new BigDecimal("30000000"), new BigDecimal("15000000"), new BigDecimal("50000000")));
        when(workforce.supply(any())).thenReturn(List.of(new SupplyRow("part", new BigDecimal("0.5")),
            new SupplyRow("idle", BigDecimal.ZERO), new SupplyRow("extend", BigDecimal.ZERO)));
        when(workforce.extensions(any())).thenReturn(List.of(new ExtensionRow("extend", BigDecimal.ONE, "H")));
        when(workforce.opportunityDemand(any())).thenReturn(List.of(
            new OpportunityDemandRow(new BigDecimal("3"), BigDecimal.ONE, new BigDecimal("60")),
            new OpportunityDemandRow(new BigDecimal("2"), BigDecimal.ONE, new BigDecimal("30"))));
        when(mapper.projectDemand(any())).thenReturn(List.of(new ProjectDemandRow(new BigDecimal("12"), new BigDecimal("9"), 3)));
        when(mapper.candidates("BE")).thenReturn(4);
        var calendar = new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC));
        service = new SalesPipelineForecastService(mapper, workforce, new WithdrawalCalculationRules(metadata), calculations, calendar);
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
    @Test void exactlyShortageThresholdDoesNotRecommendHiring() {
        when(workforce.supply(any())).thenReturn(List.of(new SupplyRow("idle", BigDecimal.ZERO)));
        when(workforce.extensions(any())).thenReturn(List.of());
        when(workforce.opportunityDemand(any())).thenReturn(List.of(new OpportunityDemandRow(new BigDecimal("2"), BigDecimal.ONE, new BigDecimal("70"))));
        when(mapper.projectDemand(any())).thenReturn(List.of());
        assertThat(service.getCommonSalesPipelineForecast().hiringReviews()).isEmpty();
    }
    @Test void noWorkforceOrOpportunitiesStillReturnsSixMonths() {
        when(workforce.supply(any())).thenReturn(List.of()); when(workforce.extensions(any())).thenReturn(List.of());
        when(workforce.opportunityDemand(any())).thenReturn(List.of()); when(mapper.projectDemand(any())).thenReturn(List.of());
        when(mapper.revenue(any())).thenReturn(new Revenue(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        var result = service.getCommonSalesPipelineForecast();
        assertThat(result.months()).hasSize(6).allSatisfy(m -> {
            assertThat(m.demand()).isZero(); assertThat(m.supply()).isZero(); assertThat(m.allWonRevenue()).isZero();
        });
        assertThat(result.hiringReviews()).isEmpty();
    }
}
