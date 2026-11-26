package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.Answer;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 외부 AI가 아직 없는 연결(규칙으로 답함). 설정 AI_API_URL·AI_API_KEY·AI_MODEL·AI_TIMEOUT_SECONDS는 환경 변수로만 받는다.
 * 키 값은 로그에 쓰지 않는다. 외부 호출은 0번.
 */
@Component
public class DisconnectedAiAnalysisGateway implements AiAnalysisExternalGateway {
    private final boolean configured;
    public DisconnectedAiAnalysisGateway() { this("", ""); }
    @Autowired
    public DisconnectedAiAnalysisGateway(@Value("${ai.api-url:}") String url, @Value("${ai.api-key:}") String key) {
        this.configured = url != null && !url.isBlank() && key != null && !key.isBlank();
        if (!configured) LoggerFactory.getLogger(DisconnectedAiAnalysisGateway.class).info("외부 AI 연결 꺼짐(규칙으로 답함)");
        else LoggerFactory.getLogger(DisconnectedAiAnalysisGateway.class).info("외부 AI 설정이 있지만 이번 판은 연결하지 않습니다(규칙으로 답함)");
    }
    public boolean configured() { return configured; }
    /** 이번 판은 설정이 있어도 보내지 않는다. */
    public boolean connected() { return false; }
    public void accept(String pseudonymousQuestion, Answer projectedAnswer) { /* Deliberately disconnected. */ }
}
