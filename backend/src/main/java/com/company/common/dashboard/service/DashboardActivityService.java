package com.company.common.dashboard.service;

import com.company.common.dashboard.dto.DashboardContracts.*;
import com.company.common.financeitems.service.CommonFinanceItemService;
import com.company.common.withdrawals.service.CommonWithdrawalService;
import com.company.common.withdrawals.dto.WithdrawalContracts;
import com.company.common.projectboard.service.CommonProjectBoardService;
import com.company.common.projectboard.dto.CommonProjectBoardContracts;
import com.company.common.billchecks.service.BillCheckService;
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
    private final DashboardAssignmentService assignments;
    private final CommonProjectBoardService projects;
    private final BillCheckService billing;
    private final DashboardFilters filters;
    public DashboardActivityService(CommonFinanceItemService finance, CommonWithdrawalService withdrawals,
        DashboardAssignmentService assignments, CommonProjectBoardService projects,
        BillCheckService billing, DashboardFilters filters) {
        this.finance = finance; this.withdrawals = withdrawals; this.assignments = assignments;
        this.projects = projects; this.billing = billing; this.filters = filters;
    }
    public List<Todo> read(YearMonth month, LocalDate today, Summary summary, DashboardOutlookService.Outlook outlook) {
        var board = projects.preview(filters.of(CommonProjectBoardContracts.Filter.class, Map.of()));
        var leaving = withdrawals.getCommonWithdrawals(filters.of(WithdrawalContracts.Filter.class,
            Map.of("year", today.getYear(), "period", "3개월 안")));
        var assigned = assignments.read(today);
        var unassigned = leaving.items().stream().filter(r -> !r.withdrawalCompleted()
            && !r.nextAssignment().hasTentativeAssignment()).toList();
        long matched = unassigned.stream().filter(r -> r.nextAssignment().recommendationCount() > 0).count();
        var result = new ArrayList<Todo>();
        result.add(todo("연체 미수", summary.overdueCount(), "입금 예정일이 지난 미수금", "UI-13", true));
        result.add(todo("법인세 납부 확인 필요", finance.overdueTaxPayments(today).size(), "납부일이 지난 미확인 법인세", "UI-10", true));
        result.add(todo("자금 부족 예상", outlook.cash().stream().filter(CashMonth::shortage).count(), "앞으로 3개월 최소 운영자금 확인", "UI-21", true));
        result.add(todo("인력 부족 예상", outlook.manpower().stream().filter(ManpowerMonth::shortage).count(), "앞으로 6개월 회사 전체 수급", "UI-17", true));
        result.add(todo("MM 1.0 초과", assigned.overAllocated(), "겹친 배정을 확인해 주세요.", "UI-12", true));
        result.add(todo("미계약 선투입", assigned.preContract(), "계약 전 투입을 확인해 주세요.", "UI-12", false));
        result.add(todo("철수 예정", unassigned.size(), "다음 배정 없는 " + unassigned.size() + "명 중 " + matched + "명은 추천 자리 있음", "UI-05", false));
        result.add(todo("영업 파트 미지정", board.items().stream().filter(p -> p.salesPartId() == null).count(), "영업 파트 기간을 확인해 주세요.", "UI-15", false));
        result.add(todo("위험 프로젝트", board.summary().riskCount(), "프로젝트 진행 신호 확인", "UI-31", true));
        result.add(todo("기한 지난 이슈", board.summary().overdueIssueCount(), "미완료 이슈의 기한 확인", "UI-31", true));
        result.add(todo("2주 초과 미갱신", board.summary().staleProjectCount(), "프로젝트 최근 기록 확인", "UI-31", false));
        result.add(todo("청구 누락 의심", billing.getCommonBillChecks(month.toString().replace("-", "")).summary().missingCount(), "선택월 청구 점검", "UI-13", false));
        result.add(todo("견적 승인 대기", outlook.pendingQuotes(), "영업 기회 견적 확인", "UI-29", false));
        return List.copyOf(result);
    }
    private static Todo todo(String title, long count, String description, String target, boolean urgent) {
        return new Todo(title, Math.toIntExact(count), description, target, urgent && count > 0);
    }
}
