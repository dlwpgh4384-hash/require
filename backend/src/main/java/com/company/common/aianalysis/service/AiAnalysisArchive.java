package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.aianalysis.mapper.AiAnalysisMapper;
import com.company.common.support.service.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAnalysisArchive {
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
    @Transactional(readOnly = true)
    public History history() { return new History(mapper.history(actor.userId())); }
    @Transactional
    public ExcelFile excel(String id) {
        if (id == null || !id.matches("[0-9]{1,12}")) throw missing();
        String saved = mapper.saved(actor.userId(), id);
        if (saved == null) throw missing();
        try {
            var answer = projection.apply(json.readValue(saved, Answer.class));
            if (!"OK".equals(answer.answerStatus()) || answer.table() == null || answer.table().columns().isEmpty()) throw missing();
            var table = answer.table();
            var rows = table.rows().stream().map(row -> row.stream().map(v -> v == null ? "" : v.toString()).toList()).toList();
            var at = calendar.now();
            var bytes = excel.write("AI 분석 답", table.columns().stream().map(Column::label).toList(), rows, actor.userId(), at);
            if (mapper.download(actor.userId(), id) != 1) throw new IllegalStateException("Download audit was not inserted");
            return new ExcelFile(bytes, excel.fileName("AI분석", answer.baseMonth(), at));
        } catch (JsonProcessingException invalid) { throw missing(); }
    }
    private CodeFailure missing() { return new CodeFailure(404, "questionId", "답을 찾을 수 없습니다."); }
}
