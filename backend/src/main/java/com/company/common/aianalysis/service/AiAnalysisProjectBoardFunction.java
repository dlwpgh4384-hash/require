package com.company.common.aianalysis.service;

import com.company.common.projectboard.service.CommonProjectBoardService;
import com.company.common.projectboard.dto.CommonProjectBoardContracts.Filter;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisProjectBoardFunction implements AiAnalysisFunction {
    private final CommonProjectBoardService board;
    private final AiAnalysisTables tables;
    public AiAnalysisProjectBoardFunction(CommonProjectBoardService board, AiAnalysisTables tables) {
        this.board = board; this.tables = tables;
    }
    public Set<String> functions() { return Set.of("F-11"); }
    public Result read(AiAnalysisInterpreter.Query query) {
        var filter = new LinkedHashMap<String, Object>();
        if (query.question().contains("위험")) filter.put("signal", "위험");
        else if (query.question().contains("주의")) filter.put("signal", "주의");
        var result = board.preview(tables.filter(filter, Filter.class), true);
        var rows = result.items();
        var named = rows.stream().filter(r -> query.question().contains(r.projectId()) || query.question().contains(r.projectName())).toList();
        if (named.size() > 1) return null;
        if (named.size() == 1) rows = named;
        return new Result(tables.table(rows, "projectName", "프로젝트", "signal", "상태", "reasonSummary", "판정 근거",
            "actualProgress", "실제 진척률", "costUsageRate", "원가 사용률"), query.period(), true, "UI-31", List.of());
    }
}
