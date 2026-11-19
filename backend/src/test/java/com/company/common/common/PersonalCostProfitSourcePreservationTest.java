package com.company.common.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class PersonalCostProfitSourcePreservationTest {
    @Test
    void manualOperationsAndPathFieldsRemainStructurallyIdentical() {
        var manual = yaml("contracts/openapi-manual.yaml").path("paths");
        var effective = yaml("contracts/openapi.yaml").path("paths");
        assertThat(manual.size()).isPositive();
        manual.fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(field ->
            assertThat(effective.path(path.getKey()).path(field.getKey()))
                .as(path.getKey() + " " + field.getKey()).isEqualTo(field.getValue())));
    }

    @Test
    void manualSchemasIncludingNullableWithdrawalTimestampRemainIdentical() {
        var manual = yaml("contracts/openapi-manual.yaml").path("components").path("schemas");
        var effective = yaml("contracts/openapi.yaml").path("components").path("schemas");
        assertThat(manual.size()).isPositive();
        manual.fields().forEachRemaining(schema -> assertThat(effective.path(schema.getKey()))
            .as(schema.getKey()).isEqualTo(schema.getValue()));
        assertThat(effective.path("CommonWithdrawalExtensionResult").path("properties")
            .path("modifiedAt").path("nullable").asBoolean()).isTrue();
    }

    @Test
    void effectiveContractDoesNotEnableAuthenticationBeforeLoginDelivery() {
        var contract = yaml("contracts/openapi.yaml");
        assertThat(contract.has("security")).isFalse();
        assertThat(contract.path("components").has("securitySchemes")).isFalse();
        assertThat(contract.findValues("bearerAuth")).isEmpty();
    }

    @Test
    void automaticSchemaInitializationRemainsDisabled() {
        var factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        var properties = factory.getObject();
        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }

    @Test
    void packagedCostSnapshotRetainsOriginalCompositeIdentityAndMoneyTypes() throws Exception {
        var sql = resource("db/migration/V12__project_operations_schema.sql");
        assertThat(sql).contains("CONSTRAINT PK_TB_CO_MON_COST PRIMARY KEY (COST_YM, DEV_ID, PROJ_ID, ASGN_ID)",
            "COST_YM CHAR(6) NOT NULL", "ASGN_MM NUMERIC(6,2) NOT NULL",
            "TEAM_EXPS_AMT NUMERIC(15) NOT NULL", "PROJ_EXPS_AMT NUMERIC(15) NOT NULL");
    }

    @Test
    void existingPackagedDefinitionsCoverTheCommonPrincipleAndEveryRequestedFormula() throws Exception {
        var seed = resource("db/migration/V8__calculation_rule_seed.sql");
        for (var id : List.of("00", "01", "02", "07", "08", "09", "10", "11", "12", "13",
                "14", "15", "16", "17", "19", "20", "21", "23", "24", "25", "27", "30",
                "34", "35", "37", "38", "39", "40", "53", "55", "57", "66", "95", "96")) {
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
