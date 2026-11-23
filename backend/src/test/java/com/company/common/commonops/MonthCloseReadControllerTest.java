package com.company.common.commonops;

import com.company.common.common.MonthCloseCodes;
import com.company.common.commonops.controller.MonthCloseReadController;
import com.company.common.commonops.mapper.MonthCloseHistoryRow;
import com.company.common.commonops.mapper.MonthCloseReadMapper;
import com.company.common.commonops.service.MonthCloseReadService;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.support.service.BusinessCalendar;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

@WebMvcTest(MonthCloseReadController.class)
@ContextConfiguration(classes = {MonthCloseReadController.class, MonthCloseReadService.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class})
class MonthCloseReadControllerTest {
    private static final String HISTORY = "/api/common/month-close-history";
    private static final String BADGE = "/api/common/month-close-badge";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean MonthCloseReadMapper mapper;
    @MockBean BusinessCalendar calendar;
    @MockBean MonthCloseCodes codes;
    @MockBean PlatformTransactionManager transactions;

    @BeforeEach void setup() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var actual = new BusinessCalendar(Clock.fixed(Instant.parse("2026-10-31T15:00:00Z"), ZoneOffset.UTC));
        when(calendar.currentMonth()).thenAnswer(call -> actual.currentMonth());
        when(calendar.toDatabaseMonth(any())).thenAnswer(call -> actual.toDatabaseMonth(call.getArgument(0)));
        when(calendar.fromDatabaseMonth(any())).thenAnswer(call -> actual.fromDatabaseMonth(call.getArgument(0)));
        when(codes.name("ACT", "CLOSE")).thenReturn("마감");
        when(codes.name("ACT", "OPEN")).thenReturn("해제");
        when(mapper.selectHistory(any())).thenReturn(List.of());
    }

    @Test void anonymousBadgeUsesTheCurrentSeoulCalendarMonthNotThePreviousMonth() throws Exception {
        mvc.perform(get(BADGE)).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.needsAttention").value(true));
        verify(mapper).isClosed("202611");
        verifyNoMoreInteractions(mapper);
    }

    @Test void badgeHidesOnlyWhenTheActualCurrentMonthIsClosed() throws Exception {
        when(mapper.isClosed("202611")).thenReturn(true);
        mvc.perform(get(BADGE)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.needsAttention").value(false));
        verify(mapper).isClosed("202611");
    }

    @Test void badgeDoesNotAdoptCallerSelectedMonthOrRoleHeaders() throws Exception {
        mvc.perform(get(BADGE).param("yearMonth", "not-a-month").header("X-Role", "GUEST"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.needsAttention").value(true));
        verify(mapper).isClosed("202611");
    }

    @Test void historyWithoutMonthReturnsAllRowsAndStoredProcessorAndWarningSnapshot() throws Exception {
        when(mapper.selectHistory(null)).thenReturn(List.of(
            row("202610", "CLOSE", "MM 초과, 영업 파트 정산", null),
            row("202609", "OPEN", "must not be exposed", "정정 검토")));
        var response = mvc.perform(get(HISTORY)).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[0].yearMonth").value("2026-10"))
            .andExpect(jsonPath("$.data.items[0].action").value("마감"))
            .andExpect(jsonPath("$.data.items[0].processor").value("저장된 처리자"))
            .andExpect(jsonPath("$.data.items[0].occurredAt").value("2026-11-01T01:02:03Z"))
            .andExpect(jsonPath("$.data.items[0].remainingChecks[0]").value("MM 초과"))
            .andExpect(jsonPath("$.data.items[0].remainingChecks[1]").value("영업 파트 정산"))
            .andExpect(jsonPath("$.data.items[1].action").value("해제"))
            .andExpect(jsonPath("$.data.items[1].reason").value("정정 검토"))
            .andExpect(jsonPath("$.data.items[1].remainingChecks").isEmpty())
            .andReturn().getResponse();
        var item = json.readTree(response.getContentAsString()).path("data").path("items").get(0);
        assertThat(item.size()).isEqualTo(6);
        assertThat(item.has("reason")).isTrue();
        assertThat(item.path("reason").isNull()).isTrue();
        verify(mapper).selectHistory(null);
        verifyNoMoreInteractions(mapper);
    }

    @Test void suppliedHistoryMonthBindsCompactDatabaseMonth() throws Exception {
        mvc.perform(get(HISTORY).param("yearMonth", "2026-10")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty());
        verify(mapper).selectHistory("202610");
        verifyNoMoreInteractions(mapper);
    }

    @Test void closeWithoutRemainingChecksReturnsAnEmptyArrayNotADash() throws Exception {
        when(mapper.selectHistory(null)).thenReturn(List.of(
            row("202610", "CLOSE", null, null), row("202609", "CLOSE", "  ", null)));
        mvc.perform(get(HISTORY)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].remainingChecks").isEmpty())
            .andExpect(jsonPath("$.data.items[1].remainingChecks").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "202610", "2026-00", "2026-13", "2026-1", "2026-10 ", "0000-01", "garbage"})
    void invalidHistoryMonthIsAFieldErrorWithoutAnyRead(String month) throws Exception {
        mvc.perform(get(HISTORY).param("yearMonth", month)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors[0].field").value("yearMonth"));
        verifyNoInteractions(mapper, codes);
    }

    @Test void readFailuresAreNotSuccessfulEmptyHistoryOrNoWarning() throws Exception {
        when(mapper.selectHistory(null)).thenThrow(new DataAccessResourceFailureException("private SQL details"));
        var response = mvc.perform(get(HISTORY)).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.data").doesNotExist()).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("private SQL details");
    }

    @Test void badgeFailureIsNotReportedAsClosed() throws Exception {
        when(mapper.isClosed("202611")).thenThrow(new DataAccessResourceFailureException("private SQL details"));
        mvc.perform(get(BADGE)).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.data").doesNotExist());
    }

    private static MonthCloseHistoryRow row(String month, String action, String names, String reason) {
        return new MonthCloseHistoryRow(month, action, "저장된 처리자",
            Instant.parse("2026-11-01T01:02:03Z"), reason, names);
    }
}
