package com.company.common.financeitems;

import com.company.common.common.FinanceItemDefinitions;
import com.company.common.financeitems.controller.CommonFinanceItemController;
import com.company.common.financeitems.dto.FinanceItemContracts.*;
import com.company.common.financeitems.mapper.FinanceItemMapper;
import com.company.common.financeitems.service.*;
import com.company.common.support.service.*;
import com.company.common.uploads.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonFinanceItemController.class)
@Import({CommonFinanceItemService.class,FinanceTaxService.class,CurrentBusinessActor.class})
class CommonFinanceItemControllerTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @MockBean FinanceItemMapper mapper; @MockBean FinanceItemDefinitions definitions;
    @MockBean BusinessCalendar calendar; @MockBean CommonUploadService uploads; @MockBean ExcelExportService excel;
    final Instant at=Instant.parse("2026-01-02T00:00:00Z");
    Item item(String id,BigDecimal amount){return new Item(id,id==null?null:at,"SGA",null,"임대료",amount,BigDecimal.ZERO,null,"Y","N",null,null);}
    TaxPayment payment(String paid){return new TaxPayment("2026:CNFM",2026,"CNFM",LocalDate.of(2027,3,31),"Y".equals(paid)?BigDecimal.TEN:null,BigDecimal.TEN,paid,null,"예정",at);}
    TaxEstimate estimate(){return new TaxEstimate(2026,BigDecimal.ZERO,BigDecimal.ZERO,false,at);}
    MonthInput input(List<Item> items){return new MonthInput(items,List.of(),List.of(),List.of(),List.of(),new EstimateInput(2026,BigDecimal.ZERO,at),List.of(),new CardPayment(BigDecimal.ZERO,false,null),at);}
    @BeforeEach void setup(){
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026,1)); when(calendar.now()).thenReturn(at);
        when(definitions.definitions()).thenReturn(List.of()); when(mapper.items(anyString())).thenReturn(List.of());
        when(mapper.loans(anyString())).thenReturn(List.of()); when(mapper.transfers(anyString())).thenReturn(List.of()); when(mapper.adjustments(anyString())).thenReturn(List.of()); when(mapper.cards(anyString())).thenReturn(List.of()); when(mapper.closedMonths()).thenReturn(List.of()); when(mapper.payments(anyString())).thenReturn(List.of());
        when(mapper.estimate(anyString())).thenReturn(estimate()); when(mapper.baseline(anyString())).thenReturn(at); when(mapper.code(anyString(),anyString())).thenAnswer(call->call.getArgument(1));
        when(mapper.updateEstimate(any())).thenReturn(1); when(mapper.saveCardPayment(any(),any())).thenReturn(1); when(mapper.history(any())).thenReturn(1); when(mapper.insertItem(any())).thenReturn(1); when(mapper.updateItem(any())).thenReturn(1); when(mapper.confirm(any())).thenReturn(1); when(mapper.nextItem(anyString())).thenReturn(1L);
    }
    @Test void publicMonthKeepsEmptyScaffoldAndDoesNotPrepareYear()throws Exception{mvc.perform(get("/api/common/finance-items/202601")).andExpect(status().isOk()).andExpect(jsonPath("$.data.months.length()").value(12)).andExpect(jsonPath("$.data.items.length()").value(0)); verify(mapper,never()).prepareYear(any(),any());}
    @Test void invalidBoundaryMonthIsFieldError()throws Exception{mvc.perform(get("/api/common/finance-items/202613")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("yearMonth"));}
    @Test void repeatedRowsAreUnsavedAndSourceFree()throws Exception{when(mapper.items("202512")).thenReturn(List.of(item("9",BigDecimal.TEN)));mvc.perform(get("/api/common/finance-items/202601")).andExpect(status().isOk()).andExpect(jsonPath("$.data.autoFillCount").value(1)).andExpect(jsonPath("$.data.items[0].itemId").isEmpty()).andExpect(jsonPath("$.data.items[0].autoFillYn").value("Y"));verify(mapper,never()).insertItem(any());}
    @Test void anyStoredRowPreventsAutomaticRefill()throws Exception{when(mapper.items("202601")).thenReturn(List.of(item("1",BigDecimal.ZERO)));mvc.perform(get("/api/common/finance-items/202601")).andExpect(jsonPath("$.data.autoFillCount").value(0));}
    @Test void previousItemsExcludeSavedAndDraftKeys()throws Exception{when(mapper.items("202512")).thenReturn(List.of(item("1",BigDecimal.TEN)));mvc.perform(get("/api/common/finance-items/202601/previous-items").param("existingKeys","SGA:임대료")).andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(0));verify(mapper,never()).insertItem(any());}
    @Test void zeroAmountSavesWithoutAuthentication()throws Exception{mvc.perform(put("/api/common/finance-items/202601").contentType("application/json").content(json.writeValueAsBytes(input(List.of(item(null,BigDecimal.ZERO)))))).andExpect(status().isOk());verify(mapper).insertItem(argThat(w->w.row().amount().signum()==0));}
    @Test void indexedMissingAmountWritesNothing()throws Exception{var body=input(List.of(item(null,BigDecimal.ZERO),item(null,BigDecimal.ONE),item(null,null)));mvc.perform(put("/api/common/finance-items/202601").contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("items[2].amount"));verify(mapper,never()).insertItem(any());}
    @Test void closedMonthRejectsWrite()throws Exception{when(mapper.isClosed("202601")).thenReturn(true);mvc.perform(put("/api/common/finance-items/202601").contentType("application/json").content(json.writeValueAsBytes(input(List.of())))).andExpect(status().isBadRequest());verify(mapper,never()).updateEstimate(any());}
    @Test void staleBaselineProtectsDeletedRows()throws Exception{when(mapper.baseline("202601")).thenReturn(at.plusSeconds(1));mvc.perform(put("/api/common/finance-items/202601").contentType("application/json").content(json.writeValueAsBytes(input(List.of())))).andExpect(status().isConflict());verify(mapper,never()).deleteItem(any());}
    @Test void omittedStoredItemIsDeletedWithVersion()throws Exception{when(mapper.items("202601")).thenReturn(List.of(item("1",BigDecimal.TEN)));when(mapper.deleteItem(any())).thenReturn(1);mvc.perform(put("/api/common/finance-items/202601").contentType("application/json").content(json.writeValueAsBytes(input(List.of())))).andExpect(status().isOk());verify(mapper).deleteItem(new Version("202601","1",at));}
    @Test void loanMustBePositive()throws Exception{var b=input(List.of());var body=new MonthInput(b.items(),List.of(new Loan(null,null,LocalDate.of(2026,1,1),"IN",BigDecimal.ZERO,null,null)),b.adjustments(),b.transfers(),b.cards(),b.taxEstimate(),b.taxPayments(),b.cardPayment(),at);mvc.perform(put("/api/common/finance-items/202601").contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("loans[0].amount"));}
    @Test void loanOutsideSelectedMonthIsRejected()throws Exception{var b=input(List.of());var body=new MonthInput(b.items(),List.of(new Loan(null,null,LocalDate.of(2026,2,1),"IN",BigDecimal.ONE,null,null)),b.adjustments(),b.transfers(),b.cards(),b.taxEstimate(),b.taxPayments(),b.cardPayment(),at);mvc.perform(put("/api/common/finance-items/202601").contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("loans[0].date"));}
    @Test void publicTaxPreparationIsExplicit()throws Exception{mvc.perform(post("/api/common/finance-items/tax-years/2026")).andExpect(status().isOk());verify(mapper).prepareYear("2026","admin");verify(mapper).preparePayments("2026","admin");}
    @Test void nonIntegerTaxYearNeverWrites()throws Exception{mvc.perform(post("/api/common/finance-items/tax-years/no")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("taxYear"));verify(mapper,never()).prepareYear(any(),any());}
    @Test void missingActualDateNeverConfirms()throws Exception{mvc.perform(post("/api/common/finance-items/tax-payments/2026:CNFM/confirm").contentType("application/json").content(json.writeValueAsBytes(new ConfirmInput(null,BigDecimal.TEN,at)))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("actualPaymentDate"));verify(mapper,never()).confirm(any());}
    @Test void closedViewStillAllowsIndependentConfirmation()throws Exception{when(mapper.isClosed(any())).thenReturn(true);when(mapper.payment(any())).thenReturn(payment("N"),payment("Y"));mvc.perform(post("/api/common/finance-items/tax-payments/2026:CNFM/confirm").contentType("application/json").content(json.writeValueAsBytes(new ConfirmInput(LocalDate.of(2026,1,2),BigDecimal.TEN,at)))).andExpect(status().isOk()).andExpect(jsonPath("$.data.paidYn").value("Y"));verify(mapper,never()).updateProfit(any(),any());}
    @Test void alreadyPaidCannotBeConfirmedAgain()throws Exception{when(mapper.payment(any())).thenReturn(payment("Y"));mvc.perform(post("/api/common/finance-items/tax-payments/2026:CNFM/confirm").contentType("application/json").content(json.writeValueAsBytes(new ConfirmInput(LocalDate.of(2026,1,2),BigDecimal.TEN,at)))).andExpect(status().isBadRequest());verify(mapper,never()).confirm(any());}
    @Test void stalePaymentSqlVersionProducesConflict()throws Exception{when(mapper.payment(any())).thenReturn(payment("N"));when(mapper.confirm(any())).thenReturn(0);mvc.perform(post("/api/common/finance-items/tax-payments/2026:CNFM/confirm").contentType("application/json").content(json.writeValueAsBytes(new ConfirmInput(LocalDate.of(2026,1,2),BigDecimal.TEN,at)))).andExpect(status().isConflict());}
}
