package com.company.common.receipts.controller;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.BillingCalculationRules;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.receipts.mapper.ReceiptMapper;
import com.company.common.receipts.mapper.ReceiptMapper.*;
import com.company.common.receipts.service.ReceiptService;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import java.math.BigDecimal;
import java.time.*;
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

@WebMvcTest(ReceiptController.class)
@ContextConfiguration(classes={ReceiptController.class,ReceiptService.class,BillingCalculationRules.class,
    CurrentBusinessActor.class,CodeExceptionHandler.class,ApiExceptionHandler.class})
class ReceiptControllerTest {
    @Autowired MockMvc mvc;
    @MockBean ReceiptMapper mapper;
    @MockBean ProjectMapper projects;
    @MockBean CalculationRuleService definitions;
    @MockBean BusinessCalendar calendar;
    final BusinessCalendar real=new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"),ZoneOffset.UTC));
    final BigDecimal total=new BigDecimal("1000");

    @BeforeEach void setup() {
        when(calendar.today()).thenReturn(real.today());
        when(calendar.toDatabaseMonth(any())).thenAnswer(c -> real.toDatabaseMonth(c.getArgument(0)));
        when(definitions.definition(anyString())).thenAnswer(c -> new Definition(c.getArgument(0),"DB 식","DB 설명"));
        when(mapper.lockBill(anyString())).thenAnswer(c -> new Bill(c.getArgument(0),total,false));
        when(mapper.normalSum(anyString())).thenReturn(BigDecimal.ZERO);
        when(mapper.insertReceipt(any())).thenReturn("101");
        when(mapper.receipt("101")).thenReturn(receipt(false));
        when(mapper.lockReceipt("101")).thenReturn(receipt(false));
        when(mapper.cancel(anyString(),anyString(),anyString())).thenReturn(1);
    }
    ReceiptRow receipt(boolean cancelled) {
        return new ReceiptRow("101","old-bill",LocalDate.of(2026,10,20),new BigDecimal("400"),cancelled,
            cancelled?"정정":null,cancelled?"admin":null,cancelled?real.now():null,!cancelled);
    }
    String input(String amount) {
        return "{\"receiptDate\":\"2026-10-20\",\"items\":[{\"billId\":\"old-bill\",\"amount\":"+amount+"}]}";
    }
    void reject(String body,String field)throws Exception {
        mvc.perform(post("/api/common/receipts").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        verify(mapper,never()).insertReceipt(any());
        verify(mapper,never()).updateBalance(any());
    }
    @Test void anonymousPartialReceiptUsesAdminAndRecomputesNormalSum()throws Exception {
        when(mapper.normalSum("old-bill")).thenReturn(BigDecimal.ZERO,new BigDecimal("400"));
        mvc.perform(post("/api/common/receipts").contentType("application/json").content(input("400")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap()).andExpect(jsonPath("$.data.billIds[0]").value("old-bill"))
            .andExpect(jsonPath("$.data.receipts[0].receiptId").value("101"))
            .andExpect(jsonPath("$.data.receipts[0].amount").value(400))
            .andExpect(jsonPath("$.data.receipts[0].cancelReason").value(org.hamcrest.Matchers.nullValue()));
        verify(mapper).insertReceipt(new ReceiptWrite("old-bill",LocalDate.of(2026,10,20),new BigDecimal("400"),"admin"));
        verify(mapper).updateBalance(new BalanceWrite("old-bill",new BigDecimal("400"),new BigDecimal("600"),"admin"));
    }
    @Test void zeroHasRequiredExactMessageAndNoWrites()throws Exception {
        reject(input("0"),"items.amount");
        mvc.perform(post("/api/common/receipts").contentType("application/json").content(input("0")))
            .andExpect(jsonPath("$.errors[0].message").value("입금액을 입력해 주세요"));
    }
    @ParameterizedTest @ValueSource(strings={"-1","0.5","999999999999999.01","1000000000000000","null","\"400\""})
    void invalidCurrencyIsRejected(String amount)throws Exception { reject(input(amount),"items.amount"); }
    @Test void overpaymentHasExactMessageAndNoWrites()throws Exception {
        reject(input("1001"),"items.amount");
        mvc.perform(post("/api/common/receipts").contentType("application/json").content(input("1001")))
            .andExpect(jsonPath("$.errors[0].message").value("입금액이 미수금보다 큽니다"));
    }
    @Test void zeroBalanceCannotReceive()throws Exception {
        when(mapper.normalSum("old-bill")).thenReturn(total);
        reject(input("1"),"items.amount");
    }
    @Test void currentMonthClosedBlocksEvenEarlierOpenReceiptMonth()throws Exception {
        when(mapper.closed("202611")).thenReturn(true);
        reject(input("400"),"receiptDate");
    }
    @Test void receiptMonthClosedBlocksRegistration()throws Exception {
        when(mapper.closed("202610")).thenReturn(true);
        reject(input("400"),"receiptDate");
    }
    @ParameterizedTest @ValueSource(strings={"2025-10-20","2026-11-16","2026-02-30","bad",""})
    void outsideYearFutureAndInvalidDatesAreFieldErrors(String date)throws Exception {
        reject(input("400").replace("2026-10-20",date),"receiptDate");
    }
    @Test void cancelledBillCannotReceive()throws Exception {
        when(mapper.lockBill("old-bill")).thenReturn(new Bill("old-bill",total,true));
        reject(input("400"),"items.billId");
    }
    @Test void missingBillIsRegistrationFieldError()throws Exception {
        when(mapper.lockBill("old-bill")).thenReturn(null);
        reject(input("400"),"items.billId");
    }
    @Test void emptyItemsAreRejected()throws Exception {
        reject("{\"receiptDate\":\"2026-10-20\",\"items\":[]}","items");
    }
    @Test void duplicateBillIdsAreRejectedBeforeWrites()throws Exception {
        reject("{\"receiptDate\":\"2026-10-20\",\"items\":[{\"billId\":\"b\",\"amount\":1},{\"billId\":\"b\",\"amount\":2}]}","items.billId");
    }
    @Test void wholeBatchValidatedAndLocksSortedBeforeAnyInsert()throws Exception {
        reject("{\"receiptDate\":\"2026-10-20\",\"items\":[{\"billId\":\"z\",\"amount\":1},{\"billId\":\"a\",\"amount\":1001}]}","items.amount");
        var order=inOrder(projects,mapper);
        order.verify(projects).lockSources();
        order.verify(mapper).lockBill("a");
        order.verify(mapper).lockBill("z");
    }
    @Test void fullPaymentRecomputesToZero()throws Exception {
        when(mapper.normalSum("old-bill")).thenReturn(BigDecimal.ZERO,total);
        mvc.perform(post("/api/common/receipts").contentType("application/json").content(input("1000"))).andExpect(status().isOk());
        verify(mapper).updateBalance(new BalanceWrite("old-bill",total,BigDecimal.ZERO,"admin"));
    }
    @Test void anonymousCancellationIgnoresBillMonthPreservesMetadataAndRecomputes()throws Exception {
        when(mapper.closed("202609")).thenReturn(true);
        when(mapper.closed("202611")).thenReturn(true);
        when(mapper.receipt("101")).thenReturn(receipt(false),receipt(true));
        mvc.perform(post("/api/common/receipts/101/cancel").contentType("application/json").content("{\"reason\":\"  정정  \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.receipts[0].cancelled").value(true))
            .andExpect(jsonPath("$.data.receipts[0].cancelReason").value("정정"))
            .andExpect(jsonPath("$.data.receipts[0].cancelledBy").value("admin"))
            .andExpect(jsonPath("$.data.receipts[0].cancelledAt").isString())
            .andExpect(jsonPath("$.data.receipts[0].canCancel").value(false));
        verify(mapper).cancel("101","정정","admin");
        verify(mapper).closed("202610");
        verify(mapper,never()).closed("202609");
        verify(mapper,never()).closed("202611");
        verify(mapper).updateBalance(new BalanceWrite("old-bill",BigDecimal.ZERO,total,"admin"));
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"reason\":\" \"}","{\"reason\":null}"})
    void missingReasonLeavesRowsUnchanged(String body)throws Exception {
        mvc.perform(post("/api/common/receipts/101/cancel").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));
        verify(mapper,never()).cancel(anyString(),anyString(),anyString());
        verify(mapper,never()).updateBalance(any());
    }
    @Test void reasonLengthIsBounded()throws Exception {
        mvc.perform(post("/api/common/receipts/101/cancel").contentType("application/json")
            .content("{\"reason\":\""+"가".repeat(501)+"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));
        verify(mapper,never()).cancel(anyString(),anyString(),anyString());
    }
    @Test void closedReceiptMonthCannotCancel()throws Exception {
        when(mapper.closed("202610")).thenReturn(true);
        mvc.perform(post("/api/common/receipts/101/cancel").contentType("application/json").content("{\"reason\":\"정정\"}"))
            .andExpect(status().isBadRequest());
        verify(mapper,never()).cancel(anyString(),anyString(),anyString());
        verify(mapper,never()).updateBalance(any());
    }
    @Test void repeatCancellationCannotReverseBalanceTwice()throws Exception {
        when(mapper.lockReceipt("101")).thenReturn(receipt(true));
        mvc.perform(post("/api/common/receipts/101/cancel").contentType("application/json").content("{\"reason\":\"다른 사유\"}"))
            .andExpect(status().isBadRequest());
        verify(mapper,never()).cancel(anyString(),anyString(),anyString());
        verify(mapper,never()).updateBalance(any());
    }
    @Test void unknownReceiptIs404()throws Exception {
        mvc.perform(post("/api/common/receipts/102/cancel").contentType("application/json").content("{\"reason\":\"정정\"}"))
            .andExpect(status().isNotFound());
        verify(mapper,never()).cancel(anyString(),anyString(),anyString());
    }
}
