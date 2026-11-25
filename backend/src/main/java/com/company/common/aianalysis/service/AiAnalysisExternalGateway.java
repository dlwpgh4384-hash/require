package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.Answer;

/** Optional future settings: AI_API_URL, AI_API_KEY, AI_MODEL, AI_TIMEOUT_SECONDS. */
public interface AiAnalysisExternalGateway {
    void accept(String pseudonymousQuestion, Answer projectedAnswer);
}
