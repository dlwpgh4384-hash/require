package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.partsettlements.service.CommonPartSettlementService;
import com.company.common.partsettlements.dto.PartSettlementContracts.Tab;
import com.company.common.projectcostanalyses.service.CommonProjectCostAnalysisService;
import com.company.common.projectcostanalyses.dto.ProjectCostAnalysisContracts;
import com.company.common.projectcostanalyses.dto.ProjectCostAnalysisContracts.CommonProjectCostAnalysisRow;
import com.company.common.commonops.service.CommonBusinessPlanComparisonService;
import com.company.common.commonops.dto.BusinessPlanComparisonContracts;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.company.common.aianalysis.service.AiAnalysisInterpreter.has;
import static com.company.common.aianalysis.service.AiAnalysisTables.*;

/** F-02 파트 이익금, F-03 프로젝트 손익, F-05 사업계획 달성률, P-02 턴키 최종 마진율. */
@Component
public class AiAnalysisFinancialFunctions implements AiAnalysisFunction {
    private final CommonPartSettlementService parts;
    private final CommonProjectCostAnalysisService projects;
    private final CommonBusinessPlanComparisonService plans;
    private final UserPreferencePeriodBasis periods;
    private final AiAnalysisTables tables;
    public AiAnalysisFinancialFunctions(CommonPartSettlementService parts, CommonProjectCostAnalysisService projects,
        CommonBusinessPlanComparisonService plans, UserPreferencePeriodBasis periods, AiAnalysisTables tables) {
        this.parts = parts; this.projects = projects; this.plans = plans; this.periods = periods; this.tables = tables;
    }
    public Set<String> functions() { return Set.of("F-02", "F-03", "F-05", "P-02"); }
    public Result read(AiAnalysisInterpreter.Query query) {
        return switch (query.function()) {
            case "F-02" -> part(query);
            case "F-03", "P-02" -> project(query);
            case "F-05" -> plan(query);
            default -> throw new NoAnswer(null);
        };
    }

    private Result part(AiAnalysisInterpreter.Query query) {
        var first = YearMonth.parse(query.period().startMonth());
        var end = YearMonth.parse(query.period().endMonth());
        Tab selected = null;
        var rows = new ArrayList<List<Object>>();
        boolean provisional = false;
        BigDecimal total = BigDecimal.ZERO;
        for (var month = first; !month.isAfter(end); month = month.plusMonths(1)) {
            var tabs = parts.getCommonPartSettlements(month.toString()).data().items();
            Tab tab = pick(query.question(), tabs, Tab::partName, Tab::partId, "영업 파트");
            if (tab == null) throw new NoAnswer("어느 영업 파트인지 알려 주세요.", null,
                tabs.stream().limit(3).map(t -> t.partName() + " " + end.getMonthValue() + "월 이익금은?").toList());
            if (selected != null && !selected.partId().equals(tab.partId())) throw new NoAnswer(null);
            selected = tab;
            var detail = parts.getCommonPartSettlement(tab.partId(), month.toString());
            provisional |= !detail.closed();
            var amount = detail.summary().partProfitAmount();
            total = total == null || amount == null ? null : total.add(amount);
            rows.add(Arrays.<Object>asList(month.toString(), detail.summary().personCount(), amount));
        }
        boolean several = rows.size() > 1;
        var table = AiAnalysisTables.table(null, List.of(Column.month("month", "연월"), Column.count("people", "인원"),
            Column.money("partProfitAmount", "파트 이익금")), rows, null, several ? Arrays.<Object>asList("합계", null, total) : null);
        String label = several ? period(query.period()) + " " + selected.partName() + " 이익금 합계는 "
            : end + " " + selected.partName() + " 이익금은 ";
        return new Result(label + bold(money(total)) + "입니다.", List.of(table), null,
            List.of("영업 파트 이익금 화면과 같은 계산입니다.", "마감 전 달은 잠정 값입니다."),
            used(tables.screen("UI-19") + " · " + selected.partName(), query.period()), end.toString(), provisional, false,
            List.of(tables.open("UI-19", query.period())));
    }

