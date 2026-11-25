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
    private final CommonMonthCloseService closes;
    private final UserPreferencePeriodBasis periods;
    private final BusinessCalendar calendar;
    public CommonDashboardService(DashboardFinancialService financial, DashboardOutlookService outlook,
        DashboardActivityService activity, CommonMonthCloseService closes,
        UserPreferencePeriodBasis periods, BusinessCalendar calendar) {
        this.financial = financial; this.outlook = outlook; this.activity = activity;
        this.closes = closes; this.periods = periods; this.calendar = calendar;
    }
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
        var money = financial.read(month, today);
        var forward = outlook.read(month, current);
        var checked = closes.preview(yearMonth);
        var close = checked.close();
        var closing = new Closing(close.totalCount() - close.attentionCount(), close.totalCount(), checked.remaining());
        String last = basis.closedMonths().stream().max(String::compareTo)
            .map(s -> s.substring(0, 4) + "-" + s.substring(4)).orElse(null);
        var result = new Result(yearMonth, today, close.isClosed() ? "마감" : "작업중", last, !close.isClosed(),
            month.equals(current) ? null : "매출·이익·가동률·마감·추이·파트·청구 누락은 선택월 기준, 미수·나머지 할일·경보·수급·파이프라인·자금은 오늘 기준입니다.",
            money.summary(), activity.read(month, today, money.summary(), forward), closing,
            forward.alerts(), money.trend(), forward.manpower(), forward.parts(), forward.pipeline(), forward.cash());
        return new Response<>(true, metadata, result);
    }
    public static YearMonth month(String value) {
        if (value == null || !value.matches("[0-9]{4}-(0[1-9]|1[0-2])"))
            throw CodeFailure.invalid("yearMonth", "기준 연월을 yyyy-MM 형식으로 선택해 주세요.");
        try { return YearMonth.parse(value); }
        catch (java.time.DateTimeException invalid) { throw CodeFailure.invalid("yearMonth", "기준 연월을 확인해 주세요."); }
    }
}
