package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.Answer;
import org.springframework.stereotype.Component;

@Component
public class DisconnectedAiAnalysisGateway implements AiAnalysisExternalGateway {
    public void accept(String pseudonymousQuestion, Answer projectedAnswer) { /* Deliberately disconnected. */ }
}
