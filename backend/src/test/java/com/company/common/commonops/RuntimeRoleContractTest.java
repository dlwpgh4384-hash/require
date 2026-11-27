package com.company.common.commonops;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;

class RuntimeRoleContractTest {
    @Test void runtimeRoleIsDeclaredAndAllowedByBothAuthenticatedOperations() throws Exception {
        var yaml = new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource("contracts/openapi.yaml"));
        var json = new ObjectMapper();
        JsonNode contract = json.valueToTree(yaml.getObject());
        JsonNode runtime;
        try (var input = new ClassPathResource("aiops.runtime-check.json").getInputStream()) {
            runtime = json.readTree(input);
        }
        String role = runtime.path("auth").path("test_role").asText();
        assertThat(role).isNotBlank();
        assertThat(strings(contract.at("/components/schemas/CommonInitialRoleCode/enum")))
            .as("runtime role must be a declared initial role code").contains(role);
        for (String name : new String[]{"identity", "first_menu"}) {
            JsonNode check = runtime.path(name);
            JsonNode operation = contract.path("paths").path(check.path("path").asText())
                .path(check.path("method").asText().toLowerCase(Locale.ROOT));
            assertThat(operation.path("operationId").asText()).isEqualTo(check.path("operation_id").asText());
            assertThat(strings(operation.path("x-roles"))).as("%s role allowance", name).contains(role);
            assertThat(operation.path("security").isEmpty()).isFalse();
        }
        Set<String> identityChecks = new HashSet<>();
        for (JsonNode check : runtime.path("identity").path("expect")) {
            if (check.path("path").asText().equals("data.roles")) {
                identityChecks.add(check.path("op").asText() + ":" + check.path("value").asText());
            }
        }
        assertThat(identityChecks).contains("type:array_of_string", "contains:$test_role");
    }

    private static Set<String> strings(JsonNode array) {
        Set<String> values = new HashSet<>();
        for (JsonNode value : array) values.add(value.asText());
        return values;
    }
}
