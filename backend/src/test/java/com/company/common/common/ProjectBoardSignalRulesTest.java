package com.company.common.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class ProjectBoardSignalRulesTest {
    final LocalDate today = LocalDate.of(2026, 11, 15);
    final ProjectBoardSignalRules rules = new ProjectBoardSignalRules();

    ProjectBoardSignalRules.Source source(String type, String planned, String actual, String cost,
                                          LocalDate memo, List<ProjectBoardSignalRules.Issue> issues) {
        long[] overdue = ProjectBoardSignalRules.overdue(issues, today);
        return new ProjectBoardSignalRules.Source(type, number(planned), number(actual), number(cost), memo,
            today.minusDays(20), overdue[0], overdue[1]);
    }
    BigDecimal number(String value) { return value == null ? null : new BigDecimal(value); }
    ProjectBoardSignalRules.Issue issue(String id, String importance, String status, LocalDate due) {
        return new ProjectBoardSignalRules.Issue(id, importance, status, due);
    }

    @ParameterizedTest @CsvSource({"40,위험", "40.1,주의", "45,주의", "45.1,정상"})
    void scheduleThresholdsAreInclusive(String actual, String signal) {
        var result = rules.evaluate(source("IN", "50", actual, null, today, List.of()), today);
        assertThat(result.signal()).isEqualTo(signal);
    }

    @ParameterizedTest @CsvSource({"15,위험", "14.9,주의", "5,주의", "4.9,정상"})
    void turnkeyCostThresholdsAreInclusive(String cost, String signal) {
        var result = rules.evaluate(source("TK", "50", "50", cost, today, List.of()), today);
        assertThat(result.signal()).isEqualTo(signal);
    }

    @ParameterizedTest @CsvSource({"IN", "MO", "DP"})
    void nonTurnkeyCostNeverCreatesReason(String type) {
        var result = rules.evaluate(source(type, "50", "50", "99", today, List.of()), today);
        assertThat(result.signal()).isEqualTo("정상");
        assertThat(result.signalReasons()).isEmpty();
    }

    @Test void reasonsUseMockupWordingAndLevels() {
        var result = rules.evaluate(source("TK", "50.5", "38", "15.5", today.minusDays(17),
            List.of(issue("42", "HIGH", "OPEN", today.minusDays(1)),
                issue("43", "MID", "OPEN", today.minusDays(1)))), today);
        assertThat(result.signal()).isEqualTo("위험");
        assertThat(result.signalReasons()).containsExactly("일정이 계획보다 12.5%p 늦음",
            "원가 사용률이 진척률보다 15.5%p 높음 — 원가 초과 위험", "기한 지난 중요(높음) 이슈 1건",
            "17일째 상태 업데이트 없음");
        assertThat(result.signalReasonLevels()).containsExactly("위험", "위험", "위험", "주의");
        assertThat(result.reasonSummary()).isEqualTo(String.join(" · ", result.signalReasons()));
    }

    @Test void cautionWordingAndWholeNumbersDropTrailingZero() {
        var result = rules.evaluate(source("TK", "50", "44", "6", today,
            List.of(issue("1", "MID", "OPEN", today.minusDays(3)), issue("2", "LOW", "ACT", today.minusDays(1)))), today);
        assertThat(result.signal()).isEqualTo("주의");
        assertThat(result.signalReasons()).containsExactly("일정이 계획보다 6%p 늦음",
            "원가 사용률이 진척률보다 6%p 높음", "기한 지난 이슈 2건");
        assertThat(result.signalReasonLevels()).containsOnly("주의");
    }

    @Test void missingNumbersDoNotMaskIndependentHighIssueAndStaleness() {
        var result = rules.evaluate(source("TK", null, null, null, today.minusDays(15),
            List.of(issue("42", "HIGH", "OPEN", today.minusDays(1)))), today);
        assertThat(result.signal()).isEqualTo("위험");
        assertThat(result.signalReasons()).containsExactly("기한 지난 중요(높음) 이슈 1건", "15일째 상태 업데이트 없음");
    }

    @Test void overdueOrdinaryIssueWarnsWithoutNumericSources() {
        var result = rules.evaluate(source("MO", null, null, null, today,
            List.of(issue("1", "MID", "OPEN", today.minusDays(1)))), today);
        assertThat(result.signal()).isEqualTo("주의");
        assertThat(result.signalReasons()).hasSize(1);
    }

    @Test void dueTodayIsNotOverdue() {
        var result = rules.evaluate(source("TK", null, null, null, today,
            List.of(issue("1", "HIGH", "OPEN", today))), today);
        assertThat(result.signal()).isEqualTo("정상");
        assertThat(result.signalReasons()).isEmpty();
    }

    @Test void closedOverdueIssueDoesNotWarn() {
        var result = rules.evaluate(source("TK", null, null, null, today,
            List.of(issue("1", "HIGH", "CLOSE", today.minusDays(1)))), today);
        assertThat(result.signal()).isEqualTo("정상");
    }

    @ParameterizedTest @CsvSource({"14,false,정상", "15,true,주의"})
    void writtenDateHasStrictFourteenDayBoundary(int age, boolean stale, String signal) {
        var result = rules.evaluate(source("IN", null, null, null, today.minusDays(age), List.of()), today);
        assertThat(result.stale()).isEqualTo(stale);
        assertThat(result.daysWithoutMemo()).isEqualTo((long) age);
        assertThat(result.lastUpdateDate()).isEqualTo(today.minusDays(age));
        assertThat(result.signal()).isEqualTo(signal);
    }

    @Test void neverWrittenMemoCountsFromStageCreationAndFutureClampsToZero() {
        var result = rules.evaluate(source("IN", null, null, null, null, List.of()), today);
        assertThat(result.daysWithoutMemo()).isEqualTo(20L);
        assertThat(result.lastUpdateDate()).isEqualTo(today.minusDays(20));
        assertThat(result.stale()).isTrue();
        var future = new ProjectBoardSignalRules.Source("IN", null, null, null, null, today.plusDays(1), 0, 0);
        assertThat(rules.evaluate(future, today).daysWithoutMemo()).isZero();
    }

    @Test void missingDatesAndNumbersStillProduceNormalWithSummary() {
        var result = rules.evaluate(new ProjectBoardSignalRules.Source("TK", null, null, null, null, null, 0, 0), today);
        assertThat(result.signal()).isEqualTo("정상");
        assertThat(result.signalReasons()).isEmpty();
        assertThat(result.signalReasonLevels()).isEmpty();
        assertThat(result.reasonSummary()).isEqualTo("계획대로 진행 중입니다.");
        assertThat(result.daysWithoutMemo()).isNull();
    }

    @Test void nextReadUsesNewMemoAndIssueSourcesWithoutCachedJudgment() {
        var before = rules.evaluate(source("IN", null, null, null, today.minusDays(15),
            List.of(issue("1", "HIGH", "OPEN", today.minusDays(1)))), today);
        var after = rules.evaluate(source("IN", null, null, null, today,
            List.of(issue("1", "HIGH", "CLOSE", today.minusDays(1)))), today);
        assertThat(before.signal()).isEqualTo("위험");
        assertThat(after.signal()).isEqualTo("정상");
        assertThat(after.signalReasons()).isEmpty();
    }
}
