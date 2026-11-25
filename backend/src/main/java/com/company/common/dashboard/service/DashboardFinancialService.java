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
    public record Financial(Summary summary, List<TrendMonth> trend) {}
    public Financial read(YearMonth month, LocalDate today) {
        String key = month.toString().replace("-", "");
        var report = profits.getCommonOperatingProfitStatements(new OperatingProfitStatementContracts.Filter(month.getYear(), key)).data();
        var plan = plans.getCommonBusinessPlanComparisons(new BusinessPlanComparisonContracts.Filter(month.getYear(), key)).data();
        var unpaid = bills.getCommonBills(filters.of(BillContracts.Filter.class, Map.of("year", today.getYear())));
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
        var overdue = unpaid.items().stream().filter(b -> !b.cancelled() && b.unpaidAmount().signum() > 0
            && b.payDueDate().isBefore(today)).toList();
        var summary = new Summary(amount, OperatingProfitStatementProjection.change(amount, previousRevenue),
            plan.summary().baseMonthRevenueAchievementRate(), operating, ratio(operating, amount),
            report.summary().baseMonthOperatingProfit().previousMonthChangePercent(),
            report.summary().cumulativeOperatingProfit().amount(), plan.summary().cumulativeOperatingProfitAchievementRate(),
            util.rate(), util.target(), util.idle(), unpaid.summary().unpaidTotal(), overdue.size(),
            overdue.stream().map(BillContracts.Bill::unpaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        var trend = new ArrayList<TrendMonth>();
        for (int i = 0; i <= index; i++) trend.add(new TrendMonth(YearMonth.of(month.getYear(), i + 1).toString(),
            revenue.values().get(i).value(), cost.values().get(i).value(),
            plan.hasTargets() ? plan.months().get(i).revenueTarget() : null));
        return new Financial(summary, trend);
    }
    private static OperatingProfitStatementContracts.Line line(OperatingProfitStatementContracts.Result report, String name) {
        return report.numericTable().rows().stream().filter(r -> name.equals(r.label())).findFirst().orElseThrow();
    }
    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        return numerator == null || denominator == null || denominator.signum() == 0 ? null
            : numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 1, RoundingMode.HALF_UP);
    }
}
