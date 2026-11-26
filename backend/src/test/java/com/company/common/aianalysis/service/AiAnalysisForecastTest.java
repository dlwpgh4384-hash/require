package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.aianalysis.service.AiAnalysisInterpreter.Query;
import com.company.common.airecommendations.service.AiRecommendationCodes;
import com.company.common.calculation.dto.CalculationRuleContracts.Standard;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.cashflowstatements.dto.CashFlowStatementContracts;
import com.company.common.cashflowstatements.service.CommonCashFlowStatementService;
import com.company.common.codegroups.dto.CodeContracts;
import com.company.common.dashboard.service.DashboardUtilizationService;
import com.company.common.manpowerboard.service.CommonManpowerBoardService;
import com.company.common.manpowerforecasts.service.CommonManpowerForecastService;
import com.company.common.parts.service.CommonPartService;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.projectboard.dto.CommonProjectBoardContracts;
import com.company.common.projectboard.service.CommonProjectBoardService;
import com.company.common.salespipeline.dto.SalesPipelineContracts.*;
import com.company.common.salespipeline.mapper.SalesPipelineRows.MonthWindow;
import com.company.common.salespipeline.service.*;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.withdrawals.service.CommonWithdrawalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiAnalysisForecastTest {
    final AiAnalysisTables tables = new AiAnalysisTables(new ObjectMapper().findAndRegisterModules(), AiAnalysisInterpreterTest.screens());
    final SalesPipelineForecastService forecast = mock(SalesPipelineForecastService.class);
    final BusinessCalendar calendar = mock(BusinessCalendar.class);
    Query query(String function, String text) { return new Query(text, function, new Period("2026-11", "2026-11"), false, false); }
    static Standard standard(String code, String value) { return new Standard(code, YearMonth.of(2026, 1), new BigDecimal(value)); }

    @Test void revenueForecastUsesSourceComponentsAndExactlySixNextMonths() {
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 11));
        var source = new ArrayList<ForecastMonth>();
        for (int i = 0; i < 6; i++) source.add(new ForecastMonth(YearMonth.of(2026, 12).plusMonths(i).toString(), new BigDecimal("100"), new BigDecimal("40"), new BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO));
        when(forecast.getCommonSalesPipelineForecast()).thenReturn(new Forecast(source, List.of(), List.of()));
        var adapter = new AiAnalysisPipelineFunctions(forecast, mock(CommonSalesPipelineService.class), calendar, tables, mock(CommonPartService.class));
        var answer = adapter.read(query("P-01", "앞으로 6개월 매출과 영업이익은?"));
        // 예측 답의 기준월(BASE_YM)은 이번 달.
        assertThat(answer.forecast()).isTrue(); assertThat(answer.baseMonth()).isEqualTo("2026-11");
        var table = answer.tables().get(0);
        assertThat(table.rows()).hasSize(6);
        assertThat(table.rows().get(0)).usingRecursiveComparison().withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .isEqualTo(List.of("2026-12", new BigDecimal("100"), new BigDecimal("140"), new BigDecimal("200")));
        assertThat(answer.assumptions()).contains("영업이익 예측은 계산 식이 없어 답하지 않습니다.");
        assertThat(answer.actions()).extracting(Action::label).containsExactly("영업 파이프라인 열기");
        assertThat(answer.assumptions()).noneMatch(a -> a.contains("개월만 답합니다"));
    }
    @Test void longerHorizonThanTheForecastAllowsIsTrimmedWithAnAssumptionLine() {
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 11));
        var source = new ArrayList<ForecastMonth>();
        for (int i = 0; i < 6; i++) source.add(new ForecastMonth(YearMonth.of(2026, 12).plusMonths(i).toString(), new BigDecimal("100"), new BigDecimal("40"), new BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO));
        when(forecast.getCommonSalesPipelineForecast()).thenReturn(new Forecast(source, List.of(), List.of()));
        var adapter = new AiAnalysisPipelineFunctions(forecast, mock(CommonSalesPipelineService.class), calendar, tables, mock(CommonPartService.class));
        var answer = adapter.read(new Query("앞으로 12개월 매출과 영업이익은?", "P-01", new Period("2026-12", "2027-11"), false, false, true, null));
        assertThat(answer.tables().get(0).rows()).hasSize(6);
        assertThat(answer.conclusion()).startsWith("앞으로 6개월(2026-12 ~ 2027-05)");
        assertThat(answer.assumptions()).contains("매출 예측은 다음 달부터 최대 6개월까지라 물어본 12개월 대신 6개월만 답합니다.",
            "영업이익 예측은 계산 식이 없어 답하지 않습니다.");
    }
    CashFlowStatementContracts.Month cashMonth(String month) {
        var n = new BigDecimal(month.substring(4));
        return new CashFlowStatementContracts.Month(month, false, n, n, n, n, n, n, n, n, n, n, n, n, n);
    }
    @Test void cashForwardStartsLikeTheDashboardAndNamesShortageMonths() {
        var cash = mock(CommonCashFlowStatementService.class); var periods = mock(UserPreferencePeriodBasis.class);
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 11));
        var adapter = new AiAnalysisCashFunctions(cash, periods, calendar, tables);
        for (String last : List.of("202609", "202610")) {
            var basis = new UserPreferencePeriodBasis.Basis("202611", "202601", List.of(last));
            var first = basis.firstOpen(YearMonth.of(2026, 11));
            assertThat(first).isEqualTo(YearMonth.parse(last.substring(0, 4) + "-" + last.substring(4)).plusMonths(1));
            when(periods.metadata()).thenReturn(Map.of("periodBasis", basis));
            var months = new ArrayList<CashFlowStatementContracts.Month>(); months.add(cashMonth(last));
            for (int i = 0; i < 3; i++) months.add(cashMonth(first.plusMonths(i).toString().replace("-", "")));
            var warning = new CashFlowStatementContracts.Warning(first.plusMonths(1).toString().replace("-", ""), new BigDecimal("5"), new BigDecimal("89"));
            when(cash.forward(first, 3)).thenReturn(new CashFlowStatementContracts.Result(2026, new BigDecimal("89"), months, List.of(warning), null));
            var result = adapter.read(new Query("앞으로 3개월 자금 괜찮아?", "P-03", new Period("2026-12", "2027-02"), false, false, true, null));
            assertThat(result.tables().get(0).rows()).hasSize(3);
            assertThat(result.tables().get(0).rows().get(0).get(0)).isEqualTo(first.toString());
            assertThat(result.conclusion()).contains("최소 운영자금").contains(first.plusMonths(1).getMonthValue() + "월 **");
            assertThat(result.tables().get(0).tones().get(1).get(4)).isEqualTo("bad");
            // 예측은 「예측」 표시만(잠정 겹쳐 달지 않음), 기준월은 이번 달.
            assertThat(result.provisional()).isFalse(); assertThat(result.baseMonth()).isEqualTo("2026-11");
            verify(cash).forward(first, 3);
        }
    }
    @Test void cashForwardWithoutBasisIsUnavailableRatherThanInvented() {
        var cash = mock(CommonCashFlowStatementService.class); var periods = mock(UserPreferencePeriodBasis.class);
        when(periods.metadata()).thenReturn(Collections.singletonMap("periodBasis", null));
        assertThatThrownBy(() -> new AiAnalysisCashFunctions(cash, periods, calendar, tables).read(query("P-03", "자금 예측")))
            .isInstanceOf(AiAnalysisFunction.NoAnswer.class);
        verifyNoInteractions(cash);
    }

    AiAnalysisWorkforceFunctions workforce(AiRecommendationCodes codes, CalculationRuleService standards) {
        return new AiAnalysisWorkforceFunctions(forecast, mock(CommonManpowerBoardService.class), mock(DashboardUtilizationService.class),
            mock(CommonWithdrawalService.class), codes, standards, calendar, tables);
    }
    CalculationRuleService standards() {
        var standards = mock(CalculationRuleService.class);
        Map<String, String> values = Map.of("SHORT_PERSON", "0.4", "SURPLUS_PERSON", "1.0", "EXT_PRBL_H", "80", "EXT_PRBL_M", "50",
            "EXT_PRBL_L", "0", "STEP_PRBL_1", "10", "STEP_PRBL_2", "30", "STEP_PRBL_3", "60", "STEP_PRBL_4", "90");
        when(standards.effectiveStandard(anyString(), any())).thenAnswer(c -> standard(c.getArgument(0), values.get(c.<String>getArgument(0))));
        return standards;
    }
    List<WorkforceMonth> months(List<MonthWindow> windows, String supply, String demand, String unfilled) {
        return windows.stream().map(m -> new WorkforceMonth(m.month().substring(0, 4) + "-" + m.month().substring(4), new BigDecimal(supply),
            new BigDecimal(demand), new BigDecimal(unfilled), List.of(
                new WorkforceJob("BE", "백엔드 개발", new BigDecimal(supply), new BigDecimal(demand), new BigDecimal(unfilled)),
                new WorkforceJob("QA", "QA·테스트", BigDecimal.ONE, new BigDecimal("1.02"), BigDecimal.ZERO)))).toList();
    }
    @Test void workforceNextCalendarQuarterUsesTheManpowerForecastRoundingAndRanges() {
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 11));
        when(calendar.toDatabaseMonth(any())).thenAnswer(c -> c.<YearMonth>getArgument(0).toString().replace("-", ""));
        var codes = mock(AiRecommendationCodes.class); when(codes.selectable(anyString())).thenReturn(List.of());
        when(forecast.getWorkforceForecast(anyList())).thenAnswer(c -> months(c.getArgument(0), "3.04", "4.26", "1.5"));
        when(forecast.getWorkforceForecast(anyList(), eq(BigDecimal.valueOf(100)))).thenAnswer(c -> months(c.getArgument(0), "3.04", "6.0", "1.5"));
        var result = workforce(codes, standards()).read(new Query("다음 분기 대기 인력은?", "P-04", new Period("2027-01", "2027-03"), false, false, true, null));
        var windows = java.util.stream.IntStream.rangeClosed(1, 3).mapToObj(month -> {
            var ym = YearMonth.of(2027, month); return new MonthWindow(ym.toString().replace("-", ""), ym.atDay(1), ym.atEndOfMonth());
        }).toList();
        verify(forecast).getWorkforceForecast(windows); verify(forecast).getWorkforceForecast(windows, BigDecimal.valueOf(100));
        var range = result.tables().get(0);
        assertThat(range.columns()).extracting(Column::label).containsExactly("범위", "2027-01", "2027-02", "2027-03");
        // 같은 계산을 쓰는 인력 수요 예측(UI-17) 「차이」와 같다: 3.0 − 4.3 = −1.3(SFR-117-06 한 자리 HALF_UP 뒤 뺄셈).
        var screen = new CommonManpowerForecastService(forecast, standards(), calendar).forward(YearMonth.of(2027, 1), 3);
        assertThat(screen.months().get(0).difference()).isEqualByComparingTo("-1.3");
        assertThat(range.rows().get(1).get(1)).isEqualTo("1.3명 부족");
        assertThat(range.rows().get(0).get(1)).isEqualTo("1.5명 남음");
        assertThat(range.rows().get(2).get(1)).isEqualTo("3.0명 부족");
        assertThat(range.tones().get(1).get(1)).isEqualTo("bad"); assertThat(range.tones().get(0).get(1)).isNull();
        var jobs = result.tables().get(1);
        assertThat(jobs.rows()).extracting(r -> r.get(0)).containsExactly("백엔드 개발");
        assertThat(jobs.totalRow()).containsExactly("전체", "1.3명 부족", "1.3명 부족", "1.3명 부족");
        assertThat(result.conclusion()).isEqualTo("다음 분기(2027-01~2027-03) 남는 인원(가용 − 필요)은 1월 **1.3명 부족** · 2월 **1.3명 부족** · 3월 **1.3명 부족**으로 예상됩니다(기본).");
        assertThat(result.assumptions().get(0)).startsWith("연장 가능성 높음 80%·보통 50%·낮음 0%, 영업 기회는 기회별 수주 확률만큼");
        assertThat(result.actions()).extracting(Action::label).containsExactly("인력 수요 예측 열기", "인력 운영 보드 열기");
        assertThat(result.forecast()).isTrue(); assertThat(result.provisional()).isFalse();
        // 시안 대기 인력 답: 표 제목 없음, 사용한 데이터 글, 기준월은 이번 달.
        assertThat(result.tables()).extracting(Table::title).containsOnlyNulls();
        assertThat(result.usedData()).isEqualTo("투입 이력, 근무 상태, 영업 파이프라인, 인원 계획 · 2027-01 ~ 2027-03");
        assertThat(result.baseMonth()).isEqualTo("2026-11");
    }
    @Test void workforceJobQuestionNarrowsToThatJob() {
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 11));
        when(calendar.toDatabaseMonth(any())).thenAnswer(c -> c.<YearMonth>getArgument(0).toString().replace("-", ""));
        var be = new CodeContracts.Item(); be.code = "BE"; be.codeName = "백엔드 개발";
        var qa = new CodeContracts.Item(); qa.code = "QA"; qa.codeName = "QA·테스트";
        var codes = mock(AiRecommendationCodes.class); when(codes.selectable("JOB")).thenReturn(List.of(be, qa));
        when(forecast.getWorkforceForecast(anyList())).thenAnswer(c -> months(c.getArgument(0), "3.04", "4.26", "1.5"));
        when(forecast.getWorkforceForecast(anyList(), eq(BigDecimal.valueOf(100)))).thenAnswer(c -> months(c.getArgument(0), "3.04", "6.0", "1.5"));
        var result = workforce(codes, standards()).read(new Query("백엔드 부족해?", "P-04", new Period("2026-11", "2026-11"), false, false, false, null));
        assertThat(result.tables().get(0).columns()).hasSize(4);
        assertThat(result.conclusion()).startsWith("2026-11~2027-01 백엔드 개발 남는 인원");
        assertThat(result.tables().get(1).totalRow().get(0)).isEqualTo("백엔드 개발");
    }
    @Test void projectBoardUsesOnlyReadOnlyPreviewOverloadAndNotStageProducingRoute() {
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 11));
        var board = mock(CommonProjectBoardService.class);
        when(board.preview(any(), eq(true))).thenReturn(new CommonProjectBoardContracts.Result(null, List.of(), List.of(), "기존 판정"));
        var result = new AiAnalysisProjectBoardFunction(board, calendar, tables).read(query("F-11", "위험 프로젝트"));
        assertThat(result.tables().get(0).rows()).isEmpty(); assertThat(result.conclusion()).isEqualTo("위험 프로젝트는 **0개**입니다.");
        verify(board).preview(any(), eq(true)); verify(board, never()).getCommonProjectBoard(any()); verify(board, never()).preview(any());
    }
}
