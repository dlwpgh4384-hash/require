package com.company.common.support.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로그인(UI-22) 작업 뒤의 설명서(openapi) 기준. 손으로 넣은 설명서(openapi-manual.yaml)는 로그인 전 그대로 두고(바이트 보존),
 * 로그인 정의(세션 쿠키 인증 SessionCookie, 동작별 security·x-roles, 401·403 응답)는 openapi.yaml에만 더한다.
 * 그래서 보존 시험은 로그인 정의를 뺀 나머지 구조가 같은지 본다.
 */
public final class LoginEraContract {
    private LoginEraContract() {}

    /** 동작에서 로그인 정의(security·x-roles·401·403 응답)를 뺀 사본. 동작이 아니면 그대로. */
    public static JsonNode withoutLogin(JsonNode operation) {
        if (!(operation instanceof ObjectNode object)) return operation;
        ObjectNode copy = object.deepCopy();
        copy.remove("security");
        copy.remove("x-roles");
        // 응답 묶음 자체(200·400…)를 받은 경우도 같다.
        if (copy.has("200") || copy.has("201") || copy.has("204")) {
            copy.remove("401");
            copy.remove("403");
        }
        if (copy.get("responses") instanceof ObjectNode responses) {
            responses.remove("401");
            responses.remove("403");
        }
        return copy;
    }

    /** 설명서 전체가 세션 쿠키 로그인을 쓰고, 다른 인증(bearerAuth)은 없다. */
    public static void assertSessionLogin(JsonNode contract) {
        assertThat(contract.path("components").path("securitySchemes").has("SessionCookie")).isTrue();
        assertThat(contract.path("security").toString()).isEqualTo("[{\"SessionCookie\":[]}]");
        assertThat(contract.findValues("bearerAuth")).isEmpty();
    }

    /** 이 동작은 로그인한 사용자만 부른다. */
    public static void assertLoggedIn(JsonNode operation, String label) {
        assertThat(operation.isObject()).as(label).isTrue();
        assertThat(operation.path("security").toString()).as(label).isEqualTo("[{\"SessionCookie\":[]}]");
    }
}
