package com.company.common.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

// 로그인(UI-22) 작업 뒤: 손 설명서는 그대로, 로그인 정의(security·x-roles·401·403)는 openapi.yaml에만 있어 그 차이는 빼고 비교한다.
class RateRenegotiationContractPreservationTest {
    private static final String PATH = "/api/common/rate-renegotiations";

    @Test
    void developmentReadsRemainPublicWithoutLoginOrMenuRoleDeclarations() {
        var contract = yaml("contracts/openapi.yaml");
        com.company.common.support.test.LoginEraContract.assertSessionLogin(contract);
        assertThat(contract.findValues("bearerAuth")).isEmpty();
        for (String path : List.of(PATH, PATH + "/excel")) {
            var operation = contract.path("paths").path(path).path("get");
            assertThat(operation.isObject()).as(path).isTrue();
            com.company.common.support.test.LoginEraContract.assertLoggedIn(operation, path);
        }
    }

    @Test
    void rateRenegotiationContractAddsNoBusinessWriteOperationOrExtraFilter() {
        var paths = yaml("contracts/openapi.yaml").path("paths");
        for (String path : List.of(PATH, PATH + "/excel")) {
            var entry = paths.path(path);
            assertThat(entry.has("get")).isTrue();
            for (String method : List.of("post", "put", "patch", "delete")) {
                assertThat(entry.has(method)).as(path + " " + method).isFalse();
            }
            var operation = entry.path("get");
            assertThat(operation.has("requestBody")).isFalse();
            var names = new ArrayList<String>();
            operation.path("parameters").forEach(parameter -> names.add(parameter.path("name").asText()));
            var expected = new ArrayList<>(List.of("renewalMonths", "profitRateThreshold",
                "employeeTypeCode", "projectId", "increaseRate"));
            if (path.endsWith("/excel")) expected.add("columns");
            assertThat(names).containsExactlyElementsOf(expected);
        }
    }

    @Test
    void manualMethodsResponsesAndExistingAdditionalGetsRemainIntact() {
        var manual = yaml("contracts/openapi-manual.yaml").path("paths");
        var effective = yaml("contracts/openapi.yaml").path("paths");
        assertThat(manual.size()).isPositive();
        manual.fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(field ->
            assertThat(effective.path(path.getKey()).path(field.getKey()))
                .as(path.getKey() + " " + field.getKey()).satisfies(actual -> assertThat(com.company.common.support.test.LoginEraContract.withoutLogin((com.fasterxml.jackson.databind.JsonNode) actual)).isEqualTo(com.company.common.support.test.LoginEraContract.withoutLogin(field.getValue())))));
        for (String path : List.of("/api/common/developers", "/api/common/projects", "/api/common/assignments")) {
            assertThat(effective.path(path).has("get")).as(path).isTrue();
        }
    }

    @Test
    void manualSchemasAndNullableWithdrawalVersionRemainIntact() {
        var manual = yaml("contracts/openapi-manual.yaml").path("components").path("schemas");
        var effective = yaml("contracts/openapi.yaml").path("components").path("schemas");
        assertThat(manual.size()).isPositive();
        manual.fields().forEachRemaining(schema -> assertThat(effective.path(schema.getKey()))
            .as(schema.getKey()).isEqualTo(schema.getValue()));
        assertThat(effective.path("CommonWithdrawalExtensionResult").path("properties")
            .path("modifiedAt").path("nullable").asBoolean()).isTrue();
    }

    @Test
    void newSchemasKeepTheResourceSpecificNamespace() {
        var schemas = yaml("contracts/openapi.yaml").path("components").path("schemas");
        for (String name : List.of("Row", "Summary", "FormulaHelp", "Result")) {
            assertThat(schemas.path("CommonRateRenegotiation" + name).isObject()).as(name).isTrue();
        }
        assertThat(schemas.path("CommonRateRenegotiationRow").path("properties").has("monthlyCost")).isTrue();
        assertThat(schemas.path("CommonRateRenegotiationSummary").path("properties")
            .path("averageProfitRate").path("nullable").asBoolean()).isTrue();
    }

    @Test
    void automaticMigrationAndSqlInitializationStayDisabled() {
        var factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        var properties = factory.getObject();
        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }

    private JsonNode yaml(String name) {
        var factory = new YamlMapFactoryBean();
        factory.setResources(new ClassPathResource(name));
        assertThat(factory.getObject()).as(name).isNotNull();
        return new ObjectMapper().valueToTree(factory.getObject());
    }
}
