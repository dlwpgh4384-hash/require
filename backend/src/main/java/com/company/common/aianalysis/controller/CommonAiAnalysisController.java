package com.company.common.aianalysis.controller;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.aianalysis.service.CommonAiAnalysisService;
import com.company.common.users.dto.UserContracts.Response;
import com.company.common.clients.service.ClientRules;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/ai-analysis")
public class CommonAiAnalysisController {
    private final CommonAiAnalysisService service;
    private final ObjectMapper json;
    public CommonAiAnalysisController(CommonAiAnalysisService service, ObjectMapper json) { this.service = service; this.json = json; }
    @GetMapping("/questions")
    public Response<History> getCommonAiAnalysisQuestions() { return Response.of(service.getCommonAiAnalysisQuestions()); }
    @PostMapping("/questions")
    public Response<Answer> postCommonAiAnalysisQuestion(@RequestBody JsonNode body) {
        ClientRules.object(body, Set.of("requestId", "question", "screenId", "period"));
        for (var name : ListFields.TEXT) {
            var value = body.get(name);
            if (value != null && !value.isNull() && !value.isTextual()) throw CodeFailure.invalid(name, "입력값을 확인해 주세요.");
        }
        var period = body.get("period");
        if (period != null && !period.isNull()) {
            ClientRules.object(period, Set.of("startMonth", "endMonth"));
            for (var name : Set.of("startMonth", "endMonth"))
                if (!period.hasNonNull(name) || !period.get(name).isTextual()) throw CodeFailure.invalid("period", "기간을 확인해 주세요.");
        }
        return Response.of(service.postCommonAiAnalysisQuestion(json.convertValue(body, Input.class)));
    }
    @GetMapping("/requests/{requestId}")
    public Response<Progress> getCommonAiAnalysisProgress(@PathVariable String requestId) { return Response.of(service.getCommonAiAnalysisProgress(requestId)); }
    @GetMapping(value = "/questions/{questionId}/excel", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> getCommonAiAnalysisQuestionExcel(@PathVariable String questionId) {
        var file = service.getCommonAiAnalysisQuestionExcel(questionId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
            .body(file.bytes());
    }
    private static class ListFields { static final Set<String> TEXT = Set.of("requestId", "question", "screenId"); }
}
