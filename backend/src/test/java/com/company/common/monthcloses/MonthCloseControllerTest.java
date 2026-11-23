package com.company.common.monthcloses;

import com.company.common.monthcloses.controller.CommonMonthCloseController;
import com.company.common.monthcloses.dto.MonthCloseContracts.*;
import com.company.common.monthcloses.mapper.MonthCloseMapper;
import com.company.common.monthcloses.service.*;
import com.company.common.partsettlements.dto.PartSettlementContracts.CloseCheck;
import com.company.common.partsettlements.dto.PartSettlementContracts.CloseItem;
import com.company.common.partsettlements.service.PartSettlementAutoConfirmService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.users.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonMonthCloseController.class)
@ContextConfiguration(classes = {CommonMonthCloseController.class, CommonMonthCloseService.class,
    MonthClosePeriodService.class, CodeExceptionHandler.class, ApiExceptionHandler.class})
class MonthCloseControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean MonthCloseMapper mapper;
    @MockBean MonthCloseCheckService checks;
    @MockBean MonthCloseSnapshotService snapshots;
    @MockBean PartSettlementAutoConfirmService settlements;
    @MockBean UserPreferencePeriodSourceMapper periods;
    @MockBean UserMapper management;
    @MockBean BusinessCalendar calendar;
    @MockBean CurrentBusinessActor actor;
    @MockBean PlatformTransactionManager transactions;
    @MockBean MonthCloseCheckRecorder recorder;
    static final Instant TOKEN = Instant.parse("2026-02-01T01:02:03.123456Z");
    static final String ROOT = "/api/common/month-closes/";
    Header saved;

    @BeforeEach void setup() {
        saved = null;
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(actor.userId()).thenReturn("admin");
        when(actor.roles()).thenReturn(Set.of("ADMIN", "FIN"));
        var realCalendar = new BusinessCalendar();
        when(calendar.toDatabaseMonth(any())).thenAnswer(call -> realCalendar.toDatabaseMonth(call.getArgument(0)));
        when(calendar.fromDatabaseMonth(any())).thenAnswer(call -> realCalendar.fromDatabaseMonth(call.getArgument(0)));
        when(calendar.firstOpenMonth(any(), any())).thenAnswer(call -> realCalendar.firstOpenMonth(call.getArgument(0), call.getArgument(1)));
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 4));
        when(periods.selectCashBaseYm()).thenReturn(new BigDecimal("202601"));
        when(mapper.selectHeader(any())).thenAnswer(call -> saved);
        when(checks.calculate(any())).thenReturn(new MonthCloseCheckService.Result(List.of(
            new Check("급여 업로드", "정상", "0명 업로드 완료", "UI-02"),
            new Check("MM 1.0 초과자", "정상", "0명 MM 초과", "UI-12"),
            new Check("세금계산서 번호 미입력", "정상", "0건 번호 미입력", "UI-13"),
            new Check("재무 항목", "정상", "0건 입력", "UI-10"),
            new Check("영업 파트 정산", "확인 필요", "1개 중 1개 미확정", "UI-19"),
            new Check("청구 누락 점검", "정상", "0건 청구 누락", "UI-13")
        ), List.of("영업 파트 정산")));
        when(settlements.getCommonPartSettlementCloseChecks(any())).thenReturn(
            new CloseCheck("2026-01", 1, 1, List.of(new CloseItem("part", false, null))));
        when(mapper.saveClose(any())).thenAnswer(call -> {
            Mutation mutation = call.getArgument(0);
            saved = new Header(mutation.state(), TOKEN, "CLOSE".equals(mutation.state()) ? TOKEN : null,
                "CLOSE".equals(mutation.state()) ? "관리자" : null);
            return 1;
        });
        when(mapper.insertHistory(any())).thenReturn(1);
        when(mapper.insertAccess(any(), any(), any())).thenReturn(1);
    }
    void closed() { saved = new Header("CLOSE", TOKEN, TOKEN, "관리자"); when(mapper.latestClosedMonth()).thenReturn("202601"); }
    String version() throws Exception {
        var body = json.createObjectNode();
        if (saved == null || saved.modifiedAt() == null) body.putNull("modifiedAt"); else body.put("modifiedAt", saved.modifiedAt().toString());
        return json.writeValueAsString(body);
    }
    String reopenBody(String reason) throws Exception {
        return json.writeValueAsString(json.createObjectNode().put("modifiedAt", TOKEN.toString()).put("reason", reason));
    }
    @Test void getAndRecheckExposeSixChecksAndOnlyRecordCheckResults() throws Exception {
        for (int i = 0; i < 2; i++) mvc.perform(get(ROOT + "2026-01")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalCount").value(6)).andExpect(jsonPath("$.data.checks.length()").value(6))
            .andExpect(jsonPath("$.data.attentionCount").value(1)).andExpect(jsonPath("$.data.modifiedAt").isEmpty())
            .andExpect(jsonPath("$.data.reopenable").value(false));
        verify(recorder, times(2)).record("202601", List.of(), "admin");
        verifyNoInteractions(snapshots, settlements, management);
        verify(mapper, never()).saveClose(any()); verify(mapper, never()).insertHistory(any());
    }
    @Test void monthsBeforeCashBaseAreImpliedClosedWithoutChecksOrReopen() throws Exception {
        mvc.perform(get(ROOT + "2025-12")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isClosed").value(true)).andExpect(jsonPath("$.data.reopenable").value(false))
            .andExpect(jsonPath("$.data.totalCount").value(0)).andExpect(jsonPath("$.data.attentionCount").value(0))
            .andExpect(jsonPath("$.data.checks").isEmpty());
        verifyNoInteractions(checks, recorder);
    }
    @Test void closedAndFutureMonthsAreNeverRecorded() throws Exception {
        closed();
        mvc.perform(get(ROOT + "2026-01")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isClosed").value(true)).andExpect(jsonPath("$.data.reopenable").value(true));
        saved = null;
        mvc.perform(get(ROOT + "2026-05")).andExpect(status().isOk()).andExpect(jsonPath("$.data.isClosed").value(false));
        verifyNoInteractions(recorder);
    }
    @Test void olderClosedMonthIsNotReopenable() throws Exception {
        closed(); when(mapper.latestClosedMonth()).thenReturn("202602");
        mvc.perform(get(ROOT + "2026-01")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isClosed").value(true)).andExpect(jsonPath("$.data.reopenable").value(false));
    }
    @Test void impactReadsOnlySelectedAndFollowingMonth() throws Exception {
        when(mapper.adjustmentCount("202612", "202701")).thenReturn(3);
        mvc.perform(get(ROOT + "2026-12/reopen-impact")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.yearMonth").value("2026-12")).andExpect(jsonPath("$.data.previousAdjustmentCount").value(3));
        verify(mapper).adjustmentCount("202612", "202701"); verifyNoMoreInteractions(mapper);
    }
    @Test void publicConfirmAllowsWarningsAndUsesServerActorAndPreSettlementNames() throws Exception {
        mvc.perform(put(ROOT + "2026-01/confirm").contentType("application/json").content(version()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.isClosed").value(true))
            .andExpect(jsonPath("$.data.closedBy").value("관리자")).andExpect(jsonPath("$.data.modifiedAt").value(TOKEN.toString()));
        var order = inOrder(management, mapper, checks, snapshots, settlements);
        order.verify(management).lockManagement(); order.verify(mapper).lockSources(); order.verify(mapper).selectHeader("202601");
        order.verify(mapper).latestClosedMonth(); order.verify(checks).calculate(YearMonth.of(2026, 1));
        order.verify(snapshots).freeze(YearMonth.of(2026, 1), "202601", "admin");
        order.verify(settlements).getCommonPartSettlementCloseChecks("2026-01");
        order.verify(settlements).putCommonPartSettlementCloseConfirm(argThat(input -> input.yearMonth().equals("2026-01") && input.items().size() == 1));
        order.verify(mapper).saveClose(argThat(input -> input.state().equals("CLOSE") && input.actor().equals("admin")));
        order.verify(mapper).insertHistory(new History("202601", "CLOSE", null, "영업 파트 정산", "admin"));
        order.verify(mapper).insertAccess("202601", "admin", "CLOSE");
        mvc.perform(get(ROOT + "2026-01")).andExpect(status().isOk()).andExpect(jsonPath("$.data.closedAt").value(TOKEN.toString()));
    }
    @ParameterizedTest @ValueSource(strings = {"2026-13", "2026-00", "202601"})
    void invalidMonthRejectsEveryOperationBeforeDatabase(String month) throws Exception {
        mvc.perform(get(ROOT + month)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("yearMonth"));
        mvc.perform(get(ROOT + month + "/reopen-impact")).andExpect(status().isBadRequest());
        mvc.perform(put(ROOT + month + "/confirm").contentType("application/json").content(version())).andExpect(status().isBadRequest());
        mvc.perform(put(ROOT + month + "/reopen").contentType("application/json").content(reopenBody("정정"))).andExpect(status().isBadRequest());
        verifyNoInteractions(mapper, snapshots, management);
    }
    @ParameterizedTest @ValueSource(strings = {"confirm", "reopen"})
    void omittedVersionIsNotExplicitNull(String operation) throws Exception {
        mvc.perform(put(ROOT + "2026-01/" + operation).contentType("application/json").content("{\"reason\":\"정정\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("modifiedAt"));
        verifyNoInteractions(mapper);
    }
    @ParameterizedTest @ValueSource(strings = {"2025-12", "2026-02", "2026-04", "2026-05"})
    void beforeBaseSkippedCurrentAndFutureMonthsDoNotWrite(String month) throws Exception {
        mvc.perform(put(ROOT + month + "/confirm").contentType("application/json").content(version()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("yearMonth"));
        verifyNoInteractions(snapshots, settlements); verify(mapper, never()).saveClose(any());
    }
    @ParameterizedTest @ValueSource(strings = {"confirm", "reopen"})
    void staleVersionRejectsBeforeAnySideEffect(String action) throws Exception {
        closed();
        mvc.perform(put(ROOT + "2026-01/" + action).contentType("application/json")
            .content("{\"modifiedAt\":null,\"reason\":\"정정\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."));
        verifyNoInteractions(snapshots, checks, settlements); verify(mapper, never()).insertHistory(any());
    }
    @Test void publicLatestMonthReopenKeepsPartSettlementConfirmed() throws Exception {
        closed();
        mvc.perform(put(ROOT + "2026-01/reopen").contentType("application/json").content(reopenBody(" 정정 사유 ")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.isClosed").value(false));
        verify(snapshots).unfix("202601"); verifyNoInteractions(settlements);
        verify(mapper).saveClose(new Mutation("202601", "WORK", "admin", TOKEN));
        verify(mapper).insertHistory(new History("202601", "OPEN", " 정정 사유 ", null, "admin"));
        verify(mapper).insertAccess("202601", "admin", "UNCLOSE");
        verify(mapper, never()).insertAccess(any(), any(), eq("CLOSE"));
    }
    @ParameterizedTest @ValueSource(strings = {"", " ", "\t\n", "　"})
    void blankReasonIsFieldErrorWithoutHistory(String reason) throws Exception {
        closed();
        mvc.perform(put(ROOT + "2026-01/reopen").contentType("application/json").content(reopenBody(reason)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));
        verifyNoInteractions(mapper, snapshots);
    }
    @Test void overFiveHundredDatabaseCharactersIsRejectedWithoutTruncation() throws Exception {
        closed();
        mvc.perform(put(ROOT + "2026-01/reopen").contentType("application/json").content(reopenBody("가".repeat(501))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));
        verify(mapper, never()).insertHistory(any());
    }
    @Test void nonAdministratorPolicyPreventsReopenBeforeWrites() throws Exception {
        closed(); when(actor.roles()).thenReturn(Set.of("FIN"));
        assertThat(MonthCloseRules.canReopen(Set.of("ADMIN"))).isTrue();
        assertThat(MonthCloseRules.canReopen(Set.of("FIN"))).isFalse();
        mvc.perform(put(ROOT + "2026-01/reopen").contentType("application/json").content(reopenBody("정정"))).andExpect(status().isBadRequest());
        verifyNoInteractions(snapshots, management); verify(mapper, never()).saveClose(any());
        // The role is checked before the month locks and the version check.
        verify(mapper, never()).lockSources(); verify(mapper, never()).selectHeader(any());
    }
    @Test void olderClosedMonthCannotBeReopened() throws Exception {
        closed(); when(mapper.latestClosedMonth()).thenReturn("202602");
        mvc.perform(put(ROOT + "2026-01/reopen").contentType("application/json").content(reopenBody("정정"))).andExpect(status().isBadRequest());
        verifyNoInteractions(snapshots); verify(mapper, never()).insertHistory(any());
    }
    @Test void snapshotFailureStopsSettlementCloseHistoryAndSuccessLog() throws Exception {
        doThrow(new IllegalStateException("snapshot unavailable")).when(snapshots).freeze(any(), any(), any());
        mvc.perform(put(ROOT + "2026-01/confirm").contentType("application/json").content(version())).andExpect(status().is5xxServerError());
        verifyNoInteractions(settlements); verify(mapper, never()).saveClose(any()); verify(mapper, never()).insertHistory(any());
        verify(mapper, never()).insertAccess(any(), any(), any());
    }
}
