package com.company.common.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

// 로그인(UI-22) 작업 뒤: 손 설명서는 그대로, 로그인 정의(security·x-roles·401·403)는 openapi.yaml에만 있어 그 차이는 빼고 비교한다.
class ProjectCostAnalysisPreservationTest {
    private static final String ROOT = "/api/common/project-cost-analyses";
    private static final Map<String, String> OPERATIONS = Map.of(
        ROOT, "getCommonProjectCostAnalyses",
        ROOT + "/excel", "getCommonProjectCostAnalysesExcel",
        ROOT + "/{projectId}/monthly-costs", "getCommonProjectCostAnalysisMonthlyCosts",
        ROOT + "/{projectId}/workforce", "getCommonProjectCostAnalysisWorkforce",
        ROOT + "/{projectId}/simulation", "getCommonProjectCostAnalysisSimulation",
        ROOT + "/{projectId}/causes", "getCommonProjectCostAnalysisCauses");

    @Test
    void approvedOperationsRemainPublicGetsWithoutBusinessMutationMethods() {
        var contract = yaml("contracts/openapi.yaml");
        var actual = new LinkedHashMap<String, String>();
        contract.path("paths").fields().forEachRemaining(path -> {
            if (!path.getKey().startsWith(ROOT)) return;
            for (String method : List.of("post", "put", "patch", "delete")) {
                assertThat(path.getValue().has(method)).as(path.getKey() + " " + method).isFalse();
            }
            var get = path.getValue().path("get");
            assertThat(get.isObject()).as(path.getKey()).isTrue();
            com.company.common.support.test.LoginEraContract.assertLoggedIn(get, path.getKey());
            assertThat(get.path("responses").has("401")).isTrue();
            assertThat(get.path("responses").has("403")).isTrue();
            actual.put(path.getKey(), get.path("operationId").asText());
        });
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(OPERATIONS);
        com.company.common.support.test.LoginEraContract.assertSessionLogin(contract);
        assertThat(contract.findValues("bearerAuth")).isEmpty();
    }

    @Test
    void manualPathMethodsAndResponsesArePreservedWithoutRemovingAdditionalGets() {
        var manual = yaml("contracts/openapi-manual.yaml").path("paths");
        var effective = yaml("contracts/openapi.yaml").path("paths");
        assertThat(manual.size()).isPositive();
        manual.fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(field ->
            assertThat(effective.path(path.getKey()).path(field.getKey()))
                .as(path.getKey() + " " + field.getKey()).satisfies(actual -> assertThat(com.company.common.support.test.LoginEraContract.withoutLogin((com.fasterxml.jackson.databind.JsonNode) actual)).isEqualTo(com.company.common.support.test.LoginEraContract.withoutLogin(field.getValue())))));
        for (String path : List.of("/api/common/developers", "/api/common/projects",
                "/api/common/assignments")) {
            assertThat(effective.path(path).has("get")).as(path).isTrue();
        }
    }

    @Test
    void nullableWithdrawalTimestampAndManualSchemasRemainUnchanged() {
        var manual = yaml("contracts/openapi-manual.yaml").path("components").path("schemas");
        var effective = yaml("contracts/openapi.yaml").path("components").path("schemas");
        assertThat(manual.size()).isPositive();
        manual.fields().forEachRemaining(schema -> assertThat(effective.path(schema.getKey()))
            .as(schema.getKey()).isEqualTo(schema.getValue()));
        assertThat(effective.path("CommonWithdrawalExtensionResult").path("properties")
            .path("modifiedAt").path("nullable").asBoolean()).isTrue();
    }

    @Test
    void ordinaryStartupStillDoesNotPrepareOrMigrateSchema() {
        var factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        var properties = factory.getObject();
        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }

    @Test
    void existingPreparationProvidesSnapshotGrainAndRequestedDefinitionIds() throws Exception {
        var schema = resource("db/migration/V12__project_operations_schema.sql");
        assertThat(schema).contains(
            "CONSTRAINT PK_TB_CO_MON_COST PRIMARY KEY (COST_YM, DEV_ID, PROJ_ID, ASGN_ID)",
            "LABOR_ALLOC_AMT NUMERIC(15) NOT NULL",
            "TEAM_EXPS_AMT NUMERIC(15) NOT NULL",
            "PROJ_EXPS_AMT NUMERIC(15) NOT NULL");
        var seed = resource("db/migration/V8__calculation_rule_seed.sql");
        for (String id : List.of("00", "02", "09", "10", "11", "13", "15", "16", "18",
                "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29",
                "31", "53", "55", "56", "57", "75", "87", "88", "90", "91", "94")) {
            assertThat(seed).as("식-" + id).contains("('식-" + id + "',");
        }
    }

    private JsonNode yaml(String name) {
        var factory = new YamlMapFactoryBean();
        factory.setResources(new ClassPathResource(name));
        assertThat(factory.getObject()).as(name).isNotNull();
        return new ObjectMapper().valueToTree(factory.getObject());
    }

    private String resource(String name) throws Exception {
        return new ClassPathResource(name).getContentAsString(StandardCharsets.UTF_8);
    }
}
