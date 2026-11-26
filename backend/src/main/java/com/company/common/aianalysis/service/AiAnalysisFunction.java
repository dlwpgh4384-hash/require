package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import java.util.List;
import java.util.Set;

/** 조회 함수 하나(F-xx·P-xx). 원래 화면 서비스를 불러 숫자를 그대로 옮기고, 결론도 그 숫자로만 만든다. */
public interface AiAnalysisFunction {
    Set<String> functions();
    Result read(AiAnalysisInterpreter.Query query);
    /** usedData는 사람이 읽는 글(「개인 원가·수익표 · 2026-10」), baseMonth는 yyyy-MM. */
    record Result(String conclusion, List<Table> tables, Chart chart, List<String> assumptions,
        String usedData, String baseMonth, boolean provisional, boolean forecast, List<Action> actions) {
        public Result {
            tables = tables == null ? List.of() : List.copyOf(tables);
            assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
            actions = actions == null ? List.of() : List.copyOf(actions);
        }
    }
    /** 답할 수 없음(NO_ANS). 지어낸 숫자 대신 이 글과 추천 질문을 보인다. */
    final class NoAnswer extends RuntimeException {
        private final List<String> suggestions;
        private final List<String> labels;
        private final String detail;
        public NoAnswer(String message) { this(message, null, null); }
        public NoAnswer(String message, String detail, List<String> suggestions) { this(message, detail, suggestions, null); }
        /** labels: 추천 질문 단추 글(suggestions와 같은 순서, 없으면 질문 글 그대로). */
        public NoAnswer(String message, String detail, List<String> suggestions, List<String> labels) {
            super(message, null, false, false);
            this.detail = detail;
            this.suggestions = suggestions;
            this.labels = labels;
        }
        public List<String> suggestions() { return suggestions; }
        public List<String> labels() { return labels; }
        public String detail() { return detail; }
    }
}
