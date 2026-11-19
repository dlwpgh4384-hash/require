package com.company.common.commonops.controller;

import com.company.common.calculation.dto.CalculationRuleContracts.*;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.BillingCalculationRules;
import com.company.common.common.BillingCalculationRules.MonthlyInput;
import com.company.common.commonops.dto.BillingLookupContracts.*;
import com.company.common.commonops.mapper.BillingLookupMapper;
import com.company.common.commonops.mapper.BillingLookupMapper.*;
import com.company.common.commonops.service.BillingLookupService;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BillingLookupController.class)
@ContextConfiguration(classes={BillingLookupController.class,BillingLookupService.class,
    BillingCalculationRules.class,CodeExceptionHandler.class,ApiExceptionHandler.class})
class BillingLookupControllerTest {
    @Autowired MockMvc mvc;
    @MockBean BillingLookupMapper mapper;
    @MockBean CalculationRuleService definitions;
    @MockBean BusinessCalendar calendar;
    final BusinessCalendar real=new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"),ZoneOffset.UTC));
    List<UnpaidBill> bills;
    List<HistoryRow> histories;

    @BeforeEach void setup() {
        bills=new ArrayList<>(); histories=new ArrayList<>();
        when(calendar.today()).thenReturn(real.today());
        when(calendar.currentMonth()).thenReturn(real.currentMonth());
        when(calendar.toDatabaseMonth(any())).thenAnswer(c -> real.toDatabaseMonth(c.getArgument(0)));
        when(calendar.fromDatabaseMonth(any())).thenAnswer(c -> real.fromDatabaseMonth(c.getArgument(0)));
        when(definitions.definition(anyString())).thenAnswer(c -> new Definition(c.getArgument(0),"DB 식","DB 설명"));
        when(definitions.effectiveStandard(anyString(),any())).thenAnswer(c -> new Standard(c.getArgument(0),c.getArgument(1),
            new BigDecimal(switch((String)c.getArgument(0)) { case "VAT_RT" -> "10"; case "DUN_D1" -> "7"; case "DUN_D2" -> "30"; default -> "60"; })));
        when(mapper.projects(any())).thenReturn(List.of(new ProjectOption("selected","청구 프로젝트","고객사","MO")));
        when(mapper.statuses()).thenReturn(List.of(new StatusOption("UNPAID","미수"),new StatusOption("CNCL","취소됨")));
        when(mapper.monthly(any())).thenReturn(List.of(new MonthlyInput("MO","NEW",new BigDecimal("0.75"),new BigDecimal("10001"))));
        when(mapper.overdue(any())).thenAnswer(c -> bills);
        when(mapper.history(any())).thenAnswer(c -> histories);
        when(mapper.recipientRoles()).thenReturn(List.of(new StatusOption("PM","DB PM"),new StatusOption("SALES_LDR","DB 파트장"),
            new StatusOption("EXEC","DB 경영진"),new StatusOption("FIN","DB 재무")));
    }
    UnpaidBill bill(String id,int days) { return new UnpaidBill(id,"selected","청구 프로젝트",new BigDecimal("12345"),real.today().minusDays(days),false); }
    HistoryRow history(int sequence,int step,boolean resend,LocalDate date) {
        return new HistoryRow("old",sequence,step,date,"실제 수신자","BOTH",resend);
    }

