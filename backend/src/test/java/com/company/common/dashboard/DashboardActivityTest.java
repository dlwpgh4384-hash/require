package com.company.common.dashboard;

import com.company.common.billchecks.dto.BillCheckContracts;
import com.company.common.billchecks.service.BillCheckService;
import com.company.common.bills.dto.BillContracts;
import com.company.common.dashboard.dto.DashboardContracts.*;
import com.company.common.dashboard.service.*;
import com.company.common.financeitems.dto.FinanceItemContracts;
import com.company.common.financeitems.service.CommonFinanceItemService;
import com.company.common.projectboard.dto.CommonProjectBoardContracts;
import com.company.common.projectboard.service.CommonProjectBoardService;
import com.company.common.projects.service.CommonProjectService;
import com.company.common.withdrawals.dto.WithdrawalContracts;
import com.company.common.withdrawals.service.CommonWithdrawalService;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 확인할 일: 시안 warns·EXTRA_WARNS의 순서·제목·설명·색·이동 화면·0건 숨김. */
class DashboardActivityTest {
    final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    final LocalDate today = LocalDate.of(2026, 11, 25);

    @Test void rowsFollowMockupOrderWordingToneAndVisibility() {
        var finance = mock(CommonFinanceItemService.class);
        var withdrawals = mock(CommonWithdrawalService.class);
        var projectList = mock(CommonProjectService.class);
        var board = mock(CommonProjectBoardService.class);
        var billing = mock(BillCheckService.class);
        when(finance.overdueTaxPayments(today)).thenReturn(List.of(new FinanceItemContracts.TaxPayment("2025:CNFM", 2025,
            "CNFM", LocalDate.of(2026, 3, 31), null, BigDecimal.TEN, "N", null, "확인 필요", null)));
        when(withdrawals.getCommonWithdrawals(any())).thenReturn(new WithdrawalContracts.WithdrawalList(List.of(
            leaving("d1", "2026-12-20"), leaving("d1", "2026-12-31"), leaving("d2", "2027-01-15")), 3,
            new WithdrawalContracts.Banner(4, 2, 1)));
        when(projectList.getCommonProjectUnassignedCount()).thenReturn(0L);
        when(board.preview(any())).thenReturn(new CommonProjectBoardContracts.Result(
            new CommonProjectBoardContracts.Summary(new CommonProjectBoardContracts.Progress(2, 1, 0, 1), 1,
                new CommonProjectBoardContracts.OpenIssues(3, 1), 3, 1),
            List.of(new CommonProjectBoardContracts.StaleProject("p2", "조용한 프로젝트", 20L)),
            List.of(row("p1", "위험 프로젝트A", "위험", List.of("일정 10%p 늦음", "기한 지난 중요(높음) 이슈 1건"),
                List.of("주의", "위험"), 2), row("p2", "조용한 프로젝트", "주의", List.of("기한 지난 이슈 1건"), List.of("주의"), 1)),
            "식"));
        when(billing.getCommonBillChecks("202610")).thenReturn(new BillCheckContracts.CheckResult("202610",
            new BillCheckContracts.Summary(3, 1, 1, 0, 1), List.of(
                check("빠진 곳", "청구 없음", "1500000"), check("적은 곳", "적게 청구", "500000"),
                check("많은 곳", "많이 청구", "-300000"), check("맞는 곳", "맞음", "0")),
            "식", false, List.of(), "202610"));
        var late = json.convertValue(Map.of("billId", "b1", "projectName", "연체 프로젝트", "unpaidAmount", 123456789,
            "status", "LATE"), BillContracts.Bill.class);
        var summary = new Summary(null, null, null, null, null, null, null, null, null, null, 0,
            new BigDecimal("200000000"), 3, 1, new BigDecimal("123456789"));
        var money = new DashboardFinancialService.Financial(summary, List.of(), List.of(late));
        var outlook = new DashboardOutlookService.Outlook(List.of(
                new ManpowerMonth("2026-12", BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("-1.5"), true),
                new ManpowerMonth("2027-01", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, false)),
            List.of(), new Pipeline(0, BigDecimal.ZERO, List.of()), List.of(), new BigDecimal("50000000"),
            new DashboardOutlookService.CashShortage(YearMonth.of(2026, 12), new BigDecimal("42000000"),
                new BigDecimal("50000000"), 1),
            List.of(), List.of(new DashboardOutlookService.PendingQuote("견적 기회", new BigDecimal("12.34"))));
        var assigned = new DashboardAssignmentService.Counts(2, 5, 3, List.of("선투입 A", "선투입 B"));
        var todos = new DashboardActivityService(finance, withdrawals, projectList, board, billing,
            new DashboardFilters(json)).read(YearMonth.of(2026, 10), today, money, outlook, assigned);

        assertThat(todos).extracting(Todo::key).containsExactly("overdueBills", "taxPayment", "cashShortage",
            "manpowerShortage", "overAllocated", "preContract", "withdrawal", "riskProjects", "overdueIssues",
            "billingMissing", "pendingQuotes", "staleProjects", "unassignedSalesPart");
        var byKey = new java.util.HashMap<String, Todo>();
        todos.forEach(t -> byKey.put(t.key(), t));
        assertThat(byKey.get("overdueBills")).extracting(Todo::title, Todo::description, Todo::tone, Todo::urgent, Todo::targetScreenId)
            .containsExactly("미수 연체 1건", "1.2억 원 · 연체 프로젝트", "bad", true, "UI-13");
        assertThat(byKey.get("taxPayment").title()).isEqualTo("법인세 납부 확인 필요 1건");
        assertThat(byKey.get("taxPayment").description())
            .isEqualTo("2025년 확정 신고(2026-03-31) — 실제 낸 금액을 [납부 확인]으로 넣어 주세요");
        assertThat(byKey.get("cashShortage")).extracting(Todo::title, Todo::description)
            .containsExactly("2026-12 자금 부족 예상", "월말 잔액 4,200만 원 (최소 운영자금 5,000만 원)");
        assertThat(byKey.get("manpowerShortage").description()).isEqualTo("12월 1.5명 부족");
        assertThat(byKey.get("overAllocated")).extracting(Todo::title, Todo::description, Todo::tone, Todo::urgent)
            .containsExactly("MM 1.0 초과 2명", "한 사람의 이번 달 투입 합계가 1.0을 넘음", "warn", false);
        assertThat(byKey.get("preContract")).extracting(Todo::title, Todo::description)
            .containsExactly("미계약 선투입 3명", "선투입 A, 선투입 B");
        assertThat(byKey.get("withdrawal")).extracting(Todo::title, Todo::description, Todo::targetScreenId)
            .containsExactly("3개월 안 철수 예정 4명", "다음 달 1명 · 다음 배정 없는 2명 중 1명은 추천 자리 있음", "UI-05");
        assertThat(byKey.get("riskProjects").description()).isEqualTo("위험 프로젝트A — 기한 지난 중요(높음) 이슈 1건");
        assertThat(byKey.get("overdueIssues")).extracting(Todo::title, Todo::description, Todo::tone)
            .containsExactly("기한 지난 이슈 3건", "높음 1건 · 위험 프로젝트A, 조용한 프로젝트", "bad");
        assertThat(byKey.get("billingMissing")).extracting(Todo::title, Todo::description, Todo::tone, Todo::targetScreenId)
            .containsExactly("10월 청구 누락 의심 3건", "200만 원 · 빠진 곳, 적은 곳, 많은 곳", "bad", "UI-13");
        assertThat(byKey.get("pendingQuotes").description()).isEqualTo("견적 기회 (이익률 12.3%)");
        assertThat(byKey.get("staleProjects").description()).isEqualTo("2주 넘게 주간 메모가 없음 · 조용한 프로젝트");
        // 0건 줄은 숨기고(시안 filter(Boolean)), 철수 예정만 늘 보인다.
        assertThat(byKey.get("unassignedSalesPart")).extracting(Todo::title, Todo::visible, Todo::targetScreenId)
            .containsExactly("영업 파트 미지정 프로젝트 0건", false, "UI-01");
        verify(billing).getCommonBillChecks("202610");
    }

