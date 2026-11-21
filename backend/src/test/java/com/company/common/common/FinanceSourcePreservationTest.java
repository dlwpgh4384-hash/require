package com.company.common.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class FinanceSourcePreservationTest {
    @Test
    void financeOperationsRemainPublicDevelopmentOperations() {
        var contract = yaml("contracts/openapi.yaml");
        assertThat(contract.has("security")).isFalse();
        assertThat(contract.path("components").has("securitySchemes")).isFalse();
        assertThat(contract.findValues("bearerAuth")).isEmpty();
        var paths = contract.path("paths");
        for (String path : List.of(
                "/api/common/finance-items/{yearMonth}",
                "/api/common/finance-items/{yearMonth}/previous-items",
                "/api/common/finance-items/{yearMonth}/excel",
                "/api/common/finance-items/upload-template")) {
            assertPublic(paths.path(path).path("get"), "GET " + path);
        }
        assertPublic(paths.path("/api/common/finance-items/{yearMonth}").path("put"), "PUT month");
        for (String path : List.of(
                "/api/common/finance-items/tax-years/{taxYear}",
                "/api/common/finance-items/tax-payments/{paymentId}/confirm",
                "/api/common/finance-items/{yearMonth}/uploads")) {
            assertPublic(paths.path(path).path("post"), "POST " + path);
        }
    }

    @Test
    void manualPathsMethodsAndResponsesArePreservedStructurally() {
        var manual = yaml("contracts/openapi-manual.yaml").path("paths");
        var effective = yaml("contracts/openapi.yaml").path("paths");
        assertThat(manual.size()).isPositive();
        manual.fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method ->
            assertThat(effective.path(path.getKey()).path(method.getKey()))
                .as(path.getKey() + " " + method.getKey()).isEqualTo(method.getValue())));
        for (String path : List.of("/api/common/developers", "/api/common/projects", "/api/common/assignments")) {
            assertThat(effective.path(path).has("get")).as(path).isTrue();
        }
    }

    @Test
    void manualSchemasAndNullableWithdrawalVersionAreNotOverwritten() {
        var manual = yaml("contracts/openapi-manual.yaml").path("components").path("schemas");
        var effective = yaml("contracts/openapi.yaml").path("components").path("schemas");
        assertThat(manual.size()).isPositive();
        manual.fields().forEachRemaining(schema -> assertThat(effective.path(schema.getKey()))
            .as(schema.getKey()).isEqualTo(schema.getValue()));
        assertThat(effective.path("CommonWithdrawalExtensionResult").path("properties")
            .path("modifiedAt").path("nullable").asBoolean()).isTrue();
    }

    private void assertPublic(JsonNode operation, String path) {
        assertThat(operation.isObject()).as(path).isTrue();
        assertThat(operation.path("security").isArray()).as(path).isTrue();
        assertThat(operation.path("security").isEmpty()).as(path).isTrue();
        assertThat(operation.has("x-roles")).as(path).isFalse();
    }

    private JsonNode yaml(String name) {
        var factory = new YamlMapFactoryBean();
        factory.setResources(new ClassPathResource(name));
        assertThat(factory.getObject()).as(name).isNotNull();
        return new ObjectMapper().valueToTree(factory.getObject());
    }
}
