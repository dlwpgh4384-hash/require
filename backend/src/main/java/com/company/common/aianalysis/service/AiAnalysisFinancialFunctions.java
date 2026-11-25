package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService;
import com.company.common.partsettlements.service.CommonPartSettlementService;
import com.company.common.projectcostanalyses.service.CommonProjectCostAnalysisService;
import com.company.common.projectcostanalyses.dto.ProjectCostAnalysisContracts;
import com.company.common.commonops.service.CommonBusinessPlanComparisonService;
import com.company.common.commonops.dto.BusinessPlanComparisonContracts;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisFinancialFunctions implements AiAnalysisFunction {
    private final ProfitStructureMonthlyProfitService monthly;
    private final CommonPartSettlementService parts;
    private final CommonProjectCostAnalysisService projects;
    private final CommonBusinessPlanComparisonService plans;
    private final UserPreferencePeriodBasis periods;
    private final AiAnalysisTables tables;
    public AiAnalysisFinancialFunctions(ProfitStructureMonthlyProfitService monthly, CommonPartSettlementService parts,
        CommonProjectCostAnalysisService projects, CommonBusinessPlanComparisonService plans,
        UserPreferencePeriodBasis periods, AiAnalysisTables tables) {
        this.monthly = monthly; this.parts = parts; this.projects = projects;
        this.plans = plans; this.periods = periods; this.tables = tables;
    }
    public Set<String> functions() { return Set.of("F-01", "F-02", "F-03", "F-05", "P-02"); }
    public Result read(AiAnalysisInterpreter.Query query) {
        var first = YearMonth.parse(query.period().startMonth());
        var end = YearMonth.parse(query.period().endMonth());
        var rows = new ArrayList<Object>();
        boolean provisional = false;
        switch (query.function()) {
            case "F-01": {
                if (!(periods.metadata().get("periodBasis") instanceof UserPreferencePeriodBasis.Basis basis)) return null;
                for (int year = first.getYear(); year <= end.getYear(); year++) {
                    var source = monthly.year(year, year == end.getYear() ? end.getMonthValue() : 12, basis.cashBaseYm());
                    Map<String, ProfitStructureMonthlyProfitService.Month> previous = query.question().contains("전년")
                        ? monthly.year(year - 1, 12, basis.cashBaseYm()) : Map.of();
                    for (var entry : source.entrySet()) {
                        var month = YearMonth.of(year, Integer.parseInt(entry.getKey().substring(4)));
                        if (month.isBefore(first) || month.isAfter(end) || !entry.getValue().available()) continue;
                        var value = entry.getValue().values();
                        var row = new LinkedHashMap<String, Object>();
                        row.put("month", month.toString());
                        row.put("revenue", value.revenue());
                        row.put("operatingProfit", value.operatingProfit());
                        row.put("netProfit", value.netProfit());
                        var old = previous.get(month.minusYears(1).toString().replace("-", ""));
                        if (old != null && old.available()) {
                            row.put("previousRevenue", old.values().revenue());
                            row.put("previousOperatingProfit", old.values().operatingProfit());
                        }
                        rows.add(row);
                        provisional |= entry.getValue().provisional();
                    }
                }
                var fields = new ArrayList<>(List.of("month", "연월"));
                boolean all = query.question().contains("구조") || query.question().contains("매출") && query.question().contains("영업이익");
                if (all || query.question().contains("매출")) fields.addAll(List.of("revenue", "매출"));
                if (all || query.question().contains("영업이익")) fields.addAll(List.of("operatingProfit", "영업이익"));
                if (query.question().contains("순이익") || query.question().contains("구조")) fields.addAll(List.of("netProfit", "순이익"));
                if (query.question().contains("전년")) fields.addAll(List.of("previousRevenue", "전년 매출", "previousOperatingProfit", "전년 영업이익"));
                return new Result(tables.table(rows, fields.toArray(String[]::new)), query.period(), provisional,
                    query.question().contains("순이익") ? "UI-09" : query.question().contains("구조") ? "UI-07" : "UI-08", List.of());
            }
            case "F-02": {
                for (YearMonth month = first; !month.isAfter(end); month = month.plusMonths(1)) {
                    var options = parts.getCommonPartSettlements(month.toString()).data().items().stream()
                        .filter(p -> query.question().contains(p.partId()) || query.question().contains(p.partName())).toList();
                    if (options.size() != 1) return null;
                    var result = parts.getCommonPartSettlement(options.get(0).partId(), month.toString());
                    var row = new LinkedHashMap<String, Object>();
                    row.put("month", month.toString()); row.put("partName", result.partName());
                    row.put("partProfitAmount", result.summary().partProfitAmount());
                    rows.add(row); provisional |= !result.closed();
                }
                return new Result(tables.table(rows, "month", "연월", "partName", "영업 파트", "partProfitAmount", "파트 이익금"),
                    query.period(), provisional, "UI-19", List.of());
            }
            case "F-03", "P-02": {
                var result = projects.getCommonProjectCostAnalyses(tables.filter(Map.of("year", end.getYear(),
                    "baseMonth", end.getMonthValue()), ProjectCostAnalysisContracts.Filter.class));
                var options = result.projectOptions().stream().filter(p -> query.question().contains(p.projectId())
                    || query.question().contains(p.projectName())).toList();
                if (options.size() != 1) return null;
                var selected = result.items().stream().filter(p -> p.projectId().equals(options.get(0).projectId())).toList();
                if ("P-02".equals(query.function())) selected = selected.stream().filter(p -> "TK".equals(p.contractTypeCode())).toList();
                var table = "P-02".equals(query.function())
                    ? tables.table(selected, "projectName", "프로젝트", "expectedFinalMarginRate", "최종 예상 마진율")
                    : tables.table(selected, "projectName", "프로젝트", "cumulativeRevenue", "누적 매출", "totalCost", "누적 원가", "cumulativeProfit", "누적 이익");
                return new Result(table, new Period(end.withMonth(1).toString(), end.toString()),
                    result.monthlyMarginNotice() != null, "UI-04", List.of("원래 프로젝트 원가 분석의 기준월 누적 계산입니다."));
            }
            case "F-05": {
                for (int year = first.getYear(); year <= end.getYear(); year++) {
                    var through = year == end.getYear() ? end : YearMonth.of(year, 12);
                    var result = plans.getCommonBusinessPlanComparisons(new BusinessPlanComparisonContracts.Filter(year,
                        through.toString().replace("-", ""))).data();
                    if (!result.hasTargets()) return null;
                    for (var row : result.months()) {
                        var month = YearMonth.parse(row.yearMonth().substring(0, 4) + "-" + row.yearMonth().substring(4));
                        if (!month.isBefore(first) && !month.isAfter(end)) rows.add(row);
                    }
                    provisional |= !result.baseMonthClosed();
                }
                return new Result(tables.table(rows, "yearMonth", "연월", "revenueTarget", "매출 계획", "revenueActual", "매출 실적",
                    "operatingProfitTarget", "영업이익 계획", "operatingProfitActual", "영업이익 실적", "achievementRate", "달성률"),
                    query.period(), provisional, "UI-18", List.of());
            }
            default: return null;
        }
    }
}
