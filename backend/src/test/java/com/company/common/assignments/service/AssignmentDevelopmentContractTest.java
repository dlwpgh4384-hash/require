package com.company.common.assignments.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class AssignmentDevelopmentContractTest {
    private JsonNode resource(String path) {
        var yaml=new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource(path));
        return new ObjectMapper().valueToTree(yaml.getObject());
    }

    @Test void ordinaryStartupDoesNotPrepareTheSchema() {
        var spring=resource("application.yml").path("spring");
        assertThat(spring.path("flyway").path("enabled").isBoolean()).isTrue();
        assertThat(spring.path("flyway").path("enabled").booleanValue()).isFalse();
        assertThat(spring.path("sql").path("init").path("mode").asText()).isEqualTo("never");
    }

    @Test void assignmentResponseContractRemainsPublicWithoutSecuritySchemes() {
        var contract=resource("contracts/assignments-openapi.yaml");
        assertThat(contract.path("components").has("securitySchemes")).isFalse();
        assertThat(contract.path("security").isMissingNode() || contract.path("security").isEmpty()).isTrue();
        var methods=Set.of("get","post","put","delete","patch");
        var paths=contract.path("paths").fields();
        int operations=0;
        while(paths.hasNext()) {
            var path=paths.next();
            if (!path.getKey().startsWith("/api/common/assignments")
                    && !path.getKey().startsWith("/api/common/uploads")) continue;
            var entries=path.getValue().fields();
            while(entries.hasNext()) {
                var entry=entries.next();
                if (!methods.contains(entry.getKey())) continue;
                var security=entry.getValue().path("security");
                assertThat(security.isArray()).as("%s %s security",entry.getKey(),path.getKey()).isTrue();
                assertThat(security.isEmpty()).isTrue();
                assertThat(entry.getValue().has("x-roles")).isFalse();
                operations++;
            }
        }
        assertThat(operations).isEqualTo(9);
    }
}