    @Test void issueToneIsWarnWithoutHighAndWithdrawalStaysVisibleAtZero() {
        var finance = mock(CommonFinanceItemService.class);
        var withdrawals = mock(CommonWithdrawalService.class);
        var board = mock(CommonProjectBoardService.class);
        var billing = mock(BillCheckService.class);
        when(withdrawals.getCommonWithdrawals(any())).thenReturn(new WithdrawalContracts.WithdrawalList(List.of(), 0,
            new WithdrawalContracts.Banner(0, 0, 0)));
        when(board.preview(any())).thenReturn(new CommonProjectBoardContracts.Result(
            new CommonProjectBoardContracts.Summary(new CommonProjectBoardContracts.Progress(1, 0, 1, 0), 0,
                new CommonProjectBoardContracts.OpenIssues(1, 0), 1, 0), List.of(),
            List.of(row("p1", "주의 프로젝트", "주의", List.of("기한 지난 이슈 1건"), List.of("주의"), 1)), "식"));
        when(billing.getCommonBillChecks(any())).thenReturn(new BillCheckContracts.CheckResult("202611",
            new BillCheckContracts.Summary(0, 0, 0, 0, 0), List.of(), "식", false, List.of(), null));
        var summary = new Summary(null, null, null, null, null, null, null, null, null, null, 0,
            BigDecimal.ZERO, 0, 0, BigDecimal.ZERO);
        var todos = new DashboardActivityService(finance, withdrawals, mock(CommonProjectService.class), board, billing,
            new DashboardFilters(json)).read(YearMonth.of(2026, 11), today,
            new DashboardFinancialService.Financial(summary, List.of(), List.of()),
            new DashboardOutlookService.Outlook(List.of(), List.of(), new Pipeline(0, BigDecimal.ZERO, List.of()),
                List.of(), null, null, List.of(), List.of()),
            new DashboardAssignmentService.Counts(0, 0, 0, List.of()));
        assertThat(todos).filteredOn(Todo::visible).extracting(Todo::key).containsExactly("withdrawal", "overdueIssues");
        var issue = todos.stream().filter(t -> "overdueIssues".equals(t.key())).findFirst().orElseThrow();
        assertThat(issue.tone()).isEqualTo("warn");
        assertThat(issue.urgent()).isFalse();
        assertThat(todos.stream().filter(t -> "withdrawal".equals(t.key())).findFirst().orElseThrow().alwaysVisible()).isTrue();
    }

    WithdrawalContracts.Row leaving(String developer, String end) {
        return json.convertValue(Map.of("assignmentId", developer + end, "developerId", developer, "endDate", end,
            "withdrawalCompleted", false), WithdrawalContracts.Row.class);
    }

    CommonProjectBoardContracts.Row row(String id, String name, String signal, List<String> reasons, List<String> levels,
        long overdue) {
        return json.convertValue(Map.of("projectId", id, "projectName", name, "signal", signal, "signalReasons", reasons,
            "signalReasonLevels", levels, "reasonSummary", String.join(" · ", reasons), "overdueIssueCount", overdue),
            CommonProjectBoardContracts.Row.class);
    }

    BillCheckContracts.CheckRow check(String name, String judgement, String difference) {
        return json.convertValue(Map.of("projectId", name, "projectName", name, "judgement", judgement,
            "difference", new BigDecimal(difference)), BillCheckContracts.CheckRow.class);
    }
}
