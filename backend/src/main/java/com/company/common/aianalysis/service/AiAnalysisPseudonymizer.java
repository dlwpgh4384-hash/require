package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.Answer;
import com.company.common.aianalysis.mapper.AiAnalysisMapper;
import com.company.common.aianalysis.mapper.AiAnalysisRows.Identity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** 외부로 보내는 글: 개발자 이름은 관리번호로, 다른 사람 이름(관리번호 없음)은 뺀다. */
@Component
public class AiAnalysisPseudonymizer {
    private final AiAnalysisMapper mapper;
    private final ObjectMapper json;
    public AiAnalysisPseudonymizer(AiAnalysisMapper mapper, ObjectMapper json) { this.mapper = mapper; this.json = json; }
    public void send(AiAnalysisExternalGateway gateway, String question, Answer answer) {
        // 보낼 곳이 없으면(외부 AI 연결 꺼짐) 이름 목록도 읽지 않는다.
        if (gateway == null || answer == null || !gateway.connected()) return;
        var source = mapper.identities();
        var identities = (source == null ? List.<Identity>of() : source).stream()
            .filter(i -> i != null && i.name() != null && !i.name().isBlank())
            .sorted(Comparator.comparingInt((Identity i) -> i.name().length()).reversed()).toList();
        gateway.accept(text(question == null ? "" : question, identities),
            json.convertValue(clean(json.valueToTree(answer), identities), Answer.class));
    }
    private static String text(String value, List<Identity> identities) {
        for (var identity : identities) value = value.replace(identity.name(), identity.replacement() == null ? "" : identity.replacement());
        return value;
    }
    private static JsonNode clean(JsonNode value, List<Identity> identities) {
        if (value == null || value.isNull()) return value;
        if (value.isTextual()) return TextNode.valueOf(text(value.textValue(), identities));
        if (value instanceof ObjectNode object) {
            var fields = new ArrayList<String>(); object.fieldNames().forEachRemaining(fields::add);
            fields.forEach(field -> object.set(field, clean(object.get(field), identities)));
        } else if (value instanceof ArrayNode array) for (int i = 0; i < array.size(); i++) array.set(i, clean(array.get(i), identities));
        return value;
    }
}
