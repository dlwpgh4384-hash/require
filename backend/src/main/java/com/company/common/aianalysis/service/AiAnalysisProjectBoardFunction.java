package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.projectboard.service.CommonProjectBoardService;
import com.company.common.projectboard.dto.CommonProjectBoardContracts.Filter;
import com.company.common.projectboard.dto.CommonProjectBoardContracts.Row;
import com.company.common.support.service.BusinessCalendar;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.company.common.aianalysis.service.AiAnalysisTables.*;

/** F-11 프로젝트 상태: 현황판 신호등(읽기 전용 preview, 빠진 단계를 만들지 않음). */
@Component
public class AiAnalysisProjectBoardFunction implements AiAnalysisFunction {
    private final CommonProjectBoardService board;
    private final BusinessCalendar calendar;
    private final AiAnalysisTables tables;
    public AiAnalysisProjectBoardFunction(CommonProjectBoardService board, BusinessCalendar calendar, AiAnalysisTables tables) {
        this.board = board; this.calendar = calendar; this.tables = tables;
    }
    public Set<String> functions() { return Set.of("F-11"); }
    public Result read(AiAnalysisInterpreter.Query query) {
        var filter = new LinkedHashMap<String, Object>();
        String signal = query.question().contains("위험") ? "위험" : query.question().contains("주의") ? "주의" : null;
        if (signal != null) filter.put("signal", signal);
        var result = board.preview(tables.filter(filter, Filter.class), true);
        var rows = result.items();
        Row named = pick(query.question(), rows, Row::projectName, Row::projectId, "프로젝트");
        if (named != null) rows = List.of(named);
        var table = AiAnalysisTables.table(null, List.of(Column.text("projectName", "프로젝트"), Column.text("signal", "상태"),
            Column.text("reasonSummary", "판정 근거"), Column.rate("plannedProgress", "계획 진척률"),
            Column.rate("actualProgress", "실제 진척률"), Column.rate("costUsageRate", "원가 사용률")),
            rows.stream().map(r -> Arrays.<Object>asList(r.projectName(), r.signal(), r.reasonSummary(), r.plannedProgress(),
                r.actualProgress(), r.costUsageRate())).toList(),
            rows.stream().map(r -> Arrays.asList(null, "위험".equals(r.signal()) ? "bad" : "주의".equals(r.signal()) ? "warn" : null,
                null, null, null, null)).toList(), null);
        String conclusion = named != null
            ? named.projectName() + "의 상태는 " + bold(String.valueOf(named.signal())) + "입니다." + (named.reasonSummary() == null ? "" : " " + named.reasonSummary())
            : (signal == null ? "진행 프로젝트는 " : signal + " 프로젝트는 ") + bold(rows.size() + "개") + "입니다.";
        var month = calendar.currentMonth();
        return new Result(conclusion, List.of(table), null, List.of("프로젝트 현황판의 신호등 판정(계획·실적 진척률, 원가 사용률)과 같습니다."),
            used(tables.screen("UI-31"), null), month.toString(), false, false, List.of(tables.open("UI-31", single(month))));
    }
}
