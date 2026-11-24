package com.company.common.manpowerboard;

import com.company.common.manpowerboard.mapper.ManpowerBoardRows.*;
import com.company.common.manpowerboard.service.ManpowerBoardRules;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ManpowerBoardRulesTest {
    final LocalDate today = LocalDate.of(2026, 11, 15);
    final YearMonth current = YearMonth.of(2026, 11);
    final Developer person = new Developer("person", "인력", "개발", LocalDate.of(2026, 1, 1), null);

    ManpowerBoardRules rules(List<Assignment> assignments, List<Status> statuses) {
        return new ManpowerBoardRules(today, assignments, List.of(), List.of(), statuses, List.of());
    }

    @Test void retirementMonthIsEligibleAndNextMonthIsGrayEvenForHistoricalRoster() {
        var retired = new Developer("person", "인력", "개발", person.hireDate(), LocalDate.of(2026, 11, 2));
        var rules = rules(List.of(), List.of());
        assertThat(rules.eligible(retired, current)).isTrue();
        assertThat(rules.cell(retired, current).statusName()).isEqualTo("비어 있음");
        assertThat(rules.cell(retired, current.plusMonths(1)).statusName()).isEqualTo("퇴사");
        assertThat(rules.available(retired, current.plusMonths(1))).isZero();
    }

    @Test void preEmploymentMonthIsDashAndNeverCapacity() {
        var future = new Developer("person", "인력", "개발", LocalDate.of(2027, 2, 20), null);
        var rules = rules(List.of(), List.of());
        assertThat(rules.cell(future, current).statusName()).isEqualTo("–");
        assertThat(rules.available(future, current)).isZero();
        assertThat(rules.available(future, YearMonth.of(2027, 2))).isEqualByComparingTo("1");
    }

    @Test void leaveOverridesConfirmedAndExcludesSupplyButVacationDoesNot() {
        var assignment = ManpowerBoardControllerTest.assignment("a", "0.4");
        var leave = new Status("person", "LEAVE", today.minusDays(1), null);
        var rules = rules(List.of(assignment), List.of(leave));
        assertThat(rules.cell(person, current).statusName()).isEqualTo("휴직");
        assertThat(rules.cell(person, current).totalMm()).isEqualByComparingTo("0.4");
        assertThat(rules.available(person, current)).isZero();
        var vacation = rules(List.of(), List.of(new Status("person", "VAC", today, null)));
        assertThat(vacation.cell(person, current).statusName()).isEqualTo("비어 있음");
        assertThat(vacation.available(person, current)).isEqualByComparingTo("1");
    }

    @Test void pastEmptyCellNeverHasIdleColorOrAvailability() {
        var rules = rules(List.of(), List.of());
        assertThat(rules.cell(person, current.minusMonths(1)).statusName()).isEqualTo("–");
        assertThat(rules.available(person, current.minusMonths(1))).isZero();
    }

    @Test void withdrawalWindowIncludesTodayAndEndOfMonthPlusTwoNotNinetyDays() {
        var rules = rules(List.of(), List.of());
        for (LocalDate end : List.of(today, LocalDate.of(2027, 1, 31))) {
            assertThat(rules.withdrawal(ending(end))).isTrue();
        }
        assertThat(rules.withdrawal(ending(today.minusDays(1)))).isFalse();
        assertThat(rules.withdrawal(ending(LocalDate.of(2027, 2, 1)))).isFalse();
    }

    Assignment ending(LocalDate end) {
        return new Assignment("a", "person", "프로젝트", "ING", LocalDate.of(2026, 1, 1), end,
            BigDecimal.ONE, "L", "낮음");
    }

    @Test void partialFullConcurrentAndExcessAreSeparateThresholds() {
        var a = new Assignment("a", "person", "프로젝트", "ING", LocalDate.of(2026, 1, 1),
            LocalDate.of(2027, 8, 31), new BigDecimal("0.4"), "L", "낮음");
        assertThat(rules(List.of(a), List.of()).cell(person, current).statusName()).isEqualTo("일부 투입");
        var b = new Assignment("b", "person", "프로젝트2", "ING", a.startDate(), a.endDate(),
            new BigDecimal("0.6"), "L", "낮음");
        var concurrent = rules(List.of(a, b), List.of()).cell(person, current);
        assertThat(concurrent.statusName()).isEqualTo("동시 투입");
        assertThat(concurrent.totalMm()).isEqualByComparingTo("1");
        var full = new Assignment("c", "person", "프로젝트3", "ING", a.startDate(), a.endDate(),
            BigDecimal.ONE, "L", "낮음");
        assertThat(rules(List.of(full), List.of()).cell(person, current).statusName()).isEqualTo("투입 중");
        assertThat(rules(List.of(full, a), List.of()).cell(person, current).totalMm()).isEqualByComparingTo("1.4");
    }

    @Test void uncontractedEarlyWarningPrecedesWithdrawal() {
        var a = ManpowerBoardControllerTest.assignment("a", "0.4");
        var pre = new Assignment(a.assignmentId(), a.developerId(), a.projectName(), "PRE", a.startDate(),
            a.endDate(), a.contractMm(), a.extensionCode(), a.extensionName());
        assertThat(rules(List.of(pre), List.of()).cell(person, current).statusName()).isEqualTo("미계약 선투입");
    }

    @Test void tentativeOnlyFillsUnassignedEligibleNonPastMonthsAndDoesNotReduceCapacity() {
        var overlay = new Tentative("person", "영업 기회", current.atDay(1), current.plusMonths(2).atEndOfMonth(),
            new BigDecimal("30"));
        var rules = new ManpowerBoardRules(today, List.of(), List.of(), List.of(), List.of(), List.of(overlay));
        assertThat(rules.cell(person, current).tentative()).isTrue();
        assertThat(rules.cell(person, current).winningProbability()).isEqualByComparingTo("30");
        assertThat(rules.available(person, current)).isEqualByComparingTo("1");
        assertThat(rules.cell(person, current.minusMonths(1)).tentative()).isFalse();
        var confirmed = new ManpowerBoardRules(today, List.of(ManpowerBoardControllerTest.assignment("a", "0.4")),
            List.of(), List.of(), List.of(), List.of(overlay));
        assertThat(confirmed.cell(person, current).tentative()).isFalse();
    }

    @Test void materializedHistoryWinsAndMissingMonthsUseLatestEffectiveHistoryAndInclusiveDays() {
        var assignment = new Assignment("a", "person", "프로젝트", "ING", LocalDate.of(2026, 11, 16),
            LocalDate.of(2027, 3, 31), BigDecimal.ONE, "L", "낮음");
        var history = List.of(new History("a", LocalDate.of(2026, 10, 1), 1, new BigDecimal("0.4")),
            new History("a", LocalDate.of(2026, 11, 1), 2, new BigDecimal("0.6")),
            new History("a", LocalDate.of(2026, 11, 1), 3, new BigDecimal("0.7")),
            new History("a", LocalDate.of(2027, 1, 1), 4, new BigDecimal("0.9")));
        var rules = new ManpowerBoardRules(today, List.of(assignment),
            List.of(new Monthly("a", "202612", new BigDecimal("0.25"))), history, List.of(), List.of());
        assertThat(rules.mm(assignment, current)).isEqualByComparingTo("0.35");
        assertThat(rules.mm(assignment, current.plusMonths(1))).isEqualByComparingTo("0.25");
        assertThat(rules.mm(assignment, current.plusMonths(2))).isEqualByComparingTo("0.90");
    }

    @Test void startMonthHistoryUsesLaterSequenceRatherThanLaterDayLikeAssignments() {
        var assignment = new Assignment("a", "person", "프로젝트", "ING", LocalDate.of(2026, 11, 16),
            LocalDate.of(2027, 3, 31), BigDecimal.ONE, "L", "낮음");
        var history = List.of(
            new History("a", LocalDate.of(2026, 11, 16), 1, BigDecimal.ONE),
            new History("a", LocalDate.of(2026, 11, 1), 2, new BigDecimal("0.6")),
            new History("a", LocalDate.of(2026, 11, 1), 3, new BigDecimal("0.7")),
            new History("a", LocalDate.of(2027, 1, 1), 4, new BigDecimal("0.9")));
        var rules = new ManpowerBoardRules(today, List.of(assignment), List.of(), history, List.of(), List.of());
        assertThat(rules.mm(assignment, current)).isEqualByComparingTo("0.35");
        assertThat(rules.mm(assignment, current.plusMonths(1))).isEqualByComparingTo("0.70");
        assertThat(rules.mm(assignment, current.plusMonths(2))).isEqualByComparingTo("0.90");
    }

    @Test void roundsEachAssignmentBeforeSumming() {
        var a = new Assignment("a", "person", "프로젝트", "ING", LocalDate.of(2026, 11, 30),
            LocalDate.of(2026, 11, 30), BigDecimal.ONE, "L", "낮음");
        var b = new Assignment("b", "person", "프로젝트2", "ING", a.startDate(), a.endDate(), BigDecimal.ONE, "L", "낮음");
        assertThat(rules(List.of(a, b), List.of()).cell(person, current).totalMm()).isEqualByComparingTo("0.06");
    }
}
