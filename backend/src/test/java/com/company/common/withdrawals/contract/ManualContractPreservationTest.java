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
            // 2026-11-18 사람이 manual에 거래처 투입 내역 칸·파트 [다시 사용] 경로를 더해 기준 지문을 새로 맞춤(LF 기준).
            assertThat(digest).isEqualTo("b591783dfaafadc2921786f7e4c10f9e82d6fbc2459b1f19148d901ce8ca960a");
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
