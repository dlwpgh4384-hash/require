package com.company.common.partsettlements;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.PartSettlementDefinitions;
import com.company.common.partsettlements.controller.CommonPartSettlementController;
import com.company.common.partsettlements.dto.PartSettlementContracts.*;
import com.company.common.partsettlements.mapper.PartSettlementMapper;
import com.company.common.partsettlements.service.*;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.company.common.users.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import static com.company.common.partsettlements.PartSettlementTestData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonPartSettlementController.class)
@ContextConfiguration(classes={CommonPartSettlementController.class,CommonPartSettlementService.class,
    PartSettlementCloseService.class,PartSettlementExcelService.class,PartSettlementDefinitions.class,
    CalculationRuleService.class,CodeExceptionHandler.class,ApiExceptionHandler.class,
    ExcelExportService.class,BusinessCalendar.class,CurrentBusinessActor.class})
class CommonPartSettlementControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean PartSettlementMapper mapper;
    @MockBean CalculationRuleMapper definitions;
    @MockBean com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper periods;
    @MockBean UserMapper management;
    @MockBean PlatformTransactionManager transactions;
    PartSettlementTestData data;
    static final String ROOT="/api/common/part-settlements";
    static final String A=ROOT+"/A/2026-01";
    @BeforeEach void setup() throws Exception {
        PartSettlementTestData.definitions(definitions);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        data=new PartSettlementTestData(mapper);
    }
    String body(Object value) throws Exception {return json.writeValueAsString(value);}
    @Test void publicTabsExcludeHeadquartersAndExposeDbDefinitions() throws Exception {
        mvc.perform(get(ROOT).param("yearMonth","2026-01")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(2)).andExpect(jsonPath("$.data.items[0].summary.personCount").value(1))
            .andExpect(jsonPath("$.meta.calculationRules.length()").value(14));
        assertThat(data.headers).isEmpty();assertThat(data.audits).isEmpty();verify(mapper,never()).insertAccess(anyString(),anyString());
    }
    @Test void distinctMonthlyPartCriteria() throws Exception {
        data.parts.get(1).distributionRate=n("50");data.parts.get(1).salesExpense=n("200000");
        mvc.perform(get(ROOT).param("yearMonth","2026-01")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[1].summary.distributionRate").value(50))
            .andExpect(jsonPath("$.data.items[1].summary.salesExpense").value(200000));
    }
    @Test void noSalesPartsHasExactNotice() throws Exception {
        data.parts.removeIf(p->!p.partId.equals("HQ"));
        mvc.perform(get(ROOT).param("yearMonth","2026-01")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.notice").value("맡은 영업 파트가 없습니다. 본사(직접 영업)는 파트 이익금 정산 대상이 아닙니다."));
    }
    @Test void publicMonthlyDetailUsesPersonSalaryOnce() throws Exception {
        mvc.perform(get(A)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].salary").value(4000000))
            .andExpect(jsonPath("$.data.items[0].retirementAmount").value(333333)).andExpect(jsonPath("$.data.items[0].profitShare").value(466666))
            .andExpect(jsonPath("$.data.modifiedAt").value(org.hamcrest.Matchers.nullValue()));
        assertThat(data.headers).isEmpty();
    }
    @Test void publicTrendsContainJanuaryThroughSelectionAndCardOnce() throws Exception {
        data.header("WORK");
        mvc.perform(get(ROOT+"/A/2026-02/trends")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].months.length()").value(2))
            .andExpect(jsonPath("$.data.monthlyTotals[0].partProfitAmount").value(466566));
    }
    @ParameterizedTest @ValueSource(strings={"","/excel","/A/2026-13","/A/2026-13/trends"})
    void invalidMonthNeverWrites(String suffix) throws Exception {
        mvc.perform(get(ROOT+suffix).param("yearMonth","2026-13").param("partId","A").param("view","monthly"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("yearMonth"));
        assertThat(data.headers).isEmpty();assertThat(data.audits).isEmpty();
    }
    @Test void publicCardCreateThenReadBackAndStaleConflict() throws Exception {
        mvc.perform(put(A+"/card").contentType("application/json").content("{\"corpCardAmount\":50000,\"modifiedAt\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.corpCardAmount").value(50000))
            .andExpect(jsonPath("$.data.summary.partProfitAmount").value(416666));
        var h=data.headers.get(new Key("A","202601"));
        mvc.perform(get(A)).andExpect(jsonPath("$.data.modifiedAt").value(h.modifiedAt.toString()));
        int count=data.audits.size();
        mvc.perform(put(A+"/card").contentType("application/json").content("{\"corpCardAmount\":60000,\"modifiedAt\":null}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."));
        assertThat(h.card).isEqualByComparingTo("50000");assertThat(data.audits).hasSize(count);assertThat(data.details).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"corpCardAmount\":2.3,\"modifiedAt\":null}","{\"corpCardAmount\":-1,\"modifiedAt\":null}","{\"corpCardAmount\":1000000000000000,\"modifiedAt\":null}"})
    void invalidCardNeverWrites(String body) throws Exception {
        mvc.perform(put(A+"/card").contentType("application/json").content(body)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("corpCardAmount"));assertThat(data.headers).isEmpty();
    }
    @Test void confirmedCardIsLocked() throws Exception {
        var h=data.header("CNFM");
        mvc.perform(put(A+"/card").contentType("application/json").content(body(new CardInput(n("200"),h.modifiedAt))))
            .andExpect(status().isBadRequest());assertThat(h.card).isEqualByComparingTo("100");assertThat(h.profit).isEqualByComparingTo("999");
    }
    @Test void closedDraftCardIsLocked() throws Exception {
        data.closed=true;
        mvc.perform(put(A+"/card").contentType("application/json").content(body(new CardInput(n("200"),null)))).andExpect(status().isBadRequest());
        assertThat(data.headers).isEmpty();
    }
    @Test void publicDirectConfirmSnapshotsAndRepeatedConfirmPreservesTimestamp() throws Exception {
        mvc.perform(put(A+"/confirm").contentType("application/json").content("{\"modifiedAt\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.confirmedBy").value("admin")).andExpect(jsonPath("$.data.confirmed").value(true));
        var h=data.headers.get(new Key("A","202601"));var at=h.modifiedAt;
        data.inputs.get(0).projectName="바뀐 프로젝트";data.inputs.get(0).employeeTypeCode="VN";
        mvc.perform(get(A)).andExpect(jsonPath("$.data.items[0].projectNames[0]").value("프로젝트"))
            .andExpect(jsonPath("$.data.items[0].employeeTypeCode").value("FT"));
        mvc.perform(put(A+"/confirm").contentType("application/json").content(body(Collections.singletonMap("modifiedAt",at))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.modifiedAt").value(at.toString()));
        assertThat(data.details.get(new Key("A","202601"))).hasSize(1);
    }
    @Test void requiredVersionPropertyIsNotNonNull() throws Exception {
        mvc.perform(put(A+"/confirm").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("modifiedAt"));assertThat(data.headers).isEmpty();
    }
    @Test void staleConfirmDoesNotAddSnapshot() throws Exception {
        data.header("WORK");
        mvc.perform(put(A+"/confirm").contentType("application/json").content("{\"modifiedAt\":null}")).andExpect(status().isConflict());
        assertThat(data.details).isEmpty();assertThat(data.audits).isEmpty();
    }
    @Test void missingStartSalaryWarnsButDoesNotBlockConfirm() throws Exception {
        var i=data.inputs.get(0);i.firstStart=java.time.LocalDate.of(2026,1,16);i.monthlySalary=null;
        mvc.perform(put(A+"/confirm").contentType("application/json").content("{\"modifiedAt\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.confirmed").value(true)).andExpect(jsonPath("$.data.items[0].missingMonthlySalary").value(true));
    }
    @Test void cancellationPersistsTypedReasonAndRetainsSnapshotButReadsLive() throws Exception {
        mvc.perform(put(A+"/confirm").contentType("application/json").content("{\"modifiedAt\":null}")).andExpect(status().isOk());
        var h=data.headers.get(new Key("A","202601"));data.inputs.get(0).projectName="현재 프로젝트";
        mvc.perform(put(A+"/cancel").contentType("application/json").content(body(new CancelInput("  정정 필요  ",h.modifiedAt))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.confirmed").value(false)).andExpect(jsonPath("$.data.items[0].projectNames[0]").value("현재 프로젝트"));
        assertThat(h.cancelReason).isEqualTo("  정정 필요  ");assertThat(data.details.get(new Key("A","202601"))).hasSize(1);
        assertThat(data.audits.get(data.audits.size()-1)).extracting(Audit::before,Audit::after,Audit::reason,Audit::actor)
            .containsExactly("CNFM","WORK","  정정 필요  ","admin");
    }
    @ParameterizedTest @ValueSource(strings={""," ","\n\t"})
    void blankCancelReasonDoesNotWrite(String reason) throws Exception {
        var h=data.header("CNFM");
        mvc.perform(put(A+"/cancel").contentType("application/json").content(body(new CancelInput(reason,h.modifiedAt))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("cancelReason"));assertThat(data.audits).isEmpty();assertThat(h.state).isEqualTo("CNFM");
    }
    @Test void overlongReasonDoesNotWrite() throws Exception {
        var h=data.header("CNFM");mvc.perform(put(A+"/cancel").contentType("application/json").content(body(new CancelInput("가".repeat(501),h.modifiedAt))))
            .andExpect(status().isBadRequest());assertThat(data.audits).isEmpty();
    }
    @Test void closedCancelThenReopenedCancel() throws Exception {
        var h=data.header("AUTO");data.closed=true;var at=h.modifiedAt;
        mvc.perform(put(A+"/cancel").contentType("application/json").content(body(new CancelInput("정정",at)))).andExpect(status().isBadRequest());
        assertThat(h.modifiedAt).isEqualTo(at);assertThat(data.audits).isEmpty();data.closed=false;
        mvc.perform(put(A+"/cancel").contentType("application/json").content(body(new CancelInput("정정",at)))).andExpect(status().isOk());
    }
    @Test void staleCancelNeverAudits() throws Exception {
        var h=data.header("CNFM");mvc.perform(put(A+"/cancel").contentType("application/json").content(body(new CancelInput("정정",null))))
            .andExpect(status().isConflict());assertThat(data.audits).isEmpty();assertThat(h.cancelReason).isNull();
    }
    @Test void duplicateCancelIsFieldError() throws Exception {
        var h=data.header("WORK");mvc.perform(put(A+"/cancel").contentType("application/json").content(body(new CancelInput("정정",h.modifiedAt))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("cancelReason"));assertThat(data.audits).isEmpty();
    }
    @Test void publicCloseConfirmUsesSameSelectorAndAdmin() throws Exception {
        var items=List.of(new CloseItem("A",false,null),new CloseItem("B",false,null));
        mvc.perform(put(ROOT+"/close-confirm").contentType("application/json").content(body(new CloseInput("2026-01",items))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.unconfirmedCount").value(0))
            .andExpect(jsonPath("$.data.items[0].confirmedBy").value("admin")).andExpect(jsonPath("$.data.items[0].confirmationNote").value("마감 시 자동 확정"));
        assertThat(data.headers.values()).allMatch(h->h.state.equals("AUTO"));
    }
    @Test void invalidTargetRejectsWholeBatch() throws Exception {
        mvc.perform(put(ROOT+"/close-confirm").contentType("application/json").content(body(new CloseInput("2026-01",List.of(new CloseItem("A",false,null),new CloseItem("HQ",false,null))))))
            .andExpect(status().isBadRequest());assertThat(data.headers).isEmpty();assertThat(data.details).isEmpty();
    }
    @Test void staleBatchHasNoPartialSave() throws Exception {
        data.header("WORK");
        mvc.perform(put(ROOT+"/close-confirm").contentType("application/json").content(body(new CloseInput("2026-01",List.of(new CloseItem("A",false,null),new CloseItem("B",false,null))))))
            .andExpect(status().isConflict());assertThat(data.headers).hasSize(1);assertThat(data.details).isEmpty();assertThat(data.audits).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings={"{}","{\"yearMonth\":\"2026-13\",\"items\":[]}"})
    void invalidBatchMonth(String body) throws Exception {
        mvc.perform(put(ROOT+"/close-confirm").contentType("application/json").content(body)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("yearMonth"));assertThat(data.headers).isEmpty();
    }
    @Test void snapshotOverflowRejectsBeforeHeaderCreation() throws Exception {
        data.inputs.get(0).projectName="가".repeat(1001);
        mvc.perform(put(A+"/confirm").contentType("application/json").content("{\"modifiedAt\":null}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("projectNames"));assertThat(data.headers).isEmpty();
    }
    @Test void downloadIsActualXlsxAndLogsOnceAfterSerialization() throws Exception {
        byte[] bytes=mvc.perform(get(ROOT+"/excel").param("partId","A").param("yearMonth","2026-01").param("view","monthly"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse().getContentAsByteArray();
        try (var workbook=new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheetAt(0).getRow(3).getCell(0).getStringCellValue()).isEqualTo("개발자 D");
        }
        verify(mapper,times(1)).insertAccess(eq("admin"),contains("A 2026-01 monthly"));assertThat(data.headers).isEmpty();
    }
    @Test void invalidDownloadDoesNotLogSuccess() throws Exception {
        mvc.perform(get(ROOT+"/excel").param("partId","A").param("yearMonth","2026-01").param("view","unknown"))
            .andExpect(status().isBadRequest());verify(mapper,never()).insertAccess(anyString(),anyString());
    }
    @Test void missingAndExtraCloseTargetsConflictBeforeAnySave() throws Exception {
        for (var items:List.of(List.of(new CloseItem("A",false,null)),List.of(new CloseItem("A",false,null),new CloseItem("A",false,null)))) {
            mvc.perform(put(ROOT+"/close-confirm").contentType("application/json").content(body(new CloseInput("2026-01",items))))
                .andExpect(status().isConflict());
        }
        assertThat(data.headers).isEmpty();assertThat(data.audits).isEmpty();
    }
    @Test void closeBatchPreservesAlreadyConfirmedSnapshotAndVersion() throws Exception {
        mvc.perform(put(A+"/confirm").contentType("application/json").content("{\"modifiedAt\":null}")).andExpect(status().isOk());
        var header=data.headers.get(new Key("A","202601"));var at=header.modifiedAt;data.inputs.clear();
        var input=new CloseInput("2026-01",List.of(new CloseItem("A",true,at),new CloseItem("B",false,null)));
        mvc.perform(put(ROOT+"/close-confirm").contentType("application/json").content(body(input))).andExpect(status().isOk());
        assertThat(header.state).isEqualTo("CNFM");assertThat(header.modifiedAt).isEqualTo(at);
        assertThat(data.details.get(new Key("A","202601"))).hasSize(1);
    }
    @Test void reconfirmReplacesOnlySelectedPartMonthAndRemovesDisappearedPeople() throws Exception {
        mvc.perform(put(A+"/confirm").contentType("application/json").content("{\"modifiedAt\":null}")).andExpect(status().isOk());
        var key=new Key("A","202601");var header=data.headers.get(key);
        mvc.perform(put(A+"/cancel").contentType("application/json").content(body(new CancelInput("인력 변경",header.modifiedAt)))).andExpect(status().isOk());
        var other=new Key("B","202601");data.details.put(other,new ArrayList<>(data.details.get(key)));data.inputs.clear();
        mvc.perform(put(A+"/confirm").contentType("application/json").content(body(Collections.singletonMap("modifiedAt",header.modifiedAt)))).andExpect(status().isOk());
        assertThat(data.details.getOrDefault(key,List.of())).isEmpty();assertThat(data.details.get(other)).hasSize(1);
        assertThat(data.audits.stream().filter(a->"정산 상태".equals(a.item())).count()).isEqualTo(3);
    }
    @Test void headquarterHistoricalMembershipNeverCreatesSalesSnapshot() throws Exception {
        var i=data.inputs.get(0);i.partId="A";
        data.history.add(PartSettlementRulesTest.history("TB_HR_DEV","D","영업 파트","HQ","2026-02-01T00:00:00Z",1));
        mvc.perform(put(A+"/confirm").contentType("application/json").content("{\"modifiedAt\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());assertThat(data.details).isEmpty();
    }
    @Test void failedSourceReadIsNotEmptySuccess() throws Exception {
        when(mapper.selectInputs(anyString())).thenThrow(new IllegalStateException("source unavailable"));
        mvc.perform(get(A)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false));
        assertThat(data.headers).isEmpty();
    }
}
