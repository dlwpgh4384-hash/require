package com.company.common.dashboard.service;

import com.company.common.dashboard.dto.DashboardContracts.*;
import com.company.common.operatingprofitstatements.dto.OperatingProfitStatementContracts;
import com.company.common.operatingprofitstatements.service.CommonOperatingProfitStatementService;
import com.company.common.operatingprofitstatements.service.OperatingProfitStatementProjection;
import com.company.common.commonops.dto.BusinessPlanComparisonContracts;
import com.company.common.commonops.service.CommonBusinessPlanComparisonService;
import com.company.common.bills.dto.BillContracts;
import com.company.common.bills.service.CommonBillService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class DashboardFinancialService {
    private final CommonOperatingProfitStatementService profits;
    private final CommonBusinessPlanComparisonService plans;
    private final DashboardUtilizationService utilization;
    private final CommonBillService bills;
    private final DashboardFilters filters;
    public DashboardFinancialService(CommonOperatingProfitStatementService profits, CommonBusinessPlanComparisonService plans,
        DashboardUtilizationService utilization, CommonBillService bills, DashboardFilters filters) {
        this.profits = profits; this.plans = plans; this.utilization = utilization; this.bills = bills; this.filters = filters;
    }
    /** late: 청구·입금 판정 「연체」(LATE) 미수 청구(확인할 일 「미수 연체」 설명에 쓴다). */
    public record Financial(Summary summary, List<TrendMonth> trend, List<BillContracts.Bill> late) {}
    public Financial read(YearMonth month, LocalDate today, int idlePeople) {
        String key = month.toString().replace("-", "");
        var report = profits.getCommonOperatingProfitStatements(new OperatingProfitStatementContracts.Filter(month.getYear(), key)).data();
        var plan = plans.getCommonBusinessPlanComparisons(new BusinessPlanComparisonContracts.Filter(month.getYear(), key)).data();
        // 미수금: 메뉴 배지(/api/common/bills/unpaid-count)와 같은 조건의 모든 해 청구, 금액은 남은 미수 잔액(식-06).
        var unpaid = bills.getCommonUnpaidBills();
        var util = utilization.read(month);
        int index = month.getMonthValue() - 1;
        var revenue = line(report, "매출액");
        var cost = line(report, "매출원가");
        var profit = line(report, "영업이익");
        BigDecimal previousRevenue;
        if (index > 0) previousRevenue = revenue.values().get(index - 1).value();
        else {
            var priorMonth = month.minusMonths(1);
            var prior = profits.getCommonOperatingProfitStatements(new OperatingProfitStatementContracts.Filter(priorMonth.getYear(),
                priorMonth.toString().replace("-", ""))).data();
            previousRevenue = line(prior, "매출액").values().get(11).value();
        }
        var amount = revenue.values().get(index).value();
        var operating = profit.values().get(index).value();
        // 연체는 청구 화면의 상태 판정(LATE)을 그대로 쓴다(날짜로 다시 계산하지 않음).
        var overdue = unpaid.stream().filter(b -> "LATE".equals(b.status())).toList();
        // 이익률은 영업이익표의 「영업이익률」 줄을 읽는다(없으면 같은 반올림으로 계산).
        var rateLine = report.numericTable().rows().stream().filter(r -> "영업이익률".equals(r.label())).findFirst();
        var operatingRate = rateLine.isPresent() ? rateLine.get().values().get(index).value() : ratio(operating, amount);
        var summary = new Summary(amount, OperatingProfitStatementProjection.change(amount, previousRevenue),
            plan.summary().baseMonthRevenueAchievementRate(), operating, operatingRate,
            report.summary().baseMonthOperatingProfit().previousMonthChangePercent(),
            report.summary().cumulativeOperatingProfit().amount(), plan.summary().cumulativeOperatingProfitAchievementRate(),
            util.rate(), util.target(), idlePeople, sum(unpaid), unpaid.size(), overdue.size(), sum(overdue));
        // 추이: 목표 매출은 12달 모두(점선), 매출·원가는 고른 달까지만(시안 k<=i).
        var trend = new ArrayList<TrendMonth>();
        for (int i = 0; i < 12; i++) trend.add(new TrendMonth(YearMonth.of(month.getYear(), i + 1).toString(),
            i <= index ? revenue.values().get(i).value() : null, i <= index ? cost.values().get(i).value() : null,
            plan.hasTargets() && i < plan.months().size() ? plan.months().get(i).revenueTarget() : null));
        return new Financial(summary, trend, overdue);
    }
    private static BigDecimal sum(List<BillContracts.Bill> rows) {
        return rows.stream().map(BillContracts.Bill::unpaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static OperatingProfitStatementContracts.Line line(OperatingProfitStatementContracts.Result report, String name) {
        return report.numericTable().rows().stream().filter(r -> name.equals(r.label())).findFirst().orElseThrow();
    }
    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        return numerator == null || denominator == null || denominator.signum() == 0 ? null
            : numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 1, RoundingMode.HALF_UP);
    }
}
