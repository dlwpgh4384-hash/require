package com.company.common.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

// 로그인(UI-22) 작업 뒤: 손 설명서는 그대로, 로그인 정의(security·x-roles·401·403)는 openapi.yaml에만 있어 그 차이는 빼고 비교한다.
class FinanceSourcePreservationTest {
    @Test
    void financeOperationsRemainPublicDevelopmentOperations() {
        var contract = yaml("contracts/openapi.yaml");
        com.company.common.support.test.LoginEraContract.assertSessionLogin(contract);
        assertThat(contract.findValues("bearerAuth")).isEmpty();
        var paths = contract.path("paths");
        for (String path : List.of(
                "/api/common/finance-items/{yearMonth}",
                "/api/common/finance-items/{yearMonth}/previous-items")) {
            assertPublic(paths.path(path).path("get"), "GET " + path);
        }
        assertPublic(paths.path("/api/common/finance-items/{yearMonth}").path("put"), "PUT month");
        for (String path : List.of(
                "/api/common/finance-items/tax-years/{taxYear}",
                "/api/common/finance-items/tax-payments/{paymentId}/confirm")) {
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
                .as(path.getKey() + " " + method.getKey()).satisfies(actual -> assertThat(com.company.common.support.test.LoginEraContract.withoutLogin((com.fasterxml.jackson.databind.JsonNode) actual)).isEqualTo(com.company.common.support.test.LoginEraContract.withoutLogin(method.getValue())))));
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
        com.company.common.support.test.LoginEraContract.assertLoggedIn(operation, path);
    }

    private JsonNode yaml(String name) {
        var factory = new YamlMapFactoryBean();
        factory.setResources(new ClassPathResource(name));
        assertThat(factory.getObject()).as(name).isNotNull();
        return new ObjectMapper().valueToTree(factory.getObject());
    }
}
