package com.company.common.preferences.dto;

import com.company.common.preferences.mapper.UserPreferenceRow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class UserPreferenceInputTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void nullTokenAndExactMicrosecondsRoundTripWithoutTimeZoneGuessing() throws Exception {
        var input = json.readValue("{\"prefVal\":\"20\",\"modDttm\":\"2026-01-02T03:04:05.123456Z\"}", UserPreferenceInput.class);
        var row = new UserPreferenceRow();
        row.setPrefVal(input.prefVal()); row.setModDttm(input.modDttm());
        assertThat(UserPreferenceResponse.from("PAGE_SIZE", "GLOBAL", row).data().modDttm())
            .isEqualTo("2026-01-02T03:04:05.123456Z");
        assertThat(json.readValue("{\"prefVal\":\"20\",\"modDttm\":null}", UserPreferenceInput.class).modDttm()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-02T03:04:05.1234567Z", "2026-01-02T03:04:05+09:00", "2026-01-02T03:04:05"})
    void cannotSilentlyLosePrecisionOrInferTimeZone(String token) {
        assertThatThrownBy(() -> json.readValue("{\"prefVal\":\"20\",\"modDttm\":\"" + token + "\"}", UserPreferenceInput.class))
            .isInstanceOf(Exception.class);
    }

    @Test
    void exactSecondStillSerializesAsDateTimeWithSeconds() {
        var row = new UserPreferenceRow();
        row.setModDttm(java.time.Instant.parse("2026-01-01T00:00:00.000000Z"));
        assertThat(UserPreferenceResponse.from("DENSE", "GLOBAL", row).data().modDttm()).isEqualTo("2026-01-01T00:00:00Z");
    }
}
