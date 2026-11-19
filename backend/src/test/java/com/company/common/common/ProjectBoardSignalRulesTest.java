package com.company.common.common;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectBoardSignalRulesTest {
    final LocalDate today = LocalDate.of(2026, 11, 15);
    CalculationRuleService definitions;
    ProjectBoardSignalRules rules;

    @BeforeEach void setup() throws Exception {
        definitions = mock(CalculationRuleService.class);
        String seed = new ClassPathResource("db/migration/V8__calculation_rule_seed.sql")
            .getContentAsString(StandardCharsets.UTF_8);
        var row = Pattern.compile("\\('식-76', '[^']*', '[^']*', '([^']*)'").matcher(seed);
        assertThat(row.find()).isTrue();
        when(definitions.definition("식-76")).thenReturn(new Definition("식-76", row.group(1), "DB 계산식 설명"));
        rules = new ProjectBoardSignalRules(definitions);
    }

    ProjectBoardSignalRules.Source source(String type, String planned, String actual, String cost,
                                          LocalDate memo, List<ProjectBoardSignalRules.Issue> issues) {
        return new ProjectBoardSignalRules.Source(type, number(planned), number(actual), number(cost), memo,
            today.minusDays(20), issues);
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

    @Test void missingNumbersDoNotMaskIndependentHighIssueAndStaleness() {
        var result = rules.evaluate(source("TK", null, null, null, today.minusDays(15),
            List.of(issue("42", "HIGH", "OPEN", today.minusDays(1)))), today);
        assertThat(result.signal()).isEqualTo("위험");
        assertThat(result.signalReasons()).hasSize(2).anyMatch(reason -> reason.contains("42"))
            .anyMatch(reason -> reason.contains("15일"));
    }

    @Test void everyOverlappingReasonIsRetained() {
        var result = rules.evaluate(source("TK", "50", "40", "15", today.minusDays(15),
            List.of(issue("42", "HIGH", "OPEN", today.minusDays(1)),
                issue("43", "MID", "OPEN", today.minusDays(1)))), today);
        assertThat(result.signal()).isEqualTo("위험");
        assertThat(result.signalReasons()).hasSize(5);
        assertThat(result.reasonSummary()).contains("일정", "원가", "42", "43", "15일");
        assertThat(result.explanation()).contains("DB 계산식 설명", "1차 경보", "UI-04");
        verify(definitions).definition("식-76");
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
        assertThat(result.signal()).isEqualTo(signal);
    }

    @Test void neverWrittenMemoUsesContractStartAndFutureStartClampsToZero() {
        var result = rules.evaluate(source("IN", null, null, null, null, List.of()), today);
        assertThat(result.daysWithoutMemo()).isEqualTo(20L);
        assertThat(result.stale()).isTrue();
        var future = new ProjectBoardSignalRules.Source("IN", null, null, null, null, today.plusDays(1), List.of());
        assertThat(rules.evaluate(future, today).daysWithoutMemo()).isZero();
    }

    @Test void missingDatesAndNumbersStillProduceNonNullNormalExplanation() {
        var result = rules.evaluate(new ProjectBoardSignalRules.Source("TK", null, null, null, null, null, List.of()), today);
        assertThat(result.signal()).isEqualTo("정상");
        assertThat(result.signalReasons()).isEmpty();
        assertThat(result.reasonSummary()).isNotBlank();
        assertThat(result.explanation()).isNotBlank();
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
        verify(definitions, times(2)).definition("식-76");
    }

    @Test void changedThresholdDefinitionCannotSilentlyChangeApprovedBusinessConstants() {
        when(definitions.definition("식-76")).thenReturn(new Definition("식-76", "임의 기준", ""));
        assertThatThrownBy(() -> rules.evaluate(source("IN", null, null, null, today, List.of()), today))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("식-76");
    }
}
