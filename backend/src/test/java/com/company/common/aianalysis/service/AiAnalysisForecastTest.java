package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.aianalysis.service.AiAnalysisInterpreter.Query;
import com.company.common.salespipeline.service.*;
import com.company.common.salespipeline.dto.SalesPipelineContracts.*;
import com.company.common.salespipeline.mapper.SalesPipelineRows.MonthWindow;
import com.company.common.cashflowstatements.service.CommonCashFlowStatementService;
import com.company.common.cashflowstatements.dto.CashFlowStatementContracts;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.projectboard.service.CommonProjectBoardService;
import com.company.common.projectboard.dto.CommonProjectBoardContracts;
import com.company.common.manpowerboard.service.CommonManpowerBoardService;
import com.company.common.dashboard.service.DashboardUtilizationService;
import com.company.common.withdrawals.service.CommonWithdrawalService;
import com.company.common.airecommendations.service.AiRecommendationCodes;
import com.company.common.parts.service.CommonPartService;
import com.company.common.salesopportunities.service.CommonSalesOpportunityService;
import com.company.common.support.service.BusinessCalendar;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiAnalysisForecastTest {
    final AiAnalysisTables tables = new AiAnalysisTables(new ObjectMapper().findAndRegisterModules());
    final SalesPipelineForecastService forecast = mock(SalesPipelineForecastService.class);
    final BusinessCalendar calendar = mock(BusinessCalendar.class);
    Query query(String function, String text) { return new Query(text, function, new Period("2026-11", "2026-11"), false, false); }
    @Test void revenueForecastUsesSourceComponentsAndExactlySixNextMonths() {
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 11));
        var source = new ArrayList<ForecastMonth>();
        for (int i = 0; i < 6; i++) source.add(new ForecastMonth(YearMonth.of(2026, 12).plusMonths(i).toString(), new BigDecimal("100"), new BigDecimal("40"), new BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO));
        when(forecast.getCommonSalesPipelineForecast()).thenReturn(new Forecast(source, List.of(), List.of()));
        var adapter = new AiAnalysisPipelineFunctions(forecast, mock(CommonSalesPipelineService.class), calendar, tables, mock(CommonPartService.class), mock(CommonSalesOpportunityService.class));
        var answer = adapter.read(query("P-01", "매출 예측"));
        assertThat(answer.period()).isEqualTo(new Period("2026-12", "2027-05")); assertThat(answer.table().rows()).hasSize(6);
        assertThat(answer.table().rows().get(0)).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .isEqualTo(List.of("2026-12", new BigDecimal("100"), new BigDecimal("140"), new BigDecimal("200")));
    }
    CashFlowStatementContracts.Month cashMonth(String month) {
        var n = new BigDecimal(month.substring(4));
        return new CashFlowStatementContracts.Month(month, false, n, n, n, n, n, n, n, n, n, n, n, n, n);
    }
    @Test void cashForwardExcludesHistoricalPrefixAndFollowsLastClosedMonth() {
        var cash = mock(CommonCashFlowStatementService.class); var periods = mock(UserPreferencePeriodBasis.class);
        var adapter = new AiAnalysisCashFunctions(cash, periods, tables);
        for (String last : List.of("202609", "202610")) {
            var first = YearMonth.parse(last.substring(0, 4) + "-" + last.substring(4)).plusMonths(1);
            when(periods.metadata()).thenReturn(Map.of("periodBasis", new UserPreferencePeriodBasis.Basis("202611", "202601", List.of(last))));
            var months = new ArrayList<CashFlowStatementContracts.Month>(); months.add(cashMonth(last));
            for (int i = 0; i < 3; i++) months.add(cashMonth(first.plusMonths(i).toString().replace("-", "")));
            when(cash.forward(first, 3)).thenReturn(new CashFlowStatementContracts.Result(2026, BigDecimal.ZERO, months, List.of(), null));
            var result = adapter.read(query("P-03", "자금 예측"));
            assertThat(result.period()).isEqualTo(new Period(first.toString(), first.plusMonths(2).toString()));
            assertThat(result.table().rows()).hasSize(3); assertThat(result.table().rows().get(0).get(0)).isEqualTo(first.toString().replace("-", ""));
            assertThat(result.table().columns()).extracting(Column::key).doesNotContain("low", "high"); verify(cash).forward(first, 3);
        }
    }
    @Test void cashForwardWithoutClosedBasisIsUnavailableRatherThanInvented() {
        var cash = mock(CommonCashFlowStatementService.class); var periods = mock(UserPreferencePeriodBasis.class);
        when(periods.metadata()).thenReturn(Map.of("periodBasis", new UserPreferencePeriodBasis.Basis("202601", "202601", List.of())));
        assertThat(new AiAnalysisCashFunctions(cash, periods, tables).read(query("P-03", "자금 예측"))).isNull(); verifyNoInteractions(cash);
    }
    @Test void workforceNextCalendarQuarterPassesEveryMonthAndSameAlgorithmWithFullProbability() {
        var codes = mock(AiRecommendationCodes.class); when(codes.selectable(anyString())).thenReturn(List.of());
        when(forecast.getWorkforceForecast(anyList())).thenAnswer(c -> workforce(c.getArgument(0), new BigDecimal("3")));
        when(forecast.getWorkforceForecast(anyList(), eq(BigDecimal.valueOf(100)))).thenAnswer(c -> workforce(c.getArgument(0), new BigDecimal("5")));
        var adapter = new AiAnalysisWorkforceFunctions(forecast, mock(CommonManpowerBoardService.class), mock(DashboardUtilizationService.class), mock(CommonWithdrawalService.class), codes, tables);
        var period = new Period("2027-10", "2027-12");
        var result = adapter.read(new Query("다음 분기 인력 수요", "P-04", period, false, false));
        assertThat(result.table().rows()).hasSize(3); assertThat(result.table().rows().get(0)).containsExactly("2027-10", "백엔드", BigDecimal.ONE, new BigDecimal("3"), new BigDecimal("5"), BigDecimal.ZERO);
        var windows = java.util.stream.IntStream.rangeClosed(10, 12).mapToObj(month -> {
            var ym = YearMonth.of(2027, month); return new MonthWindow("2027" + month, ym.atDay(1), ym.atEndOfMonth());
        }).toList();
        verify(forecast).getWorkforceForecast(windows);
        verify(forecast).getWorkforceForecast(windows, BigDecimal.valueOf(100));
    }
    List<WorkforceMonth> workforce(List<MonthWindow> months, BigDecimal demand) {
        return months.stream().map(m -> new WorkforceMonth(m.month().substring(0, 4) + "-" + m.month().substring(4), BigDecimal.ZERO, demand, BigDecimal.ONE,
            List.of(new WorkforceJob("BE", "백엔드", BigDecimal.ZERO, demand, BigDecimal.ONE)))).toList();
    }
    @Test void projectBoardUsesOnlyReadOnlyPreviewOverloadAndNotStageProducingRoute() {
        var board = mock(CommonProjectBoardService.class);
        when(board.preview(any(), eq(true))).thenReturn(new CommonProjectBoardContracts.Result(null, List.of(), List.of(), "기존 판정"));
        var result = new AiAnalysisProjectBoardFunction(board, tables).read(query("F-11", "위험 프로젝트"));
        assertThat(result.table().rows()).isEmpty(); verify(board).preview(any(), eq(true)); verify(board, never()).getCommonProjectBoard(any()); verify(board, never()).preview(any());
    }
}
