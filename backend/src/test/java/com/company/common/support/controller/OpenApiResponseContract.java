package com.company.common.support.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.ResultMatcher;
import static org.assertj.core.api.Assertions.assertThat;

/** Focused response shape assertions from the durable OpenAPI fixture, not example payloads. */
public final class OpenApiResponseContract {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonNode CONTRACT = load();

    private OpenApiResponseContract() {}

    private static JsonNode load() {
        var yaml = new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource("contracts/openapi.yaml"));
        var document = yaml.getObject();
        assertThat(document).as("durable OpenAPI fixture").isNotNull();
        return JSON.valueToTree(document);
    }

    public static ResultMatcher response(String method, String path) {
        JsonNode operation = CONTRACT.path("paths").path(path).path(method.toLowerCase(Locale.ROOT));
        assertThat(operation.isObject()).as("OpenAPI operation %s %s", method, path).isTrue();
        return result -> {
            var response = result.getResponse();
            JsonNode schema = operation.path("responses").path(Integer.toString(response.getStatus()))
                .path("content").path("application/json").path("schema");
            assertThat(schema.isObject()).as("declared JSON response %s %s: %s", method, path, response.getStatus()).isTrue();
            JsonNode body = JSON.readTree(response.getContentAsByteArray());
            assertThat(body).as("JSON response body").isNotNull();
            shape(body, schema, "$");
        };
    }

    private static void shape(JsonNode value, JsonNode schema, String location) {
        if (schema.has("$ref")) {
            String reference = schema.path("$ref").asText();
            assertThat(reference).startsWith("#/");
            JsonNode target = CONTRACT.at(reference.substring(1));
            assertThat(target.isObject()).as("resolved schema %s", reference).isTrue();
            shape(value, target, location);
            return;
        }
        if (value.isNull() && schema.path("nullable").asBoolean(false)) return;
        for (JsonNode part : schema.path("allOf")) shape(value, part, location);
        String type = schema.path("type").asText();
        boolean matches = switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "" -> true;
            default -> throw new AssertionError("Unsupported response type: " + type);
        };
        assertThat(matches).as("%s has declared type %s", location, type).isTrue();
        if (value.isObject()) {
            for (JsonNode field : schema.path("required"))
                assertThat(value.has(field.asText())).as("%s requires %s", location, field.asText()).isTrue();
            var fields = value.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                JsonNode property = schema.path("properties").path(field.getKey());
                if (schema.has("additionalProperties") && schema.path("additionalProperties").isBoolean()
                    && !schema.path("additionalProperties").asBoolean())
                    assertThat(property.isMissingNode()).as("%s.%s is a declared field", location, field.getKey()).isFalse();
                if (property.isObject()) shape(field.getValue(), property, location + "." + field.getKey());
            }
        }
        if (value.isArray() && schema.path("items").isObject()) {
            for (int index = 0; index < value.size(); index++)
                shape(value.get(index), schema.path("items"), location + "[" + index + "]");
        }
    }
}
