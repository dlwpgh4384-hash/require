package com.company.common.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AssignableTypeFilter;
import static org.assertj.core.api.Assertions.*;

class PartSettlementBoundaryPreservationTest {
    @Test
    void closeCheckAndConfirmationServicesCanBeScannedTogether() {
        var checks = com.company.common.commonops.service.PartSettlementCloseService.class;
        var confirmation = com.company.common.partsettlements.service.PartSettlementAutoConfirmService.class;
        var registry = new DefaultListableBeanFactory();
        var scanner = new ClassPathBeanDefinitionScanner(registry, false);
        scanner.addIncludeFilter(new AssignableTypeFilter(checks));
        scanner.addIncludeFilter(new AssignableTypeFilter(confirmation));

        scanner.scan(checks.getPackageName(), confirmation.getPackageName());

        assertThat(registry.getBeanDefinition("partSettlementCloseService").getBeanClassName())
            .isEqualTo(checks.getName());
        assertThat(registry.getBeanDefinition("partSettlementConfirmationService").getBeanClassName())
            .isEqualTo(confirmation.getName());
    }

    @Test
    void scopedReadsAndCommandsRemainPublicWithoutFutureRoleEnforcement() {
        var contract = yaml("contracts/openapi.yaml");
        assertThat(contract.has("security")).isFalse();
        assertThat(contract.path("components").has("securitySchemes")).isFalse();
        assertThat(contract.findValues("bearerAuth")).isEmpty();
        for (String path : List.of(
                "/api/common/part-settlements",
                "/api/common/part-settlements/{partId}/{yearMonth}",
                "/api/common/part-settlements/{partId}/{yearMonth}/trends",
                "/api/common/part-settlements/excel",
                "/api/common/part-settlement-close-checks")) {
            assertPublic(contract.path("paths").path(path).path("get"), path);
        }
        for (String path : List.of(
                "/api/common/part-settlements/{partId}/{yearMonth}/card",
                "/api/common/part-settlements/{partId}/{yearMonth}/confirm",
                "/api/common/part-settlements/{partId}/{yearMonth}/cancel",
                "/api/common/part-settlements/close-confirm")) {
            assertPublic(contract.path("paths").path(path).path("put"), path);
        }
    }

    @Test
    void manualMethodsResponsesAndAdditionalExistingGetsRemainIntact() {
        var manual = yaml("contracts/openapi-manual.yaml").path("paths");
        var effective = yaml("contracts/openapi.yaml").path("paths");
        assertThat(manual.size()).isPositive();
        manual.fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(field ->
            assertThat(effective.path(path.getKey()).path(field.getKey()))
                .as(path.getKey() + " " + field.getKey()).isEqualTo(field.getValue())));
        for (String path : List.of("/api/common/developers", "/api/common/projects", "/api/common/assignments")) {
            assertThat(effective.path(path).has("get")).as(path).isTrue();
        }
    }

    @Test
    void existingManualSchemasAndNullableWithdrawalVersionArePreserved() {
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
