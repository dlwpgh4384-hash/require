package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.aianalysis.mapper.AiAnalysisMapper;
import com.company.common.aianalysis.mapper.AiAnalysisRows.Write;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAnalysisSnapshotWriter {
    private final AiAnalysisMapper mapper;
    private final ObjectMapper json;
    public AiAnalysisSnapshotWriter(AiAnalysisMapper mapper, ObjectMapper json) { this.mapper = mapper; this.json = json; }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Answer save(String actor, String question, Answer projected) {
        var answer = projected.identified(mapper.nextId());
        try {
            if (mapper.insert(new Write(answer.questionId(), actor, question, json.writeValueAsString(answer),
                truncate(answer.usedData(), 500), answer.baseMonth().replace("-", ""),
                answer.provisional() ? "Y" : "N", answer.answerStatus())) != 1)
                throw new IllegalStateException("Question snapshot was not inserted");
            return answer;
        } catch (JsonProcessingException invalid) { throw new IllegalStateException("Question serialization failed", invalid); }
    }
    static String truncate(String value, int limit) {
        return value.codePointCount(0, value.length()) <= limit ? value : value.substring(0, value.offsetByCodePoints(0, limit));
    }
}
