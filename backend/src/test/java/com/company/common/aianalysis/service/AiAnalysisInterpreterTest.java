package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AiAnalysisInterpreterTest {
    final AiAnalysisInterpreter interpreter = new AiAnalysisInterpreter(new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-30T16:00:00Z"), ZoneOffset.UTC)));
    AiAnalysisInterpreter.Query query(String text, String screen, Period period) { return interpreter.interpret(new Input("request-1", text, screen, period)); }
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
    @Test void causeQuestionPreservesRelatedFunctionButDoesNotRequestCausalSynthesis() {
        var query = query("자금 부족 원인", "UI-26", null); assertThat(query.function()).isEqualTo("F-07"); assertThat(query.cause()).isTrue();
    }
    @Test void salaryAndUnsupportedQuestionsAreDeterministic() {
        assertThat(query("급여", "UI-26", null).salary()).isTrue(); assertThat(query("급여", "UI-26", null).function()).isEqualTo("F-04");
        assertThat(query("지원하지 않는 질문", "UI-26", null).function()).isNull();
    }
    @Test void blankScreenAndRequestIdentityAreRejected() {
        assertThatThrownBy(() -> interpreter.interpret(new Input("r", "매출", null, null))).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> interpreter.interpret(new Input("", "매출", "UI-26", null))).isInstanceOf(CodeFailure.class);
    }
    @Test void wrongMonthAndReversedOrderDoNotReachSourceSelection() {
        assertThatThrownBy(() -> query("매출", "UI-08", new Period("2026-00", "2026-01"))).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> query("매출", "UI-08", new Period("2026-11", "2026-10"))).isInstanceOf(CodeFailure.class);
    }
}
