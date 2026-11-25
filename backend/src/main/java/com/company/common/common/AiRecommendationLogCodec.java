package com.company.common.common;

import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Version 1: each compact score is [awarded, maximum]; f=request fingerprint, n=original need, e=exclusion active. */
@Component
public class AiRecommendationLogCodec {
    private static final List<String> DIMENSIONS = List.of(
        "job", "skill", "customerIndustry", "grade", "performance", "cost", "availability", "workplace"
    );
    private static final List<String> KEYS = List.of("j", "s", "c", "g", "p", "o", "a", "w");
    private final ObjectMapper json;

    public AiRecommendationLogCodec(ObjectMapper json) {
        this.json = json;
    }

    public record Score(BigDecimal score, BigDecimal maximumScore) {
        public Score {
            if (score != null) score = score.stripTrailingZeros();
            if (maximumScore != null) maximumScore = maximumScore.stripTrailingZeros();
        }
    }
    public record Metadata(String fingerprint, Integer originalNeedSn, Boolean exclusionActive) {}
    public record Snapshot(Map<String, Score> scores, Metadata metadata) {
        public Snapshot {
            scores = Map.copyOf(scores);
        }
    }

    public String reason(String value) {
        if (value == null || value.codePointCount(0, value.length()) <= 300) return value;
        return value.substring(0, value.offsetByCodePoints(0, 300));
    }

    /** Accepts the approved eight-score object, ignoring only non-contract descriptive fields. */
    public String normalize(JsonNode document, Metadata metadata) {
        if (document == null || !document.isObject()) throw invalid();
        var scores = new LinkedHashMap<String, Score>();
        JsonNode values = document.has("scores") ? document.get("scores") : document;
        for (String dimension : DIMENSIONS) {
            JsonNode item = values == null ? null : values.get(dimension);
            if (item == null || !item.isObject()) throw invalid();
            scores.put(dimension, new Score(number(item.get("score")), number(item.get("maximumScore"))));
        }
        return encode(new Snapshot(scores, metadata));
    }

    public String encode(Snapshot snapshot) {
        if (snapshot == null || !snapshot.scores().keySet().equals(java.util.Set.copyOf(DIMENSIONS))) {
            throw invalid();
        }
        var root = json.createObjectNode().put("v", 1);
        for (int index = 0; index < DIMENSIONS.size(); index++) {
            Score item = snapshot.scores().get(DIMENSIONS.get(index));
            validate(item);
            root.putArray(KEYS.get(index))
                .add(item.score().stripTrailingZeros())
                .add(item.maximumScore().stripTrailingZeros());
        }
        Metadata metadata = snapshot.metadata();
        validate(metadata);
        root.put("f", metadata.fingerprint());
        if (metadata.originalNeedSn() != null) root.put("n", metadata.originalNeedSn());
        if (metadata.exclusionActive() != null) root.put("e", metadata.exclusionActive());
        try {
            String encoded = json.writer().without(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(root);
            if (encoded.codePointCount(0, encoded.length()) > 500) throw invalid();
            return encoded;
        } catch (JsonProcessingException exception) {
            throw invalid();
        }
    }

    public Snapshot decode(String encoded) {
        if (encoded == null || encoded.codePointCount(0, encoded.length()) > 500) throw invalid();
        try {
            JsonNode root = json.reader()
                .with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(encoded);
            if (root == null || !root.isObject() || !root.path("v").isIntegralNumber()
                || root.path("v").intValue() != 1) throw invalid();
            var scores = new LinkedHashMap<String, Score>();
            for (int index = 0; index < DIMENSIONS.size(); index++) {
                JsonNode pair = root.get(KEYS.get(index));
                if (pair == null || !pair.isArray() || pair.size() != 2) throw invalid();
                var score = new Score(number(pair.get(0)), number(pair.get(1)));
                validate(score);
                scores.put(DIMENSIONS.get(index), score);
            }
            if (!root.path("f").isTextual()
                || root.has("n") && (!root.get("n").isIntegralNumber() || !root.get("n").canConvertToInt())
                || root.has("e") && !root.get("e").isBoolean()) throw invalid();
            var metadata = new Metadata(root.get("f").textValue(),
                root.has("n") ? root.get("n").intValue() : null,
                root.has("e") ? root.get("e").booleanValue() : null);
            validate(metadata);
            return new Snapshot(scores, metadata);
        } catch (JsonProcessingException exception) {
            throw invalid();
        }
    }

    public String exclusionActive(String encoded, boolean active) {
        var snapshot = decode(encoded);
        var old = snapshot.metadata();
        if (active && Boolean.FALSE.equals(old.exclusionActive())) throw CodeFailure.conflict();
        if (old.exclusionActive() == null && !active) return encoded;
        return encode(new Snapshot(snapshot.scores(), new Metadata(old.fingerprint(), old.originalNeedSn(), active)));
    }

    private static BigDecimal number(JsonNode value) {
        if (value == null || !value.isNumber()) throw invalid();
        return value.decimalValue();
    }

    private static void validate(Score value) {
        if (value == null || value.score() == null || value.maximumScore() == null
            || value.score().signum() < 0 || value.maximumScore().signum() < 0
            || value.score().compareTo(value.maximumScore()) > 0) throw invalid();
    }

    private static void validate(Metadata value) {
        if (value == null || value.fingerprint() == null || !value.fingerprint().matches("[a-f0-9]{64}")
            || value.originalNeedSn() != null && (value.originalNeedSn() < 1 || value.originalNeedSn() > 999)) {
            throw invalid();
        }
    }

    private static CodeFailure invalid() {
        return CodeFailure.invalid("scores", "추천 점수 기록을 확인해 주세요.");
    }
}
