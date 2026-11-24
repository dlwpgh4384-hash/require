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
            // 2026-11-18 인원 계획 대비 투입 엑셀(GET /api/common/manpower-comparisons/excel) 경로를 더해 다시 맞춤(LF 기준).
            // 2026-11-19 프로젝트 현황판 이슈 모아 보기 엑셀(GET /api/common/project-issues/excel) 경로와 프로젝트 상세 신호등 이유 칸(CommonProject)을 더해 다시 맞춤(LF 기준).
            // 2026-11-19 청구 목록 엑셀(GET /api/common/bills/excel) 경로를 더해 다시 맞춤(LF 기준).
            // 2026-11-24 영업 파이프라인(UI-29) 저장 전 줄 가배정 점검(POST /api/common/sales-opportunities/tentative-assignment-check) 경로와
            //   카드 직무 인원(CommonSalesOpportunityCard.requiredJobHeadcounts)·경고 needSn null 칸을 더해 다시 맞춤(LF 기준).
            // 2026-11-24 영업 파이프라인(UI-29) 매출 예측의 기회별 줄(CommonSalesForecast.opportunityRevenues·CommonSalesForecastOpportunity·
            //   CommonSalesForecastOpportunityMonth)을 더해 다시 맞춤(LF 기준).
            // 2026-11-24 영업 파이프라인(UI-29) 상세의 저장된 이익률·승인 이익률·승인 기준(CommonSalesOpportunity.expectedProfitRate·
            //   approvedProfitRate·approvalRate)과 종료 탭 건수(CommonSalesPipelineSummary.closedCount)를 더하며 두 스키마 전체를 manual에 옮겨 다시 맞춤(LF 기준).
            // 2026-11-24 인력 운영 보드(UI-28): 예측 시작 달(GET /api/common/sales-pipeline/forecast startMonth) 경로와
            //   칸 상태 키·관리번호(CommonManpowerBoardCell.statusCode·CommonManpowerBoardPerson.managementNumber) 스키마를 manual에 옮겨 다시 맞춤(LF 기준).
            assertThat(digest).isEqualTo("feba39a639286aa1b38c37ebacf83e76c636a242bcc96adbaa2da4c84ff8aafc");
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
