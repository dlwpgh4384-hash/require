package com.company.common.dashboard.service;

import com.company.common.dashboard.dto.DashboardContracts.*;
import com.company.common.financeitems.service.CommonFinanceItemService;
import com.company.common.withdrawals.service.CommonWithdrawalService;
import com.company.common.withdrawals.dto.WithdrawalContracts;
import com.company.common.projectboard.service.CommonProjectBoardService;
import com.company.common.projectboard.dto.CommonProjectBoardContracts;
import com.company.common.billchecks.service.BillCheckService;
import com.company.common.projects.service.CommonProjectService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DashboardActivityService {
    private final CommonFinanceItemService finance;
    private final CommonWithdrawalService withdrawals;
    private final CommonProjectService projectList;
    private final CommonProjectBoardService projects;
    private final BillCheckService billing;
    private final DashboardFilters filters;
    public DashboardActivityService(CommonFinanceItemService finance, CommonWithdrawalService withdrawals,
        CommonProjectService projectList, CommonProjectBoardService projects,
        BillCheckService billing, DashboardFilters filters) {
        this.finance = finance; this.withdrawals = withdrawals; this.projectList = projectList;
        this.projects = projects; this.billing = billing; this.filters = filters;
    }
    /**
     * 시안 warns(SCR['UI-16'])·EXTRA_WARNS 순서·문구: 연체 → 법인세 → 자금 → 인력 → MM 1.0 초과 → 미계약 선투입 →
     * 철수 예정 → 위험 프로젝트 → 기한 지난 이슈 → 청구 누락 의심(고른 달) → 견적 승인 대기 → 상태 업데이트 없음 →
     * 영업 파트 미지정. 0건 줄은 visible=false(시안은 빼고 그림), 철수 예정만 전체 모양에서 늘 보인다.
     */
    public List<Todo> read(YearMonth month, LocalDate today, DashboardFinancialService.Financial money,
        DashboardOutlookService.Outlook outlook, DashboardAssignmentService.Counts assigned) {
        var board = projects.preview(filters.of(CommonProjectBoardContracts.Filter.class, Map.of()));
        var result = new ArrayList<Todo>();
        var late = money.late();
        result.add(todo("overdueBills", "bad", "미수 연체 " + late.size() + "건", late.size(),
            kor(money.summary().overdueAmount()) + " · " + String.join(", ", late.stream().map(b -> b.projectName()).toList()),
            money.summary().overdueAmount(), "UI-13", "청구·입금", false));
        var taxes = finance.overdueTaxPayments(today);
        result.add(todo("taxPayment", "bad", "법인세 납부 확인 필요 " + taxes.size() + "건", taxes.size(),
            String.join(" · ", taxes.stream().map(t -> t.taxYear() + "년 " + ("MID".equals(t.paymentType()) ? "중간예납" : "확정 신고")
                + "(" + t.scheduledDate() + ") — 실제 낸 금액을 [납부 확인]으로 넣어 주세요").toList()),
            null, "UI-10", "재무 항목", false));
        var shortage = outlook.cashShortage();
        result.add(shortage == null
            ? todo("cashShortage", "bad", "자금 부족 예상", 0, "", null, "UI-21", "자금수지표", false)
            : todo("cashShortage", "bad", shortage.month() + " 자금 부족 예상", shortage.monthCount(),
                "월말 잔액 " + kor(shortage.balance()) + " (최소 운영자금 " + kor(shortage.minimumCash()) + ")",
                shortage.balance(), "UI-21", "자금수지표", false));
        var gaps = outlook.manpower().stream().filter(ManpowerMonth::shortage).toList();
        result.add(todo("manpowerShortage", "bad", "인력 부족 예상", gaps.size(),
            String.join(" · ", gaps.stream().limit(3).map(m -> monthNumber(m.month()) + "월 "
                + m.difference().abs().setScale(1, RoundingMode.HALF_UP).toPlainString() + "명 부족").toList()),
            null, "UI-17", "인력 수요 예측", false));
        result.add(todo("overAllocated", "warn", "MM 1.0 초과 " + assigned.overAllocated() + "명", assigned.overAllocated(),
            "한 사람의 이번 달 투입 합계가 1.0을 넘음", null, "UI-12", "투입 배정", false));
        result.add(todo("preContract", "warn", "미계약 선투입 " + assigned.preContract() + "명", assigned.preContract(),
            String.join(", ", assigned.preContractProjects()), null, "UI-12", "투입 배정", false));
        result.add(withdrawal(today));
        var risk = board.items().stream().filter(r -> "위험".equals(r.signal())).toList();
        result.add(todo("riskProjects", "bad", "위험 프로젝트 " + risk.size() + "건", risk.size(),
            String.join(" · ", risk.stream().map(r -> r.projectName() + " — " + dangerReason(r)).toList()),
            null, "UI-31", "프로젝트 현황판", false));
        long overdueIssues = board.summary().overdueIssueCount();
        long highIssues = board.items().stream().mapToLong(DashboardActivityService::highOverdue).sum();
        result.add(todo("overdueIssues", highIssues > 0 ? "bad" : "warn", "기한 지난 이슈 " + overdueIssues + "건", overdueIssues,
            "높음 " + highIssues + "건 · " + String.join(", ", board.items().stream().filter(r -> r.overdueIssueCount() > 0)
                .map(CommonProjectBoardContracts.Row::projectName).distinct().toList()),
            null, "UI-31", "프로젝트 현황판", false));
        var checks = billing.getCommonBillChecks(month.toString().replace("-", ""));
        var suspicious = checks.items().stream().filter(r -> !"맞음".equals(r.judgement())).toList();
        var missingAmount = suspicious.stream().map(r -> r.difference() == null ? BigDecimal.ZERO : r.difference().max(BigDecimal.ZERO))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        result.add(todo("billingMissing", "bad", month.getMonthValue() + "월 청구 누락 의심 " + suspicious.size() + "건",
            suspicious.size(), kor(missingAmount) + " · " + String.join(", ", suspicious.stream()
                .map(r -> r.projectName()).toList()), missingAmount, "UI-13", "청구·입금", false));
        var quotes = outlook.pendingQuotes();
        result.add(todo("pendingQuotes", "warn", "견적 승인 대기 " + quotes.size() + "건", quotes.size(),
            String.join(" · ", quotes.stream().map(q -> q.opportunityName() + " (이익률 " + pct(q.profitRate()) + ")").toList()),
            null, "UI-29", "영업 파이프라인", false));
        var stale = board.staleProjects();
        result.add(todo("staleProjects", "warn", "상태 업데이트 없음 " + stale.size() + "건", stale.size(),
            "2주 넘게 주간 메모가 없음 · " + String.join(", ", stale.stream()
                .map(CommonProjectBoardContracts.StaleProject::projectName).toList()),
            null, "UI-31", "프로젝트 현황판", false));
        long noPart = projectList.getCommonProjectUnassignedCount();
        result.add(todo("unassignedSalesPart", "warn", "영업 파트 미지정 프로젝트 " + noPart + "건", noPart,
            "파트별 실적·권한·정산에서 빠짐", null, "UI-01", "프로젝트", false));
        return List.copyOf(result);
    }

    /** 철수 예정: 철수 리스트(UI-05) 안내 띠(식-82)와 같은 값. 다음 달 n명은 다음 달에 끝나는 사람 수(시안 nextEnd). */
    private Todo withdrawal(LocalDate today) {
        var current = YearMonth.from(today);
        var leaving = withdrawals.getCommonWithdrawals(filters.of(WithdrawalContracts.Filter.class,
            Map.of("year", today.getYear(), "period", "3개월 안")));
        var banner = leaving.banner();
        var next = current.plusMonths(1);
        var rows = next.getYear() == today.getYear() ? leaving.items()
            : withdrawals.getCommonWithdrawals(filters.of(WithdrawalContracts.Filter.class,
                Map.of("year", next.getYear(), "period", "3개월 안"))).items();
        long nextMonth = rows.stream().filter(r -> !r.withdrawalCompleted() && YearMonth.from(r.endDate()).equals(next))
            .map(WithdrawalContracts.Row::developerId).distinct().count();
        int soon = banner == null ? 0 : banner.soonCount();
        int unassigned = banner == null ? 0 : banner.unassignedCount();
        int matched = banner == null ? 0 : banner.matchedCount();
        return todo("withdrawal", "warn", "3개월 안 철수 예정 " + soon + "명", soon,
            "다음 달 " + nextMonth + "명 · 다음 배정 없는 " + unassigned + "명 중 " + matched + "명은 추천 자리 있음",
            null, "UI-05", "철수 리스트", true);
    }

    private static final java.util.regex.Pattern HIGH_OVERDUE = java.util.regex.Pattern.compile("기한 지난 중요\\(높음\\) 이슈 (\\d+)건");

    /** 현황판 신호 이유(ProjectBoardSignalRules)의 「기한 지난 중요(높음) 이슈 n건」에서 높음 수를 읽는다. */
    private static long highOverdue(CommonProjectBoardContracts.Row row) {
        if (row.signalReasons() == null) return 0;
        for (var reason : row.signalReasons()) {
            var found = HIGH_OVERDUE.matcher(reason);
            if (found.matches()) return Long.parseLong(found.group(1));
        }
        return 0;
    }

    private static String dangerReason(CommonProjectBoardContracts.Row row) {
        var reasons = row.signalReasons() == null ? List.<String>of() : row.signalReasons();
        var levels = row.signalReasonLevels() == null ? List.<String>of() : row.signalReasonLevels();
        for (int i = 0; i < reasons.size() && i < levels.size(); i++) if ("위험".equals(levels.get(i))) return reasons.get(i);
        return row.reasonSummary();
    }

    private static int monthNumber(String month) {
        return Integer.parseInt(month.substring(month.length() - 2));
    }

    private static Todo todo(String key, String tone, String title, long count, String description, BigDecimal amount,
        String target, String targetName, boolean alwaysVisible) {
        boolean visible = alwaysVisible || count > 0;
        return new Todo(key, title, Math.toIntExact(count), description, amount, target, targetName, tone,
            "bad".equals(tone) && count > 0, alwaysVisible, visible);
    }

    /** 시안 kor(): 1억 이상은 「n.n억 원」, 아니면 「n만 원」(반올림 HALF_UP), 음수는 「−」. */
    static String kor(BigDecimal value) {
        if (value == null) return "–";
        var abs = value.abs();
        String sign = value.signum() < 0 ? "−" : "";
        if (abs.compareTo(HUNDRED_MILLION) >= 0)
            return sign + abs.divide(HUNDRED_MILLION, 1, RoundingMode.HALF_UP).toPlainString() + "억 원";
        var manwon = abs.divide(TEN_THOUSAND, 0, RoundingMode.HALF_UP);
        return sign + new java.text.DecimalFormat("#,##0").format(manwon) + "만 원";
    }

    /** 시안 pct(): 소수 한 자리 %, 없으면 「–」. */
    static String pct(BigDecimal value) {
        if (value == null) return "–";
        return (value.signum() < 0 ? "−" : "") + value.abs().setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private static final BigDecimal HUNDRED_MILLION = new BigDecimal("100000000");
    private static final BigDecimal TEN_THOUSAND = new BigDecimal("10000");
}
