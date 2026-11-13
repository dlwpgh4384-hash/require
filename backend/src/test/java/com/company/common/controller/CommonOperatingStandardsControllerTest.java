package com.company.common.controller;

import com.company.common.dto.UserContracts.History;
import com.company.common.mapper.OperatingStandardsMapper;
import com.company.common.mapper.UserMapper;
import com.company.common.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.service.CommonOperatingStandardsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonOperatingStandardsController.class)
@ContextConfiguration(classes = {CommonOperatingStandardsController.class, CommonOperatingStandardsService.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class})
class CommonOperatingStandardsControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean OperatingStandardsMapper mapper;
    @MockBean UserMapper management;
    @MockBean UserPreferencePeriodSourceMapper periods;
    OperatingStandardsTestData data;

    @BeforeEach void setup() throws Exception {
        String contract = new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8);
        assertThat(contract).contains("/api/common/operating-standards:",
            "operationId: getCommonOperatingStandards", "operationId: putCommonOperatingStandards");
        data = new OperatingStandardsTestData(mapper, management, periods);
    }
    private ObjectNode item(String code, String month, String action, String value) {
        var node = json.createObjectNode().put("standardCode", code).put("action", action);
        if (month == null) node.putNull("effectiveMonth"); else node.put("effectiveMonth", month);
        if (value != null) node.put("value", value);
        return node;
    }
    private org.springframework.test.web.servlet.ResultActions save(ObjectNode... items) throws Exception {
        var body = json.createObjectNode();
        var array = body.putArray("items");
        for (var item : items) array.add(item);
        return mvc.perform(put("/api/common/operating-standards").contentType("application/json").content(body.toString()));
    }
    private void unchanged() {
        verify(mapper, never()).insertStandard(any());
        verify(mapper, never()).updateStandard(any());
        verify(mapper, never()).deleteStandard(any());
        assertThat(data.histories).isEmpty();
    }
    @Test void anonymousGetReturnsExactSevenFieldsAndPreservesFixedRowsOutsideView() throws Exception {
        var response = mvc.perform(get("/api/common/operating-standards")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].effectiveMonth").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.items[0].value").value("2026-01"))
            .andExpect(jsonPath("$.data.items[0].length()").value(7)).andReturn();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("BDGT_WARN_RT");
        assertThat(data.rows).containsKey("BDGT_WARN_RT:202601");
        verifyNoInteractions(management);
    }
    @Test void futureAddIsAnonymousAuditedAndKeepsEarlierHistory() throws Exception {
        mvc.perform(put("/api/common/operating-standards").contentType("application/json")
            .content("{\"items\":[{\"standardCode\":\"VAT_RT\",\"effectiveMonth\":\"2027-02\",\"action\":\"add\",\"value\":\"12.1250\"}]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[?(@.standardCode == 'VAT_RT' && @.effectiveMonth == '2027-02')].value")
                .value(org.hamcrest.Matchers.contains("12.125")));
        mvc.perform(get("/api/common/operating-standards")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[?(@.standardCode == 'VAT_RT')]").value(org.hamcrest.Matchers.hasSize(2)));
        assertThat(data.rows.get("VAT_RT:202601").value).isEqualByComparingTo("10");
        assertThat(data.rows.get("VAT_RT:202702").value).isEqualByComparingTo("12.1250");
        assertThat(data.histories).containsExactly(new History("TB_SY_OPER_STD", "VAT_RT:202702", "NEW", "VAT_RT:2027-02", null, "12.125"));
        var order = inOrder(management, mapper);
        order.verify(management).lockManagement();
        order.verify(mapper).lockStandardsAndPeriods();
        order.verify(mapper).selectStandards();
    }
    @Test void inclusiveFirstOpenMonthBetweenClosedMonthsIsAccepted() throws Exception {
        data.closed.addAll(java.util.List.of("202601", "202603"));
        save(item("VAT_RT", "2026-02", "add", "11")).andExpect(status().isOk());
        assertThat(data.rows.get("VAT_RT:202602").value).isEqualByComparingTo("11");
    }
    @Test void openUpdateAndDeleteReadBackAndAuditBothValues() throws Exception {
        data.row("VAT_RT", "202612", "12", "%");
        save(item("VAT_RT", "2026-12", "update", "13")).andExpect(status().isOk());
        assertThat(data.histories).contains(new History("TB_SY_OPER_STD", "VAT_RT:202612", "UPD", "VAT_RT:2026-12", "12", "13"));
        save(item("VAT_RT", "2026-12", "delete", null)).andExpect(status().isOk());
        assertThat(data.rows).doesNotContainKey("VAT_RT:202612");
        assertThat(data.histories).contains(new History("TB_SY_OPER_STD", "VAT_RT:202612", "DEL", "VAT_RT:2026-12", "13", null));
        mvc.perform(get("/api/common/operating-standards")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[?(@.standardCode == 'VAT_RT')]").value(org.hamcrest.Matchers.hasSize(1)));
    }
    @ParameterizedTest @ValueSource(strings = {"add", "update", "delete"})
    void closedIdentityRejectsWholeBatchAndCreatesNoSuccessfulHistory(String action) throws Exception {
        data.closed.add("202602");
        if (!"add".equals(action)) data.row("VAT_RT", "202602", "11", "%");
        save(item("PART_DSTRB_RT", "2027-01", "add", "45"), item("VAT_RT", "2026-02", action, "12"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("마감한 달에는 적용할 수 없습니다."));
        unchanged();
        if (!"add".equals(action)) assertThat(data.rows.get("VAT_RT:202602").value).isEqualByComparingTo("11");
    }
    @Test void importedMonthRejectedWithoutCloseRow() throws Exception {
        save(item("VAT_RT", "2025-12", "add", "11")).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void aiSumUsesUnchangedMembersAndRejectsPartialInvalidTotal() throws Exception {
        save(item("AI_SCORE_JOB", "2027-01", "add", "26")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("value")); unchanged();
    }
    @Test void aiSumOneHundredAllowsCoordinatedChange() throws Exception {
        save(item("AI_SCORE_JOB", "2027-01", "add", "26"), item("AI_SCORE_SKILL", "2027-01", "add", "19"))
            .andExpect(status().isOk());
        assertThat(data.histories).hasSize(2);
    }
    @Test void unchangedFutureIntervalIsRevalidatedBeforeAnyWrite() throws Exception {
        data.row("AI_SCORE_JOB", "202703", "30", "점");
        data.row("AI_SCORE_SKILL", "202703", "15", "점");
        save(item("AI_SCORE_JOB", "2027-01", "add", "26"), item("AI_SCORE_SKILL", "2027-01", "add", "19"),
            item("AI_SCORE_JOB", "2027-03", "delete", null)).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void deletingMemberRechecksEffectiveGroup() throws Exception {
        data.row("AI_SCORE_JOB", "202612", "30", "점");
        data.row("AI_SCORE_SKILL", "202612", "15", "점");
        save(item("AI_SCORE_JOB", "2026-12", "delete", null)).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void reverseReceivableStagesAreRejected() throws Exception {
        save(item("DUN_D1", "2027-01", "add", "30"), item("DUN_D2", "2027-01", "add", "7"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("value")); unchanged();
    }
    @Test void receivableStagesSevenThirtySixtyAreValid() throws Exception {
        save(item("DUN_D1", "2027-01", "add", "7"), item("DUN_D2", "2027-01", "add", "30"),
            item("DUN_D3", "2027-01", "add", "60")).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings = {"0", "100"})
    void probabilityBoundariesAllowed(String value) throws Exception {
        save(item("EXT_PRBL_H", "2027-01", "add", value)).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings = {"-0.0001", "100.0001"})
    void probabilityOutsideBoundsRejected(String value) throws Exception {
        save(item("STEP_PRBL_1", "2027-01", "add", value)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("value")); unchanged();
    }
    @Test void immediateCashRetainsBothPhysicalKeysWithoutMonthlyHistories() throws Exception {
        save(item("CASH_BASE_YM", null, "update", "2026-02"), item("CASH_BASE_AMT", null, "update", "123456789.1234"))
            .andExpect(status().isOk());
        assertThat(data.rows.get("CASH_BASE_YM:202601").value).isEqualByComparingTo("202602");
        assertThat(data.rows.get("CASH_BASE_AMT:202601").value).isEqualByComparingTo("123456789.1234");
        verify(mapper, never()).insertStandard(any());
        assertThat(data.histories).hasSize(2);
    }
    @Test void cashSelectsSameCurrentRowAsPeriodSource() throws Exception {
        data.row("CASH_BASE_AMT", "202612", "8", "원");
        save(item("CASH_BASE_AMT", null, "update", "17")).andExpect(status().isOk());
        assertThat(data.rows.get("CASH_BASE_AMT:202601").value).isEqualByComparingTo("17");
        assertThat(data.rows.get("CASH_BASE_AMT:202612").value).isEqualByComparingTo("8");
    }
    @Test void closedOldCashBaseCannotBeMovedToBypassLock() throws Exception {
        data.closed.add("202601");
        mvc.perform(put("/api/common/operating-standards").contentType("application/json")
            .content("{\"items\":[{\"standardCode\":\"CASH_BASE_YM\",\"effectiveMonth\":null,\"action\":\"update\",\"value\":\"2026-02\"},{\"standardCode\":\"CASH_BASE_AMT\",\"effectiveMonth\":null,\"action\":\"update\",\"value\":\"100\"}]}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("마감한 달에는 적용할 수 없습니다.")); unchanged();
    }
    @Test void closedRequestedCashBaseRejectsBothValues() throws Exception {
        data.closed.add("202602");
        save(item("CASH_BASE_AMT", null, "update", "100"), item("CASH_BASE_YM", null, "update", "2026-02"))
            .andExpect(status().isBadRequest()); unchanged();
    }
    @ParameterizedTest @ValueSource(strings = {"add", "delete"})
    void immediateValuesAreUpdateOnly(String action) throws Exception {
        save(item("CASH_BASE_AMT", null, action, "100")).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void cashCannotAcceptMonthlyIdentity() throws Exception {
        save(item("CASH_BASE_AMT", "2027-01", "update", "100")).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void ordinaryValueRequiresMonth() throws Exception {
        save(item("VAT_RT", null, "add", "11")).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void missingUpdateIdentityCannotUpsert() throws Exception {
        save(item("VAT_RT", "2027-01", "update", "11")).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void existingAddIdentityCannotOverwrite() throws Exception {
        save(item("VAT_RT", "2026-01", "add", "11")).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void missingDeleteIdentityRejected() throws Exception {
        save(item("VAT_RT", "2027-01", "delete", null)).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void unclosedSingleHistoryCanBeDeleted() throws Exception {
        save(item("VAT_RT", "2026-01", "delete", null)).andExpect(status().isOk());
        assertThat(data.rows).doesNotContainKey("VAT_RT:202601");
        assertThat(data.histories).hasSize(1);
    }
    @Test void noOpUpdateCreatesNoHistory() throws Exception {
        save(item("VAT_RT", "2026-01", "update", "10.0000")).andExpect(status().isOk()); unchanged();
    }
    @ParameterizedTest @ValueSource(strings = {"100000000000", "1.12345", "NaN", "1e2", "", "2026-13"})
    void precisionAndMalformedValueRejected(String value) throws Exception {
        save(item("VAT_RT", "2027-01", "add", value)).andExpect(status().isBadRequest()); unchanged();
    }
    @ParameterizedTest @ValueSource(strings = {"2026-00", "2026-13", "0000-01", "202601"})
    void invalidMonthRejected(String month) throws Exception {
        save(item("VAT_RT", month, "add", "11")).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void unknownFieldRejected() throws Exception {
        save(item("VAT_RT", "2027-01", "add", "11").put("processor", "caller"))
            .andExpect(status().isBadRequest()); unchanged();
    }
    @Test void duplicateIdentityRejected() throws Exception {
        save(item("VAT_RT", "2027-01", "add", "11"), item("VAT_RT", "2027-01", "add", "12"))
            .andExpect(status().isBadRequest()); unchanged();
    }
    @Test void fixedThresholdCannotBeEdited() throws Exception {
        save(item("BDGT_WARN_RT", "2027-01", "add", "90")).andExpect(status().isBadRequest()); unchanged();
    }
    @ParameterizedTest @ValueSource(strings = {"standardCode", "effectiveMonth", "action", "value"})
    void missingRequiredFieldRejected(String field) throws Exception {
        var node = item("VAT_RT", "2027-01", "add", "11"); node.remove(field);
        save(node).andExpect(status().isBadRequest()); unchanged();
    }
    @Test void malformedBodyUsesExistingErrorEnvelope() throws Exception {
        mvc.perform(put("/api/common/operating-standards").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("body")); unchanged();
    }
    @Test void emptyBatchRejected() throws Exception { save().andExpect(status().isBadRequest()); unchanged(); }
    @Test void emptyDatabaseReadReturnsActualEmptyList() throws Exception {
        data.rows.clear();
        mvc.perform(get("/api/common/operating-standards")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(0));
    }
    @Test void databaseFailureDoesNotLeakSql() throws Exception {
        when(mapper.selectStandards()).thenThrow(new IllegalStateException("private statement"));
        mvc.perform(get("/api/common/operating-standards")).andExpect(status().isInternalServerError())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private statement"))));
    }
}
