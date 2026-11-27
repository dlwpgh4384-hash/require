package com.company.common.dashboard.service;

import com.company.common.dashboard.dto.DashboardContracts.*;
import com.company.common.manpowerforecasts.service.CommonManpowerForecastService;
import com.company.common.cashflowstatements.service.CommonCashFlowStatementService;
import com.company.common.profitstructures.service.CommonProfitStructureService;
import com.company.common.support.auth.RequestAccess;
import com.company.common.salespipeline.service.CommonSalesPipelineService;
import com.company.common.salespipeline.dto.SalesPipelineContracts.Card;
import com.company.common.projectcostanalyses.service.CommonProjectCostAnalysisService;
import com.company.common.projectcostanalyses.dto.ProjectCostAnalysisContracts;
import com.company.common.salesopportunities.service.CommonSalesOpportunityService;
import java.math.BigDecimal;
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
    private final CommonProfitStructureService structures;
    private final CommonSalesPipelineService pipeline;
    private final CommonProjectCostAnalysisService projects;
    private final CommonSalesOpportunityService opportunities;
    private final DashboardFilters filters;
    public DashboardOutlookService(CommonManpowerForecastService forecast, CommonCashFlowStatementService cash,
        CommonProfitStructureService structures, CommonSalesPipelineService pipeline,
        CommonProjectCostAnalysisService projects, CommonSalesOpportunityService opportunities, DashboardFilters filters) {
        this.forecast = forecast; this.cash = cash; this.structures = structures; this.pipeline = pipeline;
        this.projects = projects; this.opportunities = opportunities; this.filters = filters;
    }
    /** 자금수지표의 첫 부족 예상 달(식-51): 마감 안 된 달 중 월말 잔액이 최소 운영자금보다 적은 첫 달. */
    public record CashShortage(YearMonth month, BigDecimal balance, BigDecimal minimumCash, int monthCount) {}
    /** 견적 승인 대기 기회(영업 기회 상세의 저장된 예상 이익률). */
    public record PendingQuote(String opportunityName, BigDecimal profitRate) {}
    public record Outlook(List<ManpowerMonth> manpower, List<SalesPart> parts, Pipeline pipeline,
        List<CashMonth> cash, BigDecimal minimumCash, CashShortage cashShortage, List<ProjectAlert> alerts,
        List<PendingQuote> pendingQuotes) {}
    /**
     * @param firstOpen 마지막 마감 달의 다음 달(시안 CF.m &gt; closedTo). 자금 3칸은 이 달부터 3달,
     *                  부족 예상은 이 달부터 올해 끝까지(시안 CF는 올해 12달) 마감 안 된 달 전체를 본다.
     */
    public Outlook read(YearMonth selected, YearMonth current, YearMonth firstOpen) {
        var supply = forecast.forward(current, 6);
        var manpower = supply.months().stream().map(m -> new ManpowerMonth(m.month(), m.supply(), m.demand(),
            m.difference(), m.difference().compareTo(supply.thresholds().shortageThreshold().negate()) < 0)).toList();
        int span = Math.max(3, (int) java.time.temporal.ChronoUnit.MONTHS.between(firstOpen, YearMonth.of(current.getYear(), 12)) + 1);
        var balances = cash.forward(firstOpen, span);
        var open = balances.months().stream().filter(m -> !cashMonth(m.yearMonth()).isBefore(firstOpen)).toList();
        var cashRows = open.stream().limit(3).map(m -> {
            boolean shortage = balances.warnings().stream().anyMatch(w -> w.yearMonth().equals(m.yearMonth()));
            return new CashMonth(cashMonth(m.yearMonth()).toString(), m.closingBalance(), shortage,
                shortage ? "최소 운영자금보다 부족할 것으로 예상됩니다." : null);
        }).toList();
        var shortMonths = balances.warnings().stream().filter(w -> !cashMonth(w.yearMonth()).isBefore(firstOpen)).toList();
        var cashShortage = shortMonths.isEmpty() ? null : new CashShortage(cashMonth(shortMonths.get(0).yearMonth()),
            shortMonths.get(0).expectedBalance(), shortMonths.get(0).minimumCash(), shortMonths.size());
        // 영업 파트별 = 전체 수익 구조(UI-07) 월 보기의 영업 파트별(프로젝트를 따온 파트 기준, 시안 byP, 내부 제외).
        // 담당 파트 범위면 자기 파트 줄만(시안 myParts), 미지정 줄은 전체 범위에서만 보인다.
        var scope = RequestAccess.currentScope();
        var salesParts = structures.monthSalesParts(selected).stream()
            .filter(p -> scope.all() || scope.partIds().contains(p.partId()))
            .map(p -> new SalesPart(p.partId(), p.row().name(), p.row().revenue(), p.row().profitRate())).toList();
        var sales = pipeline.getCommonSalesPipeline();
        var stageNames = new java.util.HashMap<String, String>();
        sales.stages().forEach(s -> s.items().forEach(c -> stageNames.put(c.opportunityId(), s.stageName())));
        var cards = sales.stages().stream().flatMap(s -> s.items().stream()).toList();
        // 시안 soon: 진행 기회 중 시작월이 오늘+3개월 이내(이미 시작한 것 포함), 확률 높은 순 3건.
        var horizon = current.plusMonths(3);
        var upcoming = cards.stream().filter(c -> c.startMonth() != null && !YearMonth.parse(c.startMonth()).isAfter(horizon))
            .sorted(Comparator.comparing(Card::winningProbability, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Card::startMonth).thenComparing(Card::opportunityId))
            .limit(3)
            .map(c -> new Opportunity(c.opportunityId(), c.opportunityName(), c.startMonth(),
                stageNames.get(c.opportunityId()), c.winningProbability())).toList();
        // 원가 분석(UI-04)이 고를 수 있는 가장 늦은 달은 첫 미마감 달이라, 오늘 달이 그보다 뒤면 그 달로 본다.
        var alertMonth = alertMonth(firstOpen, current);
        var projectRows = projects.getCommonProjectCostAnalyses(filters.of(ProjectCostAnalysisContracts.Filter.class,
            Map.of("year", alertMonth.getYear(), "baseMonth", alertMonth.getMonthValue())));
        // 시안 projAl: 경보가 있는 프로젝트, 경보 수 많은 순(같으면 원가 분석 목록 순서).
        var alerts = projectRows.items().stream().filter(p -> p.lowMargin() || p.budgetExceeded())
            .sorted(Comparator.comparingInt(ProjectCostAnalysisContracts.CommonProjectCostAnalysisRow::alertCount).reversed())
            .map(p -> {
                boolean turnkey = "TK".equals(p.contractTypeCode());
                boolean cause = p.primaryCauseName() != null && p.primaryCauseAmount() != null && p.primaryCauseAmount().signum() > 0;
                return new ProjectAlert(p.projectId(), p.projectName(), p.contractTypeCode(),
                    turnkey ? "예상 최종 마진율" : "월간 마진율", turnkey ? p.expectedFinalMarginRate() : p.monthlyMarginRate(),
                    p.budgetUsageRate(), List.copyOf(p.alerts()),
                    p.alerts().stream().map(a -> new AlertBadge(a, "예산 초과".equals(a) ? "bad" : "warn")).toList(),
                    String.join(" · ", p.alerts()), cause ? p.primaryCauseName() : null,
                    cause ? p.primaryCauseAmount() : null);
            }).toList();
        var pending = cards.stream().filter(c -> "WAIT".equals(c.approvalStatusCode()))
            .map(c -> new PendingQuote(c.opportunityName(),
                opportunities.getCommonSalesOpportunity(c.opportunityId()).expectedProfitRate())).toList();
        return new Outlook(manpower, salesParts,
            new Pipeline(sales.summary().ongoingCount(), sales.summary().weightedAmount(), upcoming), cashRows,
            balances.minimumCash(), cashShortage, alerts, pending);
    }
    /** 프로젝트 경보를 읽는 달: 첫 미마감 달과 오늘 달 중 빠른 달(원가 분석이 받는 가장 늦은 달 이내). */
    public static YearMonth alertMonth(YearMonth firstOpen, YearMonth current) {
        return firstOpen.isBefore(current) ? firstOpen : current;
    }
    private static YearMonth cashMonth(String value) {
        return YearMonth.parse(value, DateTimeFormatter.ofPattern("uuuuMM"));
    }
}
