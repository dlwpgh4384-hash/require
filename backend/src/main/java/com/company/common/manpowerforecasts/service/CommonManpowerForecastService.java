package com.company.common.manpowerforecasts.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.manpowerforecasts.dto.ManpowerForecastContracts.*;
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
    private final BusinessCalendar calendar;

    public CommonManpowerForecastService(SalesPipelineForecastService forecast,
        CalculationRuleService standards, BusinessCalendar calendar) {
        this.forecast = forecast;
        this.standards = standards;
        this.calendar = calendar;
    }

    public Result getCommonManpowerForecasts(Integer year) {
        if (year == null) throw CodeFailure.invalid("year", "조회 연도를 넣어 주세요.");
        var base = calendar.currentMonth();
        // 시안 YR['UI-17'] = 올해·내년만(한국 시간 기준 이번 달의 연도).
        if (year != base.getYear() && year != base.getYear() + 1)
            throw CodeFailure.invalid("year", "선택할 수 있는 조회 연도를 확인해 주세요.");
        var windows = new ArrayList<MonthWindow>();
        for (int number = 1; number <= 12; number++) {
            var month = YearMonth.of(year, number);
            if (!month.isBefore(base))
                windows.add(new MonthWindow(calendar.toDatabaseMonth(month), month.atDay(1), month.atEndOfMonth()));
        }
        var core = forecast.getWorkforceForecast(windows);
        // 가용·필요·미충원은 인력 운영 보드(UI-28)가 쓰는 영업 파이프라인 예측과 같은 두 자리, 차이는 화면·엑셀에 보이는 한 자리 값끼리 뺀다.
        var months = core.stream().map(m -> {
            var supply = mm(m.supply());
            var demand = mm(m.demand());
            return new Month(m.month(), supply, demand, mm(m.unfilledDemand()), one(supply).subtract(one(demand)));
        }).toList();
        JobShortage shortage = null;
        BigDecimal minimum = new BigDecimal("-0.05");
        if (!core.isEmpty()) {
            // 시안 worst: 직무·달마다 한 자리로 반올림한 가용 − 필요, 직무별 기간 최솟값 중 가장 작은 직무.
            // 직무 순서를 바깥에 두어 다른 달의 동률도 기존 SORT_ORD,CODE 순서로 결정한다.
            for (var job : core.get(0).jobs()) {
                for (var month : core) {
                    for (var value : month.jobs()) {
                        if (!value.jobCode().equals(job.jobCode())) continue;
                        var difference = one(value.supply()).subtract(one(value.demand()));
                        if (difference.compareTo(minimum) < 0) {
                            minimum = difference;
                            shortage = new JobShortage(value.jobName(), difference.abs());
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
    /** 화면·엑셀에 보이는 「x.x명」 값(HALF_UP). */
    private static BigDecimal one(BigDecimal value) { return value.setScale(1, RoundingMode.HALF_UP); }
}
