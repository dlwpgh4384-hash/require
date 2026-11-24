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
    final Developer person = ManpowerBoardControllerTest.developer("person", "인력", LocalDate.of(2026, 1, 1), null);

    ManpowerBoardRules rules(List<Assignment> assignments, List<Status> statuses) {
        return new ManpowerBoardRules(today, assignments, List.of(), List.of(), statuses, List.of());
    }

    @Test void retirementMonthIsEligibleAndNextMonthIsGrayEvenForHistoricalRoster() {
        var retired = ManpowerBoardControllerTest.developer("person", "인력", person.hireDate(), LocalDate.of(2026, 11, 2));
        var rules = rules(List.of(), List.of());
        assertThat(rules.retired(retired, current)).isFalse();
        assertThat(rules.cell(retired, current).statusName()).isEqualTo("비어 있음");
        assertThat(rules.cell(retired, current.plusMonths(1)).statusName()).isEqualTo("퇴사");
        assertThat(rules.cell(retired, current.plusMonths(1)).statusCode()).isEqualTo("ret");
        assertThat(rules.available(retired, current.plusMonths(1))).isZero();
    }

    @Test void preEmploymentMonthIsDashAndNeverCapacity() {
        var future = ManpowerBoardControllerTest.developer("person", "인력", LocalDate.of(2027, 2, 20), null);
        var rules = rules(List.of(), List.of());
        assertThat(rules.cell(future, current).statusName()).isEqualTo("–");
        assertThat(rules.available(future, current)).isZero();
        assertThat(rules.available(future, YearMonth.of(2027, 2))).isEqualByComparingTo("1");
    }

    @Test void leaveOverridesConfirmedAndExcludesSupplyButVacationDoesNot() {
        var assignment = ManpowerBoardControllerTest.assignment("a", "0.4");
        var leave = new Status("person", "LEAVE", current.atDay(1), null);
        var rules = rules(List.of(assignment), List.of(leave));
        assertThat(rules.cell(person, current).statusName()).isEqualTo("휴직");
        assertThat(rules.cell(person, current).statusCode()).isEqualTo("leave");
        // 시안 cellInfo: 휴직 칸은 MM·프로젝트를 세지 않는다.
        assertThat(rules.cell(person, current).totalMm()).isZero();
        assertThat(rules.cell(person, current).projects()).isEmpty();
        assertThat(rules.available(person, current)).isZero();
        // 그 달 1일을 덮지 않는 휴직(15일 시작)은 다음 달부터 휴직.
        var midMonth = rules(List.of(), List.of(new Status("person", "LEAVE", today, null)));
        assertThat(midMonth.cell(person, current).statusCode()).isEqualTo("idle");
        assertThat(midMonth.cell(person, current.plusMonths(1)).statusCode()).isEqualTo("leave");
        // 이력이 없으면 지금 근무 상태 LEAVE를 이번 달부터 휴직으로 본다(지난 달은 그대로).
        var onLeaveNow = ManpowerBoardControllerTest.developer("person", "인력", "BE", "LEAVE", null, null, null, null);
        assertThat(rules(List.of(), List.of()).cell(onLeaveNow, current).statusCode()).isEqualTo("leave");
        assertThat(rules(List.of(), List.of()).cell(onLeaveNow, current.minusMonths(1)).statusCode()).isEqualTo("none");
        var vacation = rules(List.of(), List.of(new Status("person", "VAC", today, null)));
        assertThat(vacation.cell(person, current).statusName()).isEqualTo("비어 있음");
        assertThat(vacation.available(person, current)).isEqualByComparingTo("1");
    }

    @Test void pastEmptyCellNeverHasIdleColorOrAvailability() {
        var rules = rules(List.of(), List.of());
        assertThat(rules.cell(person, current.minusMonths(1)).statusName()).isEqualTo("–");
        assertThat(rules.available(person, current.minusMonths(1))).isZero();
    }

    @Test void withdrawalWindowIsThisMonthThroughPlusTwoComparedByMonth() {
        for (LocalDate end : List.of(today.minusDays(1), today, LocalDate.of(2027, 1, 31))) {
            assertThat(rules(List.of(ending(end)), List.of()).cell(person, current).statusCode()).as(end.toString()).isEqualTo("end");
        }
        assertThat(rules(List.of(ending(LocalDate.of(2027, 2, 1))), List.of()).cell(person, current).statusCode()).isEqualTo("on");
        assertThat(rules(List.of(ending(today)), List.of()).cell(person, current.minusMonths(1)).statusCode()).isEqualTo("on");
    }

    @Test void summaryWithdrawalCountsOnlyAssignmentsEndingTodayOrLater() {
        // 칸 색은 달로 비교(어제 끝나도 철수 예정), 요약 칸은 철수 리스트처럼 D-n ≥ 0만 센다(식-77).
        assertThat(rules(List.of(ending(today.minusDays(1))), List.of()).assignedFromToday(person, current)).isFalse();
        assertThat(rules(List.of(ending(today)), List.of()).assignedFromToday(person, current)).isTrue();
    }

    @Test void assignmentEndedEarlierThisMonthDoesNotBlockWithdrawalOfOneEndingNextMonth() {
        var ended = new Assignment("a", "person", "끝난 프로젝트", "ING", LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 11, 2), new BigDecimal("0.5"), "L", "낮음 (0%)");
        var next = new Assignment("b", "person", "다음 달 종료", "ING", LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31), new BigDecimal("0.5"), "M", "보통 (50%)");
        var cell = rules(List.of(ended, next), List.of()).cell(person, current);
        assertThat(cell.statusCode()).isEqualTo("end");
        assertThat(cell.statusName()).isEqualTo("철수 예정");
        // 여럿이면 가장 높은 연장 가능성, 「(50%)」 꼬리 없이.
        assertThat(cell.extensionPossibilityName()).isEqualTo("보통");
        var on = rules(List.of(new Assignment("c", "person", "길게", "ING", LocalDate.of(2026, 1, 1),
            LocalDate.of(2027, 8, 31), BigDecimal.ONE, "H", "높음 (80%)")), List.of()).cell(person, current);
        assertThat(on.extensionPossibilityName()).isNull();
    }

    @Test void neverAssignedWorkingPersonIsNoneAndPersonWhoseAssignmentEndedIsIdleOnlyFromThisMonth() {
        var working = ManpowerBoardControllerTest.developer("person", "인력", "BE", "WORK", null, null, null, null);
        var rules = rules(List.of(), List.of());
        assertThat(rules.cell(working, current).statusCode()).isEqualTo("none");
        assertThat(rules.cell(working, current).statusName()).isEqualTo("–");
        assertThat(rules.available(working, current)).isZero();
        var ended = ManpowerBoardControllerTest.developer("person", "인력", "BE", "WORK", null, null,
            LocalDate.of(2026, 8, 31), LocalDate.of(2026, 8, 31));
        assertThat(rules.cell(ended, current).statusCode()).isEqualTo("idle");
        assertThat(rules.available(ended, current)).isEqualByComparingTo("1");
        assertThat(rules.cell(ended, YearMonth.of(2026, 10)).statusCode()).isEqualTo("none");
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
        assertThat(rules.cell(person, current).statusCode()).isEqualTo("tent");
        assertThat(rules.cell(person, current).projects()).isEmpty();
        assertThat(rules.tentatives(person, current)).extracting(Tentative::projectName).containsExactly("영업 기회");
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
