package com.company.common.dashboard;

import com.company.common.dashboard.service.*;
import com.company.common.operatingprofitstatements.dto.OperatingProfitStatementContracts.*;
import com.company.common.operatingprofitstatements.service.CommonOperatingProfitStatementService;
import com.company.common.commonops.dto.BusinessPlanComparisonContracts;
import com.company.common.commonops.service.CommonBusinessPlanComparisonService;
import com.company.common.bills.dto.BillContracts;
import com.company.common.bills.service.CommonBillService;
import com.company.common.users.dto.UserContracts.Response;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardFinancialTest {
    @Test void selectedValuesAndCumulativeAchievementComeFromOwningServicesButBillsUseTodayYear() {
        var profits = mock(CommonOperatingProfitStatementService.class);
        var plans = mock(CommonBusinessPlanComparisonService.class);
        var util = mock(DashboardUtilizationService.class);
        var bills = mock(CommonBillService.class);
        var json = JsonMapper.builder().findAndAddModules().build();
        var filters = new DashboardFilters(json);
        var metric = new Metric(new BigDecimal("420"), null, null);
        var summary = new Summary(metric, metric, metric, BigDecimal.TEN,
            new Metric(new BigDecimal("9"), null, new BigDecimal("-25")));
        var cells = IntStream.range(1, 13).mapToObj(i -> new Cell(BigDecimal.valueOf(i * 100L), "money")).toList();
        var report = new Result(2026, "202602", false, null, summary, new NumericTable(List.of(), List.of(
            new Line("매출액", "bold", false, cells), new Line("매출원가", "plain", true, cells),
            new Line("영업이익", "bold", false, cells))));
        when(profits.getCommonOperatingProfitStatements(any())).thenReturn(new Response<>(true, Map.of(), report));
        var planSummary = new BusinessPlanComparisonContracts.Summary(null, BigDecimal.ONE, null,
            new BigDecimal("63.7"), BigDecimal.ONE, BigDecimal.TEN, null, null);
        when(plans.getCommonBusinessPlanComparisons(any())).thenReturn(new Response<>(true, Map.of(),
            new BusinessPlanComparisonContracts.Result(2026, "202602", false, false, planSummary, List.of())));
        when(util.read(any())).thenReturn(new DashboardUtilizationService.Result(null, BigDecimal.ONE, BigDecimal.ZERO));
        var late = json.convertValue(Map.of("billId", "owned-late", "unpaidAmount", 33,
            "cancelled", false, "payDueDate", "2027-01-01"), BillContracts.Bill.class);
        when(bills.getCommonBills(any())).thenReturn(new BillContracts.BillList(List.of(late),
            new BillContracts.Summary(1, BigDecimal.ONE, BigDecimal.ONE, 1, new BigDecimal("33"), 1),
            new BillContracts.Totals(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE), false));
        var result = new DashboardFinancialService(profits, plans, util, bills, filters)
            .read(YearMonth.of(2026, 2), LocalDate.of(2027, 1, 2));
        assertThat(result.summary().revenue()).isEqualByComparingTo("200");
        assertThat(result.summary().revenuePreviousComparison()).isEqualByComparingTo("100");
        assertThat(result.summary().cumulativeAchievementRate()).isEqualByComparingTo("63.7");
        assertThat(result.summary().cumulativeOperatingProfit()).isEqualByComparingTo("420");
        assertThat(result.summary().revenueTargetRate()).isNull();
        assertThat(result.summary().overdueCount()).isEqualTo(1);
        assertThat(result.summary().overdueAmount()).isEqualByComparingTo("33");
        assertThat(result.trend()).hasSize(2).allSatisfy(m -> assertThat(m.targetRevenue()).isNull());
        var billFilter = ArgumentCaptor.forClass(BillContracts.Filter.class);
        verify(bills).getCommonBills(billFilter.capture());
        assertThat(billFilter.getValue().year()).isEqualTo(2027);
        verify(profits).getCommonOperatingProfitStatements(new Filter(2026, "202602"));
        verify(plans).getCommonBusinessPlanComparisons(new BusinessPlanComparisonContracts.Filter(2026, "202602"));
    }
}
