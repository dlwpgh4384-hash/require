package com.company.common.session.controller;

import com.company.common.session.dto.SessionContracts.Remaining;
import com.company.common.session.service.SessionFailure;
import com.company.common.session.service.SessionPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice
@Order(0)
public class SessionResponseAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper json;

    // 시각(Instant)은 앱 기본과 같게 글자(ISO)로 둔다. 주입된 매퍼 설정이 달라도 응답 모양이 같다.
    public SessionResponseAdvice(ObjectMapper json) {
        this.json = json.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @ExceptionHandler(SessionFailure.class)
    public ResponseEntity<Map<String, Object>> failure(SessionFailure failure) {
        return ResponseEntity.status(HttpStatusCode.valueOf(failure.status()))
            .header("Cache-Control", "no-store")
            .body(Map.of("success", false, "meta", Map.of(), "message", failure.getMessage(),
                "errors", List.of()));
    }

    @Override public boolean supports(MethodParameter method,
        Class<? extends HttpMessageConverter<?>> converter) {
        return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converter);
    }

    @Override public Object beforeBodyWrite(Object body, MethodParameter method, MediaType media,
        Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request,
        ServerHttpResponse response) {
        if (!(request instanceof ServletServerHttpRequest servlet)
            || !(servlet.getServletRequest().getAttribute(SessionPolicy.RESPONSE) instanceof Remaining timing)
            || body == null) return body;
        Remaining responded = SessionPolicy.responded(servlet.getServletRequest());
        if (responded != null) timing = responded;
        setHeader(response, "X-Session-Remaining-Seconds", Long.toString(timing.remainingSeconds()));
        setHeader(response, "X-Session-Minutes", Integer.toString(timing.sessionMinutes()));
        setHeader(response, "X-Session-Server-Responded-At", timing.serverRespondedAt().toString());
        if (body instanceof com.company.common.session.dto.SessionContracts.Response) {
            body = com.company.common.session.dto.SessionContracts.Response.of(timing);
        }
        var tree = json.valueToTree(body);
        if (!(tree instanceof ObjectNode envelope) || !envelope.path("success").asBoolean()) return body;
        ObjectNode meta = envelope.get("meta") instanceof ObjectNode existing
            ? existing : envelope.putObject("meta");
        meta.put("remainingSeconds", timing.remainingSeconds());
        meta.put("sessionMinutes", timing.sessionMinutes());
        meta.put("serverRespondedAt", timing.serverRespondedAt().toString());
        return envelope;
    }

    private static void setHeader(ServerHttpResponse response, String name, String value) {
        if (response instanceof ServletServerHttpResponse servlet) {
            // Buffered headers are appended on write; replace the interceptor's servlet header directly.
            servlet.getServletResponse().setHeader(name, value);
        } else {
            response.getHeaders().set(name, value);
        }
    }
}
