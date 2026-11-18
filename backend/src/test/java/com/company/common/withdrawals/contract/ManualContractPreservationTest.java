package com.company.common.withdrawals.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class ManualContractPreservationTest {
    private JsonNode resource(String name) {
        var yaml = new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource(name));
        return new ObjectMapper().valueToTree(yaml.getObject());
    }

    @Test void manualContractBytesRemainIdenticalToApprovedBaseline() throws Exception {
        try (var input = new ClassPathResource("contracts/openapi-manual.yaml").getInputStream()) {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(input.readAllBytes()));
            assertThat(digest).isEqualTo("f41c5f7f85cb37951ada97821060478e4f7265c51b436db1a82d42cab0d2e7d9");
        }
    }


    @Test void existingManualSchemaFieldsAreStillPreservedAfterAddingWithdrawalSchemas() {
        JsonNode manual = resource("contracts/openapi-manual.yaml").path("components").path("schemas");
        JsonNode effective = resource("contracts/openapi.yaml").path("components").path("schemas");
        assertThat(manual.isObject()).isTrue();
        var schemas = manual.fields();
        while (schemas.hasNext()) {
            var schema = schemas.next();
            var other = effective.path(schema.getKey());
            assertThat(other.isObject()).as(schema.getKey()).isTrue();
            schema.getValue().path("properties").fieldNames().forEachRemaining(name ->
                assertThat(other.path("properties").has(name)).as(schema.getKey() + "." + name).isTrue());
        }
        assertThat(effective.isObject()).isTrue();
    }
}
