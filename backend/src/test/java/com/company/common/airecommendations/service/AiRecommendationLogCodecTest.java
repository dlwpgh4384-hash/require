package com.company.common.airecommendations.service;

import com.company.common.airecommendations.service.AiRecommendationLogCodec.Metadata;
import com.company.common.airecommendations.service.AiRecommendationLogCodec.Score;
import com.company.common.airecommendations.service.AiRecommendationLogCodec.Snapshot;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AiRecommendationLogCodecTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AiRecommendationLogCodec codec = new AiRecommendationLogCodec(json);

    @Test
    void requestFingerprintIsStableHexAndRejectsBlank() {
        String first = AiRecommendationLogCodec.fingerprint("../request/한글");
        assertThat(first).matches("[a-f0-9]{64}");
        assertThat(first).isEqualTo(AiRecommendationLogCodec.fingerprint("../request/한글"));
        assertThat(first).isNotEqualTo(AiRecommendationLogCodec.fingerprint(" ../request/한글"));
        assertThatThrownBy(() -> AiRecommendationLogCodec.fingerprint(" ")).isInstanceOf(CodeFailure.class);
    }
    private final Metadata metadata = new Metadata("a".repeat(64), 7, null);

    private Snapshot snapshot() {
        var scores = new LinkedHashMap<String, Score>();
        var dimensions = List.of("job", "skill", "customerIndustry", "grade", "performance", "cost", "availability", "workplace");
        var awarded = List.of(25, 14, 15, 5, 4, 4, 10, 10);
        var maxima = List.of(25, 20, 15, 10, 5, 5, 10, 10);
        for (int i = 0; i < dimensions.size(); i++) {
            scores.put(dimensions.get(i), new Score(BigDecimal.valueOf(awarded.get(i)), BigDecimal.valueOf(maxima.get(i))));
        }
        return new Snapshot(scores, metadata);
    }

    @Test
    void restoresAllEightHistoricScoresAndMaximaWithoutOperatingStandards() {
        var before = snapshot();
        String stored = codec.encode(before);
        var restored = codec.decode(stored);
        assertThat(stored).hasSizeLessThanOrEqualTo(500);
        assertThat(restored).isEqualTo(before);
        assertThat(restored.scores().values().stream().map(Score::score).reduce(BigDecimal.ZERO, BigDecimal::add))
            .isEqualByComparingTo("87");
        assertThat(restored.metadata().originalNeedSn()).isEqualTo(7);
        assertThatThrownBy(() -> restored.scores().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void verbose501CharacterInputBecomesCompleteValidBoundedJson() throws Exception {
        ObjectNode verbose = json.createObjectNode();
        snapshot().scores().forEach((dimension, item) -> {
            verbose.putObject(dimension).put("score", item.score()).put("maximumScore", item.maximumScore());
        });
        verbose.put("diagnostic", "");
        int padding = 501 - json.writeValueAsString(verbose).length();
        assertThat(padding).isNotNegative();
        verbose.put("diagnostic", "가".repeat(padding));
        assertThat(json.writeValueAsString(verbose)).hasSize(501);
        String stored = codec.normalize(verbose, metadata);
        assertThat(stored).hasSizeLessThanOrEqualTo(500);
        assertThat(json.readTree(stored).isObject()).isTrue();
        assertThat(codec.decode(stored)).isEqualTo(snapshot());
        assertThat(stored).doesNotContain("diagnostic");
    }

    @Test
    void reasonIsLimitedAtUnicodeCodePointBoundary() {
        String input = "가".repeat(299) + "😀" + "나";
        String stored = codec.reason(input);
        assertThat(stored.codePointCount(0, stored.length())).isEqualTo(300);
        assertThat(stored).endsWith("😀");
        assertThat(codec.reason("가".repeat(300))).isEqualTo("가".repeat(300));
        assertThat(codec.reason(null)).isNull();
    }

    @Test
    void missingCanonicalScoreCannotBeIgnoredAsDiagnostic() {
        var scores = new LinkedHashMap<>(snapshot().scores());
        scores.remove("skill");
        assertThatThrownBy(() -> codec.encode(new Snapshot(scores, metadata))).isInstanceOf(CodeFailure.class);
    }

    @Test
    void nonNumericCanonicalScoreCannotBeSilentlyRecomputed() {
        ObjectNode verbose = json.valueToTree(snapshot());
        ((ObjectNode) verbose.path("scores").path("job")).put("score", "25");
        assertThatThrownBy(() -> codec.normalize(verbose, metadata)).isInstanceOf(CodeFailure.class);
    }

    @Test
    void oversizedCanonicalNumbersAreRejectedRatherThanSlicedOrDropped() {
        var scores = new LinkedHashMap<>(snapshot().scores());
        BigDecimal precise = new BigDecimal("0." + "1".repeat(600));
        scores.put("skill", new Score(precise, BigDecimal.ONE));
        assertThatThrownBy(() -> codec.encode(new Snapshot(scores, metadata))).isInstanceOf(CodeFailure.class);
    }

    @Test
    void fractionalScoresRoundtripWithoutBinaryFloatingPointLoss() {
        var scores = new LinkedHashMap<>(snapshot().scores());
        scores.put("skill", new Score(new BigDecimal("7.1234567890123456789"), new BigDecimal("20.1")));
        var restored = codec.decode(codec.encode(new Snapshot(scores, metadata)));
        assertThat(restored.scores().get("skill").score()).isEqualByComparingTo("7.1234567890123456789");
        assertThat(restored.scores().get("skill").maximumScore()).isEqualByComparingTo("20.1");
    }

    @Test
    void awardedScoreCannotExceedItsSavedMaximum() {
        var scores = new LinkedHashMap<>(snapshot().scores());
        scores.put("job", new Score(new BigDecimal("26"), new BigDecimal("25")));
        assertThatThrownBy(() -> codec.encode(new Snapshot(scores, metadata))).isInstanceOf(CodeFailure.class);
    }

    @Test
    void malformedOrSlicedJsonDoesNotReturnZeroScores() {
        String stored = codec.encode(snapshot());
        assertThatThrownBy(() -> codec.decode(stored.substring(0, stored.length() - 1)))
            .isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> codec.decode(stored + " {}"))
            .isInstanceOf(CodeFailure.class);
    }

    @Test
    void expiryChangesOnlyScopeMetadataAndRetainsScoresAndOriginalNeed() {
        String initial = codec.encode(snapshot());
        String active = codec.exclusionActive(initial, true);
        String expired = codec.exclusionActive(active, false);
        var restored = codec.decode(expired);
        assertThat(restored.scores()).isEqualTo(snapshot().scores());
        assertThat(restored.metadata()).isEqualTo(new Metadata(metadata.fingerprint(), 7, false));
        assertThat(codec.exclusionActive(initial, false)).isEqualTo(initial);
    }

    @Test
    void expiredExclusionCannotBeReactivatedByReopeningTheTarget() {
        String expired = codec.exclusionActive(codec.exclusionActive(codec.encode(snapshot()), true), false);
        assertThatThrownBy(() -> codec.exclusionActive(expired, true)).isInstanceOf(CodeFailure.class);
        assertThat(codec.decode(expired).metadata().exclusionActive()).isFalse();
    }

    @Test
    void rawRequestIdsCannotBeStoredAsFingerprintMetadata() {
        assertThatThrownBy(() -> codec.encode(new Snapshot(snapshot().scores(), new Metadata("raw-request", 1, null))))
            .isInstanceOf(CodeFailure.class);
    }
}