    @Test void anonymousOptionsDefaultToKoreanCurrentMonthWithoutFabricatingEstimate()throws Exception {
        mvc.perform(get("/api/common/bill-options")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.defaultBillMonth").value("202611"))
            .andExpect(jsonPath("$.data.defaultBillDate").value("2026-11-15"))
            .andExpect(jsonPath("$.data.defaultPayDueDate").value("2026-12-25"))
            .andExpect(jsonPath("$.data.estimatedSupplyAmount").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.projects[0].customerName").value("고객사"))
            .andExpect(jsonPath("$.data.statusOptions[1].code").value("CNCL"));
        verify(mapper).projects(new OptionFilter(null,"202611")); verify(mapper,never()).monthly(any());
    }
    @Test void selectedProjectEstimateUsesStoredMonthValuesAndTypedRounding()throws Exception {
        mvc.perform(get("/api/common/bill-options").param("projectId","selected").param("billMonth","202510"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.estimatedSupplyAmount").value(7500))
            .andExpect(jsonPath("$.data.defaultPayDueDate").value("2025-11-25"));
        verify(mapper).monthly(new OptionFilter("selected","202510"));
        verify(definitions).definition("식-02");
        verify(definitions).effectiveStandard("VAT_RT",YearMonth.of(2025,10));
    }
    @Test void turnkeyEstimateIsNullRatherThanInventedAmount()throws Exception {
        when(mapper.projects(any())).thenReturn(List.of(new ProjectOption("selected","턴키","고객사","TK")));
        mvc.perform(get("/api/common/bill-options").param("projectId","selected"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.estimatedSupplyAmount").value(org.hamcrest.Matchers.nullValue()));
        verify(mapper,never()).monthly(any());
    }
    @Test void freeSupportDoesNotIncreaseEstimatedSupply()throws Exception {
        when(mapper.monthly(any())).thenReturn(List.of(new MonthlyInput("DP","FREE",BigDecimal.ONE,new BigDecimal("999"))));
        mvc.perform(get("/api/common/bill-options").param("projectId","selected"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.estimatedSupplyAmount").value(0));
    }
    @Test void emptyOptionsRemainSuccessful()throws Exception {
        when(mapper.projects(any())).thenReturn(List.of()); when(mapper.statuses()).thenReturn(List.of());
        mvc.perform(get("/api/common/bill-options")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projects").isEmpty()).andExpect(jsonPath("$.data.statusOptions").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"202613","202600","000011","2026-11","", "2026111"})
    void invalidMonthIsAFieldError(String month)throws Exception {
        mvc.perform(get("/api/common/bill-options").param("billMonth",month)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.errors[0].field").value("billMonth"));
        verify(mapper,never()).projects(any());
    }
    @Test void ineligibleSelectedProjectIsA400NotAnAuthorizationError()throws Exception {
        when(mapper.projects(any())).thenReturn(List.of());
        mvc.perform(get("/api/common/bill-options").param("projectId","unknown"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("projectId"));
    }
    @Test void invalidProjectIdentifierIsRejectedBeforeQuery()throws Exception {
        mvc.perform(get("/api/common/bill-options").param("projectId"," "))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("projectId"));
        verify(mapper,never()).projects(any());
    }
    @Test void emptyDunningReturnsDatabaseRoleCardsAndNoHistory()throws Exception {
        mvc.perform(get("/api/common/bill-dunning")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.cards.length()").value(3))
            .andExpect(jsonPath("$.data.cards[0].recipients[0]").value("DB PM"))
            .andExpect(jsonPath("$.data.cards[2].recipients.length()").value(4));
    }
    @Test void priorYearOutstandingBillIsIncludedAndStageThreeHasNoLaterStage()throws Exception {
        bills.add(bill("prior-year",500));
        histories.add(new HistoryRow("prior-year",1,3,real.today(),"받는 사람","BOTH",false));
        mvc.perform(get("/api/common/bill-dunning")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].billId").value("prior-year"))
            .andExpect(jsonPath("$.data.items[0].currentStep").value(3))
            .andExpect(jsonPath("$.data.items[0].canResend").value(true))
            .andExpect(jsonPath("$.data.items[0].nextNotification").value(org.hamcrest.Matchers.nullValue()));
    }
    @Test void firstOverdueDayIsVisibleBeforeNotificationThreshold()throws Exception {
        bills.add(bill("old",1));
        mvc.perform(get("/api/common/bill-dunning")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].currentStep").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.items[0].daysOverdue").value(1))
            .andExpect(jsonPath("$.data.items[0].canResend").value(false))
            .andExpect(jsonPath("$.data.items[0].nextNotification").value("2026-11-21 1단계 예정"));
    }
    @ParameterizedTest @ValueSource(ints={7,30,60})
    void stagesUseInclusiveEffectiveThresholds(int days)throws Exception {
        bills.add(bill("old",days));
        int step=days==7?1:days==30?2:3;
        mvc.perform(get("/api/common/bill-dunning")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].currentStep").value(step))
            .andExpect(jsonPath("$.data.items[0].canResend").value(true));
    }
    @Test void currentStageResendHidesActionAndHistorySortsByDateThenSequence()throws Exception {
        bills.add(bill("old",35));
        histories.add(history(3,2,true,real.today()));
        histories.add(history(2,2,false,real.today()));
        histories.add(history(1,1,true,real.today().minusDays(20)));
        mvc.perform(get("/api/common/bill-dunning")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].canResend").value(false))
            .andExpect(jsonPath("$.data.items[0].resendMessage").value("이 단계 다시 알림 보냄"))
            .andExpect(jsonPath("$.data.items[0].history[0].step").value(1))
            .andExpect(jsonPath("$.data.items[0].history[1].resend").value(false))
            .andExpect(jsonPath("$.data.items[0].history[2].resend").value(true));
    }
    @Test void previousStageResendDoesNotBlockNewStage()throws Exception {
        bills.add(bill("old",30)); histories.add(history(1,1,true,real.today().minusDays(5)));
        mvc.perform(get("/api/common/bill-dunning")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].canResend").value(true));
    }
    @Test void paidCancelledAndNotYetOverdueBillsCannotBecomeDunningTargets()throws Exception {
        bills.add(new UnpaidBill("paid","selected","청구",BigDecimal.ZERO,real.today().minusDays(30),false));
        bills.add(new UnpaidBill("cancelled","selected","청구",BigDecimal.ONE,real.today().minusDays(30),true));
        bills.add(bill("due-today",0));
        mvc.perform(get("/api/common/bill-dunning")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }
    @Test void invalidEffectiveThresholdsReturn400WithoutFabricatedDefaults()throws Exception {
        when(definitions.effectiveStandard(eq("DUN_D2"),any())).thenReturn(new Standard("DUN_D2",YearMonth.of(2026,11),BigDecimal.ONE));
        mvc.perform(get("/api/common/bill-dunning")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("standardCode"));
        verify(mapper,never()).overdue(any());
    }
}
