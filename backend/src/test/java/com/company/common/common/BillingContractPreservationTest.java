package com.company.common.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class BillingContractPreservationTest {

    @Test
    void noAuthenticationSchemeOrGlobalPolicyIsIntroduced() {
        var contract = resource("contracts/openapi.yaml");
        assertThat(contract.has("security")).isFalse();
        assertThat(contract.path("components").has("securitySchemes")).isFalse();
        assertThat(contract.findValues("bearerAuth")).isEmpty();
    }

    @Test
    void everyManualPathFieldAndExistingMethodIsPreservedStructurally() {
        var manual = resource("contracts/openapi-manual.yaml").path("paths");
        var effective = resource("contracts/openapi.yaml").path("paths");
        assertThat(manual.size()).isPositive();
        manual.fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(field ->
            assertThat(effective.path(path.getKey()).path(field.getKey()))
                .as(path.getKey() + " " + field.getKey()).isEqualTo(field.getValue())));
    }

    @Test
    void withdrawalExtensionTimestampRemainsNullable() {
        assertThat(resource("contracts/openapi.yaml").path("components").path("schemas")
            .path("CommonWithdrawalExtensionResult").path("properties").path("modifiedAt")
            .path("nullable").asBoolean()).isTrue();
    }

    @Test
    void billingDoesNotReplaceExistingManualSchemaDefinitions() {
        var manual = resource("contracts/openapi-manual.yaml").path("components").path("schemas");
        var effective = resource("contracts/openapi.yaml").path("components").path("schemas");
        assertThat(manual.size()).isPositive();
        manual.fields().forEachRemaining(schema -> assertThat(effective.path(schema.getKey()))
            .as(schema.getKey()).isEqualTo(schema.getValue()));
    }

    @Test
    void excludedBillingExportAttendanceAndConfirmationPathsAreAbsent() {
        var paths = resource("contracts/openapi.yaml").path("paths");
        // 2026-11-19 청구 목록 엑셀(SFR-011)은 사용자 결정으로 더함: 목록 도구줄 [엑셀 다운로드]
        assertThat(paths.path("/api/common/bills/excel").has("get")).isTrue();
        for (String path : List.of("/api/common/bills/attendance",
                "/api/common/bills/{billId}/confirm", "/api/common/customer-receivables")) {
            assertThat(paths.has(path)).as(path).isFalse();
        }
    }

    private JsonNode resource(String name) {
        var yaml = new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource(name));
        assertThat(yaml.getObject()).as(name).isNotNull();
        return new ObjectMapper().valueToTree(yaml.getObject());
    }
}
