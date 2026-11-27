package com.company.common.dashboard.service;

import com.company.common.dashboard.dto.DashboardContracts.*;
import com.company.common.monthcloses.service.CommonMonthCloseService;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.users.dto.UserContracts.Response;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommonDashboardService {
    private final DashboardFinancialService financial;
    private final DashboardOutlookService outlook;
    private final DashboardActivityService activity;
    private final DashboardAssignmentService assignments;
    private final CommonMonthCloseService closes;
    private final UserPreferencePeriodBasis periods;
    private final BusinessCalendar calendar;
    private final org.springframework.beans.factory.ObjectProvider<com.company.common.partsettlements.service.CommonPartSettlementService> partSettlements;
    public CommonDashboardService(DashboardFinancialService financial, DashboardOutlookService outlook,
        DashboardActivityService activity, DashboardAssignmentService assignments, CommonMonthCloseService closes,
        UserPreferencePeriodBasis periods, BusinessCalendar calendar,
        org.springframework.beans.factory.ObjectProvider<com.company.common.partsettlements.service.CommonPartSettlementService> partSettlements) {
        this.financial = financial; this.outlook = outlook; this.activity = activity; this.assignments = assignments;
        this.closes = closes; this.periods = periods; this.calendar = calendar; this.partSettlements = partSettlements;
    }
    static final String PAST_MONTH_NOTICE = "미수금·확인할 일·프로젝트 경보·인력 수급·파이프라인·자금은 오늘 기준(청구 누락 의심은 고른 달)";
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Response<Result> getCommonDashboard(String yearMonth) {
        var month = month(yearMonth);
        var metadata = periods.metadata();
        if (!(metadata.get("periodBasis") instanceof UserPreferencePeriodBasis.Basis basis))
            throw CodeFailure.invalid("yearMonth", "기간 기준을 확인해 주세요.");
        String key = month.format(DateTimeFormatter.ofPattern("uuuuMM"));
        if (key.compareTo(basis.cashBaseYm()) < 0 || key.compareTo(basis.latestYm()) > 0)
            throw CodeFailure.invalid("yearMonth", "조회할 수 있는 기준월을 선택해 주세요.");
        var today = calendar.today();
        var current = YearMonth.from(today);
        String last = basis.closedMonths().stream().max(String::compareTo)
            .map(s -> s.substring(0, 4) + "-" + s.substring(4)).orElse(null);
        var assigned = assignments.read(today);
        var money = financial.read(month, today, assigned.idle());
        var open = basis.firstOpen(current);
        var forward = outlook.read(month, current, open);
        var checked = closes.preview(yearMonth);
        var close = checked.close();
        var closing = new Closing(close.totalCount() - close.attentionCount(), close.totalCount(),
            checked.remaining().stream().map(c -> new ClosingItem(c.name(), c.status(), c.description(), c.targetScreenId())).toList());
        // 시안 asof: 「오늘 날짜 · n월까지 마감」, 미마감이면 「[잠정] n월은 마감 전이라 숫자는 잠정입니다」, 지난달이면 오늘 기준 안내.
        String asOf = "오늘 " + today + (last == null ? "" : " · " + YearMonth.parse(last).getMonthValue() + "월까지 마감");
        String provisional = close.isClosed() ? null : month.getMonthValue() + "월은 마감 전이라 숫자는 잠정입니다";
        var result = new Result(yearMonth, today, close.isClosed() ? "마감" : "작업중", last, !close.isClosed(),
            asOf, provisional, month.equals(current) ? null : PAST_MONTH_NOTICE,
            money.summary(), activity.read(month, today, money, forward, assigned), closing,
            forward.alerts(), DashboardOutlookService.alertMonth(open, current).toString(), money.trend(), forward.manpower(), forward.parts(), forward.pipeline(), forward.cash(),
            forward.minimumCash(), "all");
        // 모양은 로그인 사용자의 데이터 범위로(명세 ⑧-4): 전체 / 파트장 / PM.
        String mode = com.company.common.support.auth.RequestAccess.dashboardMode();
        result = DashboardScopeShape.shape(mode, result, month, "part".equals(mode) ? partSettlements.getIfAvailable() : null);
        return new Response<>(true, metadata, result);
    }
    public static YearMonth month(String value) {
        if (value == null || !value.matches("[0-9]{4}-(0[1-9]|1[0-2])"))
            throw CodeFailure.invalid("yearMonth", "기준 연월을 yyyy-MM 형식으로 선택해 주세요.");
        try { return YearMonth.parse(value); }
        catch (java.time.DateTimeException invalid) { throw CodeFailure.invalid("yearMonth", "기준 연월을 확인해 주세요."); }
    }
}