    private Result project(AiAnalysisInterpreter.Query query) {
        boolean forecast = "P-02".equals(query.function());
        if (!(periods.metadata().get("periodBasis") instanceof UserPreferencePeriodBasis.Basis basis)) throw new NoAnswer(null);
        var end = YearMonth.parse(query.period().endMonth());
        var result = projects.getCommonProjectCostAnalyses(tables.filter(Map.of("year", end.getYear(),
            "baseMonth", end.getMonthValue()), ProjectCostAnalysisContracts.Filter.class));
        // 잠정 판단은 프로젝트 원가 분석 화면과 같다(CASH_BASE_YM 전 달이나 마감한 달은 확정).
        boolean provisional = !CommonProjectCostAnalysisService.closed(key(end), basis);
        var period = new Period(YearMonth.of(end.getYear(), 1).toString(), end.toString());
        var option = pick(query.question(), result.projectOptions(), ProjectCostAnalysisContracts.ProjectOption::projectName,
            ProjectCostAnalysisContracts.ProjectOption::projectId, "프로젝트");
        var action = List.of(tables.open("UI-04", single(end)));
        var assumption = "프로젝트 원가 분석 화면의 " + end + " 기준 누적 계산과 같습니다.";
        if (option == null) {
            if (forecast) throw new NoAnswer("어느 프로젝트인지 알려 주세요.", null, result.items().stream()
                .filter(r -> "TK".equals(r.contractTypeCode())).limit(3).map(r -> r.projectName() + " 최종 마진율은?").toList());
            boolean loss = has(query.question(), "적자");
            var items = result.items().stream().filter(r -> !"IN".equals(r.contractTypeCode()))
                .filter(r -> !loss || r.cumulativeProfit() != null && r.cumulativeProfit().signum() < 0).toList();
            var table = listTable(items);
            BigDecimal total = items.stream().map(CommonProjectCostAnalysisRow::cumulativeProfit).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            String conclusion = loss ? end + " 적자 프로젝트는 " + bold(items.size() + "개") + (items.isEmpty() ? "입니다."
                : ", 누적 이익 합계 " + bold(money(total)) + "입니다.")
                : end + " 프로젝트 " + bold(items.size() + "개") + "의 누적 이익 합계는 " + bold(money(total)) + "입니다.";
            return new Result(conclusion, List.of(table), null, List.of(assumption, "내부 프로젝트는 뺐습니다."),
                used(tables.screen("UI-04"), period), end.toString(), provisional, false, action);
        }
        var row = result.items().stream().filter(r -> r.projectId().equals(option.projectId())).findFirst()
            .orElseThrow(() -> new NoAnswer(null));
        var used = used(tables.screen("UI-04") + " · " + row.projectName(), period);
        if (forecast) {
            if (!"TK".equals(row.contractTypeCode())) {
                return new Result("턴키 프로젝트만 최종 마진율을 예측합니다. " + row.projectName() + "의 " + end + " 월간 마진율은 "
                    + bold(rate(row.monthlyMarginRate())) + "입니다.", List.of(AiAnalysisTables.table(null,
                        List.of(Column.text("projectName", "프로젝트"), Column.text("contractTypeName", "계약 유형"),
                            Column.rate("monthlyMarginRate", "월간 마진율")),
                        List.of(Arrays.<Object>asList(row.projectName(), row.contractTypeName(), row.monthlyMarginRate())), null, null)),
                    null, List.of(assumption), used, end.toString(), provisional, true, action);
            }
            String target = row.targetDifference() == null ? "" : "(목표 대비 " + (row.targetDifference().signum() >= 0 ? "+" : "−")
                + row.targetDifference().abs().setScale(1, java.math.RoundingMode.HALF_UP).toPlainString() + "%p)";
            return new Result(row.projectName() + " 예상 최종 마진율은 " + bold(rate(row.expectedFinalMarginRate())) + target + "입니다.",
                List.of(AiAnalysisTables.table(null, List.of(Column.text("projectName", "프로젝트"),
                    Column.rate("cumulativeMarginRate", "누적 마진율"), Column.rate("expectedFinalMarginRate", "예상 최종 마진율"),
                    Column.rate("targetDifference", "목표 대비"), Column.money("remainingCost", "남은 예상 원가")),
                    List.of(Arrays.<Object>asList(row.projectName(), row.cumulativeMarginRate(), row.expectedFinalMarginRate(),
                        row.targetDifference(), row.remainingCost())), null, null)),
                null, List.of(assumption, "남은 기간 예상 원가는 지금까지의 월 평균 원가로 봅니다."), used, end.toString(), provisional, true, action);
        }
        var monthly = projects.getCommonProjectCostAnalysisMonthlyCosts(row.projectId(),
            new ProjectCostAnalysisContracts.Period(end.getYear(), end.getMonthValue())).items();
        var monthRows = new ArrayList<List<Object>>();
        var labels = new ArrayList<String>();
        var rates = new ArrayList<BigDecimal>();
        for (var m : monthly) {
            String ym = month(m.yearMonth()).toString();
            monthRows.add(Arrays.<Object>asList(ym, m.mm(), m.revenue(), m.cost(), m.marginRate()));
            labels.add(month(m.yearMonth()).getMonthValue() + "월");
            rates.add(m.marginRate());
        }
        var summary = listTable(List.of(row));
        var trend = AiAnalysisTables.table("월별", List.of(Column.month("month", "연월"), Column.mm("mm", "MM"),
            Column.money("revenue", "매출"), Column.money("cost", "원가"), Column.rate("marginRate", "마진율")), monthRows, null, null);
        Chart chart = has(query.question(), "추이") && !monthly.isEmpty()
            ? new Chart("line", labels, List.of(new Series("마진율", rates)), "rate") : null;
        return new Result(row.projectName() + " " + end + " 누적 마진율은 " + bold(rate(row.cumulativeMarginRate()))
            + ", 누적 이익은 " + bold(money(row.cumulativeProfit())) + "입니다.", List.of(summary, trend), chart,
            List.of(assumption), used, end.toString(), provisional, false, action);
    }
    private static Table listTable(List<CommonProjectCostAnalysisRow> items) {
        var rows = items.stream().map(r -> Arrays.<Object>asList(r.projectName(), r.contractTypeName(), r.cumulativeRevenue(),
            r.totalCost(), r.cumulativeProfit(), r.cumulativeMarginRate())).toList();
        return AiAnalysisTables.table(null, List.of(Column.text("projectName", "프로젝트"), Column.text("contractTypeName", "계약 유형"),
            Column.money("cumulativeRevenue", "누적 매출"), Column.money("totalCost", "누적 원가"),
            Column.money("cumulativeProfit", "누적 이익"), Column.rate("cumulativeMarginRate", "누적 마진율")), rows, null, null);
    }

