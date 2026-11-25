package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.Answer;
import com.company.common.aianalysis.mapper.AiAnalysisMapper;
import com.company.common.aianalysis.mapper.AiAnalysisRows.Identity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisPseudonymizer {
    private final AiAnalysisMapper mapper;
    private final ObjectMapper json;
    public AiAnalysisPseudonymizer(AiAnalysisMapper mapper, ObjectMapper json) { this.mapper = mapper; this.json = json; }
    public void send(AiAnalysisExternalGateway gateway, String question, Answer answer) {
        var identities = mapper.identities().stream().filter(i -> i.name() != null && !i.name().isBlank())
            .sorted(Comparator.comparingInt((Identity i) -> i.name().length()).reversed()).toList();
        gateway.accept(text(question, identities), json.convertValue(clean(json.valueToTree(answer), identities), Answer.class));
    }
    private String text(String value, List<Identity> identities) {
        for (var identity : identities) value = value.replace(identity.name(), identity.replacement());
        return value;
    }
    private JsonNode clean(JsonNode value, List<Identity> identities) {
        if (value.isTextual()) return TextNode.valueOf(text(value.textValue(), identities));
        if (value instanceof ObjectNode object) {
            var fields = new ArrayList<String>(); object.fieldNames().forEachRemaining(fields::add);
            fields.forEach(field -> object.set(field, clean(object.get(field), identities)));
        } else if (value instanceof ArrayNode array) for (int i = 0; i < array.size(); i++) array.set(i, clean(array.get(i), identities));
        return value;
    }
}
