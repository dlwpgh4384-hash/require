package com.company.common.support.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사람이 더한 경로·칸(openapi-manual.yaml)이 도구가 다시 만든 openapi.yaml에서 빠지면 크게 실패한다.
 * openapi.yaml을 옛것으로 덮어써도 여기서 바로 드러난다.
 */
class OpenApiManualContractTest {
    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options");

    private static JsonNode load(String resource) {
        var yaml = new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource(resource));
        var document = yaml.getObject();
        assertThat(document).as(resource).isNotNull();
        return new ObjectMapper().valueToTree(document);
    }

    @Test
    void everyManualPathAndSchemaStillExistsInGeneratedContractWithAtLeastTheManualFields() {
        JsonNode manual = load("contracts/openapi-manual.yaml");
        JsonNode generated = load("contracts/openapi.yaml");
        var missing = new ArrayList<String>();

        var paths = manual.path("paths").fields();
        int operations = 0;
        while (paths.hasNext()) {
            var path = paths.next();
            var methods = path.getValue().fields();
            while (methods.hasNext()) {
                var method = methods.next();
                if (!HTTP_METHODS.contains(method.getKey())) continue;
                operations++;
                JsonNode other = generated.path("paths").path(path.getKey()).path(method.getKey());
                if (!other.isObject()) { missing.add(method.getKey().toUpperCase() + " " + path.getKey()); continue; }
                String manualBody = method.getValue().path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText("");
                String otherBody = other.path("requestBody").path("content").path("application/json").path("schema").path("$ref").asText("");
                if (!manualBody.equals(otherBody)) missing.add(method.getKey().toUpperCase() + " " + path.getKey() + " requestBody " + manualBody);
                var statuses = method.getValue().path("responses").fieldNames();
                while (statuses.hasNext()) {
                    String status = statuses.next();
                    if (!other.path("responses").has(status)) missing.add(method.getKey().toUpperCase() + " " + path.getKey() + " response " + status);
                }
            }
        }

        var schemas = manual.path("components").path("schemas").fields();
        int schemaCount = 0;
        while (schemas.hasNext()) {
            var schema = schemas.next();
            schemaCount++;
            JsonNode other = generated.path("components").path("schemas").path(schema.getKey());
            if (!other.isObject()) { missing.add("schema " + schema.getKey()); continue; }
            var properties = schema.getValue().path("properties").fieldNames();
            while (properties.hasNext()) {
                String property = properties.next();
                if (!other.path("properties").has(property)) missing.add("schema " + schema.getKey() + "." + property);
            }
            List<String> required = new ArrayList<>();
            other.path("required").forEach(r -> required.add(r.asText()));
            for (JsonNode field : schema.getValue().path("required"))
                if (!required.contains(field.asText())) missing.add("schema " + schema.getKey() + " required " + field.asText());
        }

        assertThat(operations).as("manual operations").isPositive();
        assertThat(schemaCount).as("manual schemas").isPositive();
        assertThat(missing).as("openapi.yaml lost hand-added contract entries from openapi-manual.yaml; copy them back").isEmpty();
    }
}
