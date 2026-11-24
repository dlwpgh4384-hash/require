package com.company.common.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.assertj.core.api.Assertions.*;

class CashFlowStatementBoundaryTest {
    private JsonNode yaml(String name) {
        var factory = new YamlMapFactoryBean();
        factory.setResources(new ClassPathResource(name));
        var values = factory.getObject();
        assertThat(values).as(name).isNotNull();
        return new ObjectMapper().valueToTree(values);
    }

    @Test void cashContractHasOnlyTheTwoApprovedAnonymousGetOperations() {
        var contract = yaml("contracts/openapi.yaml");
        assertThat(contract.has("security")).isFalse();
        assertThat(contract.path("components").has("securitySchemes")).isFalse();
        assertThat(contract.findValues("bearerAuth")).isEmpty();
        var paths = contract.path("paths");
        for (String suffix : List.of("", "/excel")) {
            var path = paths.path("/api/common/cash-flow-statements" + suffix);
            assertThat(path.isObject()).isTrue();
            assertThat(path.size()).isEqualTo(1);
            var operation = path.path("get");
            assertThat(operation.path("operationId").asText()).isEqualTo(suffix.isEmpty()
                ? "getCommonCashFlowStatements" : "getCommonCashFlowStatementsExcel");
            assertThat(operation.path("security").isArray()).isTrue();
            assertThat(operation.path("security").isEmpty()).isTrue();
            assertThat(operation.has("x-roles")).isFalse();
            assertThat(operation.path("responses").has("401")).isFalse();
            assertThat(operation.path("responses").has("403")).isFalse();
            var names = new java.util.ArrayList<String>();
            operation.path("parameters").forEach(parameter -> names.add(parameter.path("name").asText()));
            assertThat(names).containsExactlyElementsOf(suffix.isEmpty()
                ? List.of("year") : List.of("year", "amountUnit"));
        }
    }

    @Test void baselineManualPathsAndSchemasRemainStructurallyUnchanged() {
        var manual = yaml("contracts/openapi-manual.yaml");
        var effective = yaml("contracts/openapi.yaml");
        assertThat(manual.path("paths").size()).isPositive();
        manual.path("paths").fields().forEachRemaining(path ->
            path.getValue().fields().forEachRemaining(method ->
                assertThat(effective.path("paths").path(path.getKey()).path(method.getKey()))
                    .as(path.getKey() + " " + method.getKey()).isEqualTo(method.getValue())));
        manual.path("components").path("schemas").fields().forEachRemaining(schema ->
            assertThat(effective.path("components").path("schemas").path(schema.getKey()))
                .as(schema.getKey()).isEqualTo(schema.getValue()));
    }

    @Test void numericCellsAreRawNullableAmountsRatherThanNetProfitCellObjects() {
        var schemas = yaml("contracts/openapi.yaml").path("components").path("schemas");
        for (String name : List.of("CommonCashFlowWarning", "CommonCashFlowMonth", "CommonCashFlowNumericRow",
            "CommonCashFlowNumericTable", "CommonCashFlowStatementResult")) {
            assertThat(schemas.path(name).isObject()).as(name).isTrue();
        }
        var values = schemas.path("CommonCashFlowNumericRow").path("properties").path("values");
        assertThat(values.path("minItems").asInt()).isEqualTo(15);
        assertThat(values.path("maxItems").asInt()).isEqualTo(15);
        assertThat(values.path("items").path("type").asText()).isEqualTo("number");
        assertThat(values.path("items").path("nullable").asBoolean()).isTrue();
        var columns = schemas.path("CommonCashFlowNumericTable").path("properties").path("columns");
        assertThat(columns.path("minItems").asInt()).isEqualTo(15);
        assertThat(columns.path("maxItems").asInt()).isEqualTo(15);
        assertThat(columns.path("items").path("properties").has("heading")).isFalse();
        assertThat(columns.path("items").path("properties").has("label")).isTrue();
        assertThat(columns.path("items").path("properties").has("actual")).isTrue();
        assertThat(columns.path("items").path("properties").has("warning")).isTrue();
    }

    @Test void noCashMigrationIsAddedToTheTwentyPackagedResources() throws Exception {
        var resources = new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/V*__*.sql");
        // V19 is the month close history table (UI-14), not a cash flow migration.
        assertThat(Arrays.stream(resources).map(resource -> resource.getFilename()).toList())
            .hasSize(20).contains("V19__month_close_schema.sql", "V20__candidate_schema.sql").doesNotContain("V19__cash_flow_schema.sql", "V20__cash_flow_schema.sql",
                "V21__cash_flow_schema.sql");
    }

    @Test void ordinaryStartupKeepsFlywayDisabledAndDoesNotInitializeSql() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var properties = yaml.getObject();
        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }
}
