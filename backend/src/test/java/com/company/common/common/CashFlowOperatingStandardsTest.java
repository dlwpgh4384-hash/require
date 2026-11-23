package com.company.common.common;

import com.company.common.operatingstandards.controller.CommonOperatingStandardsController;
import com.company.common.operatingstandards.dto.OperatingStandardsContracts.Mutation;
import com.company.common.operatingstandards.mapper.OperatingStandardRow;
import com.company.common.operatingstandards.mapper.OperatingStandardsMapper;
import com.company.common.operatingstandards.service.CommonOperatingStandardsService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.users.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class CashFlowOperatingStandardsTest {
    private static final String PATH = "/api/common/operating-standards";
    private static final String VERSION = "2026-10-01T00:00:00Z";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean OperatingStandardsMapper mapper;
    @MockBean UserMapper management;
    @MockBean UserPreferencePeriodSourceMapper periods;
    private final List<OperatingStandardRow> rows = new ArrayList<>();
    private final List<String> closed = new ArrayList<>();

    @BeforeEach void setup() {
        rows.clear();
        closed.clear();
        rows.add(row("CASH_BASE_YM", "202601", "연월"));
        rows.add(row("CASH_BASE_AMT", "1150000000", "원"));
        when(mapper.selectStandards()).thenAnswer(call -> new ArrayList<>(rows));
        when(mapper.selectCurrentMonth()).thenReturn("202611");
        when(periods.selectClosedMonths()).thenAnswer(call -> new ArrayList<>(closed));
        when(mapper.updateStandard(any())).thenAnswer(call -> {
            Mutation value = call.getArgument(0);
            rows.stream().filter(row -> row.standardCode.equals(value.standardCode())
                && row.month.equals(value.month())).findFirst().orElseThrow().value = value.value();
            return 1;
        });
    }

    private OperatingStandardRow row(String code, String value, String unit) {
        var row = new OperatingStandardRow();
        row.standardCode = code;
        row.standardName = code;
        row.month = "202601";
        row.value = new BigDecimal(value);
        row.unit = unit;
        row.changedAt = Instant.parse(VERSION);
        return row;
    }

    private org.springframework.test.web.servlet.ResultActions save(String month, String amount,
        String version) throws Exception {
        var body = json.createObjectNode();
        var items = body.putArray("items");
        items.addObject().put("standardCode", "CASH_BASE_YM").put("value", month)
            .putNull("effectiveMonth").put("action", "update").put("modifiedAt", version);
        items.addObject().put("standardCode", "CASH_BASE_AMT").put("value", amount)
            .putNull("effectiveMonth").put("action", "update").put("modifiedAt", version);
        return mvc.perform(put(PATH).contentType("application/json").content(body.toString()));
    }

    @Test @DisplayName("REQ-1256-AC1: 미마감 시작월과 기초잔액 저장 뒤 기존 운영값으로 재조회")
    void openBaseUpdatesBothExistingRowsAndReadsThemBackAnonymously() throws Exception {
        save("2026-02", "123456789", VERSION).andExpect(status().isOk());
        mvc.perform(get(PATH)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].value").value("2026-02"))
            .andExpect(jsonPath("$.data.items[1].value").value("123456789"))
            .andExpect(jsonPath("$.data.items[0].immediate").value(true))
            .andExpect(jsonPath("$.data.items[0].effectiveMonth").isEmpty());
        verify(mapper, times(2)).updateStandard(any());
        verify(mapper, never()).insertStandard(any());
        verify(mapper, never()).deleteStandard(any());
        verify(management, times(2)).insertHistory(any());
        var order = inOrder(management, mapper);
        order.verify(management).lockManagement();
        order.verify(mapper).lockStandardsAndPeriods();
    }

    @Test @DisplayName("REQ-1256-AC1: 기존 시작월 마감 후 두 값 변경은 거부되고 불변")
    void closedOriginalBaseCannotBeMovedToBypassTheLock() throws Exception {
        closed.add("202601");
        save("2026-02", "100", VERSION).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("effectiveMonth"))
            .andExpect(jsonPath("$.message").value("마감한 달에는 적용할 수 없습니다."));
        unchanged();
        mvc.perform(get(PATH)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].value").value("2026-01"))
            .andExpect(jsonPath("$.data.items[1].value").value("1150000000"))
            .andExpect(jsonPath("$.data.items[0].closed").value(true))
            .andExpect(jsonPath("$.data.items[1].closed").value(true));
    }

    @Test void closedProposedBaseRejectsTheWholeBatchBeforeAnyWrite() throws Exception {
        closed.add("202602");
        save("2026-02", "100", VERSION).andExpect(status().isBadRequest());
        unchanged();
    }

    @Test void staleOptimisticTokenDoesNotReplaceTheExistingExplicitUpdateContract() throws Exception {
        save("2026-02", "100", "2026-09-01T00:00:00Z").andExpect(status().isConflict())
            .andExpect(jsonPath("$.errors[0].field").value("modifiedAt"));
        unchanged();
    }

    @ParameterizedTest @ValueSource(strings = {"add", "delete"})
    void immediateBaseDoesNotAcquireANewUpsertOrDeleteAction(String action) throws Exception {
        var body = json.createObjectNode();
        body.putArray("items").addObject().put("standardCode", "CASH_BASE_AMT")
            .put("value", "100").putNull("effectiveMonth").put("action", action)
            .put("modifiedAt", VERSION);
        mvc.perform(put(PATH).contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("action"));
        unchanged();
    }

    private void unchanged() {
        assertThat(rows.get(0).value).isEqualByComparingTo("202601");
        assertThat(rows.get(1).value).isEqualByComparingTo("1150000000");
        verify(mapper, never()).updateStandard(any());
        verify(mapper, never()).insertStandard(any());
        verify(mapper, never()).deleteStandard(any());
        verify(management, never()).insertHistory(any());
    }
}
