package com.company.common.dto;

import com.company.common.service.UserPreferenceFailure;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

@JsonDeserialize(using = UserPreferenceInput.Reader.class)
public record UserPreferenceInput(String prefVal, Instant modDttm) {
    public static class Reader extends JsonDeserializer<UserPreferenceInput> {
        @Override
        public UserPreferenceInput deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode body = parser.getCodec().readTree(parser);
            if (parser.nextToken() != null) {
                throw UserPreferenceFailure.invalid("prefVal", "입력 형식을 확인해 주세요.");
            }
            if (!body.isObject()) throw UserPreferenceFailure.invalid("prefVal", "설정 값을 입력해 주세요.");
            var names = body.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!name.equals("prefVal") && !name.equals("modDttm")) {
                    throw UserPreferenceFailure.invalid("prefVal", "정해진 설정 항목만 입력해 주세요.");
                }
            }
            if (!body.has("prefVal") || !body.get("prefVal").isTextual()) {
                throw UserPreferenceFailure.invalid("prefVal", "설정 값을 입력해 주세요.");
            }
            if (!body.has("modDttm")) {
                throw UserPreferenceFailure.invalid("modDttm", "조회한 수정 시각을 보내 주세요.");
            }
            JsonNode token = body.get("modDttm");
            Instant local = null;
            if (!token.isNull()) {
                try {
                    if (!token.isTextual()) throw new DateTimeParseException("type", "", 0);
                    OffsetDateTime value = OffsetDateTime.parse(token.textValue());
                    if (!value.getOffset().equals(ZoneOffset.UTC) || value.getNano() % 1000 != 0) {
                        throw new DateTimeParseException("precision", "", 0);
                    }
                    local = value.toInstant();
                } catch (DateTimeParseException failure) {
                    throw UserPreferenceFailure.invalid("modDttm", "수정 시각의 형식을 확인해 주세요.");
                }
            }
            return new UserPreferenceInput(body.get("prefVal").textValue(), local);
        }
    }
}
