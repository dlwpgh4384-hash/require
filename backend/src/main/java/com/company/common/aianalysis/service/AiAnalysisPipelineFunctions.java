package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.salespipeline.service.SalesPipelineForecastService;
import com.company.common.salespipeline.service.CommonSalesPipelineService;
import com.company.common.support.service.BusinessCalendar;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisPipelineFunctions implements AiAnalysisFunction {
    private final SalesPipelineForecastService forecast;
    private final CommonSalesPipelineService pipeline;
    private final BusinessCalendar calendar;
    private final AiAnalysisTables tables;
    private final com.company.common.parts.service.CommonPartService parts;
    private final com.company.common.salesopportunities.service.CommonSalesOpportunityService opportunities;
    public AiAnalysisPipelineFunctions(SalesPipelineForecastService forecast, CommonSalesPipelineService pipeline,
        BusinessCalendar calendar, AiAnalysisTables tables, com.company.common.parts.service.CommonPartService parts,
        com.company.common.salesopportunities.service.CommonSalesOpportunityService opportunities) {
        this.forecast = forecast; this.pipeline = pipeline; this.calendar = calendar; this.tables = tables;
        this.parts = parts; this.opportunities = opportunities;
    }
    public Set<String> functions() { return Set.of("F-12", "P-01"); }
    public Result read(AiAnalysisInterpreter.Query query) {
        if ("P-01".equals(query.function())) {
            var first = calendar.currentMonth().plusMonths(1);
            var result = forecast.getCommonSalesPipelineForecast();
            var rows = result.months().stream().map(r -> Map.of("month", r.month(), "low", r.projectRevenue(),
                "basic", r.projectRevenue().add(r.weightedOpportunityRevenue()), "high", r.allWonRevenue())).toList();
            var assumptions = new ArrayList<String>();
            assumptions.add("낮음=프로젝트 몫, 기본=프로젝트 몫+확률 반영 기회 매출, 높음=모든 기회 수주입니다.");
            result.opportunityRevenues().forEach(r -> assumptions.add(r.opportunityName() + ": 수주 확률 " + r.winningProbability() + "%"));
            return new Result(tables.table(rows, "month", "연월", "low", "낮음", "basic", "기본", "high", "높음"),
                new Period(first.toString(), first.plusMonths(5).toString()), true, "UI-29", List.copyOf(assumptions));
        }
        var result = pipeline.getCommonSalesPipeline();
        var named = result.stages().stream().filter(s -> query.question().contains(s.stageName())
            || query.question().contains(s.stageCode())).toList();
        if (named.size() > 1) return null;
        var stages = named.isEmpty() ? result.stages() : named;
        var rows = stages.stream().flatMap(s -> s.items().stream()).toList();
        var selectedParts = parts.getCommonParts().items().stream().filter(p -> query.question().contains(p.partId())
            || query.question().contains(p.partName())).toList();
        if (selectedParts.size() > 1 || query.question().contains("파트") && selectedParts.size() != 1) return null;
        if (selectedParts.size() == 1) rows = rows.stream().filter(r -> selectedParts.get(0).partId()
            .equals(opportunities.getCommonSalesOpportunity(r.opportunityId()).salesPartId())).toList();
        return new Result(tables.table(rows, "opportunityName", "영업 기회", "stageCode", "단계", "winningProbability", "수주 확률",
            "expectedAmount", "예상 금액", "nextActionDueDate", "다음 행동일"), query.period(), true, "UI-29",
            stages.stream().map(s -> s.stageName() + ": 기본 확률 " + s.defaultProbability() + "%").toList());
    }
}
