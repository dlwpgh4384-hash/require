package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.aianalysis.mapper.AiAnalysisMapper;
import com.company.common.support.service.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAnalysisArchive {
    static final String TITLE = "AI 분석 도우미 답";
    private final AiAnalysisMapper mapper;
    private final CurrentBusinessActor actor;
    private final AiAnalysisProjection projection;
    private final ObjectMapper json;
    private final ExcelExportService excel;
    private final BusinessCalendar calendar;
    public AiAnalysisArchive(AiAnalysisMapper mapper, CurrentBusinessActor actor, AiAnalysisProjection projection,
        ObjectMapper json, ExcelExportService excel, BusinessCalendar calendar) {
        this.mapper = mapper; this.actor = actor; this.projection = projection;
        this.json = json; this.excel = excel; this.calendar = calendar;
    }
    public String actorId() { return actor.userId(); }
    /** 본인 기록만, 같은 질문 글은 가장 최근 하나, 최근 20개(SQL이 거름). */
    @Transactional(readOnly = true)
    public History history() { return new History(mapper.history(actor.userId())); }
    /** 저장된 답의 표 그대로(다시 계산하지 않음). 표마다 시트 하나, 숫자는 숫자 칸. */
    @Transactional
    public ExcelFile excel(String id) {
        if (id == null || !id.matches("[0-9]{1,12}")) throw missing();
        String saved = mapper.saved(actor.userId(), id);
        if (saved == null) throw missing();
        try {
            var answer = projection.apply(json.readValue(saved, Answer.class));
            var tables = answer.tables() == null ? List.<Table>of() : answer.tables().stream().filter(t -> !t.columns().isEmpty()).toList();
            if (!"OK".equals(answer.answerStatus()) || tables.isEmpty()) throw missing();
            var sheets = new ArrayList<ExcelExportService.SheetData>();
            int count = 0;
            for (var table : tables) {
                var rows = new ArrayList<List<String>>();
                for (var row : table.rows()) rows.add(text(row));
                count += table.rows().size();
                if (table.totalRow() != null) rows.add(text(table.totalRow()));
                String name = table.title() == null ? TITLE : table.title();
                sheets.add(new ExcelExportService.SheetData(name, TITLE + (table.title() == null ? "" : " · " + table.title()),
                    table.columns().stream().map(Column::label).toList(), rows));
            }
            var at = calendar.now();
            var bytes = excel.write(sheets, actor.userId(), at);
            if (mapper.download(actor.userId(), count) != 1) throw new IllegalStateException("Download audit was not inserted");
            return new ExcelFile(bytes, excel.fileName(TITLE, answer.baseMonth(), at));
        } catch (JsonProcessingException invalid) { throw missing(); }
    }
    private static List<String> text(List<Object> row) {
        return row.stream().map(v -> v == null ? "" : v instanceof BigDecimal n ? n.toPlainString()
            : v instanceof Number n ? new BigDecimal(n.toString()).toPlainString() : v.toString()).toList();
    }
    private CodeFailure missing() { return new CodeFailure(404, "questionId", "답을 찾을 수 없습니다."); }
}
