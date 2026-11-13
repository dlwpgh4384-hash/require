package com.company.common.controller;

import com.company.common.service.CodeFailure;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ExceptionHandler;
import static org.assertj.core.api.Assertions.*;

class CodeExceptionHandlerTest {
    @Test void codeFailureUsesExisting400FieldErrorEnvelope() throws Exception {
        var response = new CodeExceptionHandler().invalid(CodeFailure.invalid("excelText", "표기를 확인해 주세요."));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        var json = new ObjectMapper().valueToTree(response.getBody());
        assertThat(json.get("success").asBoolean()).isFalse();
        assertThat(json.get("meta").size()).isZero();
        assertThat(json.get("message").asText()).isEqualTo("표기를 확인해 주세요.");
        assertThat(json.get("errors").get(0).get("field").asText()).isEqualTo("excelText");
        assertThat(json.get("errors").get(0).get("message").asText()).isEqualTo("표기를 확인해 주세요.");
    }
    @Test void handlerOnlyCatchesItsOwnFailureType() throws Exception {
        var method = CodeExceptionHandler.class.getMethod("invalid", CodeFailure.class);
        assertThat(method.getAnnotation(ExceptionHandler.class).value()).containsExactly(CodeFailure.class);
    }
}