    private Result plan(AiAnalysisInterpreter.Query query) {
        var end = YearMonth.parse(query.period().endMonth());
        var start = YearMonth.parse(query.period().startMonth());
        if (start.getYear() != end.getYear()) start = YearMonth.of(end.getYear(), 1);
        var result = plans.getCommonBusinessPlanComparisons(new BusinessPlanComparisonContracts.Filter(end.getYear(), key(end))).data();
        if (!result.hasTargets()) throw new NoAnswer(end.getYear() + "년 사업계획 목표가 없습니다. 사업계획 대비 실적 화면에서 목표를 먼저 넣어 주세요.");
        var rows = new ArrayList<List<Object>>();
        for (var row : result.months()) {
            var month = month(row.yearMonth());
            if (month.isBefore(start) || month.isAfter(end)) continue;
            rows.add(Arrays.<Object>asList(month.toString(), row.revenueTarget(), row.revenueActual(), row.operatingProfitTarget(),
                row.operatingProfitActual(), row.achievementRate()));
        }
        var table = AiAnalysisTables.table(null, List.of(Column.month("month", "연월"), Column.money("revenueTarget", "매출 계획"),
            Column.money("revenueActual", "매출 실적"), Column.money("operatingProfitTarget", "영업이익 계획"),
            Column.money("operatingProfitActual", "영업이익 실적"), Column.rate("achievementRate", "달성률")), rows, null, null);
        var s = result.summary();
        boolean profit = has(query.question(), "영업이익");
        boolean single = start.equals(end) && !has(query.question(), "올해", "누계", "누적");
        String conclusion;
        if (single && !profit) conclusion = end + " 매출 달성률은 " + bold(rate(s.baseMonthRevenueAchievementRate())) + "입니다.";
        else if (profit) conclusion = end.getYear() + "년 1~" + end.getMonthValue() + "월 영업이익 달성률은 "
            + bold(rate(s.cumulativeOperatingProfitAchievementRate())) + "(실적 " + money(s.cumulativeOperatingProfitActual())
            + " / 계획 " + money(s.cumulativeOperatingProfitTarget()) + ")입니다.";
        else conclusion = end.getYear() + "년 1~" + end.getMonthValue() + "월 매출 달성률은 "
            + bold(rate(s.cumulativeRevenueAchievementRate())) + "(실적 " + money(s.cumulativeRevenueActual())
            + " / 계획 " + money(s.cumulativeRevenueTarget()) + ")입니다.";
        var period = new Period(start.toString(), end.toString());
        return new Result(conclusion, List.of(table), null, List.of("달성률은 사업계획 대비 실적 화면과 같이 실적 ÷ 계획입니다.",
            "마감 전 달은 잠정 값입니다."), used(tables.screen("UI-18"), period), end.toString(), !result.baseMonthClosed(), false,
            List.of(tables.open("UI-18", period)));
    }
}
