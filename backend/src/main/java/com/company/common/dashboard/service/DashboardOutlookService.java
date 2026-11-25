package com.company.common.dashboard.service;

import com.company.common.dashboard.dto.DashboardContracts.*;
import com.company.common.manpowerforecasts.service.CommonManpowerForecastService;
import com.company.common.cashflowstatements.service.CommonCashFlowStatementService;
import com.company.common.partsettlements.service.CommonPartSettlementService;
import com.company.common.salespipeline.service.CommonSalesPipelineService;
import com.company.common.salespipeline.dto.SalesPipelineContracts.Card;
import com.company.common.projectcostanalyses.service.CommonProjectCostAnalysisService;
import com.company.common.projectcostanalyses.dto.ProjectCostAnalysisContracts;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DashboardOutlookService {
    private final CommonManpowerForecastService forecast;
    private final CommonCashFlowStatementService cash;
    private final CommonPartSettlementService parts;
    private final CommonSalesPipelineService pipeline;
    private final CommonProjectCostAnalysisService projects;
    private final DashboardFilters filters;
    public DashboardOutlookService(CommonManpowerForecastService forecast, CommonCashFlowStatementService cash,
        CommonPartSettlementService parts, CommonSalesPipelineService pipeline,
        CommonProjectCostAnalysisService projects, DashboardFilters filters) {
        this.forecast = forecast; this.cash = cash; this.parts = parts; this.pipeline = pipeline;
        this.projects = projects; this.filters = filters;
    }
    public record Outlook(List<ManpowerMonth> manpower, List<SalesPart> parts, Pipeline pipeline,
        List<CashMonth> cash, List<ProjectAlert> alerts, long pendingQuotes) {}
    public Outlook read(YearMonth selected, YearMonth current) {
        var supply = forecast.forward(current, 6);
        var manpower = supply.months().stream().map(m -> new ManpowerMonth(m.month(), m.supply(), m.demand(),
            m.difference(), m.difference().compareTo(supply.thresholds().shortageThreshold().negate()) < 0)).toList();
        var balances = cash.forward(current, 3);
        var cashRows = balances.months().stream().filter(m -> !cashMonth(m.yearMonth()).isBefore(current))
            .limit(3).map(m -> {
            boolean shortage = balances.warnings().stream().anyMatch(w -> w.yearMonth().equals(m.yearMonth()));
            return new CashMonth(cashMonth(m.yearMonth()).toString(), m.closingBalance(), shortage,
                shortage ? "최소 운영자금보다 부족할 것으로 예상됩니다." : null);
        }).toList();
        var salesParts = parts.performance(selected.toString()).stream()
            .map(p -> new SalesPart(p.partId(), p.partName(), p.revenue(), p.profitRate())).toList();
        var sales = pipeline.getCommonSalesPipeline();
        var cards = sales.stages().stream().flatMap(s -> s.items().stream()).toList();
        var upcoming = cards.stream().filter(c -> c.startMonth() != null && !YearMonth.parse(c.startMonth()).isBefore(current))
            .sorted(Comparator.comparing(Card::startMonth).thenComparing(Card::opportunityId))
            .limit(3)
            .map(c -> new Opportunity(c.opportunityName(), c.startMonth())).toList();
        var projectRows = projects.getCommonProjectCostAnalyses(filters.of(ProjectCostAnalysisContracts.Filter.class,
            Map.of("year", current.getYear(), "baseMonth", current.getMonthValue())));
        var alerts = projectRows.items().stream().filter(p -> p.lowMargin() || p.budgetExceeded())
            .map(p -> new ProjectAlert(p.projectId(), p.projectName(),
                p.lowMargin() && p.budgetExceeded() ? "저마진 · 예산 초과" : p.lowMargin() ? "저마진" : "예산 초과",
                p.primaryCauseName() == null ? "원가 분석에서 주원인을 확인해 주세요." : p.primaryCauseName())).toList();
        return new Outlook(manpower, salesParts,
            new Pipeline(sales.summary().ongoingCount(), sales.summary().weightedAmount(), upcoming), cashRows, alerts,
            cards.stream().filter(c -> "WAIT".equals(c.approvalStatusCode())).count());
    }
    private static YearMonth cashMonth(String value) {
        return YearMonth.parse(value, DateTimeFormatter.ofPattern("uuuuMM"));
    }
}
