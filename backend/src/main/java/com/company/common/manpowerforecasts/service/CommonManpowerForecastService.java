package com.company.common.manpowerforecasts.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.manpowerforecasts.dto.ManpowerForecastContracts.*;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.salespipeline.mapper.SalesPipelineRows.MonthWindow;
import com.company.common.salespipeline.service.SalesPipelineForecastService;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CommonManpowerForecastService {
    private final SalesPipelineForecastService forecast;
    private final CalculationRuleService standards;
    private final UserPreferencePeriodBasis periods;
    private final BusinessCalendar calendar;

    public CommonManpowerForecastService(SalesPipelineForecastService forecast,
        CalculationRuleService standards, UserPreferencePeriodBasis periods, BusinessCalendar calendar) {
        this.forecast = forecast;
        this.standards = standards;
        this.periods = periods;
        this.calendar = calendar;
    }

    public Result getCommonManpowerForecasts(Integer year) {
        if (year == null) throw CodeFailure.invalid("year", "조회 연도를 넣어 주세요.");
        if (!(periods.metadata().get("periodBasis") instanceof UserPreferencePeriodBasis.Basis basis))
            throw CodeFailure.invalid("year", "기간 기준을 확인할 수 없습니다. 운영 기준값을 확인해 주세요.");
        var base = calendar.currentMonth();
        int firstYear = Integer.parseInt(basis.cashBaseYm().substring(0, 4));
        if (year < firstYear || year > base.getYear() + 1)
            throw CodeFailure.invalid("year", "선택할 수 있는 조회 연도를 확인해 주세요.");
        var windows = new ArrayList<MonthWindow>();
        for (int number = 1; number <= 12; number++) {
            var month = YearMonth.of(year, number);
            if (!month.isBefore(base))
                windows.add(new MonthWindow(calendar.toDatabaseMonth(month), month.atDay(1), month.atEndOfMonth()));
        }
        var core = forecast.getWorkforceForecast(windows);
        var months = core.stream().map(m -> new Month(m.month(), mm(m.supply()), mm(m.demand()),
            mm(m.unfilledDemand()), m.supply().subtract(m.demand()))).toList();
        JobShortage shortage = null;
        BigDecimal minimum = new BigDecimal("-0.05");
        if (!core.isEmpty()) {
            // 직무 순서를 바깥에 두어 다른 달의 동률도 기존 SORT_ORD,CODE 순서로 결정한다.
            for (var job : core.get(0).jobs()) {
                for (var month : core) {
                    for (var value : month.jobs()) {
                        if (!value.jobCode().equals(job.jobCode())) continue;
                        var difference = value.supply().subtract(value.demand());
                        if (difference.compareTo(minimum) < 0) {
                            minimum = difference;
                            shortage = new JobShortage(value.jobName(), difference.negate());
                        }
                    }
                }
            }
        }
        var last = months.isEmpty() ? null : months.get(months.size() - 1);
        var summary = new Summary(last == null ? BigDecimal.ZERO : last.supply(),
            last == null ? BigDecimal.ZERO : last.demand(), shortage);
        var thresholds = new Thresholds(standards.effectiveStandard("SHORT_PERSON", base).value(),
            standards.effectiveStandard("SURPLUS_PERSON", base).value());
        return new Result(months, summary, thresholds);
    }

    private static BigDecimal mm(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
}
