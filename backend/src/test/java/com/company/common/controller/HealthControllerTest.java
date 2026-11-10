package com.company.common.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HealthController.class)
@ContextConfiguration(classes = HealthController.class)
class HealthControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void anonymousLivenessUsesApprovedEnvelopeWithoutDatabase() throws Exception {
        String contract = new ClassPathResource("contracts/openapi.yaml").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(contract).contains("/api/health:", "operationId: getHealth");
        mvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(content().json("{\"success\":true,\"meta\":{}}", true));
    }

    @Test
    void livenessDoesNotInventQueryValidationOrReflectCallerInput() throws Exception {
        mvc.perform(get("/api/health").queryParam("requestId", "private-value").header("X-Request-Id", "private-value"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("X-Request-Id"))
            .andExpect(content().json("{\"success\":true,\"meta\":{}}", true));
    }

    @Test
    void unknownRouteIsNotLiveness() throws Exception {
        mvc.perform(get("/api/health/unknown")).andExpect(status().isNotFound());
    }
}
