package com.company.common.aianalysis.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AiAnalysisContracts {
    private AiAnalysisContracts() {}
    public record Period(String startMonth, String endMonth) {}
    public record Input(String requestId, String question, String screenId, Period period) {}
    /**
     * 표 칸. align: left·right, format: text·money·rate·mm·count·month·date.
     * sensitive: 민감 칸(급여·지급 단가·개인별 원가). 볼 수 없는 사람에게서는 AiAnalysisProjection이 뺀다.
     */
    public record Column(String key, String label, String align, String format,
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) boolean sensitive) {
        public static Column text(String key, String label) { return new Column(key, label, "left", "text", false); }
        public static Column right(String key, String label) { return new Column(key, label, "right", "text", false); }
        public static Column money(String key, String label) { return new Column(key, label, "right", "money", false); }
        public static Column rate(String key, String label) { return new Column(key, label, "right", "rate", false); }
        public static Column mm(String key, String label) { return new Column(key, label, "right", "mm", false); }
        public static Column count(String key, String label) { return new Column(key, label, "right", "count", false); }
        public static Column month(String key, String label) { return new Column(key, label, "left", "month", false); }
        public static Column date(String key, String label) { return new Column(key, label, "left", "date", false); }
        public Column hidden() { return new Column(key, label, align, format, true); }
    }
    /**
     * tones는 rows와 같은 모양(null·bad·warn), totalRow는 맨 아래 합계 줄.
     * hidden=true면 화면에는 그리지 않고 엑셀에만 쓴다(시안 매출 답: 결론+그래프만).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Table(String title, List<Column> columns, List<List<Object>> rows,
        List<List<String>> tones, List<Object> totalRow, Boolean hidden) {
        @JsonCreator public Table {}
        public Table(String title, List<Column> columns, List<List<Object>> rows, List<List<String>> tones, List<Object> totalRow) {
            this(title, columns, rows, tones, totalRow, null);
        }
        public Table hide() { return new Table(title, columns, rows, tones, totalRow, Boolean.TRUE); }
    }
    public record Series(String name, List<BigDecimal> values) {}
    /** format: 값 모양 money(원, 눈금 백만 원)·rate(%, 눈금 %). 없으면 money. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Chart(String type, List<String> labels, List<Series> series, String format) {
        @JsonCreator public Chart {}
        public Chart(String type, List<String> labels, List<Series> series) { this(type, labels, series, null); }
    }
    public record Action(String label, String targetScreenId, Period period) {}
    /**
     * provisionalMonth: 답 안의 가장 늦은 잠정 달(yyyy-MM), 잠정이 없으면 null.
     * suggestionLabels: 추천 질문 단추 글(suggestedQuestions와 같은 순서). 없으면 질문 글을 그대로 단추에 쓴다
     * (예: 프로젝트 후보는 단추에 이름만, 누르면 「이름 + 나머지 질문」을 묻는다).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Answer(String questionId, String answerStatus, String conclusion, boolean forecast,
        boolean provisional, String provisionalMonth, List<Table> tables, Chart chart, List<String> assumptions, String usedData,
        String baseMonth, List<Action> actions, boolean excel, List<String> suggestedQuestions, List<String> suggestionLabels) {
        @JsonCreator public Answer {}
        public Answer(String questionId, String answerStatus, String conclusion, boolean forecast,
            boolean provisional, String provisionalMonth, List<Table> tables, Chart chart, List<String> assumptions, String usedData,
            String baseMonth, List<Action> actions, boolean excel, List<String> suggestedQuestions) {
            this(questionId, answerStatus, conclusion, forecast, provisional, provisionalMonth, tables, chart, assumptions, usedData,
                baseMonth, actions, excel, suggestedQuestions, null);
        }
        public Answer identified(String id) {
            return new Answer(id, answerStatus, conclusion, forecast, provisional, provisionalMonth, tables, chart, assumptions,
                usedData, baseMonth, actions, excel, suggestedQuestions, suggestionLabels);
        }
        public Answer projected(List<Table> value, List<String> notes) {
            return new Answer(questionId, answerStatus, conclusion, forecast, provisional, provisionalMonth, value, chart, notes,
                usedData, baseMonth, actions, excel && value.stream().anyMatch(t -> !t.columns().isEmpty()),
                suggestedQuestions, suggestionLabels);
        }
    }
    public record HistoryItem(String questionId, String question, Instant askedAt, String answerStatus) {}
    public record History(List<HistoryItem> items) {}
    public record Progress(String requestId, String stage, boolean completed) {}
    public record ExcelFile(byte[] bytes, String fileName) {}
}
