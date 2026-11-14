package com.company.common.support.controller;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 서버 상태 확인과 모든 API 공통 오류 응답(2026-11-12 사용자 결정). */
@WebMvcTest(HealthController.class)
@ContextConfiguration(classes = {HealthController.class, ApiExceptionHandler.class})
class HealthControllerTest {
    @Autowired MockMvc mvc;
    @MockBean DataSource dataSource;

    private void database(boolean up) throws SQLException {
        if (!up) {
            when(dataSource.getConnection()).thenThrow(new SQLException("private host and password details"));
            return;
        }
        Connection connection = mock(Connection.class);
        when(connection.isValid(anyInt())).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(connection);
    }

    @Test
    void healthyServerAnswers200WithTheCommonEnvelope() throws Exception {
        database(true);
        String contract = new ClassPathResource("contracts/openapi.yaml").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(contract).contains("/api/health:", "operationId: getHealth");
        mvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(content().json("{\"success\":true,\"meta\":{}}", true));
    }

    @Test
    void databaseDownAnswers503WithoutLeakingDetails() throws Exception {
        database(false);
        String body = mvc.perform(get("/api/health"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("잠시 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("private", "password", "host");
    }

    @Test
    void livenessDoesNotReflectCallerInput() throws Exception {
        database(true);
        mvc.perform(get("/api/health").queryParam("requestId", "private-value").header("X-Request-Id", "private-value"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("X-Request-Id"))
            .andExpect(content().json("{\"success\":true,\"meta\":{}}", true));
    }

    @Test
    void unknownAddressIs404InTheCommonErrorFormat() throws Exception {
        mvc.perform(get("/api/health/unknown"))
            .andExpect(status().isNotFound())
            .andExpect(content().json(
                "{\"success\":false,\"meta\":{},\"message\":\"요청한 주소를 찾을 수 없습니다.\",\"errors\":[]}", true));
    }

    @Test
    void wrongMethodIs405InTheCommonErrorFormat() throws Exception {
        mvc.perform(post("/api/health"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value("이 주소에서 쓸 수 없는 요청 방식입니다."));
    }
}
