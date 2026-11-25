package com.company.common.dashboard;

import com.company.common.dashboard.service.DashboardFilters;
import com.company.common.dashboard.service.DashboardOutlookService;
import com.company.common.cashflowstatements.dto.CashFlowStatementContracts;
import com.company.common.cashflowstatements.service.CommonCashFlowStatementService;
import com.company.common.manpowerforecasts.dto.ManpowerForecastContracts;
import com.company.common.manpowerforecasts.service.CommonManpowerForecastService;
import com.company.common.partsettlements.service.CommonPartSettlementService;
import com.company.common.projectcostanalyses.dto.ProjectCostAnalysisContracts;
import com.company.common.projectcostanalyses.service.CommonProjectCostAnalysisService;
import com.company.common.salespipeline.dto.SalesPipelineContracts;
import com.company.common.salespipeline.service.CommonSalesPipelineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DashboardOutlookTest {
    @Test void sourceCashKeysCrossYearWithoutChangingNullsWarningsOrPipelineOrder() {
        var json = new ObjectMapper();
        var forecast = mock(CommonManpowerForecastService.class);
        var cash = mock(CommonCashFlowStatementService.class);
        var parts = mock(CommonPartSettlementService.class);
        var pipeline = mock(CommonSalesPipelineService.class);
        var projects = mock(CommonProjectCostAnalysisService.class);
        var current = YearMonth.of(2026, 12);
        var selected = YearMonth.of(2026, 10);
        when(forecast.forward(current, 6)).thenReturn(new ManpowerForecastContracts.Result(
            List.of(new ManpowerForecastContracts.Month("2026-12", BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.ZERO, new BigDecimal("-9"))), null,
            new ManpowerForecastContracts.Thresholds(BigDecimal.ONE, BigDecimal.ONE)));
        var december = json.convertValue(Map.of("yearMonth", "202612", "closingBalance", 17),
            CashFlowStatementContracts.Month.class);
        var january = json.convertValue(Map.of("yearMonth", "202701"), CashFlowStatementContracts.Month.class);
        var february = json.convertValue(Map.of("yearMonth", "202702", "closingBalance", 8),
            CashFlowStatementContracts.Month.class);
        when(cash.forward(current, 3)).thenReturn(new CashFlowStatementContracts.Result(2026, BigDecimal.TEN,
            List.of(december, january, february),
            List.of(new CashFlowStatementContracts.Warning("202702", new BigDecimal("8"), BigDecimal.TEN)), null));
        when(parts.performance(selected.toString())).thenReturn(List.of());
        var cards = List.of(card(json, "future", "2027-03"), card(json, "second", "2027-01"),
            card(json, "past", "2026-11"), card(json, "first", "2026-12"));
        when(pipeline.getCommonSalesPipeline()).thenReturn(new SalesPipelineContracts.Pipeline(
            new SalesPipelineContracts.Summary(4, 0, BigDecimal.TEN, new BigDecimal("7"), BigDecimal.ONE, 0),
            List.of(new SalesPipelineContracts.Stage("PROP", 4, BigDecimal.TEN, cards, "제안", BigDecimal.TEN))));
        when(projects.getCommonProjectCostAnalyses(any())).thenReturn(json.convertValue(Map.of("items", List.of()),
            ProjectCostAnalysisContracts.CommonProjectCostAnalysisResult.class));
        var service = new DashboardOutlookService(forecast, cash, parts, pipeline, projects, new DashboardFilters(json));
        var result = service.read(selected, current);
        assertThat(result.cash()).extracting(v -> v.month()).containsExactly("2026-12", "2027-01", "2027-02");
        assertThat(result.cash().get(1).monthEndBalance()).isNull();
        assertThat(result.cash().get(0).shortage()).isFalse();
        assertThat(result.cash().get(2).shortage()).isTrue();
        assertThat(result.pipeline().upcoming()).extracting(v -> v.opportunityName())
            .containsExactly("first", "second", "future");
        assertThat(result.pipeline().weightedExpectedRevenue()).isEqualByComparingTo("7");
        assertThat(result.manpower().get(0).shortage()).isTrue();
        verify(cash).forward(current, 3);
        verify(forecast).forward(current, 6);
        verify(parts).performance("2026-10");
    }

    private static SalesPipelineContracts.Card card(ObjectMapper json, String id, String month) {
        return json.convertValue(Map.of("opportunityId", id, "opportunityName", id,
            "startMonth", month, "approvalStatusCode", "DONE"), SalesPipelineContracts.Card.class);
    }
}
