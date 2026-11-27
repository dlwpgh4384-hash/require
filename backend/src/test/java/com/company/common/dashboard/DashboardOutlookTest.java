package com.company.common.dashboard;

import com.company.common.dashboard.service.DashboardFilters;
import com.company.common.dashboard.service.DashboardOutlookService;
import com.company.common.cashflowstatements.dto.CashFlowStatementContracts;
import com.company.common.cashflowstatements.service.CommonCashFlowStatementService;
import com.company.common.manpowerforecasts.dto.ManpowerForecastContracts;
import com.company.common.manpowerforecasts.service.CommonManpowerForecastService;
import com.company.common.profitstructures.dto.ProfitStructureContracts.CompositionRow;
import com.company.common.profitstructures.service.CommonProfitStructureService;
import com.company.common.profitstructures.service.ProfitStructureRules.PartRow;
import com.company.common.projectcostanalyses.dto.ProjectCostAnalysisContracts;
import com.company.common.projectcostanalyses.service.CommonProjectCostAnalysisService;
import com.company.common.salespipeline.dto.SalesPipelineContracts;
import com.company.common.salespipeline.service.CommonSalesPipelineService;
import com.company.common.salesopportunities.dto.SalesOpportunityContracts;
import com.company.common.salesopportunities.service.CommonSalesOpportunityService;
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
        var parts = mock(CommonProfitStructureService.class);
        var pipeline = mock(CommonSalesPipelineService.class);
        var projects = mock(CommonProjectCostAnalysisService.class);
        var opportunities = mock(CommonSalesOpportunityService.class);
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
        when(parts.monthSalesParts(selected)).thenReturn(List.of(
            new PartRow("P1", new CompositionRow("금융", new BigDecimal("74800000"), new BigDecimal("61.4"), new BigDecimal("23.9"))),
            new PartRow("", new CompositionRow("미지정", new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("-5.0")))));
        // 시안 soon: 오늘+3개월(2027-03) 안 시작(이미 시작 포함), 확률 높은 순 3건. 2027-04는 빠진다.
        var cards = List.of(card(json, "future", "2027-03", 30, "DONE"), card(json, "late", "2027-04", 95, "DONE"),
            card(json, "past", "2026-11", 90, "WAIT"), card(json, "first", "2026-12", 10, "DONE"),
            card(json, "second", "2027-01", 60, "DONE"));
        when(pipeline.getCommonSalesPipeline()).thenReturn(new SalesPipelineContracts.Pipeline(
            new SalesPipelineContracts.Summary(5, 0, BigDecimal.TEN, new BigDecimal("7"), BigDecimal.ONE, 0),
            List.of(new SalesPipelineContracts.Stage("PROP", 5, BigDecimal.TEN, cards, "제안", BigDecimal.TEN))));
        when(opportunities.getCommonSalesOpportunity("past")).thenReturn(json.convertValue(
            Map.of("opportunityId", "past", "expectedProfitRate", 14.5), SalesOpportunityContracts.Opportunity.class));
        when(projects.getCommonProjectCostAnalyses(any())).thenReturn(json.convertValue(Map.of("items", List.of(
                alert("one", "MO", List.of("저마진")), alert("two", "TK", List.of("저마진", "예산 초과")))),
            ProjectCostAnalysisContracts.CommonProjectCostAnalysisResult.class));
        var service = new DashboardOutlookService(forecast, cash, parts, pipeline, projects, opportunities, new DashboardFilters(json));
        var result = service.read(selected, current, current);
        assertThat(result.cash()).extracting(v -> v.month()).containsExactly("2026-12", "2027-01", "2027-02");
        assertThat(result.cash().get(1).monthEndBalance()).isNull();
        assertThat(result.cash().get(0).shortage()).isFalse();
        assertThat(result.cash().get(2).shortage()).isTrue();
        assertThat(result.cashShortage().month()).isEqualTo(YearMonth.of(2027, 2));
        assertThat(result.cashShortage().balance()).isEqualByComparingTo("8");
        assertThat(result.minimumCash()).isEqualByComparingTo("10");
        assertThat(result.pipeline().upcoming()).extracting(v -> v.opportunityName())
            .containsExactly("past", "second", "future");
        assertThat(result.pipeline().upcoming().get(0).stageName()).isEqualTo("제안");
        assertThat(result.pipeline().upcoming().get(0).probability()).isEqualByComparingTo("90");
        assertThat(result.pendingQuotes()).singleElement().satisfies(q -> {
            assertThat(q.opportunityName()).isEqualTo("past");
            assertThat(q.profitRate()).isEqualByComparingTo("14.5");
        });
        assertThat(result.pipeline().weightedExpectedRevenue()).isEqualByComparingTo("7");
        assertThat(result.manpower().get(0).shortage()).isTrue();
        // 경보 수 많은 순, 턴키는 예상 최종 마진율, 예산 초과는 bad 배지, 주원인 금액이 없으면 주원인 없음.
        assertThat(result.alerts()).extracting(a -> a.projectId()).containsExactly("two", "one");
        var turnkey = result.alerts().get(0);
        assertThat(turnkey.marginLabel()).isEqualTo("예상 최종 마진율");
        assertThat(turnkey.marginRate()).isEqualByComparingTo("3.5");
        assertThat(turnkey.budgetBurnRate()).isEqualByComparingTo("104.2");
        assertThat(turnkey.alertBadges()).extracting(b -> b.tone()).containsExactly("warn", "bad");
        assertThat(turnkey.mainCause()).isEqualTo("무상 지원");
        assertThat(turnkey.mainCauseAmount()).isEqualByComparingTo("1200000");
        assertThat(result.alerts().get(1).marginLabel()).isEqualTo("월간 마진율");
        assertThat(result.alerts().get(1).marginRate()).isEqualByComparingTo("11.0");
        verify(cash).forward(current, 3);
        verify(forecast).forward(current, 6);
        verify(parts).monthSalesParts(selected);
        // 전체 수익 구조(UI-07) 영업 파트별 그대로(프로젝트를 따온 파트 기준), 전체 범위면 미지정 줄도.
        assertThat(result.parts()).extracting(p -> p.partId(), p -> p.partName())
            .containsExactly(org.assertj.core.groups.Tuple.tuple("P1", "금융"), org.assertj.core.groups.Tuple.tuple("", "미지정"));
        assertThat(result.parts().get(0).revenue()).isEqualByComparingTo("74800000");
        assertThat(result.parts().get(0).profitRate()).isEqualByComparingTo("23.9");
    }

    @Test void cashStartsAfterLastClosedMonthAndShortageLooksAtEveryOpenMonthThisYear() {
        var json = new ObjectMapper();
        var forecast = mock(CommonManpowerForecastService.class);
        var cash = mock(CommonCashFlowStatementService.class);
        var parts = mock(CommonProfitStructureService.class);
        var pipeline = mock(CommonSalesPipelineService.class);
        var projects = mock(CommonProjectCostAnalysisService.class);
        var current = YearMonth.of(2026, 11);
        var firstOpen = YearMonth.of(2026, 10);
        when(forecast.forward(current, 6)).thenReturn(new ManpowerForecastContracts.Result(List.of(), null,
            new ManpowerForecastContracts.Thresholds(BigDecimal.ONE, BigDecimal.ONE)));
        var months = java.util.stream.IntStream.rangeClosed(1, 12).mapToObj(m -> json.convertValue(
            Map.of("yearMonth", String.format("2026%02d", m), "closingBalance", 100 - m), CashFlowStatementContracts.Month.class)).toList();
        when(cash.forward(firstOpen, 3)).thenReturn(new CashFlowStatementContracts.Result(2026, new BigDecimal("89"),
            months, List.of(new CashFlowStatementContracts.Warning("202612", new BigDecimal("88"), new BigDecimal("89"))), null));
        when(parts.monthSalesParts(any())).thenReturn(List.of());
        when(pipeline.getCommonSalesPipeline()).thenReturn(new SalesPipelineContracts.Pipeline(
            new SalesPipelineContracts.Summary(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0), List.of()));
        when(projects.getCommonProjectCostAnalyses(any())).thenReturn(json.convertValue(Map.of("items", List.of()),
            ProjectCostAnalysisContracts.CommonProjectCostAnalysisResult.class));
        var result = new DashboardOutlookService(forecast, cash, parts, pipeline, projects,
            mock(CommonSalesOpportunityService.class), new DashboardFilters(json)).read(current, current, firstOpen);
        // 9월까지 마감: 10·11·12월 3칸, 부족 예상은 3칸 밖 달이라도 올해 안 마감 전 달이면 잡는다(시안 short).
        assertThat(result.cash()).extracting(v -> v.month()).containsExactly("2026-10", "2026-11", "2026-12");
        assertThat(result.cashShortage().month()).isEqualTo(YearMonth.of(2026, 12));
        verify(cash).forward(firstOpen, 3);
    }

    private static Map<String, Object> alert(String id, String type, List<String> alerts) {
        boolean turnkey = "TK".equals(type);
        var row = new java.util.HashMap<String, Object>(Map.of("projectId", id, "projectName", id, "contractTypeCode", type,
            "lowMargin", alerts.contains("저마진"), "budgetExceeded", alerts.contains("예산 초과"),
            "alertCount", alerts.size(), "alerts", alerts, "budgetUsageRate", turnkey ? 104.2 : 80.0));
        row.put(turnkey ? "expectedFinalMarginRate" : "monthlyMarginRate", turnkey ? 3.5 : 11.0);
        row.put(turnkey ? "monthlyMarginRate" : "expectedFinalMarginRate", 99.0);
        if (turnkey) { row.put("primaryCauseName", "무상 지원"); row.put("primaryCauseAmount", 1200000); }
        else row.put("primaryCauseName", "투입이 계획보다 많음");
        return row;
    }

    private static SalesPipelineContracts.Card card(ObjectMapper json, String id, String month, int probability, String approval) {
        return json.convertValue(Map.of("opportunityId", id, "opportunityName", id, "winningProbability", probability,
            "startMonth", month, "approvalStatusCode", approval), SalesPipelineContracts.Card.class);
    }
}
