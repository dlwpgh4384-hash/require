package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.support.service.CodeFailure;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CommonAiAnalysisService {
    /** 시안 EXQ 추천 질문 3개. */
    public static final List<String> SUGGESTIONS = List.of("다음 분기 대기 인력은?", "이번 달 적자 인력은?", "이번 달 매출과 영업이익은?");
    public static final String NOT_FOUND = "이 질문에는 아직 답을 찾지 못했습니다. 아래처럼 물어보세요.";
    public static final String DENIED = "이 내용을 볼 권한이 없습니다.";
    static final String DENIED_SCOPE = "급여는 급여를 볼 수 있는 권한(시스템 관리자·경영진·재무 담당)에서만 답합니다.";
    static final String FAILED = "답을 만들지 못했습니다. 잠시 뒤 다시 물어 주세요.";
    static final String CAUSE = "원인은 시스템이 판단하지 않습니다. 아래 숫자와 원래 화면을 확인해 주세요.";
    private final AiAnalysisInterpreter interpreter;
    private final AiAnalysisReadExecutor reader;
    private final AiAnalysisSnapshotWriter writer;
    private final AiAnalysisProjection projection;
    private final AiAnalysisProgress progress;
    private final AiAnalysisArchive archive;
    public CommonAiAnalysisService(AiAnalysisInterpreter interpreter, AiAnalysisReadExecutor reader,
        AiAnalysisSnapshotWriter writer, AiAnalysisProjection projection, AiAnalysisProgress progress, AiAnalysisArchive archive) {
        this.interpreter = interpreter; this.reader = reader; this.writer = writer;
        this.projection = projection; this.progress = progress; this.archive = archive;
    }
    public History getCommonAiAnalysisQuestions() { return archive.history(); }
    public Progress getCommonAiAnalysisProgress(String id) { return progress.read(archive.actorId(), id); }
    public ExcelFile getCommonAiAnalysisQuestionExcel(String id) { return archive.excel(id); }
    public Answer postCommonAiAnalysisQuestion(Input input) {
        var query = interpreter.interpret(input);
        String actor = archive.actorId();
        var claim = progress.claim(actor, input.requestId());
        if (!claim.owner()) {
            try { return claim.execution().answer.join(); }
            catch (CompletionException failure) { throw new IllegalStateException("Question execution failed", failure.getCause()); }
        }
        try {
            Answer answer;
            if (query.salary() && !projection.salaryAllowed())
                answer = unavailable(query, DENIED, DENIED_SCOPE, List.of(), "NO_ANS");
            else if (!projection.functionAllowed(query.function()))
                // 그 화면 조회 권한이 없으면 숫자를 읽지 않고 답하지 않는다(명세 SFR-126-06·⑨-4).
                answer = unavailable(query, DENIED, null, List.of(), "NO_ANS");
            else if (query.function() == null)
                answer = query.notice() == null ? unavailable(query, NOT_FOUND, null, SUGGESTIONS, "NO_ANS")
                    : unavailable(query, query.notice(), null, List.of("앞으로 6개월 매출은?"), "NO_ANS");
            else answer = execute(query, claim.execution());
            claim.execution().stage("정리 중");
            Answer projected = projection.apply(answer);
            Answer saved = writer.save(actor, query.question(), projected);
            if ("OK".equals(saved.answerStatus())) {
                try { reader.external(query.question(), saved); }
                catch (RuntimeException unavailable) {
                    LoggerFactory.getLogger(CommonAiAnalysisService.class).warn("event=ai_analysis_external_unavailable administratorDelivery=pending");
                }
            }
            claim.execution().answer.complete(saved);
            return saved;
        } catch (RuntimeException failure) { claim.execution().answer.completeExceptionally(failure); throw failure; }
        finally { progress.finish(actor, input.requestId(), claim.execution()); }
    }
    private Answer execute(AiAnalysisInterpreter.Query query, AiAnalysisProgress.Execution execution) {
        try {
            var result = reader.read(query, execution);
            execution.stage("정리 중");
            if (result == null) return unavailable(query, NOT_FOUND, null, SUGGESTIONS, "NO_ANS");
            String conclusion = query.cause() ? result.conclusion() + " " + CAUSE : result.conclusion();
            var tables = result.tables();
            boolean excel = tables.stream().anyMatch(t -> !t.columns().isEmpty());
            // 예측 답은 「예측」 표시가 잠정을 대신한다(잠정 표시를 겹쳐 달지 않음).
            boolean provisional = result.provisional() && !result.forecast();
            return new Answer(null, "OK", conclusion, result.forecast(), provisional,
                provisional ? result.baseMonth() : null, tables, result.chart(), result.assumptions(),
                result.usedData(), result.baseMonth(), result.actions(), excel, List.of());
        } catch (AiAnalysisFunction.NoAnswer missing) {
            var suggestions = missing.suggestions() == null ? SUGGESTIONS : missing.suggestions();
            var answer = unavailable(query, missing.getMessage() == null ? NOT_FOUND : missing.getMessage(), missing.detail(), suggestions, "NO_ANS");
            if (missing.labels() == null || missing.suggestions() == null || missing.labels().size() != missing.suggestions().size()) return answer;
            return new Answer(null, answer.answerStatus(), answer.conclusion(), false, false, null, List.of(), null, List.of(),
                answer.usedData(), answer.baseMonth(), List.of(), false, answer.suggestedQuestions(), List.copyOf(missing.labels()));
        } catch (CodeFailure invalid) {
            // 원래 화면이 고를 수 없는 기간·조건이라고 하면(400) 지어내지 않고 답할 수 없음.
            if (invalid.getStatus() == 400) return unavailable(query, NOT_FOUND, null, SUGGESTIONS, "NO_ANS");
            LoggerFactory.getLogger(CommonAiAnalysisService.class).error("event=ai_analysis_source_error function={} status={} administratorDelivery=pending",
                query.function(), invalid.getStatus());
            return unavailable(query, FAILED, null, List.of(), "ERR");
        } catch (RuntimeException failure) {
            LoggerFactory.getLogger(CommonAiAnalysisService.class).error("event=ai_analysis_source_error function={} exceptionType={} administratorDelivery=pending",
                query.function(), failure.getClass().getSimpleName());
            return unavailable(query, FAILED, null, List.of(), "ERR");
        }
    }
    /** usedData는 따로 알릴 둘째 줄이 있을 때만(예: 급여 권한 안내). 없으면 null(화면에 「사용한 데이터」 줄 없음). */
    private Answer unavailable(AiAnalysisInterpreter.Query query, String message, String detail, List<String> suggestions, String status) {
        return new Answer(null, status, message, false, false, null, List.of(), null, List.of(),
            detail, query.period().endMonth(), List.of(), false, new ArrayList<>(suggestions));
    }
}
