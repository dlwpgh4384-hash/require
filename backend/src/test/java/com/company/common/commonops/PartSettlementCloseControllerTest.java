package com.company.common.commonops;

import com.company.common.commonops.controller.PartSettlementCloseController;
import com.company.common.commonops.mapper.PartSettlementCloseMapper;
import com.company.common.commonops.mapper.PartSettlementCloseRow;
import com.company.common.commonops.service.PartSettlementCloseService;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PartSettlementCloseController.class)
@ContextConfiguration(classes = {PartSettlementCloseController.class, PartSettlementCloseService.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class})
class PartSettlementCloseControllerTest {
    static final String PATH = "/api/common/part-settlement-close-checks";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean PartSettlementCloseMapper mapper;
    @MockBean PlatformTransactionManager transactions;

    @BeforeEach void setup() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(mapper.selectTargets(any())).thenReturn(List.of(
            row("part-a", false, null), row("part-b", true, Instant.parse("2026-10-31T15:00:00Z"))));
    }

    @Test void anonymousGetReturnsAllTargetsAndTheirNullableVersions() throws Exception {
        var response = mvc.perform(get(PATH).param("yearMonth", "2026-10"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.yearMonth").value("2026-10"))
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.unconfirmedCount").value(1))
            .andExpect(jsonPath("$.data.items[0].partId").value("part-a"))
            .andExpect(jsonPath("$.data.items[0].confirmed").value(false))
            .andExpect(jsonPath("$.data.items[1].confirmed").value(true))
            .andReturn().getResponse();
        var data = json.readTree(response.getContentAsString()).path("data");
        assertThat(data.size()).isEqualTo(4);
        assertThat(data.path("items").get(0).size()).isEqualTo(3);
        assertThat(data.path("items").get(0).has("modifiedAt")).isTrue();
        assertThat(data.path("items").get(0).path("modifiedAt").isNull()).isTrue();
        assertThat(data.path("items").get(1).path("modifiedAt").asText()).isEqualTo("2026-10-31T15:00:00Z");
        verify(mapper).selectTargets(argThat(period -> period.month().equals("202610")
            && period.nextMonthStart().equals(Instant.parse("2026-10-31T15:00:00Z"))));
        verifyNoMoreInteractions(mapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-13", "2026-00", "202610", "26-10", "2026-1", "2026-10 ", ""})
    void invalidMonthIsAFieldErrorBeforeAnyDatabaseRead(String month) throws Exception {
        mvc.perform(get(PATH).param("yearMonth", month))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors[0].field").value("yearMonth"));
        verifyNoInteractions(mapper);
    }

    @Test void missingMonthIsAFieldError() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("yearMonth"));
        verifyNoInteractions(mapper);
    }

    @Test void noTargetsIsARealEmptyResult() throws Exception {
        when(mapper.selectTargets(any())).thenReturn(List.of());
        mvc.perform(get(PATH).param("yearMonth", "2026-10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalCount").value(0))
            .andExpect(jsonPath("$.data.unconfirmedCount").value(0))
            .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test void rereadAfterAutomaticConfirmationReflectsZeroUnconfirmedWithoutCachedTargets() throws Exception {
        mvc.perform(get(PATH).param("yearMonth", "2026-10"))
            .andExpect(jsonPath("$.data.unconfirmedCount").value(1));
        when(mapper.selectTargets(any())).thenReturn(List.of(
            row("part-a", true, Instant.parse("2026-11-01T01:00:00Z")),
            row("part-b", true, Instant.parse("2026-10-31T15:00:00Z"))));
        mvc.perform(get(PATH).param("yearMonth", "2026-10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.unconfirmedCount").value(0));
    }

    @Test void failedReadIsNotReportedAsSuccessfulEmptyAndDoesNotLeakSql() throws Exception {
        when(mapper.selectTargets(any())).thenThrow(new DataAccessResourceFailureException("private SQL details"));
        var response = mvc.perform(get(PATH).param("yearMonth", "2026-10"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").doesNotExist()).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("private SQL details");
    }

    private static PartSettlementCloseRow row(String partId, boolean confirmed, Instant modifiedAt) {
        var row = new PartSettlementCloseRow();
        row.partId = partId;
        row.confirmed = confirmed;
        row.modifiedAt = modifiedAt;
        return row;
    }
}
