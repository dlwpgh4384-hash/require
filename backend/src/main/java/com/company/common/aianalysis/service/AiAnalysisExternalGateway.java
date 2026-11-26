package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.Answer;

/** Optional future settings: AI_API_URL, AI_API_KEY, AI_MODEL, AI_TIMEOUT_SECONDS. */
public interface AiAnalysisExternalGateway {
    void accept(String pseudonymousQuestion, Answer projectedAnswer);
    /** 실제로 밖에 보내는 연결인지. 아니면 이름 바꾸기(개발자·사용자 이름 읽기)도 하지 않는다. */
    boolean connected();
}
