package com.company.common.manpowerforecasts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

// 로그인(UI-22) 작업 뒤: 손 설명서는 그대로, 로그인 정의(security·x-roles·401·403)는 openapi.yaml에만 있어 그 차이는 빼고 비교한다.
class ManpowerForecastContractTest {
    JsonNode contract() {
        var yaml = new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource("contracts/openapi.yaml"));
        return new ObjectMapper().valueToTree(yaml.getObject());
    }
    @Test void bothApprovedOperationsArePublicRequiredYearGetsWithJsonErrors() {
        var paths = contract().path("paths");
        for (String suffix : List.of("", "/excel")) {
            var operation = paths.path("/api/common/manpower-forecasts" + suffix).path("get");
            assertThat(operation.path("operationId").asText()).isEqualTo(suffix.isEmpty()
                ? "getCommonManpowerForecasts" : "getCommonManpowerForecastsExcel");
            com.company.common.support.test.LoginEraContract.assertLoggedIn(operation, suffix);
            var year = operation.path("parameters").findValues("name");
            assertThat(year).extracting(JsonNode::asText).contains("year");
            var parameters = operation.path("parameters");
            boolean requiredInteger = false;
            for (var parameter : parameters) if (parameter.path("name").asText().equals("year"))
                requiredInteger = parameter.path("required").asBoolean() && parameter.path("schema").path("type").asText().equals("integer");
            assertThat(requiredInteger).isTrue();
            assertThat(operation.path("responses").path("400").path("content").path("application/json").path("schema").path("$ref").asText())
                .isEqualTo("#/components/schemas/ApiError");
        }
    }
    @Test void forecastSchemasAndBinaryDownloadCountAreRetained() throws Exception {
        var document = contract();
        for (String name : List.of("Month", "JobShortage", "Summary", "Thresholds", "Result"))
            assertThat(document.path("components").path("schemas").path("CommonManpowerForecast" + name).isObject()).isTrue();
        var response = document.path("paths").path("/api/common/manpower-forecasts/excel").path("get").path("responses").path("200");
        assertThat(response.path("content").path("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet").path("schema").path("format").asText()).isEqualTo("binary");
        assertThat(response.path("headers").has("X-Total-Count")).isTrue();
        try (var input = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            assertThat(input.readAllBytes().length).isLessThanOrEqualTo(1000000);
        }
    }
}
