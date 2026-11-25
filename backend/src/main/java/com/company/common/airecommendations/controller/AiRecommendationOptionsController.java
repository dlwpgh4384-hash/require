package com.company.common.airecommendations.controller;

import com.company.common.airecommendations.dto.AiRecommendationOptionsContracts.*;
import com.company.common.airecommendations.service.AiRecommendationOptionsService;
import com.company.common.users.dto.UserContracts.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiRecommendationOptionsController {
    private final AiRecommendationOptionsService service;

    public AiRecommendationOptionsController(AiRecommendationOptionsService service) {
        this.service = service;
    }

    @GetMapping(value = "/api/common/ai-recommendation-options", produces = "application/json")
    public Response<Options> getCommonAiRecommendationOptions(
        @RequestParam(required = false) String targetType, @RequestParam(required = false) String targetId) {
        return Response.of(service.getCommonAiRecommendationOptions(new Filter(targetType, targetId)));
    }
}
