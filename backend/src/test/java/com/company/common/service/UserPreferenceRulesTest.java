package com.company.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class UserPreferenceRulesTest {
    private final UserPreferenceRules rules = new UserPreferenceRules(new ObjectMapper());

    @ParameterizedTest
    @CsvSource({"PAGE_SIZE,20", "PAGE_SIZE,50", "PAGE_SIZE,100", "DENSE,넓게", "DENSE,좁게",
        "MENU_FOLD,true", "MENU_FOLD,false", "FAV,[]", "RECENT,[]"})
    void acceptedScalarValues(String type, String value) {
        assertThatCode(() -> rules.validate(type, "GLOBAL", value)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"PAGE_SIZE,30", "PAGE_SIZE,020", "DENSE,dense", "MENU_FOLD,TRUE",
        "FAV,{}", "RECENT,null"})
    void eachInvalidValueIsRejected(String type, String value) {
        assertThatThrownBy(() -> rules.validate(type, "GLOBAL", value)).isInstanceOf(UserPreferenceFailure.class);
    }

    @Test
    void favMaximumAcceptedAndEleventhRejected() throws Exception {
        var json = new ObjectMapper();
        String ten = json.writeValueAsString(IntStream.range(0, 10).mapToObj(i -> "UI-" + i).toList());
        rules.validate("FAV", "GLOBAL", ten);
        String eleven = json.writeValueAsString(IntStream.range(0, 11).mapToObj(i -> "UI-" + i).toList());
        assertThatThrownBy(() -> rules.validate("FAV", "GLOBAL", eleven)).hasMessage("즐겨찾기는 최대 10개까지 추가할 수 있습니다.");
    }

    @Test
    void recentMaximumAcceptedAndSixthRejected() {
        rules.validate("RECENT", "GLOBAL", "[\"A\",\"B\",\"C\",\"D\",\"E\"]");
        assertThatThrownBy(() -> rules.validate("RECENT", "GLOBAL", "[\"A\",\"B\",\"C\",\"D\",\"E\",\"F\"]"))
            .isInstanceOf(UserPreferenceFailure.class);
    }

    @Test
    void repeatedScreenRejectedInBothLists() {
        for (String type : new String[]{"FAV", "RECENT"}) {
            assertThatThrownBy(() -> rules.validate(type, "GLOBAL", "[\"UI-12\",\"UI-12\"]"))
                .isInstanceOf(UserPreferenceFailure.class);
        }
    }

    @Test
    void periodOnlyValidatesSerializationNotBusinessDataYear() {
        rules.validate("PERIOD", "UI-12", "{\"year\":1999}");
        rules.validate("PERIOD", "UI-16", "{\"year\":2099,\"month\":12}");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"{}", "{\"year\":\"2026\"}",
        "{\"year\":2026,\"month\":0}", "{\"year\":2026,\"month\":13}",
        "{\"year\":2026,\"month\":1,\"other\":1}", "{\"year\":2026,\"month\":1} {}"})
    void malformedPeriodRejected(String value) {
        assertThatThrownBy(() -> rules.validate("PERIOD", "UI-16", value)).isInstanceOf(UserPreferenceFailure.class);
    }

    @Test
    void emptyOverlongAndNonStringListRejected() {
        assertThatThrownBy(() -> rules.validate("DENSE", "GLOBAL", "")).isInstanceOf(UserPreferenceFailure.class);
        assertThatThrownBy(() -> rules.validate("DENSE", "GLOBAL", "가".repeat(2001))).isInstanceOf(UserPreferenceFailure.class);
        assertThatThrownBy(() -> rules.validate("FAV", "GLOBAL", "[1]")).isInstanceOf(UserPreferenceFailure.class);
        assertThatThrownBy(() -> rules.validate("PERIOD", "UI-12", "{\"year\":2026,\"month\":1}"))
            .isInstanceOf(UserPreferenceFailure.class);
    }
}
