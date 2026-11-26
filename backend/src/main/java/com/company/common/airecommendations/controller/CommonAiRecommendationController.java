package com.company.common.airecommendations.controller;

import com.company.common.airecommendations.dto.AiRecommendationContracts.*;
import com.company.common.airecommendations.service.*;
import com.company.common.users.dto.UserContracts.Response;
import com.company.common.clients.service.ClientRules;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonAiRecommendationController {
    private final CommonAiRecommendationService recommendations;
    private final AiRecommendationActionService actions;
    private final ObjectMapper json;

    public CommonAiRecommendationController(CommonAiRecommendationService recommendations, AiRecommendationActionService actions, ObjectMapper json) {
        this.recommendations = recommendations;
        this.actions = actions;
        this.json = json;
    }
    @PostMapping("/api/common/ai-recommendations")
    public Response<Result> postCommonAiRecommendation(@RequestBody JsonNode input) {
        return Response.of(recommendations.postCommonAiRecommendation(input(input, Input.class,
            Set.of("requestId", "targetType", "targetId", "jobCode", "startMonth", "gradeCode", "needSn"))));
    }
    @PostMapping("/api/common/ai-recommendations/{recommendationId}/assignment")
    public Response<AssignmentResult> postCommonAiRecommendationAssignment(@PathVariable String recommendationId,
        @RequestBody JsonNode input) {
        return Response.of(actions.assignment(recommendationId, input(input, AssignmentInput.class, Set.of("developerId", "assignmentId"))));
    }
    @PostMapping("/api/common/ai-recommendations/{recommendationId}/exclusions")
    public Response<ExclusionResult> postCommonAiRecommendationExclusion(@PathVariable String recommendationId,
        @RequestBody JsonNode input) {
        return Response.of(actions.exclusion(recommendationId, input(input, ExclusionInput.class, Set.of("developerId", "reasonCode"))));
    }
    @PutMapping("/api/common/ai-recommendations/{recommendationId}/tentative-assignment")
    public Response<TentativeResult> putCommonAiRecommendationTentativeAssignment(@PathVariable String recommendationId,
        @RequestBody JsonNode input) {
        return Response.of(actions.tentative(recommendationId, input(input, TentativeInput.class, Set.of("developerId", "needSn"))));
    }
    private <T> T input(JsonNode body, Class<T> type, Set<String> fields) {
        ClientRules.object(body, fields);
        for (String field : fields) {
            JsonNode value = body.get(field);
            if (value == null || value.isNull()) continue;
            boolean valid = "needSn".equals(field) ? value.isIntegralNumber() && value.canConvertToInt() : value.isTextual();
            if (!valid) throw CodeFailure.invalid(field, "입력값을 확인해 주세요.");
        }
        return json.convertValue(body, type);
    }
}
