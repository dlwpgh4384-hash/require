package com.company.common.aianalysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public final class AiAnalysisContracts {
    private AiAnalysisContracts() {}
    public record Period(String startMonth, String endMonth) {}
    public record Input(String requestId, String question, String screenId, Period period) {}
    public record Column(String key, String label) {}
    public record Table(List<Column> columns, List<List<Object>> rows) {}
    public record Action(String label, String targetScreenId, Period period) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Answer(String questionId, String answerStatus, String conclusion, Table table,
        List<String> assumptions, String usedData, String baseMonth, boolean provisional,
        List<Action> actions, List<String> suggestedQuestions) {
        public Answer identified(String id) {
            return new Answer(id, answerStatus, conclusion, table, assumptions, usedData, baseMonth,
                provisional, actions, suggestedQuestions);
        }
    }
    public record HistoryItem(String questionId, String question, Instant askedAt, String answerStatus) {}
    public record History(List<HistoryItem> items) {}
    public record Progress(String requestId, String stage, boolean completed) {}
    public record ExcelFile(byte[] bytes, String fileName) {}
}
