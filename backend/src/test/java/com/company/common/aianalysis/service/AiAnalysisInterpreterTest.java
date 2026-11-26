package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.menus.mapper.MenuMapper;
import com.company.common.menus.service.DevelopedScreenRegistry;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiAnalysisInterpreterTest {
    static AiAnalysisScreens screens() {
        var menus = mock(MenuMapper.class); when(menus.selectCandidates()).thenReturn(List.of());
        return new AiAnalysisScreens(new DevelopedScreenRegistry(), menus);
    }
    final AiAnalysisInterpreter interpreter = new AiAnalysisInterpreter(new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-30T16:00:00Z"), ZoneOffset.UTC)), screens());
    /** 한국 날짜 2026-11-10. */
    final AiAnalysisInterpreter november = new AiAnalysisInterpreter(new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"), ZoneOffset.UTC)), screens());
    AiAnalysisInterpreter.Query query(String text, String screen, Period period) { return interpreter.interpret(new Input("request-1", text, screen, period)); }

    /** 명세 함수 표의 예시 질문 전부 + 시안 기록 5줄 + 시안 추천 질문 3개 + 입력칸 안내 글. 기준 2026-11. */
    @ParameterizedTest(name = "{0} → {1} {2}~{3}")
    @CsvSource(delimiter = '|', value = {
        "9월 매출과 영업이익은?|F-01|2026-09|2026-09",
        "상반기 순이익률은?|F-01|2026-01|2026-06",
        "A파트 9월 이익금은?|F-02|2026-09|2026-09",
        "가온은행 차세대 이익률 추이는?|F-03|2026-11|2026-11",
        "9월 적자 인력은?|F-04|2026-09|2026-09",
        "이익률 10% 미만 인력은?|F-04|2026-11|2026-11",
        "올해 매출 달성률은?|F-05|2026-01|2026-11",
        "지금 미수금은 얼마?|F-06|2026-11|2026-11",
        "청구 누락 의심은?|F-06|2026-11|2026-11",
        "10월 말 잔액은?|F-07|2026-10|2026-10",
        "이번 달 가동률은?|F-09|2026-11|2026-11",
        "대기 인원은?|F-09|2026-11|2026-11",
        "다음 달 철수 예정자는?|F-10|2026-12|2026-12",
        "위험 프로젝트는?|F-11|2026-11|2026-11",
        "프로젝트 상태 알려줘|F-11|2026-11|2026-11",
        "프로젝트 현황은?|F-11|2026-11|2026-11",
        "백엔드 적자 인력은?|F-04|2026-11|2026-11",
        "앞으로 12개월 매출은?|P-01|2026-12|2027-11",
        "진행 중 영업 기회 금액은?|F-12|2026-11|2026-11",
        "앞으로 6개월 매출은?|P-01|2026-12|2027-05",
        "가온은행 차세대 최종 마진율은?|P-02|2026-11|2026-11",
        "앞으로 3개월 자금 괜찮아?|P-03|2026-12|2027-02",
        "다음 분기 대기 인력은?|P-04|2027-01|2027-03",
        "백엔드 부족해?|P-04|2026-11|2026-11",
        "연체될 것 같은 미수는?|P-05|2026-11|2026-11",
        "다음 분기에 대기 인력이 몇 명쯤 될까?|P-04|2027-01|2027-03",
        "9월 적자 프로젝트는?|F-03|2026-09|2026-09",
        "가온은행 차세대 이익률 추이|F-03|2026-11|2026-11",
        "11월 자금 부족 원인|F-07|2026-11|2026-11",
        "백엔드 개발자 수요 예측|P-04|2026-11|2026-11",
        "이번 달 적자 인력은?|F-04|2026-11|2026-11",
        "이번 달 매출과 영업이익은?|F-01|2026-11|2026-11",
        "다음 달 철수하는 백엔드 개발자는 몇 명?|F-10|2026-12|2026-12",
        "작년 9월 매출|F-01|2025-09|2025-09",
        "하반기 매출|F-01|2026-07|2026-12",
        "2025년 상반기 순이익|F-01|2025-01|2025-06",
        "앞으로 3개월 잔액은?|P-03|2026-12|2027-02",
        "10월 잔액|F-07|2026-10|2026-10",
        "작년 매출|F-01|2025-01|2025-12",
        "전년 대비 9월 매출|F-01|2026-09|2026-09",
        "이번 달 급여|F-04|2026-11|2026-11"})
    void everyExampleQuestionRoutesToItsFunctionAndPeriod(String text, String function, String start, String end) {
        var query = november.interpret(new Input("request-1", text, "UI-26", null));
        assertThat(query.function()).isEqualTo(function);
        assertThat(query.period()).isEqualTo(new Period(start, end));
    }
    @Test void causeQuestionKeepsNumbersAndAFutureOperatingProfitHasTheDecidedNotice() {
        assertThat(november.interpret(new Input("r", "11월 자금 부족 원인", "UI-26", null)).cause()).isTrue();
        var query = november.interpret(new Input("r", "다음 달 영업이익 예측", "UI-26", null));
        assertThat(query.function()).isNull(); assertThat(query.notice()).isEqualTo(AiAnalysisInterpreter.NO_OPERATING_FORECAST);
        assertThat(november.interpret(new Input("r", "앞으로 6개월 매출과 영업이익은?", "UI-26", null)).function()).isEqualTo("P-01");
    }
    record Option(String id, String name) {}
    @Test void ambiguousProjectButtonsShowOnlyTheNameAndAskTheNameWithTheRestOfTheQuestion() {
        var options = List.of(new Option("P-A", "가온은행 차세대"), new Option("P-B", "가온카드 차세대"));
        assertThatThrownBy(() -> AiAnalysisTables.pick("가온 이익률 추이", options, Option::name, Option::id, "프로젝트"))
            .isInstanceOfSatisfying(AiAnalysisFunction.NoAnswer.class, missing -> {
                assertThat(missing.getMessage()).isEqualTo("어느 프로젝트인지 골라 주세요.");
                assertThat(missing.labels()).containsExactly("가온은행 차세대", "가온카드 차세대");
                assertThat(missing.suggestions()).containsExactly("가온은행 차세대 이익률 추이", "가온카드 차세대 이익률 추이");
            });
        var exact = List.of(new Option("P-A", "차세대"), new Option("P-B", "가온"));
        assertThatThrownBy(() -> AiAnalysisTables.pick("가온 차세대 손익", exact, Option::name, Option::id, "프로젝트"))
            .isInstanceOfSatisfying(AiAnalysisFunction.NoAnswer.class, missing -> {
                assertThat(missing.labels()).containsExactly("차세대", "가온");
                assertThat(missing.suggestions()).containsExactly("차세대 손익", "가온 손익");
            });
    }
    @Test void periodWordsAreRecordedSoForecastsKnowWhetherAPeriodWasStated() {
        assertThat(november.interpret(new Input("r", "백엔드 부족해?", "UI-26", null)).periodGiven()).isFalse();
        assertThat(november.interpret(new Input("r", "다음 분기 대기 인력은?", "UI-26", null)).periodGiven()).isTrue();
    }
    @Test void explicitMonthWinsOverHeaderContext() {
        assertThat(query("2026년 8월 매출", "UI-08", new Period("2026-01", "2026-10")).period()).isEqualTo(new Period("2026-08", "2026-08"));
    }
    @Test void headerWithoutExplicitPeriodUsesActualSourcePeriod() {
        var period = new Period("2026-01", "2026-09"); assertThat(query("매출", "UI-08", period).period()).isEqualTo(period);
    }
    @Test void ui26AndPeriodlessHeaderUseKoreanCurrentMonth() {
        assertThat(query("매출", "UI-26", new Period("2026-01", "2026-09")).period()).isEqualTo(new Period("2026-12", "2026-12"));
        assertThat(query("매출", "UI-31", null).period()).isEqualTo(new Period("2026-12", "2026-12"));
    }
    @Test void nextQuarterIsCalendarQuarterAcrossYearBoundary() {
        var query = query("다음 분기 대기 인력", "UI-26", null);
        assertThat(query.function()).isEqualTo("P-04"); assertThat(query.period()).isEqualTo(new Period("2027-01", "2027-03"));
    }
    @Test void explicitQuarterAndCumulativePeriodAreUnderstood() {
        assertThat(query("2026년 2분기 매출", "UI-26", null).period()).isEqualTo(new Period("2026-04", "2026-06"));
        assertThat(query("2026년 1분기 매출", "UI-26", null).period()).isEqualTo(new Period("2026-01", "2026-03"));
        assertThat(query("2026년 3분기 매출", "UI-26", null).period()).isEqualTo(new Period("2026-07", "2026-09"));
        assertThat(query("2026년 4분기 매출", "UI-26", null).period()).isEqualTo(new Period("2026-10", "2026-12"));
        assertThat(query("2026-02 매출", "UI-26", null).period()).isEqualTo(new Period("2026-02", "2026-02"));
        assertThat(query("2026년 8월 누계 매출", "UI-26", null).period()).isEqualTo(new Period("2026-01", "2026-08"));
    }
    @Test void historicalOperatingProfitAndFutureOperatingProfitHaveDifferentAvailability() {
        assertThat(query("2026-10 영업이익", "UI-26", null).function()).isEqualTo("F-01");
        assertThat(query("다음 달 영업이익 예측", "UI-26", null).function()).isNull();
    }
    @Test void salaryAndUnsupportedQuestionsAreDeterministic() {
        assertThat(query("급여", "UI-26", null).salary()).isTrue(); assertThat(query("급여", "UI-26", null).function()).isEqualTo("F-04");
        assertThat(query("지원하지 않는 질문", "UI-26", null).function()).isNull();
    }
    @Test void blankScreenUnknownScreenAndRequestIdentityAreRejected() {
        assertThatThrownBy(() -> interpreter.interpret(new Input("r", "매출", null, null))).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> interpreter.interpret(new Input("r", "매출", "UI-99", null)))
            .isInstanceOfSatisfying(CodeFailure.class, f -> assertThat(f.getField()).isEqualTo("screenId"));
        assertThatThrownBy(() -> interpreter.interpret(new Input("", "매출", "UI-26", null))).isInstanceOf(CodeFailure.class);
    }
    @Test void wrongMonthAndReversedOrderDoNotReachSourceSelection() {
        assertThatThrownBy(() -> query("매출", "UI-08", new Period("2026-00", "2026-01"))).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> query("매출", "UI-08", new Period("2026-11", "2026-10"))).isInstanceOf(CodeFailure.class);
    }
}
