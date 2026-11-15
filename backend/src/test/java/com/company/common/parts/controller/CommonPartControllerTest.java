package com.company.common.parts.controller;

import com.company.common.parts.mapper.PartMapper;
import com.company.common.parts.service.CommonPartService;
import com.company.common.parts.service.PartTestData;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.calculation.dto.CalculationRuleContracts.Standard;
import com.company.common.users.mapper.UserMapper;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.clients.service.ClientPartPolicy;
import com.company.common.support.service.CurrentBusinessActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static com.company.common.support.controller.OpenApiResponseContract.response;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonPartController.class)
@ContextConfiguration(classes = {CommonPartController.class, CommonPartService.class,
    CurrentBusinessActor.class, CodeExceptionHandler.class, ApiExceptionHandler.class,
    com.company.common.support.service.ExcelExportService.class})
class CommonPartControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean PartMapper mapper;
    @MockBean UserMapper management;
    @MockBean UserPreferencePeriodSourceMapper periods;
    @MockBean CalculationRuleService calculations;
    @MockBean PlatformTransactionManager transactions;
    PartTestData data;
    ArrayList<String> closed;

    @BeforeEach void setup() throws Exception {
        String contract = new org.springframework.core.io.ClassPathResource("contracts/openapi.yaml")
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(contract).contains("operationId: getCommonParts", "operationId: postCommonPart",
            "operationId: putCommonPart", "operationId: putCommonPartStandards", "operationId: putCommonPartStop");
        data = new PartTestData(mapper, management);
        closed = new ArrayList<>();
        when(periods.selectClosedMonths()).thenAnswer(call -> closed);
        when(periods.selectCashBaseYm()).thenReturn(new BigDecimal("202601"));
        when(calculations.effectiveStandard(anyString(), any())).thenAnswer(call -> new Standard(
            call.getArgument(0), YearMonth.of(2026, 1), new BigDecimal(
                "PART_DSTRB_RT".equals(call.getArgument(0)) ? "43.25" : "1600000")));
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
    }
    private ObjectNode values(String month, String rate) {
        return json.createObjectNode().put("effectiveMonth", month).put("leaderUserId", "leader-a")
            .put("distributionRate", new BigDecimal(rate)).put("salesExpense", 1500000).putNull("modifiedAt");
    }
    private ObjectNode create(String name) {
        var body = values("2026-09", "40").put("partName", name); body.remove("modifiedAt"); return body;
    }
    private org.springframework.test.web.servlet.ResultActions add(ObjectNode body) throws Exception {
        return mvc.perform(post("/api/common/parts").contentType("application/json").content(body.toString()));
    }
    private org.springframework.test.web.servlet.ResultActions standard(ObjectNode body) throws Exception {
        return mvc.perform(put("/api/common/parts/part-a/standards").contentType("application/json").content(body.toString()));
    }
    private org.springframework.test.web.servlet.ResultActions rename(String name) throws Exception {
        return mvc.perform(put("/api/common/parts/part-a").contentType("application/json")
            .content(json.createObjectNode().put("partName", name).putNull("modifiedAt").toString()));
    }
    private org.springframework.test.web.servlet.ResultActions stop(ObjectNode body) throws Exception {
        return mvc.perform(put("/api/common/parts/part-a/stop").contentType("application/json").content(body.toString()));
    }
    private void noWrites() {
        verify(mapper, never()).touch(anyString(), any());
        verify(mapper, never()).insertPart(anyString(), anyString());
        verify(mapper, never()).insertPeriod(any());
        verify(mapper, never()).updatePeriod(any());
        assertThat(data.histories).isEmpty();
    }
    @Test
    @org.junit.jupiter.api.DisplayName("auth happy side-effect: TB_BS_PART와 TB_BS_PART_STD를 원자 저장한다")
    void anonymousCreationReadsBackMasterAndFirstPeriodWithAdminHistory() throws Exception {
        var result = mvc.perform(post("/api/common/parts").contentType("application/json").content(create("신규 파트").toString()))
            .andExpect(response("POST", "/api/common/parts"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.partName").value("신규 파트"))
            .andExpect(jsonPath("$.data.modifiedAt").value(org.hamcrest.Matchers.nullValue())).andReturn();
        String id = json.readTree(result.getResponse().getContentAsString()).at("/data/partId").asText();
        assertThat(id).matches("[a-f0-9]{20}");
        mvc.perform(get("/api/common/parts/{id}/standards", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].effectiveMonth").value("2026-09"));
        mvc.perform(get("/api/common/parts")).andExpect(jsonPath("$.data.items.length()").value(2));
        var order = inOrder(mapper); order.verify(mapper).insertPart(eq(id), anyString());
        order.verify(mapper).insertPeriod(any());
        verify(transactions).commit(any());
        assertThat(data.histories).extracting(h -> h.table()).contains("TB_BS_PART", "TB_BS_PART_STD");
    }
    @Test void createPolicyAcceptsUnionButRejectsSalesLeaderAlone() {
        assertThat(ClientPartPolicy.canCreatePart(Set.of("SALES_LDR"))).isFalse();
        assertThat(ClientPartPolicy.canCreatePart(Set.of("FIN", "SALES_LDR"))).isTrue();
        assertThat(ClientPartPolicy.canCreatePart(Set.of("ADMIN"))).isTrue();
    }
    @ParameterizedTest @ValueSource(strings = {"101", "-1", "0.001", "40.1234567890123456789"})
    void rateValidationRejectsWithoutMasterInsert(String rate) throws Exception {
        var body = create("금액 검증").put("distributionRate", new BigDecimal(rate));
        add(body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("distributionRate"));
        noWrites();
    }
    @ParameterizedTest @ValueSource(strings = {"-1", "1.1", "1000000000000000"})
    void expensePrecisionIsNotRounded(String expense) throws Exception {
        add(create("비용 검증").put("salesExpense", new BigDecimal(expense)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("salesExpense")); noWrites();
    }
    @Test
    @org.junit.jupiter.api.DisplayName("business: 중복 파트명 등록은 기존 파트와 기준을 보존한다")
    void duplicateCreateLeavesPartCountUnchanged() throws Exception {
        mvc.perform(post("/api/common/parts").contentType("application/json").content(create("기존 파트").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("partName"));
        assertThat(data.masters).hasSize(1); noWrites();
    }
    @Test void sameLeaderCanLeadTwoPartsWithoutUserAssignmentMutation() throws Exception {
        add(create("두 번째 파트")).andExpect(status().isOk());
        assertThat(data.rows.values()).extracting(row -> row.leaderUserId).containsOnly("leader-a");
        verify(management, never()).insertScope(anyString(), anyString(), anyString());
        verify(management, never()).insertRole(anyString(), anyString());
    }
    @Test void stoppedLeaderIsRejected() throws Exception {
        data.leaders.removeIf(row -> row.userId.equals("leader-a"));
        add(create("중지 파트장")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("leaderUserId")); noWrites();
    }
    @Test void futureCreationIsVisibleWithRequiredValuesAndSchedule() throws Exception {
        add(create("미래 파트").put("effectiveMonth", "2027-02")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.leaderUserId").value("leader-a"))
            .andExpect(jsonPath("$.data.scheduledChange").value("2027-02월부터 40%"));
    }
    @Test void currentMonthDefaultsAreReloadedWithoutChangingExistingPartValues() throws Exception {
        mvc.perform(get("/api/common/parts/options")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.distributionRate").value(43.25))
            .andExpect(jsonPath("$.data.salesExpense").value(1600000));
        when(calculations.effectiveStandard(eq("PART_DSTRB_RT"), any()))
            .thenReturn(new Standard("PART_DSTRB_RT", YearMonth.of(2026, 9), new BigDecimal("51")));
        mvc.perform(get("/api/common/parts/options")).andExpect(jsonPath("$.data.distributionRate").value(51));
        mvc.perform(get("/api/common/parts")).andExpect(jsonPath("$.data.items[0].distributionRate").value(40));
    }
    @Test void beforeCashBaseIsRejected() throws Exception {
        add(create("이전 월").put("effectiveMonth", "2025-12")).andExpect(status().isBadRequest()); noWrites();
    }
    @Test void cashBaseMonthIsAllowedWhenThereIsNoClose() throws Exception {
        add(create("시작 월").put("effectiveMonth", "2026-01")).andExpect(status().isOk());
    }
    @Test void lastClosedMonthIsRejectedEvenWhenEarlierMonthIsOpen() throws Exception {
        closed.add("202608"); add(create("마감 월").put("effectiveMonth", "2026-08"))
            .andExpect(status().isBadRequest()); noWrites();
    }
    @Test void followingClosedMonthAndFutureMonthAreAllowed() throws Exception {
        closed.add("202608"); add(create("열린 월")).andExpect(status().isOk());
        add(create("미래 열린 월").put("effectiveMonth", "2027-01")).andExpect(status().isOk());
    }
    @Test
    @org.junit.jupiter.api.DisplayName("auth happy side-effect: 파트명 변경 후 같은 PART_ID로 새 이름을 읽는다")
    void anonymousRenameKeepsIdentityAndReadsNewName() throws Exception {
        mvc.perform(put("/api/common/parts/part-a").contentType("application/json")
            .content(json.createObjectNode().put("partName", "새 파트명").putNull("modifiedAt").toString()))
            .andExpect(response("PUT", "/api/common/parts/{partId}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.partId").value("part-a"))
            .andExpect(jsonPath("$.data.partName").value("새 파트명"));
        mvc.perform(get("/api/common/parts")).andExpect(jsonPath("$.data.items[0].partName").value("새 파트명"));
        assertThat(data.rows.get("part-a:202601").partId).isEqualTo("part-a");
        verify(mapper).rename("part-a", "새 파트명");
        verify(mapper, never()).updatePeriod(any());
    }
    @Test
    @org.junit.jupiter.api.DisplayName("validation: 파트명 31자는 필드 오류이며 TB_BS_PART와 기준·이력 불변")
    void nameLength31IsFieldErrorAndOriginalRemains() throws Exception {
        mvc.perform(put("/api/common/parts/part-a").contentType("application/json")
            .content(json.createObjectNode().put("partName", "가".repeat(31)).putNull("modifiedAt").toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("partName"));
        assertThat(data.masters.get("part-a").partName).isEqualTo("기존 파트");
        verify(mapper, never()).rename(anyString(), anyString()); noWrites();
    }
    @Test void duplicateRenamePreservesBothNames() throws Exception {
        data.master("part-b", "다른 파트"); rename("다른 파트").andExpect(status().isBadRequest());
        assertThat(data.masters.values()).extracting(row -> row.partName).containsExactly("기존 파트", "다른 파트"); noWrites();
    }
    @Test void insertionPreservesScheduledRowAndEndsPredecessor() throws Exception {
        data.rows.get("part-a:202601").endMonth = "202611";
        data.period("part-a", "202612", null, "leader-b", "50", "1700000");
        standard(values("2026-10", "45")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(3));
        assertThat(data.rows.get("part-a:202610").endMonth).isEqualTo("202611");
        assertThat(data.rows.get("part-a:202601").endMonth).isEqualTo("202609");
        assertThat(data.rows.get("part-a:202612").distributionRate).isEqualByComparingTo("50");
        verify(calculations).definition("식-45"); verify(calculations).definition("식-00");
    }
    @Test
    @org.junit.jupiter.api.DisplayName("auth happy side-effect: 미래 기준 수정은 TB_SY_CHG_HIST에 전후값을 기록한다")
    void sameFutureMonthUpdatesWithoutIncreasingRowCountAndAuditsBeforeAfter() throws Exception {
        data.rows.get("part-a:202601").endMonth = "202611";
        data.period("part-a", "202612", null, "leader-a", "50", "1500000");
        mvc.perform(put("/api/common/parts/part-a/standards").contentType("application/json")
            .content(values("2026-12", "55").toString())).andExpect(status().isOk())
            .andExpect(response("PUT", "/api/common/parts/{partId}/standards"))
            .andExpect(jsonPath("$.data.items[1].distributionRate").value(55));
        assertThat(data.rows).hasSize(2);
        assertThat(data.histories).anySatisfy(h -> {
            assertThat(h.item()).isEqualTo("이익 배분율"); assertThat(h.before()).isEqualTo("50"); assertThat(h.after()).isEqualTo("55");
        });
        mvc.perform(get("/api/common/parts/part-a/change-history")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].changedBy").value("admin"));
    }
    @Test void aggregateVersionIsIdenticalInEveryPeriodAndMaster() throws Exception {
        standard(values("2026-10", "45")).andExpect(status().isOk());
        String version = data.masters.get("part-a").modifiedAt.toString();
        mvc.perform(get("/api/common/parts/part-a/standards"))
            .andExpect(jsonPath("$.data.items[0].modifiedAt").value(version))
            .andExpect(jsonPath("$.data.items[1].modifiedAt").value(version));
        mvc.perform(get("/api/common/parts")).andExpect(jsonPath("$.data.items[0].modifiedAt").value(version));
    }
    @ParameterizedTest @ValueSource(strings = {"2026-00", "2026-13", "202610", "0000-01"})
    void malformedMonthPreservesAllPeriods(String month) throws Exception {
        standard(values(month, "45")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("effectiveMonth")); noWrites();
    }
    @Test
    @org.junit.jupiter.api.DisplayName("business: 확정 정산이 있는 배분율 변경은 기준과 이력을 보존한다")
    void confirmedFinancialChangeRefusedWithAllowedMonth() throws Exception {
        data.confirmed = "202610";
        mvc.perform(put("/api/common/parts/part-a/standards").contentType("application/json")
            .content(values("2026-10", "45").toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("2026-11")));
        noWrites(); assertThat(data.rows).hasSize(1);
    }
    @Test void confirmedMonthAllowsLeaderOnlyChange() throws Exception {
        data.confirmed = "202610";
        standard(values("2026-10", "40").put("leaderUserId", "leader-b")).andExpect(status().isOk());
        assertThat(data.rows.get("part-a:202610").leaderUserId).isEqualTo("leader-b");
        verify(mapper, never()).selectLastConfirmed(anyString(), anyString());
    }
    @Test void numericallyIdenticalValuesAreRejectedWithoutHistory() throws Exception {
        standard(values("2026-10", "40.00")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("지금 기준과 같습니다")); noWrites();
    }
    @Test void closedStandardMonthRejectedBeforeMutation() throws Exception {
        closed.add("202610"); standard(values("2026-10", "45")).andExpect(status().isBadRequest()); noWrites();
    }
    @Test void nullableStaleTokenDoesNotBypassVersionComparison() throws Exception {
        data.masters.get("part-a").modifiedAt = Instant.parse("2026-09-01T00:00:00Z");
        standard(values("2026-10", "45")).andExpect(status().isConflict()); noWrites();
    }
    @Test void renameStaleTokenIs409() throws Exception {
        data.masters.get("part-a").modifiedAt = Instant.parse("2026-09-01T00:00:00Z");
        rename("새 이름").andExpect(status().isConflict()); noWrites();
    }
    @Test void stopStaleTokenIs409() throws Exception {
        data.masters.get("part-a").modifiedAt = Instant.parse("2026-09-01T00:00:00Z");
        stop(json.createObjectNode().putNull("modifiedAt")).andExpect(status().isConflict()); noWrites();
    }
    @Test
    @org.junit.jupiter.api.DisplayName("auth happy side-effect: useYn Y→N 중지 후 기존 기준 기간과 배분율을 보존한다")
    void anonymousStopKeepsPeriodsAndReadsStoppedState() throws Exception {
        mvc.perform(put("/api/common/parts/part-a/stop").contentType("application/json").content("{\"modifiedAt\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.useYn").value("N"));
        mvc.perform(get("/api/common/parts")).andExpect(jsonPath("$.data.items[0].useYn").value("N"));
        assertThat(data.rows.get("part-a:202601").distributionRate).isEqualByComparingTo("40");
        verify(mapper).stop("part-a");
        verify(mapper, never()).updatePeriod(any()); verify(mapper, never()).endPeriod(anyString(), anyString(), anyString());
    }
    @Test
    @org.junit.jupiter.api.DisplayName("validation: modifiedAt 누락 중지는 400이며 TB_BS_PART 사용 상태 불변")
    void stopRequiresNullableVersionKey() throws Exception {
        mvc.perform(put("/api/common/parts/part-a/stop").contentType("application/json").content("{}"))
            .andExpect(response("PUT", "/api/common/parts/{partId}/stop"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("modifiedAt"));
        assertThat(data.masters.get("part-a").useYn).isEqualTo("Y");
        verify(mapper, never()).stop(anyString()); noWrites();
    }
    @Test
    @org.junit.jupiter.api.DisplayName("business: 소속 인력이 있는 파트 중지는 useYn=Y와 기존 기준을 보존한다")
    void affiliatedDeveloperBlocksStop() throws Exception {
        data.masters.get("part-a").personCount = 1L;
        mvc.perform(put("/api/common/parts/part-a/stop").contentType("application/json").content("{\"modifiedAt\":null}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("다른 파트로 이동")));
        assertThat(data.masters.get("part-a").useYn).isEqualTo("Y"); noWrites();
    }
    @Test void currentInProgressProjectBlocksStop() throws Exception {
        data.masters.get("part-a").projectCount = 1L;
        stop(json.createObjectNode().putNull("modifiedAt")).andExpect(status().isBadRequest()); noWrites();
    }
    @Test void absentPartUsesContract400() throws Exception {
        mvc.perform(get("/api/common/parts/missing/standards")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/common/parts/missing/change-history")).andExpect(status().isBadRequest());
    }
    @Test void emptyListAndEmptyHistoryAreRealEmptyResults() throws Exception {
        mvc.perform(get("/api/common/parts/part-a/change-history")).andExpect(jsonPath("$.data.items").isEmpty());
        data.masters.clear(); mvc.perform(get("/api/common/parts")).andExpect(jsonPath("$.data.items").isEmpty());
    }
    @Test void unknownCreateIdentityIsNotAccepted() throws Exception {
        add(create("미허용 값").put("partId", "caller")).andExpect(status().isBadRequest()); noWrites();
    }
    @Test void malformedBodyUsesSafeErrorEnvelope() throws Exception {
        mvc.perform(post("/api/common/parts").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("body"));
    }
}
