package com.company.common.airecommendations.service;

import com.company.common.common.*;
import com.company.common.support.service.BusinessCalendar;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public record AiRecommendationResources(AiRecommendationCodes codes, AiRecommendationDefinitions definitions,
    AiRecommendationLogCodec codec, AiRecommendationExecutionJournal journal,
    ObjectMapper json, BusinessCalendar calendar) {}
