package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CommonAiAnalysisService {
    public static final List<String> SUGGESTIONS = List.of("다음 분기 대기 인력", "이번 달 적자 인력", "이번 달 매출·영업이익");
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
                answer = unavailable(query, "이 내용을 볼 권한이 없습니다.", "NO_ANS");
            else if (query.function() == null) answer = unavailable(query, "이 질문에는 아직 답을 찾지 못했습니다. 아래처럼 물어보세요.", "NO_ANS");
            else {
                try {
                    var result = reader.read(query, claim.execution());
                    claim.execution().stage("정리 중");
                    if (result == null || result.table() == null || result.table().rows().isEmpty())
                        answer = unavailable(query, "이 질문에는 아직 답을 찾지 못했습니다. 아래처럼 물어보세요.", "NO_ANS");
                    else answer = new Answer(null, "OK", query.cause() ? "관련 조회 결과입니다. 이 자료만으로 원인을 단정하지 않습니다." : "요청한 기간의 원래 화면 조회 결과입니다.",
                        result.table(), result.assumptions(), query.function() + " · " + result.targetScreen() + " · 대상: " + query.function()
                            + " · 기간 " + result.period().startMonth() + "~" + result.period().endMonth()
                            + " · 기준월 " + result.period().endMonth() + (result.provisional() ? " 작업 중" : " 마감"),
                        result.period().endMonth(), result.provisional(),
                        List.of(new Action("원래 화면 열기", result.targetScreen(), result.period())), List.of());
                } catch (RuntimeException failure) {
                    LoggerFactory.getLogger(CommonAiAnalysisService.class).error("event=ai_analysis_source_error function={} exceptionType={} administratorDelivery=pending",
                        query.function(), failure.getClass().getSimpleName());
                    answer = unavailable(query, "자료를 조회하지 못했습니다. 잠시 후 다시 질문해 주세요.", "ERR");
                }
            }
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
    private Answer unavailable(AiAnalysisInterpreter.Query query, String message, String status) {
        return new Answer(null, status, message, null, List.of(), "사용 가능한 원천 결과 없음", query.period().endMonth(),
            false, List.of(), SUGGESTIONS);
    }
}
