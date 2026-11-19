package com.company.common.bills.controller;

import com.company.common.bills.mapper.BillMapper;
import com.company.common.bills.mapper.BillRows.*;
import com.company.common.bills.service.*;
import com.company.common.common.*;
import com.company.common.calculation.dto.CalculationRuleContracts.*;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonBillController.class)
@ContextConfiguration(classes={CommonBillController.class,CommonBillService.class,BillDunningService.class,
    BillingCalculationRules.class,BillingNumberAllocator.class,CurrentBusinessActor.class,
    CodeExceptionHandler.class,ApiExceptionHandler.class})
class CommonBillControllerTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @MockBean BillMapper mapper; @MockBean ProjectMapper projects;
    @MockBean BusinessCalendar calendar; @MockBean BillingNumberMapper numberMapper;
    @MockBean CalculationRuleService metadata; @MockBean BillLocalMailbox mailbox;
    @MockBean PlatformTransactionManager transactions;
    final LocalDate today=LocalDate.of(2026,11,15);
    Map<String,Invoice> rows;
    @BeforeEach void setup() {
        rows=new LinkedHashMap<>(); rows.put("B-202611-001",invoice(false,false,0,"MO",true,"202611"));
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(calendar.today()).thenReturn(today); when(calendar.currentMonth()).thenReturn(YearMonth.of(2026,11));
        when(calendar.fromDatabaseMonth(anyString())).thenAnswer(c -> YearMonth.parse(((String)c.getArgument(0)).substring(0,4)+"-"+((String)c.getArgument(0)).substring(4)));
        when(calendar.toDatabaseMonth(any())).thenAnswer(c -> ((YearMonth)c.getArgument(0)).toString().replace("-",""));
        when(metadata.definition(anyString())).thenAnswer(c -> new Definition(c.getArgument(0),"DB formula","DB details"));
        when(metadata.effectiveStandard(anyString(),any())).thenAnswer(c -> new Standard(c.getArgument(0),c.getArgument(1),new BigDecimal(switch((String)c.getArgument(0)) {case "DUN_D1" -> "7";case "DUN_D2" -> "14";default -> "30";})));
        when(mapper.invoice(anyString())).thenAnswer(c -> rows.get(c.getArgument(0)));
        when(mapper.lockInvoice(anyString())).thenAnswer(c -> rows.get(c.getArgument(0)));
        when(mapper.yearRows(anyInt())).thenAnswer(c -> rows.values().stream().filter(r -> r.billMonth().startsWith(String.valueOf((int)c.getArgument(0)))).toList());
        when(mapper.project(anyString())).thenReturn(new Project("P-0001","MO","ING",LocalDate.of(2026,1,1),LocalDate.of(2027,12,31)));
        when(mapper.normalReceipts(anyString())).thenAnswer(c -> rows.containsKey(c.getArgument(0))?rows.get(c.getArgument(0)).receiptSumAmount():BigDecimal.ZERO);
        when(mapper.receipts(anyString())).thenReturn(List.of());
        when(numberMapper.nextMonthNumber(anyString())).thenReturn(2); when(numberMapper.nextProjectOrder(anyString())).thenReturn(2);
        when(mapper.insert(any())).thenAnswer(c -> { Write w=c.getArgument(0);rows.put(w.billId(),written(w));return 1; });
        when(mapper.update(any())).thenAnswer(c -> {Write w=c.getArgument(0);rows.put(w.billId(),written(w));return 1;});
        when(mapper.cancel(any())).thenAnswer(c -> { Cancellation w=c.getArgument(0);Invoice r=rows.get(w.billId());rows.put(w.billId(),new Invoice(r.billId(),r.projectId(),r.billOrder(),r.billMonth(),r.billDate(),r.supplyAmount(),r.vatRate(),r.vatAmount(),r.totalAmount(),r.receiptSumAmount(),r.unpaidAmount(),r.payDueDate(),r.taxInvoiceNo(),true,w.reason(),w.actor(),Instant.parse("2026-11-15T00:00:00Z"),r.projectName(),r.customerName(),r.projectType(),r.projectStatus(),r.projectStart(),r.projectEnd(),false,r.closed(),r.normalReceiptCount()));return 1;});
        when(mapper.monthly(anyString(),anyString())).thenReturn(List.of(new BillingCalculationRules.MonthlyInput("MO","NEW",BigDecimal.ONE,new BigDecimal("2000"))));
        when(mapper.recipients(any())).thenReturn(List.of(new Recipient("admin","관리자","admin@example.test")));
        when(mapper.notification(any())).thenReturn(1);when(mapper.history(any())).thenReturn(1);
        when(mailbox.deliver(anyList(),anyString())).thenReturn(java.nio.file.Path.of("billing-mail","accepted.eml"));
    }
    Invoice invoice(boolean cancelled,boolean closed,int receipts,String type,boolean latest,String month) {
        return new Invoice("B-202611-001","P-0001",1,month,today,new BigDecimal("1000"),BigDecimal.TEN,new BigDecimal("100"),new BigDecimal("1100"),new BigDecimal(receipts),new BigDecimal(1100-receipts),today.minusDays(15),null,cancelled,null,null,null,"시험 프로젝트","시험 거래처",type,"ING",LocalDate.of(2026,1,1),LocalDate.of(2027,12,31),latest,closed,receipts>0?1:0);
    }
    Invoice written(Write w) {
        return new Invoice(w.billId(),w.projectId(),w.billOrder(),w.billMonth(),w.values().billDate(),w.amounts().supplyAmount(),w.amounts().vatRate(),w.amounts().vatAmount(),w.amounts().totalAmount(),w.receipts(),w.amounts().totalAmount().subtract(w.receipts()),w.values().payDueDate(),w.values().taxInvoiceNo(),false,null,null,null,"시험 프로젝트","시험 거래처","MO","ING",LocalDate.of(2026,1,1),LocalDate.of(2027,12,31),true,false,w.receipts().signum()>0?1:0);
    }
    ObjectNode values() {return json.createObjectNode().put("supplyAmount",2000).put("vatRate",10).put("billDate","2026-11-15").put("payDueDate","2026-12-25").putNull("taxInvoiceNo");}
    ObjectNode create() {return values().put("projectId","P-0001").put("billMonth","202611");}
    String target(){return "/api/common/bills/B-202611-001";}
    @Test void publicCreateStoresAndReadsNewBillAsAdmin() throws Exception {
        mvc.perform(post("/api/common/bills").contentType("application/json").content(create().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.bill.billId").value("B-202611-002"));
        verify(mapper).insert(argThat(w -> w.actor().equals("admin") && w.billOrder()==2));
        mvc.perform(get("/api/common/bills/B-202611-002")).andExpect(status().isOk()).andExpect(jsonPath("$.data.bill.supplyAmount").value(2000)).andExpect(jsonPath("$.data.bill.vatRate").value(10)).andExpect(jsonPath("$.data.bill.payDueDate").value("2026-12-25"));
        assertThat(rows).hasSize(2);
    }
    @Test void defaultTaxRateIsTen() throws Exception { var body=create();body.remove("vatRate");mvc.perform(post("/api/common/bills").contentType("application/json").content(body.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.bill.vatRate").value(10)); }
    @Test void taxRateOverHundredDoesNotInsert() throws Exception {mvc.perform(post("/api/common/bills").contentType("application/json").content(create().put("vatRate",101).toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("vatRate"));verify(mapper,never()).insert(any());}
    @Test void earlierDueDateDoesNotInsert() throws Exception {mvc.perform(post("/api/common/bills").contentType("application/json").content(create().put("payDueDate","2026-11-14").toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("payDueDate"));verify(mapper,never()).insert(any());}
    @Test void closedMonthDoesNotInsert() throws Exception {when(mapper.closed("202611")).thenReturn(true);mvc.perform(post("/api/common/bills").contentType("application/json").content(create().toString())).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());}
    @Test void priorYearDoesNotInsert() throws Exception {mvc.perform(post("/api/common/bills").contentType("application/json").content(create().put("billMonth","202511").toString())).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());}
    @Test void duplicateWarningDoesNotAllocateUntilConfirmed() throws Exception {
        when(mapper.exists("P-0001","202611")).thenReturn(true);
        mvc.perform(post("/api/common/bills").contentType("application/json").content(create().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty()).andExpect(jsonPath("$.warning").isNotEmpty());
        verify(numberMapper,never()).nextMonthNumber(anyString());verify(mapper,never()).insert(any());
        mvc.perform(post("/api/common/bills").contentType("application/json").content(create().put("duplicateConfirmed",true).toString())).andExpect(status().isOk());assertThat(rows).hasSize(2);
    }
    @Test void cancelledIdsAndOrdersAreNotReused() throws Exception {
        var original=invoice(true,false,0,"MO",false,"202611");rows.put(original.billId(),original);
        mvc.perform(post("/api/common/bills").contentType("application/json").content(create().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.bill.billOrder").value(2));
        assertThat(rows.get(original.billId())).isEqualTo(original);verify(numberMapper).lockBills();
    }
    @ParameterizedTest @ValueSource(strings={"TK","IN"}) void nextMonthRefusesIneligibleType(String type) throws Exception {
        rows.put("B-202611-001",invoice(false,false,0,type,true,"202611"));mvc.perform(post(target()+"/next-month")).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());
    }
    @Test void publicNextMonthUsesEstimateAndPreservesOriginal() throws Exception {
        var original=rows.get("B-202611-001");mvc.perform(post(target()+"/next-month")).andExpect(status().isOk()).andExpect(jsonPath("$.data.bill.billMonth").value("202612")).andExpect(jsonPath("$.data.bill.billDate").value("2026-11-15")).andExpect(jsonPath("$.data.bill.payDueDate").value("2027-01-25")).andExpect(jsonPath("$.data.bill.taxInvoiceNo").isEmpty()).andExpect(jsonPath("$.data.bill.vatRate").value(10));
        assertThat(rows.get(original.billId())).isEqualTo(original);verify(mapper).insert(argThat(w -> w.actor().equals("admin") && w.billOrder()==2));
    }
    @Test void nextMonthMissingIs404() throws Exception {mvc.perform(post("/api/common/bills/missing/next-month")).andExpect(status().isNotFound());verify(mapper,never()).insert(any());}
    @Test void nextMonthDuplicateDoesNotInsert() throws Exception {when(mapper.exists("P-0001","202612")).thenReturn(true);mvc.perform(post(target()+"/next-month")).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());}
    @Test void nextMonthClosedDoesNotInsert() throws Exception {when(mapper.closed("202612")).thenReturn(true);mvc.perform(post(target()+"/next-month")).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());}
    @Test void nextMonthZeroSupplyDoesNotInsert() throws Exception {when(mapper.monthly(anyString(),anyString())).thenReturn(List.of());mvc.perform(post(target()+"/next-month")).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());}
    @Test void nonlatestHasNoQuickActions() throws Exception {rows.put("B-202611-001",invoice(false,false,0,"MO",false,"202611"));mvc.perform(get(target())).andExpect(status().isOk()).andExpect(jsonPath("$.data.bill.canCopy").value(false)).andExpect(jsonPath("$.data.bill.canNextMonth").value(false));mvc.perform(post(target()+"/next-month")).andExpect(status().isBadRequest());}
    @Test void publicUpdateRecalculatesAmountsKeepsIdentityAndReceipts() throws Exception {
        rows.put("B-202611-001",invoice(false,false,500,"MO",true,"202611"));
        mvc.perform(put(target()).contentType("application/json").content(values().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.bill.vatAmount").value(200)).andExpect(jsonPath("$.data.bill.totalAmount").value(2200)).andExpect(jsonPath("$.data.bill.unpaidAmount").value(1700)).andExpect(jsonPath("$.data.bill.projectId").value("P-0001")).andExpect(jsonPath("$.data.bill.billMonth").value("202611")).andExpect(jsonPath("$.data.bill.billOrder").value(1));
        verify(mapper).update(argThat(w -> w.actor().equals("admin") && w.receipts().intValue()==500));
    }
    @Test void updateBelowReceiptsPreservesOriginal() throws Exception {var original=invoice(false,false,1100,"MO",true,"202611");rows.put(original.billId(),original);mvc.perform(put(target()).contentType("application/json").content(values().put("supplyAmount",500).toString())).andExpect(status().isBadRequest());assertThat(rows.get(original.billId())).isEqualTo(original);verify(mapper,never()).update(any());}
    @Test void updateClosedMonthPreservesOriginal() throws Exception {when(mapper.closed("202611")).thenReturn(true);mvc.perform(put(target()).contentType("application/json").content(values().toString())).andExpect(status().isBadRequest());verify(mapper,never()).update(any());}
    @Test void updatePriorYearRefused() throws Exception {rows.put("B-202611-001",invoice(false,false,0,"MO",true,"202511"));mvc.perform(put(target()).contentType("application/json").content(values().toString())).andExpect(status().isBadRequest());verify(mapper,never()).update(any());}
    @Test void updateCancelledRefused() throws Exception {rows.put("B-202611-001",invoice(true,false,0,"MO",true,"202611"));mvc.perform(put(target()).contentType("application/json").content(values().toString())).andExpect(status().isBadRequest());verify(mapper,never()).update(any());}
    @Test void updateEarlierDueDatePreservesDate() throws Exception {var original=rows.get("B-202611-001");mvc.perform(put(target()).contentType("application/json").content(values().put("payDueDate","2026-11-01").toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("payDueDate"));assertThat(rows.get(original.billId())).isEqualTo(original);}
    @Test void updateCannotMutateIdentity() throws Exception {mvc.perform(put(target()).contentType("application/json").content(values().put("projectId","other").toString())).andExpect(status().isBadRequest());verify(mapper,never()).update(any());}
    @Test void publicCancelPreservesRowAndMetadataExcludesSummary() throws Exception {
        mvc.perform(post(target()+"/cancel").contentType("application/json").content("{\"reason\":\"사유\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.bill.cancelled").value(true)).andExpect(jsonPath("$.data.bill.cancelledBy").value("admin")).andExpect(jsonPath("$.data.bill.cancelReason").value("사유")).andExpect(jsonPath("$.data.bill.cancelledAt").isNotEmpty());
        mvc.perform(get("/api/common/bills").param("year","2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].status").value("CNCL")).andExpect(jsonPath("$.data.summary.yearBillCount").value(0)).andExpect(jsonPath("$.data.totals.totalAmount").value(0));
        assertThat(rows).hasSize(1);
    }
    @Test void blankCancelReasonRefused() throws Exception {mvc.perform(post(target()+"/cancel").contentType("application/json").content("{\"reason\":\"  \"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));verify(mapper,never()).cancel(any());}
    @Test void activeReceiptPreventsCancellation() throws Exception {rows.put("B-202611-001",invoice(false,false,1,"MO",true,"202611"));mvc.perform(post(target()+"/cancel").contentType("application/json").content("{\"reason\":\"사유\"}")).andExpect(status().isBadRequest());verify(mapper,never()).cancel(any());}
    @Test void closedMonthPreventsCancellation() throws Exception {when(mapper.closed("202611")).thenReturn(true);mvc.perform(post(target()+"/cancel").contentType("application/json").content("{\"reason\":\"사유\"}")).andExpect(status().isBadRequest());verify(mapper,never()).cancel(any());}
    @Test void repeatCancelPreservesFirstMetadata() throws Exception {mvc.perform(post(target()+"/cancel").contentType("application/json").content("{\"reason\":\"첫 사유\"}")).andExpect(status().isOk());var original=rows.get("B-202611-001");mvc.perform(post(target()+"/cancel").contentType("application/json").content("{\"reason\":\"다른 사유\"}")).andExpect(status().isBadRequest());assertThat(rows.get(original.billId())).isEqualTo(original);}
    @Test void publicResendRecordsServerRecipientsAfterDelivery() throws Exception {mvc.perform(post(target()+"/dunning-resend")).andExpect(status().isOk()).andExpect(jsonPath("$.data.history.step").value(2)).andExpect(jsonPath("$.data.history.resend").value(true)).andExpect(jsonPath("$.data.history.recipientDescription").value("관리자 (admin)"));var order=inOrder(mailbox,mapper);order.verify(mailbox).deliver(anyList(),anyString());order.verify(mapper).notification(argThat(n -> n.userId().equals("admin") && n.actor().equals("admin")));order.verify(mapper).history(argThat(h -> h.resend() && h.step()==2));}
    @Test void resendMissingIs404AndNoDelivery() throws Exception {mvc.perform(post("/api/common/bills/missing/dunning-resend")).andExpect(status().isNotFound());verify(mailbox,never()).deliver(anyList(),anyString());verify(mapper,never()).history(any());}
    @Test void repeatedStageResendRefused() throws Exception {when(mapper.resent("B-202611-001",2)).thenReturn(true);mvc.perform(post(target()+"/dunning-resend")).andExpect(status().isBadRequest());verify(mailbox,never()).deliver(anyList(),anyString());verify(mapper,never()).history(any());}
    @Test void failedMailLeavesNoSuccessHistory() throws Exception {when(mailbox.deliver(anyList(),anyString())).thenThrow(CodeFailure.invalid("delivery","배달 실패"));mvc.perform(post(target()+"/dunning-resend")).andExpect(status().isBadRequest());verify(mapper,never()).history(any());verify(mapper,never()).notification(any());}
    @Test void failedSystemNotificationDoesNotRecordHistory() throws Exception {when(mapper.notification(any())).thenReturn(0);mvc.perform(post(target()+"/dunning-resend")).andExpect(status().isBadRequest());verify(mapper,never()).history(any());verify(mailbox).discard(any());}
    @Test void cancelledBillCannotResend() throws Exception {rows.put("B-202611-001",invoice(true,false,0,"MO",true,"202611"));mvc.perform(post(target()+"/dunning-resend")).andExpect(status().isBadRequest());verify(mailbox,never()).deliver(anyList(),anyString());}
    @Test void filteredTotalsDoNotNarrowYearSummary() throws Exception {mvc.perform(get("/api/common/bills").param("year","2026").param("projectId","other")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.summary.yearBillCount").value(1)).andExpect(jsonPath("$.data.totals.totalAmount").value(0));}
    @Test void detailMissingIs404() throws Exception {mvc.perform(get("/api/common/bills/missing")).andExpect(status().isNotFound());}
    @Test void updateMissingIs404() throws Exception {mvc.perform(put("/api/common/bills/missing").contentType("application/json").content(values().toString())).andExpect(status().isNotFound());verify(mapper,never()).update(any());}
    @Test void cancelMissingIs404() throws Exception {mvc.perform(post("/api/common/bills/missing/cancel").contentType("application/json").content("{\"reason\":\"사유\"}")).andExpect(status().isNotFound());verify(mapper,never()).cancel(any());}
    @Test void ineligibleProjectCannotCreate() throws Exception {when(mapper.project(anyString())).thenReturn(new Project("P-0001","IN","ING",today,today.plusYears(1)));mvc.perform(post("/api/common/bills").contentType("application/json").content(create().toString())).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());}
    @Test void sourceCopyCannotChangeMonth() throws Exception {mvc.perform(post("/api/common/bills").contentType("application/json").content(create().put("sourceBillId","B-202611-001").put("billMonth","202612").toString())).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());}
    @ParameterizedTest @ValueSource(strings={"0","-1","1.5","1000000000000000"}) void invalidSupplyDoesNotInsert(String supply) throws Exception {mvc.perform(post("/api/common/bills").contentType("application/json").content(create().put("supplyAmount",new BigDecimal(supply)).toString())).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());}
    @Test void fractionalRateBeyondTwoDigitsRefused() throws Exception {mvc.perform(post("/api/common/bills").contentType("application/json").content(create().put("vatRate",new BigDecimal("10.001")).toString())).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());}
    @Test void exhaustedMonthDoesNotInsert() throws Exception {when(numberMapper.nextMonthNumber(anyString())).thenReturn(1000);mvc.perform(post("/api/common/bills").contentType("application/json").content(create().toString())).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());}
    @Test void exhaustedProjectOrderDoesNotInsert() throws Exception {when(numberMapper.nextProjectOrder(anyString())).thenReturn(1000);mvc.perform(post("/api/common/bills").contentType("application/json").content(create().toString())).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());}
    @Test void paidBillCannotResend() throws Exception {rows.put("B-202611-001",invoice(false,false,1100,"MO",true,"202611"));mvc.perform(post(target()+"/dunning-resend")).andExpect(status().isBadRequest());verify(mailbox,never()).deliver(anyList(),anyString());}
    @Test void failureCanBeRetriedWithoutSuccessHistory() throws Exception {when(mailbox.deliver(anyList(),anyString())).thenThrow(CodeFailure.invalid("delivery","실패")).thenReturn(java.nio.file.Path.of("billing-mail","accepted.eml"));mvc.perform(post(target()+"/dunning-resend")).andExpect(status().isBadRequest());verify(mapper,never()).history(any());mvc.perform(post(target()+"/dunning-resend")).andExpect(status().isOk());verify(mapper,times(1)).history(any());}
}
