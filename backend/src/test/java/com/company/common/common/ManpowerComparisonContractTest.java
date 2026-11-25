package com.company.common.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class ManpowerComparisonContractTest {
    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace");
    private static final String COMPARISON = "/api/common/manpower-comparisons";

    private JsonNode resource(String name) {
        var yaml = new YamlMapFactoryBean();
        yaml.setResources(new ClassPathResource(name));
        assertThat(yaml.getObject()).as(name).isNotNull();
        return new ObjectMapper().valueToTree(yaml.getObject());
    }

    // Check the merged artifact itself; rebuilding overlays here would hide integration drift.
    private ObjectNode effective() {
        return resource("contracts/openapi.yaml").deepCopy();
    }

    @Test void manualBytesRemainTheExistingHumanApprovedBytes() throws Exception {
        try (var input = new ClassPathResource("contracts/openapi-manual.yaml").getInputStream()) {
            assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes())))
                .isEqualTo("269faaf30abc1ff35733a3663947a6d7f802221d3ac0367c86ce08eef2ce1eb7"); // 2026-11-18 UI-11 엑셀 경로 추가, 2026-11-19 UI-31 이슈 엑셀 경로·프로젝트 신호등 이유 칸 추가, 2026-11-19 UI-13 청구 목록 엑셀 경로 추가, 2026-11-24 UI-29 저장 전 줄 가배정 점검 경로·카드 직무 인원 칸 추가, 2026-11-24 UI-29 매출 예측 기회별 줄 추가, 2026-11-24 UI-29 상세 저장 이익률·승인 기준·종료 탭 건수 칸 추가, 2026-11-24 UI-28 예측 시작 달 경로·보드 칸 상태 키·관리번호 스키마 추가, 2026-11-25 프로젝트·영업 기회 근무지 지역(workAreaCode·workAreaName) 칸 추가(LF 기준)
        }
    }

    @Test void effectiveOverlayPreservesEveryManualMethodParameterBodyAndResponse() {
        var manual = resource("contracts/openapi-manual.yaml");
        var effective = effective();
        assertThat(manual.path("paths").size()).isPositive();
        manual.path("paths").fields().forEachRemaining(path ->
            path.getValue().fields().forEachRemaining(method -> {
                if (!METHODS.contains(method.getKey())) return;
                assertThat(effective.path("paths").path(path.getKey()).path(method.getKey()))
                    .as(method.getKey() + " " + path.getKey()).isEqualTo(method.getValue());
            }));
    }

    @Test void sameNameSchemaUsesTheEntireManualDefinitionNotOnlyItsPropertyNames() {
        var manual = resource("contracts/openapi-manual.yaml").path("components").path("schemas");
        var schemas = effective().path("components").path("schemas");
        assertThat(manual.size()).isPositive();
        manual.fields().forEachRemaining(schema ->
            assertThat(schemas.path(schema.getKey())).as(schema.getKey()).isEqualTo(schema.getValue()));
    }

    @Test void assignmentSchemasAreDeclaredExactlyOnceInTheMergedArtifact() throws Exception {
        try (var input = new ClassPathResource("contracts/openapi.yaml").getInputStream()) {
            var lines = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
            for (String schema : List.of("CommonAssignment", "CommonAssignmentList",
                "CommonClientAssignment", "CommonClientAssignmentList")) {
                assertThat(lines.stream().filter(line -> line.equals("    " + schema + ":")).count())
                    .as("single merged schema declaration for " + schema).isEqualTo(1L);
            }
        }
    }

    @Test void baselineClientAssignmentsAndAssignmentsKeepDistinctSchemas() {
        var schemas = resource("contracts/openapi.yaml").path("components").path("schemas");
        assertThat(schemas.path("CommonClientAssignmentList").path("properties").path("items")
            .path("items").path("$ref").asText()).isEqualTo("#/components/schemas/CommonClientAssignment");
        assertThat(schemas.path("CommonClientAssignment").path("properties").has("personName")).isTrue();
        assertThat(schemas.path("CommonClientAssignment").path("properties").has("withdrawalDate")).isTrue();
        assertThat(schemas.path("CommonAssignmentList").path("properties").path("items")
            .path("items").path("$ref").asText()).isEqualTo("#/components/schemas/CommonAssignment");
        assertThat(schemas.path("CommonAssignment").path("properties").has("assignmentId")).isTrue();
        assertThat(schemas.path("CommonAssignment").path("properties").has("monthlyMm")).isTrue();
    }

    @Test void comparisonOverlayMatchesIndependentDurableOperationAndSchemas() {
        var comparison = resource("contracts/manpower-comparisons-openapi.yaml");
        var contract = effective();
        var operation = contract.path("paths").path(COMPARISON).path("get");
        comparison.path("paths").path(COMPARISON).path("get").fields().forEachRemaining(field ->
            assertThat(operation.path(field.getKey())).as("comparison GET " + field.getKey())
                .isEqualTo(field.getValue()));
        comparison.path("components").path("schemas").fields().forEachRemaining(schema ->
            assertThat(contract.path("components").path("schemas").path(schema.getKey()))
                .as(schema.getKey()).isEqualTo(schema.getValue()));
    }

    @Test void manualPathLevelParametersAndMetadataArePreservedWithOperations() {
        var manual = resource("contracts/openapi-manual.yaml").path("paths");
        var paths = effective().path("paths");
        assertThat(manual.size()).isPositive();
        manual.fields().forEachRemaining(path ->
            path.getValue().fields().forEachRemaining(field ->
                assertThat(paths.path(path.getKey()).path(field.getKey()))
                    .as(path.getKey() + " " + field.getKey()).isEqualTo(field.getValue())));
    }

    @Test void tentativeAssignmentRetainsBothPartsOfTheSelectedNeedIdentity() {
        var contract = effective();
        var operation = contract.path("paths")
            .path("/api/common/withdrawals/{assignmentId}/tentative-assignment").path("put");
        assertThat(operation.path("operationId").asText()).isEqualTo("putCommonWithdrawalTentativeAssignment");
        assertThat(operation.path("requestBody").path("content").path("application/json")
            .path("schema").path("$ref").asText())
            .isEqualTo("#/components/schemas/CommonWithdrawalTentativeInput");
        var input = contract.path("components").path("schemas").path("CommonWithdrawalTentativeInput");
        assertThat(input).isEqualTo(resource("contracts/openapi-manual.yaml")
            .path("components").path("schemas").path("CommonWithdrawalTentativeInput"));
        assertThat(input.path("required"))
            .isEqualTo(new ObjectMapper().valueToTree(List.of("opportunityId", "needSn")));
        assertThat(input.path("properties").path("opportunityId").path("type").asText()).isEqualTo("string");
        assertThat(input.path("properties").path("needSn").path("type").asText()).isEqualTo("integer");
        assertThat(input.path("additionalProperties").asBoolean(true)).isFalse();
    }

    @Test void effectiveTestContractHasNoAuthenticationDefinitionsOrGlobalSecurity() {
        var contract = effective();
        assertThat(contract.has("security")).isFalse();
        assertThat(contract.path("components").has("securitySchemes")).isFalse();
        assertThat(contract.findValues("bearerAuth")).isEmpty();
        contract.path("paths").fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
            if (!METHODS.contains(method.getKey())) return;
            JsonNode operation = method.getValue();
            assertThat(operation.has("x-roles")).as(path.getKey()).isFalse();
            if (operation.has("security")) {
                assertThat(operation.path("security").isArray()).isTrue();
                assertThat(operation.path("security")).isEmpty();
            }
        }));
        assertThat(contract.path("paths").path(COMPARISON).path("get").path("security").isArray()).isTrue();
    }

    @Test void comparisonIsGetOnlyWithTheApprovedYearAndOptionalFilters() {
        var path = effective().path("paths").path(COMPARISON);
        assertThat(path.size()).isEqualTo(1);
        var get = path.path("get");
        assertThat(get.path("operationId").asText()).isEqualTo("getCommonManpowerComparisons");
        assertThat(get.has("requestBody")).isFalse();
        var parameters = get.path("parameters");
        assertThat(parameters.size()).isEqualTo(3);
        var year = parameters.get(0);
        assertThat(year.path("name").asText()).isEqualTo("year");
        assertThat(year.path("in").asText()).isEqualTo("query");
        assertThat(year.path("required").asBoolean()).isTrue();
        assertThat(year.path("schema").path("type").asText()).isEqualTo("integer");
        assertThat(year.path("schema").path("minimum").asInt()).isEqualTo(2025);
        assertThat(year.path("schema").path("maximum").asInt()).isEqualTo(2027);
        assertThat(parameters.get(1).path("name").asText()).isEqualTo("projectId");
        assertThat(parameters.get(1).path("required").asBoolean()).isFalse();
        assertThat(parameters.get(2).path("name").asText()).isEqualTo("differenceStatus");
        assertThat(parameters.get(2).path("required").asBoolean()).isFalse();
        assertThat(parameters.get(2).path("schema").path("enum"))
            .isEqualTo(new ObjectMapper().valueToTree(List.of("부족", "초과", "맞음")));
        assertThat(effective().path("paths").path("/api/common/projects").path("get").path("parameters")).isEmpty();
    }

    @Test void successEnvelopeResultRowsTotalsAndErrorReferencesResolve() {
        var contract = effective();
        var responses = contract.path("paths").path(COMPARISON).path("get").path("responses");
        var success = responses.path("200").path("content").path("application/json").path("schema");
        assertThat(success.path("allOf").get(0).path("$ref").asText()).isEqualTo("#/components/schemas/ApiResponse");
        assertThat(success.path("allOf").get(1).path("properties").path("data").path("$ref").asText())
            .isEqualTo("#/components/schemas/CommonManpowerComparisonResult");
        assertThat(responses.path("400").path("content").path("application/json").path("schema").path("$ref").asText())
            .isEqualTo("#/components/schemas/ApiError");
        var schemas = contract.path("components").path("schemas");
        var result = schemas.path("CommonManpowerComparisonResult");
        assertThat(result.path("properties").path("items").path("items").path("$ref").asText())
            .isEqualTo("#/components/schemas/CommonManpowerComparisonRow");
        assertThat(result.path("properties").path("totals").path("$ref").asText())
            .isEqualTo("#/components/schemas/CommonManpowerComparisonTotals");
        var row = schemas.path("CommonManpowerComparisonRow");
        assertThat(row.path("required").size()).isEqualTo(8);
        assertThat(row.path("properties").path("differenceStatus").path("enum"))
            .isEqualTo(new ObjectMapper().valueToTree(List.of("부족", "초과", "맞음")));
        for (String field : List.of("plannedMm", "actualMm", "differenceMm")) {
            assertThat(row.path("properties").path(field).path("type").asText()).isEqualTo("number");
            assertThat(schemas.path("CommonManpowerComparisonTotals").path("properties").path(field).path("type").asText())
                .isEqualTo("number");
        }
        resource("contracts/manpower-comparisons-openapi.yaml").findValues("$ref").forEach(ref -> {
            assertThat(ref.asText()).startsWith("#/components/schemas/");
            assertThat(schemas.has(ref.asText().substring("#/components/schemas/".length()))).as(ref.asText()).isTrue();
        });
    }
}
