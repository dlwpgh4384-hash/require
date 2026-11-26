package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.salespipeline.service.SalesPipelineForecastService;
import com.company.common.salespipeline.service.CommonSalesPipelineService;
import com.company.common.support.service.BusinessCalendar;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.company.common.aianalysis.service.AiAnalysisInterpreter.has;
import static com.company.common.aianalysis.service.AiAnalysisTables.*;

/** F-12 영업 기회(단계별 합), P-01 6개월 매출 예측(영업 파이프라인 예측 탭과 같음). */
@Component
public class AiAnalysisPipelineFunctions implements AiAnalysisFunction {
    private final SalesPipelineForecastService forecast;
    private final CommonSalesPipelineService pipeline;
    private final BusinessCalendar calendar;
    private final AiAnalysisTables tables;
    private final com.company.common.parts.service.CommonPartService parts;
    public AiAnalysisPipelineFunctions(SalesPipelineForecastService forecast, CommonSalesPipelineService pipeline,
        BusinessCalendar calendar, AiAnalysisTables tables, com.company.common.parts.service.CommonPartService parts) {
        this.forecast = forecast; this.pipeline = pipeline; this.calendar = calendar; this.tables = tables; this.parts = parts;
    }
    public Set<String> functions() { return Set.of("F-12", "P-01"); }
    public Result read(AiAnalysisInterpreter.Query query) {
        return "P-01".equals(query.function()) ? revenue(query) : opportunities(query);
    }
    private Result revenue(AiAnalysisInterpreter.Query query) {
        var first = calendar.currentMonth().plusMonths(1);
        var result = forecast.getCommonSalesPipelineForecast();
        int count = 6;
        if (query.periodGiven()) count = (int) Math.max(1, Math.min(6, java.time.temporal.ChronoUnit.MONTHS.between(
            YearMonth.parse(query.period().startMonth()), YearMonth.parse(query.period().endMonth())) + 1));
        var months = result.months().stream().limit(count).toList();
        var rows = months.stream().map(r -> Arrays.<Object>asList(r.month(), r.projectRevenue(),
            r.projectRevenue().add(r.weightedOpportunityRevenue()), r.allWonRevenue())).toList();
        BigDecimal low = BigDecimal.ZERO, basic = BigDecimal.ZERO, high = BigDecimal.ZERO;
        for (var m : months) {
            low = low.add(m.projectRevenue()); basic = basic.add(m.projectRevenue().add(m.weightedOpportunityRevenue()));
            high = high.add(m.allWonRevenue());
        }
        var table = AiAnalysisTables.table(null, List.of(Column.month("month", "연월"), Column.money("low", "낮음"),
            Column.money("basic", "기본"), Column.money("high", "높음")), rows, null,
            Arrays.<Object>asList("합계", low, basic, high));
        var end = first.plusMonths(months.size() - 1);
        var period = span(first, end);
        String conclusion = "앞으로 " + months.size() + "개월(" + period(period) + ") 매출은 " + bold(money(basic))
            + "으로 예상됩니다(기본). 낮음 " + money(low) + " · 높음 " + money(high) + ".";
        var assumptions = new ArrayList<String>();
        assumptions.add("낮음은 영업 기회를 하나도 못 따면(진행 프로젝트 몫만), 기본은 수주 확률만큼, 높음은 영업 기회를 모두 따면입니다.");
        assumptions.add("턴키 프로젝트 몫은 남은 계약금액 ÷ 남은 개월로 나눕니다.");
        result.opportunityRevenues().forEach(r -> assumptions.add(r.opportunityName() + ": 수주 확률 "
            + r.winningProbability().stripTrailingZeros().toPlainString() + "%"));
        long asked = query.periodGiven() ? java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.parse(query.period().startMonth()),
            YearMonth.parse(query.period().endMonth())) + 1 : 0;
        if (asked > months.size()) assumptions.add(trimmed("매출", asked, months.size(), "다음 달부터 최대 6개월"));
        if (has(query.question(), "영업이익")) assumptions.add(AiAnalysisInterpreter.NO_OPERATING_FORECAST);
        // 예측 답의 기준월(BASE_YM)은 이번 달.
        return new Result(conclusion, List.of(table), null, assumptions, used(tables.screen("UI-29") + " 예측", period),
            calendar.currentMonth().toString(), false, true, List.of(tables.open("UI-29", period)));
    }
    /** 물어본 기간이 예측할 수 있는 길이보다 길어 줄였을 때 가정 줄. */
    static String trimmed(String what, long asked, int answered, String limit) {
        return what + " 예측은 " + limit + "까지라 물어본 " + asked + "개월 대신 " + answered + "개월만 답합니다.";
    }
    /** 진행 중 영업 기회: 영업 파이프라인 화면의 단계별 건수·금액 그대로(기회마다 따로 읽지 않음). */
    private Result opportunities(AiAnalysisInterpreter.Query query) {
        // 영업 파이프라인 화면은 파트로 나누지 않으므로 파트를 물으면 답하지 않는다.
        var named = AiAnalysisTables.pick(query.question(), parts.getCommonParts().items(),
            p -> p.partName(), p -> p.partId(), "영업 파트");
        if (named != null) throw new NoAnswer("영업 기회는 영업 파트별로 나누어 답하지 못합니다. 전체로 물어보세요.", null,
            List.of("진행 중 영업 기회 금액은?"));
        var result = pipeline.getCommonSalesPipeline();
        var stages = result.stages().stream().filter(s -> query.question().contains(s.stageName())).toList();
        if (stages.isEmpty()) stages = result.stages();
        var rows = stages.stream().map(s -> Arrays.<Object>asList(s.stageName(), s.count(), s.amount(), s.defaultProbability())).toList();
        int count = stages.stream().mapToInt(s -> s.count()).sum();
        BigDecimal amount = stages.stream().map(s -> s.amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        var table = AiAnalysisTables.table(null, List.of(Column.text("stageName", "단계"), Column.count("count", "건수"),
            Column.money("amount", "예상 금액"), Column.rate("defaultProbability", "단계 확률")), rows, null,
            stages.size() > 1 ? Arrays.<Object>asList("합계", count, amount, null) : null);
        String target = stages.size() == 1 ? stages.get(0).stageName() + " 단계 " : "진행 중 ";
        String conclusion = target + "영업 기회는 " + bold(count(count)) + ", 예상 금액 " + bold(money(amount)) + "입니다."
            + (stages.size() > 1 ? " 확률 반영 금액은 " + money(result.summary().weightedAmount()) + "입니다." : "");
        var month = calendar.currentMonth();
        return new Result(conclusion, List.of(table), null, List.of("영업 파이프라인 화면의 진행 중 기회와 같은 숫자입니다.",
            "확률 반영 금액은 기회마다 예상 금액 × 수주 확률을 더한 값입니다."), used(tables.screen("UI-29"), null), month.toString(),
            false, false, List.of(tables.open("UI-29", single(month))));
    }
}
