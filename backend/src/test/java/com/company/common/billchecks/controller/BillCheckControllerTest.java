package com.company.common.billchecks.controller;

import com.company.common.billchecks.mapper.BillCheckMapper;
import com.company.common.billchecks.mapper.BillCheckSources.*;
import com.company.common.billchecks.service.BillCheckService;
import com.company.common.calculation.dto.CalculationRuleContracts.*;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.BillingCalculationRules;
import com.company.common.common.BillingNumberAllocator;
import com.company.common.common.BillingNumberMapper;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BillCheckController.class)
@ContextConfiguration(classes={BillCheckController.class,BillCheckService.class,
    BillingCalculationRules.class,BillingNumberAllocator.class,CurrentBusinessActor.class,CodeExceptionHandler.class,ApiExceptionHandler.class})
class BillCheckControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean BillCheckMapper mapper;
    @MockBean BillingNumberMapper numbers;
    @MockBean ProjectMapper projects;
    @MockBean BusinessCalendar calendar;
    @MockBean CalculationRuleService definitions;
    final BusinessCalendar realCalendar=new BusinessCalendar(Clock.fixed(
        Instant.parse("2026-11-15T01:00:00Z"),ZoneOffset.UTC));
    List<Monthly> monthly;
    Map<String,Invoice> invoices;
    Set<String> closed;

    @BeforeEach void setup() {
        monthly=new ArrayList<>(List.of(new Monthly("project","점검 프로젝트","MO","person","김투입",
            LocalDate.of(2026,11,1),"NEW",BigDecimal.ONE,new BigDecimal("10000"),new BigDecimal("10000"))));
        invoices=new LinkedHashMap<>();
        closed=new HashSet<>();
        when(calendar.today()).thenReturn(realCalendar.today());
        when(calendar.currentMonth()).thenReturn(realCalendar.currentMonth());
        when(calendar.fromDatabaseMonth(any())).thenAnswer(c -> realCalendar.fromDatabaseMonth(c.getArgument(0)));
        when(calendar.toDatabaseMonth(any())).thenAnswer(c -> realCalendar.toDatabaseMonth(c.getArgument(0)));
        when(definitions.definition(anyString())).thenAnswer(c -> new Definition(c.getArgument(0),"DB 식","DB 설명"));
        when(definitions.effectiveStandard(anyString(),any())).thenAnswer(c -> new Standard(c.getArgument(0),c.getArgument(1),
            new BigDecimal(switch((String)c.getArgument(0)) {
                case "BILL_MISS_MIN" -> "100";
                case "BILL_MISS_RT" -> "1";
                case "DUN_D1" -> "7";
                case "DUN_D2" -> "30";
                default -> "60";
            })));
        when(mapper.closed(anyString())).thenAnswer(c -> closed.contains(c.getArgument(0)));
        when(mapper.monthly(any())).thenAnswer(c -> monthly);
        when(mapper.invoices(anyString())).thenAnswer(c -> invoices.values().stream()
            .filter(b -> b.billMonth.equals(c.getArgument(0)) && !b.cancelled).toList());
        when(mapper.invoice(anyString())).thenAnswer(c -> invoices.get(c.getArgument(0)));
        when(mapper.receipts(anyString())).thenReturn(new ArrayList<>());
        when(mapper.normalReceipts(anyString())).thenReturn(BigDecimal.ZERO);
        when(mapper.latestProjectBill(anyString())).thenAnswer(c -> invoices.values().stream()
            .filter(b -> !b.cancelled && b.projectId.equals(c.getArgument(0)))
            .max(Comparator.comparingInt(b -> b.billOrder)).map(b -> b.billId).orElse(null));
        when(numbers.nextMonthNumber(anyString())).thenReturn(3);
        when(numbers.nextProjectOrder(anyString())).thenReturn(3);
        when(mapper.insert(any())).thenAnswer(c -> { apply(c.getArgument(0)); return 1; });
        when(mapper.correct(any())).thenAnswer(c -> { apply(c.getArgument(0)); return 1; });
    }

    Invoice bill(String id,int order,String supply) {
        var b=new Invoice();
        b.billId=id;b.billOrder=order;b.projectId="project";b.projectName="점검 프로젝트";
        b.customerName="고객사";b.projectType="MO";b.projectStatus="ING";
        b.projectStart=LocalDate.of(2026,1,1);b.projectEnd=LocalDate.of(2026,12,31);
        b.billMonth="202611";b.billDate=realCalendar.today();b.payDueDate=LocalDate.of(2026,12,25);
        b.supplyAmount=new BigDecimal(supply);b.vatRate=BigDecimal.TEN;
        b.vatAmount=b.supplyAmount.movePointLeft(1);b.totalAmount=b.supplyAmount.add(b.vatAmount);
        b.receiptSumAmount=BigDecimal.ZERO;b.unpaidAmount=b.totalAmount;
        b.modifiedAt=READ;
        invoices.put(id,b);return b;
    }
    /** 화면이 고칠 청구를 읽었을 때의 수정 시각. [청구 고치기]는 이 값을 함께 보낸다. */
    static final Instant READ=Instant.parse("2026-11-15T00:30:00.123456Z");
    void apply(Write w) {
        var b=bill(w.billId(),w.billOrder(),w.amounts().supplyAmount().toPlainString());
        b.billMonth=w.input().checkMonth();b.billDate=w.input().billDate();b.payDueDate=w.input().payDueDate();
        b.vatRate=w.amounts().vatRate();b.vatAmount=w.amounts().vatAmount();b.totalAmount=w.amounts().totalAmount();
        b.receiptSumAmount=w.receipts();b.unpaidAmount=b.totalAmount.subtract(w.receipts());
        b.taxInvoiceNo=w.input().taxInvoiceNo();
    }
    ObjectNode input() {
        return json.createObjectNode().put("projectId","project").put("checkMonth","202611")
            .put("vatRate",10).put("billDate","2026-11-15").put("payDueDate","2026-12-25");
    }
    ResultActions command(String action,ObjectNode body)throws Exception {
        if (action.equals("correction") && !body.has("modifiedAt")) body.put("modifiedAt",READ.toString());
        return mvc.perform(post("/api/common/bill-checks/"+action).contentType("application/json").content(body.toString()));
    }
    JsonNode data(ResultActions result)throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }
    JsonNode checks()throws Exception {
        return data(mvc.perform(get("/api/common/bill-checks").param("checkMonth","202611")).andExpect(status().isOk()));
    }
    void eligible(String action) {
        if (action.equals("additional")) { closed.add("202611");bill("original",1,"5000"); }
        if (action.equals("correction")) { bill("older",1,"3000");bill("latest",2,"2000"); }
    }

    @Test void missingCreatesRecomputedSupply()throws Exception {
        var result=data(command("missing",input()).andExpect(status().isOk()));
        assertThat(result.path("bill").path("supplyAmount").decimalValue()).isEqualByComparingTo("10000");
        assertThat(result.path("bill").path("billId").asText()).isEqualTo("B-202611-003");
        assertThat(result.path("missingTaxInvoiceNumber").asBoolean()).isTrue();
    }
    @Test void correctionResponseDisablesNextMonthBeyondContractEndMonth()throws Exception {
        eligible("correction");
        doAnswer(call -> {
            Write write=call.getArgument(0);
            apply(write);
            invoices.get(write.billId()).projectEnd=LocalDate.of(2026,11,30);
            return 1;
        }).when(mapper).correct(any());
        // 명세 SFR-113-12: 다음 달이 계약 종료월을 넘으면 빠른 청구 단추 둘 다 「–」
        command("correction",input()).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bill.canCopy").value(false))
            .andExpect(jsonPath("$.data.bill.canNextMonth").value(false));
    }
    @Test void correctionResponseUsesProjectStatusNotTodayForQuickActions()throws Exception {
        eligible("correction");
        doAnswer(call -> {
            Write write=call.getArgument(0);
            apply(write);
            invoices.get(write.billId()).projectStart=LocalDate.of(2026,12,1);
            return 1;
        }).when(mapper).correct(any());
        command("correction",input()).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bill.canCopy").value(true))
            .andExpect(jsonPath("$.data.bill.canNextMonth").value(true));
    }
    @Test void additionalPreservesOriginalAndCreatesOnlyDifference()throws Exception {
        eligible("additional");var original=invoices.get("original");
        var result=data(command("additional",input()).andExpect(status().isOk()));
        assertThat(result.path("bill").path("supplyAmount").decimalValue()).isEqualByComparingTo("5000");
        assertThat(invoices.get("original")).isSameAs(original);
        verify(mapper,never()).correct(any());
    }
    @Test void correctionAddsTotalDifferenceToMaximumOrderNotWholeReference()throws Exception {
        eligible("correction");var older=invoices.get("older");
        var result=data(command("correction",input()).andExpect(status().isOk()));
        assertThat(result.path("bill").path("billId").asText()).isEqualTo("latest");
        assertThat(result.path("bill").path("supplyAmount").decimalValue()).isEqualByComparingTo("7000");
        assertThat(invoices.get("older")).isSameAs(older);
        verify(mapper,never()).insert(any());
    }
    @Test void correctionTargetsNewestBillDateInTheMonth()throws Exception {
        // 시안 bchkFix: 그 달 청구 중 청구일이 가장 늦은 청구를 고친다(차수 최대가 아님)
        eligible("correction");var latest=invoices.get("latest");
        invoices.get("older").billDate=LocalDate.of(2026,11,30);
        var result=data(command("correction",input()).andExpect(status().isOk()));
        assertThat(result.path("bill").path("billId").asText()).isEqualTo("older");
        assertThat(result.path("bill").path("supplyAmount").decimalValue()).isEqualByComparingTo("8000");
        assertThat(invoices.get("latest")).isSameAs(latest);
    }
    @Test void editedSupplyFromFormIsAccepted()throws Exception {
        var result=data(command("missing",input().put("supplyAmount",9500)).andExpect(status().isOk()));
        assertThat(result.path("bill").path("supplyAmount").decimalValue()).isEqualByComparingTo("9500");
    }
    @ParameterizedTest @ValueSource(strings={"missing","additional","correction"})
    void publicCommandsUseDevelopmentAdmin(String action)throws Exception {
        eligible(action);command(action,input()).andExpect(status().isOk());
        var captor=org.mockito.ArgumentCaptor.forClass(Write.class);
        if (action.equals("correction")) verify(mapper).correct(captor.capture());
        else verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().actor()).isEqualTo("admin");
    }
    @ParameterizedTest @ValueSource(strings={"missing","additional","correction"})
    void invalidMonthLeavesEveryInvoiceUnchanged(String action)throws Exception {
        eligible(action);var before=new LinkedHashMap<>(invoices);
        command(action,input().put("checkMonth","2026-11")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("checkMonth"));
        assertThat(invoices).isEqualTo(before);verify(mapper,never()).insert(any());verify(mapper,never()).correct(any());
    }
    @ParameterizedTest @ValueSource(strings={"missing","additional","correction"})
    void staleEligibilityIsRecomputedAfterSourceLocks(String action)throws Exception {
        eligible(action);checks();
        if (action.equals("missing")) bill("concurrent",1,"10000");
        else if (action.equals("additional")) closed.clear();
        else closed.add("202611");
        var before=new LinkedHashMap<>(invoices);
        command(action,input()).andExpect(status().isBadRequest());
        assertThat(invoices).isEqualTo(before);verify(mapper,never()).insert(any());verify(mapper,never()).correct(any());
        var order=inOrder(projects,mapper);
        order.verify(projects).lockSources();order.verify(mapper).lockMonthlySources();
        order.verify(mapper).lockInvoices();
    }
    @ParameterizedTest @ValueSource(strings={"missing","additional","correction"})
    void successRecalculatesChecksWithoutMutatingMonthlyOrCloseSources(String action)throws Exception {
        eligible(action);var before=List.copyOf(monthly);var beforeClose=Set.copyOf(closed);
        command(action,input()).andExpect(status().isOk());
        assertThat(checks().path("items").get(0).path("difference").decimalValue()).isEqualByComparingTo("0");
        assertThat(monthly).isEqualTo(before);assertThat(closed).isEqualTo(beforeClose);
    }
    @Test void correctionCannotFallBelowNormalReceipts()throws Exception {
        bill("latest",1,"15000");when(mapper.normalReceipts("latest")).thenReturn(new BigDecimal("12000"));
        command("correction",input()).andExpect(status().isBadRequest());verify(mapper,never()).correct(any());
        assertThat(invoices.get("latest").supplyAmount).isEqualByComparingTo("15000");
    }
    @Test void closedOverbillingOffersDetailAndCannotBeReduced()throws Exception {
        bill("latest",1,"15000");closed.add("202611");
        assertThat(checks().path("items").get(0).path("allowedAction").asText()).isEqualTo("DETAIL");
        command("correction",input()).andExpect(status().isBadRequest());
        command("additional",input()).andExpect(status().isBadRequest());verify(mapper,never()).correct(any());
    }
    @ParameterizedTest @ValueSource(strings={"TK","IN","FREE"})
    void nonBillableWorkCannotCreateFromCheck(String type)throws Exception {
        var source=monthly.get(0);
        monthly=List.of(new Monthly(source.projectId(),source.projectName(),type.equals("FREE")?"MO":type,
            source.developerId(),source.developerName(),source.inputStart(),type.equals("FREE")?"FREE":"NEW",
            source.monthlyMm(),source.billingPrice(),source.salesAmount()));
        assertThat(checks().path("items")).isEmpty();
        command("missing",input()).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());
    }
    @ParameterizedTest @ValueSource(strings={"supplyAmount","billId","allowClosedMonth","duplicateConfirmed"})
    // supplyAmount는 창에서 고친 공급가(숫자)만 받는다. true 같은 값은 그 칸 오류.
    void inputCannotOverrideServerIdentityOrEligibility(String field)throws Exception {
        command("missing",input().put(field,true)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value(field));verify(mapper,never()).insert(any());
    }
    @Test void numberExhaustionDoesNotInsert()throws Exception {
        when(numbers.nextMonthNumber(anyString())).thenReturn(1000);
        command("missing",input()).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());
    }
    @Test void orderExhaustionDoesNotInsert()throws Exception {
        when(numbers.nextProjectOrder(anyString())).thenReturn(1000);
        command("missing",input()).andExpect(status().isBadRequest());verify(mapper,never()).insert(any());
    }
    @ParameterizedTest @ValueSource(strings={"missing","additional"})
    void priorYearClosedMonthOffersNoActionAndCannotCreate(String action)throws Exception {
        // 사용자 결정(2026-11-19): 마감한 달의 예외(청구 등록·추가 청구)는 올해 달만
        closed.add("202511");
        if (action.equals("additional")) bill("prior-original",1,"5000").billMonth="202511";
        var rows=data(mvc.perform(get("/api/common/bill-checks").param("checkMonth","202511")).andExpect(status().isOk())).path("items");
        assertThat(rows.get(0).path("allowedAction").asText()).isEqualTo("NONE");
        command(action,input().put("checkMonth","202511")).andExpect(status().isBadRequest());
        verify(mapper,never()).insert(any());verify(mapper,never()).correct(any());
    }
    @Test void priorYearOpenMonthCannotCorrect()throws Exception {
        bill("prior-original",1,"5000").billMonth="202511";
        command("correction",input().put("checkMonth","202511")).andExpect(status().isBadRequest());
        verify(mapper,never()).correct(any());
    }
    @Test void correctionCannotCreateZeroOrNegativeLatestSupply()throws Exception {
        bill("older",1,"11000");bill("latest",2,"2000");
        command("correction",input()).andExpect(status().isBadRequest());
        verify(mapper,never()).correct(any());
        assertThat(invoices.get("latest").supplyAmount).isEqualByComparingTo("2000");
    }
    @Test void noCloseDefaultsToRealKoreanCurrentMonth()throws Exception {
        var result=data(mvc.perform(get("/api/common/bill-checks")).andExpect(status().isOk()));
        assertThat(result.path("checkMonth").asText()).isEqualTo("202611");
        assertThat(result.path("plannedBasis").asBoolean()).isTrue();
    }
    @Test void latestRealClosedMonthIsDefaultAndGetDoesNotLockOrWrite()throws Exception {
        when(mapper.lastClosedMonth()).thenReturn("202611");closed.add("202611");
        var result=data(mvc.perform(get("/api/common/bill-checks")).andExpect(status().isOk()));
        assertThat(result.path("plannedBasis").asBoolean()).isFalse();
        verify(projects,never()).lockSources();verify(mapper,never()).lockInvoices();
        verify(mapper,never()).insert(any());verify(mapper,never()).correct(any());
    }
    @Test void thresholdEqualityIsMatchedAndNewcomerExplanationUsesRealName()throws Exception {
        bill("latest",1,"9900");assertThat(checks().path("items").get(0).path("judgement").asText()).isEqualTo("맞음");
        invoices.clear();assertThat(checks().path("items").get(0).path("explanation").asText()).isEqualTo("투입은 있는데 이 달 청구가 없습니다");
        monthly.add(new Monthly("project","점검 프로젝트","MO","old","기존인",LocalDate.of(2026,1,1),"NEW",
            BigDecimal.ONE,new BigDecimal("10000"),new BigDecimal("10000")));
        bill("latest",1,"10000");
        assertThat(checks().path("items").get(0).path("explanation").asText())
            .isEqualTo("이 달 새로 투입한 김투입(1.00 MM)이 청구에서 빠진 것으로 보입니다");
    }
    @Test void newcomerExplanationUsesTheSameOperatingThresholdAsJudgement()throws Exception {
        // 투입 기준 20,000 · 청구 10,500 → 차이 9,500. 판정 기준 max(BILL_MISS_MIN 100, 20,000 × BILL_MISS_RT 1%)=200.
        // 새로 투입한 사람 금액 10,000과 차이 9,500은 500 다르므로 그 사람 탓으로 보지 않는다(예전 고정 1만 원이면 맞다고 봄).
        monthly.add(new Monthly("project","점검 프로젝트","MO","old","기존인",LocalDate.of(2026,1,1),"NEW",
            BigDecimal.ONE,new BigDecimal("10000"),new BigDecimal("10000")));
        bill("latest",1,"10500");
        assertThat(checks().path("items").get(0).path("explanation").asText()).isEqualTo("투입 기준보다 적게 청구했습니다");
        bill("latest",1,"10150");
        assertThat(checks().path("items").get(0).path("explanation").asText())
            .isEqualTo("이 달 새로 투입한 김투입(1.00 MM)이 청구에서 빠진 것으로 보입니다");
    }
    @Test void correctionRequiresTheReadModificationTime()throws Exception {
        eligible("correction");
        command("correction",input().putNull("modifiedAt")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("modifiedAt"));
        verify(mapper,never()).correct(any());
    }
    @ParameterizedTest @ValueSource(strings={"missing","additional"})
    void onlyCorrectionAcceptsModificationTime(String action)throws Exception {
        eligible(action);
        command(action,input().put("modifiedAt",READ.toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("modifiedAt"));
        verify(mapper,never()).insert(any());
    }
    @Test void staleCorrectionReturnsCommonConflictWithoutWriting()throws Exception {
        eligible("correction");
        var before=invoices.get("latest").supplyAmount;
        command("correction",input().put("modifiedAt","2026-11-15T00:00:00Z")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.errors[0].field").value("modifiedAt"))
            .andExpect(jsonPath("$.errors[0].message").value("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."));
        verify(mapper,never()).correct(any());
        assertThat(invoices.get("latest").supplyAmount).isEqualByComparingTo(before);
    }
    @Test void correctionSendsTheReadTimeToTheGuardedUpdateAndZeroRowsIsConflict()throws Exception {
        eligible("correction");
        doReturn(0).when(mapper).correct(any());
        command("correction",input()).andExpect(status().isConflict());
        var captor=org.mockito.ArgumentCaptor.forClass(Write.class);
        verify(mapper).correct(captor.capture());
        assertThat(captor.getValue().input().modifiedAt()).isEqualTo(READ);
    }
    @Test void fieldMessagesFollowCommonWording()throws Exception {
        command("missing",input().put("billDate","")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("billDate"))
            .andExpect(jsonPath("$.errors[0].message").value("청구일을 입력해 주세요."));
        command("missing",input().putNull("payDueDate")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].message").value("입금 예정일을 입력해 주세요."));
        command("missing",input().put("supplyAmount",0)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].message").value("공급가를 입력해 주세요."));
        command("missing",input().put("taxInvoiceNo","1".repeat(31))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("taxInvoiceNo"))
            .andExpect(jsonPath("$.errors[0].message").value("세금계산서 번호는 30자까지 입력해 주세요."));
        verify(mapper,never()).insert(any());
    }
    @ParameterizedTest @ValueSource(strings={"202600","202613","000001","20261","bad"})
    void invalidCalendarMonthHasFieldError(String month)throws Exception {
        mvc.perform(get("/api/common/bill-checks").param("checkMonth",month)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("checkMonth"));
    }
}
