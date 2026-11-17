package com.company.common.assignments.controller;

import com.company.common.assignments.mapper.AssignmentMapper;
import com.company.common.assignments.mapper.AssignmentRows.*;
import com.company.common.assignments.service.*;
import com.company.common.projects.dto.ProjectContracts.Project;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.projects.service.ProjectAmountRules;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
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

@WebMvcTest(CommonAssignmentController.class)
@ContextConfiguration(classes={CommonAssignmentController.class,CommonAssignmentService.class,
    CodeExceptionHandler.class,ApiExceptionHandler.class,CurrentBusinessActor.class,ExcelExportService.class})
class CommonAssignmentControllerTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @MockBean AssignmentMapper mapper; @MockBean ProjectMapper projects; @MockBean BusinessCalendar calendar;
    @MockBean AssignmentMonthRules rules; @MockBean ProjectAmountRules amounts;
    @MockBean PlatformTransactionManager transactions;
    Map<String,Row> rows; List<HistoryRow> histories; List<MonthWrite> months; Project project;
    final LocalDate today=LocalDate.of(2026,11,15);
    final Instant now=Instant.parse("2026-11-15T00:00:00Z");
    @BeforeEach void setup() {
        rows=new LinkedHashMap<>();histories=new ArrayList<>();months=new ArrayList<>();
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(calendar.today()).thenReturn(today);when(calendar.currentMonth()).thenReturn(YearMonth.from(today));when(calendar.now()).thenReturn(now);
        when(calendar.fromDatabaseMonth(anyString())).thenAnswer(c -> YearMonth.parse(((String)c.getArgument(0)).substring(0,4)+"-"+((String)c.getArgument(0)).substring(4)));
        project=new Project();project.projectId="P-0001";project.projectName="개발 프로젝트";project.contractTypeCode="MO";project.statusCode="ING";
        project.startDate=LocalDate.of(2026,1,1);project.endDate=LocalDate.of(2027,12,31);project.salesPartId="part";
        project.teamExpenseAmount=BigDecimal.ZERO;project.projectExpenseAmount=BigDecimal.ZERO;
        when(projects.lockProject(anyString())).thenReturn("P-0001");when(projects.selectProject(anyString(),any(),anyString())).thenReturn(project);
        when(projects.codeName(anyString(),anyString())).thenAnswer(c -> switch((String)c.getArgument(1)) { case "NEW" -> "신규";case "ADD" -> "추가투입";case "SUB" -> "대체투입";case "FREE" -> "무상지원";case "H" -> "높음";case "L" -> "낮음";default -> c.getArgument(1); });
        when(mapper.rows()).thenAnswer(c -> new ArrayList<>(rows.values()));when(mapper.ids()).thenAnswer(c -> new ArrayList<>(rows.keySet()));
        when(mapper.row(anyString())).thenAnswer(c -> rows.get(c.getArgument(0)));when(mapper.lockRow(anyString())).thenAnswer(c -> rows.get(c.getArgument(0)));
        when(mapper.person(anyString(),anyString(),any(),any())).thenAnswer(c -> person(c.getArgument(0),null));
        when(mapper.history(anyString())).thenAnswer(c -> histories.stream().filter(h -> h.assignmentId().equals(c.getArgument(0))).sorted(Comparator.comparingInt(HistoryRow::sequence).reversed()).toList());
        when(mapper.nextHistory(anyString())).thenAnswer(c -> histories.stream().filter(h -> h.assignmentId().equals(c.getArgument(0))).mapToInt(HistoryRow::sequence).max().orElse(0)+1);
        when(mapper.insert(any())).thenAnswer(c -> {Write w=c.getArgument(0);rows.put(w.row().assignmentId(),w.row());return 1;});
        when(mapper.update(any())).thenAnswer(c -> {Write w=c.getArgument(0);rows.put(w.row().assignmentId(),w.row());return 1;});
        doAnswer(c -> {HistoryWrite w=c.getArgument(0);HistoryRow h=w.row();histories.add(new HistoryRow(h.sequence(),h.assignmentId(),h.type(),h.effectiveDate(),h.beforeValue(),h.afterValue(),h.reasonCode(),h.memo(),h.price(),h.mm(),h.retroactiveYn(),h.closedYn(),h.correctedYn(),h.target(),w.actor(),now));return 1;}).when(mapper).insertHistory(any());
        when(mapper.closedMonths()).thenReturn(List.of());when(mapper.yearMm(anyString(),anyInt())).thenReturn(BigDecimal.ONE);when(mapper.overload(anyString(),anyInt())).thenReturn(BigDecimal.ONE);
        when(mapper.labor(anyString(),anyString())).thenReturn(new BigDecimal("5000000"));
        when(mapper.saveMonth(any())).thenAnswer(c -> {months.add(c.getArgument(0));return 1;});
        when(rules.effective(anyList(),anyString(),any(),any())).thenAnswer(c -> {
            List<HistoryRow> h=c.getArgument(0);String command=c.getArgument(1);YearMonth month=c.getArgument(2);
            return h.stream().filter(x -> (x.type().equals("INPUT") || x.type().equals(command)) && !YearMonth.from(x.effectiveDate()).isAfter(month) && (command.equals("UPRC")?x.price():x.mm())!=null)
                .max(Comparator.comparing((HistoryRow x) -> YearMonth.from(x.effectiveDate()))
                    .thenComparingInt(HistoryRow::sequence))
                .map(x -> command.equals("UPRC")?x.price():x.mm()).orElse(c.getArgument(3));
        });
        when(rules.mm(any(),any(),any(),any())).thenAnswer(c -> c.getArgument(0));when(rules.days(any(),any(),any())).thenReturn(30);
        when(rules.sales(any(),any(),anyBoolean())).thenAnswer(c -> (boolean)c.getArgument(2)?((BigDecimal)c.getArgument(0)).multiply(c.getArgument(1)):BigDecimal.ZERO);
        when(rules.over(any())).thenAnswer(c -> ((BigDecimal)c.getArgument(0)).compareTo(BigDecimal.ONE)>0);
        when(amounts.personnelDirectCost(anyString(),any(),any(),any())).thenReturn(new ProjectAmountRules.PersonnelDirectCost(BigDecimal.ZERO,BigDecimal.ZERO));
        rows.put("A-0001",row("A-0001","developer",null,"NEW","MO"));
    }
    Person person(String id,LocalDate retirement) { return new Person(id,"개발자","FT","part","영업파트",retirement,new BigDecimal("8000000"),false); }
    Row row(String id,String developer,String extension,String input,String contract) { return new Row(id,"P-0001",developer,"DEV",LocalDate.of(2026,10,16),LocalDate.of(2026,12,31),BigDecimal.ONE,new BigDecimal("8000000"),input,null,extension,0,"N","ACT",null,"개발 프로젝트",developer,"M-"+developer,"개발",contract,"ING"); }
    ObjectNode input() { var b=json.createObjectNode();b.put("projectId","P-0001");b.put("developerId","new-developer");b.put("jobCode","DEV");b.put("startDate","2026-11-15");b.put("endDate","2026-12-31");b.put("monthlyMm",1);b.put("inputTypeCode","NEW");b.put("billingPrice",8000000);return b; }
    ObjectNode change(String type) { var b=json.createObjectNode();b.putNull("modifiedAt");b.put("changeTypeCode",type);b.put("effectiveDate","2026-11-15");return b; }
    ObjectNode bulk(String... ids) {var b=json.createObjectNode();var a=b.putArray("assignments");for(String id:ids){var v=a.addObject();v.put("assignmentId",id);v.putNull("modifiedAt");}return b;}
    void postInput(ObjectNode b,int expected) throws Exception {mvc.perform(post("/api/common/assignments").contentType("application/json").content(b.toString())).andExpect(status().is(expected));}
    void change(ObjectNode b,int expected) throws Exception {mvc.perform(post("/api/common/assignments/A-0001/changes").contentType("application/json").content(b.toString())).andExpect(status().is(expected));}
    void noWrites() {verify(mapper,never()).insert(any());verify(mapper,never()).update(any());verify(mapper,never()).insertHistory(any());}
    private static org.springframework.test.web.servlet.ResultMatcher response(String method,String path) {
        return com.company.common.support.controller.OpenApiResponseContract.response(method,path,"contracts/assignments-openapi.yaml");
    }
    @Test void anonymousRegistrationAndDetailUseRealServiceAndApprovedShapes() throws Exception {
        mvc.perform(post("/api/common/assignments").contentType("application/json").content(input().toString())).andExpect(status().isOk()).andExpect(response("POST","/api/common/assignments"));
        mvc.perform(get("/api/common/assignments/A-0002")).andExpect(status().isOk()).andExpect(response("GET","/api/common/assignments/{assignmentId}"))
            .andExpect(jsonPath("$.data.projectId").value("P-0001")).andExpect(jsonPath("$.data.developerId").value("new-developer")).andExpect(jsonPath("$.data.monthlyMm").value(1));
        assertThat(histories).singleElement().satisfies(h -> {assertThat(h.type()).isEqualTo("INPUT");assertThat(h.actor()).isEqualTo("admin");assertThat(h.price()).isEqualByComparingTo("8000000");assertThat(h.mm()).isEqualByComparingTo("1");});
    }
    @Test void missingDeveloperHasSpecificFieldAndNoWrites() throws Exception {var b=input();b.remove("developerId");mvc.perform(post("/api/common/assignments").contentType("application/json").content(b.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("developerId")).andExpect(jsonPath("$.errors[0].message").value("개발자를 골라 주세요."));noWrites();}
    @Test void contractEndExceededIsRejected() throws Exception {var b=input();b.put("endDate","2028-01-01");postInput(b,400);noWrites();}
    @Test void retirementEndExceededIsRejected() throws Exception {when(mapper.person(anyString(),anyString(),any(),any())).thenReturn(person("new-developer",today));postInput(input(),400);noWrites();}
    @Test void endedProjectCannotBeSelected() throws Exception {project.statusCode="END";postInput(input(),400);noWrites();}
    @Test void lostProjectCannotBeSelected() throws Exception {project.statusCode="LOSE";postInput(input(),400);noWrites();}
    @Test void zeroMmCannotBeCreated() throws Exception {var b=input();b.put("monthlyMm",0);postInput(b,400);noWrites();}
    @Test void missingMonthlyMmIsRejectedInsteadOfDefaultingTheHttpContract() throws Exception {var b=input();b.remove("monthlyMm");mvc.perform(post("/api/common/assignments").contentType("application/json").content(b.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("monthlyMm"));noWrites();}
    @Test void missingInputTypeIsRejectedInsteadOfDefaultingTheHttpContract() throws Exception {var b=input();b.remove("inputTypeCode");mvc.perform(post("/api/common/assignments").contentType("application/json").content(b.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("inputTypeCode"));noWrites();}
    @Test void freeSupportNeedsNoPriceAndStoresZero() throws Exception {var b=input();b.put("inputTypeCode","FREE");b.remove("billingPrice");postInput(b,200);assertThat(rows.get("A-0002").billingPrice()).isZero();}
    @Test void createDoesNotAcceptCandidateLinkOrExtensionInput() throws Exception {var b=input();b.put("candidateId","1");postInput(b,400);noWrites();}
    @Test void differentSalesPartOnlyWarns() throws Exception {project.salesPartId="another";postInput(input(),200);mvc.perform(get("/api/common/assignments/A-0002")).andExpect(jsonPath("$.data.warnings[0]").value(org.hamcrest.Matchers.containsString("영업 파트")));}
    @Test void onLeaveOnlyWarns() throws Exception {when(mapper.person(anyString(),anyString(),any(),any())).thenReturn(new Person("new-developer","개발자","FT","part","파트",null,new BigDecimal("8000000"),true));postInput(input(),200);assertThat(rows).containsKey("A-0002");}
    @Test void togetherRequiresReasonBeforeAnyMutation() throws Exception {var b=input();b.put("inputTypeCode","SUB");b.put("contractDeveloperId","developer");b.put("replacementAction","together");b.putArray("earlyWithdrawalAssignmentIds").add("A-0001");postInput(b,400);noWrites();}
    @Test void togetherWithdrawsOnlySelectedSourceAndWritesBothHistories() throws Exception {var b=input();b.put("inputTypeCode","SUB");b.put("contractDeveloperId","developer");b.put("replacementAction","together");b.put("reasonCode","PERSON");b.putArray("earlyWithdrawalAssignmentIds").add("A-0001");postInput(b,200);assertThat(rows.get("A-0001").endDate()).isEqualTo(today.minusDays(1));assertThat(histories).extracting(HistoryRow::type).containsExactly("INPUT","EARLY");assertThat(histories.get(1).reasonCode()).isEqualTo("PERSON");}
    @Test void replacementOnlyLeavesSourcePeriodAlone() throws Exception {var b=input();b.put("inputTypeCode","SUB");b.put("contractDeveloperId","developer");b.put("replacementAction","assignment-only");postInput(b,200);assertThat(rows.get("A-0001").endDate()).isEqualTo(LocalDate.of(2026,12,31));}
    @Test void selectionMustStillBeMatchingProjectAndPerson() throws Exception {var b=input();b.put("inputTypeCode","SUB");b.put("contractDeveloperId","developer");b.put("replacementAction","together");b.put("reasonCode","PERSON");b.putArray("earlyWithdrawalAssignmentIds").add("unknown");postInput(b,400);noWrites();}
    @Test void listYearFiltersOutNonoverlappingRowsAndUsesAllPersonMonths() throws Exception {mvc.perform(get("/api/common/assignments").param("year","2025")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());when(mapper.overload(anyString(),anyInt())).thenReturn(new BigDecimal("1.01"));mvc.perform(get("/api/common/assignments").param("year","2026").param("warning","MM 1.0 초과").param("projectId","P-0001")).andExpect(status().isOk()).andExpect(response("GET","/api/common/assignments")).andExpect(jsonPath("$.data.items[0].monthlyMmTotal").value(1.01));}
    @Test void exactlyOneMmIsNotAnOverload() throws Exception {mvc.perform(get("/api/common/assignments").param("warning","MM 1.0 초과")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());}
    @Test void unknownWarningIsRejected() throws Exception {mvc.perform(get("/api/common/assignments").param("warning","unknown")).andExpect(status().isBadRequest());}
    @Test void detailNotFound() throws Exception {mvc.perform(get("/api/common/assignments/missing")).andExpect(status().isNotFound());}
    @Test void candidatesAreClippedWithoutAssignmentInsert() throws Exception {when(mapper.candidates()).thenReturn(List.of(new CandidateRow("12","P-0001","개발 프로젝트","developer","사람","DEV","개발","202610",2,today,LocalDate.of(2026,11,20),"CNVT")));mvc.perform(get("/api/common/assignments/candidates")).andExpect(status().isOk()).andExpect(response("GET","/api/common/assignments/candidates")).andExpect(jsonPath("$.data.items[0].startDate").value("2026-11-15")).andExpect(jsonPath("$.data.items[0].endDate").value("2026-11-20"));noWrites();}
    @Test void emptyClippedCandidatePeriodIsAnErrorNotInventedDates() throws Exception {when(mapper.candidates()).thenReturn(List.of(new CandidateRow("12","P-0001","개발 프로젝트","developer","사람","DEV","개발","202610",1,today,LocalDate.of(2026,11,20),"CNVT")));mvc.perform(get("/api/common/assignments/candidates")).andExpect(status().isBadRequest());noWrites();}
    @Test void anonymousCandidateRemovalOnlyUpdatesSource() throws Exception {when(mapper.lockCandidate("12")).thenReturn(new CandidateRow("12","P-0001","프로젝트","developer","사람","DEV","개발","202610",1,today,today,"CNVT"));mvc.perform(post("/api/common/assignments/candidates/12/remove")).andExpect(status().isOk()).andExpect(response("POST","/api/common/assignments/candidates/{candidateId}/remove"));verify(mapper).removeCandidate("12","admin");noWrites();}
    @Test void alreadyDroppedCandidateIsIdempotent() throws Exception {when(mapper.lockCandidate("12")).thenReturn(new CandidateRow("12","P-0001","프로젝트","developer","사람","DEV","개발","202610",1,today,today,"DROP"));mvc.perform(post("/api/common/assignments/candidates/12/remove")).andExpect(status().isOk());verify(mapper,never()).removeCandidate(anyString(),anyString());}
    @Test void unknownCandidateIsNotSilentlySuccessful() throws Exception {mvc.perform(post("/api/common/assignments/candidates/999/remove")).andExpect(status().isBadRequest());noWrites();}
    @Test void malformedCandidateIdIsRejected() throws Exception {mvc.perform(post("/api/common/assignments/candidates/abc/remove")).andExpect(status().isBadRequest());noWrites();}
    @Test void contractDevelopersDeduplicateExcludeSelfAndIncludeEndedPeople() throws Exception {rows.put("A-0002",row("A-0002","developer",null,"NEW","MO"));mvc.perform(get("/api/common/assignments/contract-developers").param("projectId","P-0001").param("developerId","new-developer").param("startDate","2026-11-15")).andExpect(status().isOk()).andExpect(response("GET","/api/common/assignments/contract-developers")).andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].overlappingAssignments.length()").value(2));}
    @Test void futurePriceChangesOnlyFutureMonthsAndCurrentCacheStays() throws Exception {var b=change("UPRC");b.put("effectiveDate","2026-12-15");b.put("newBillingPrice",8500000);change(b,200);assertThat(rows.get("A-0001").billingPrice()).isEqualByComparingTo("8000000");assertThat(months).allSatisfy(m -> {assertThat(m.month()).isEqualTo("202612");assertThat(m.price()).isEqualByComparingTo("8500000");});assertThat(histories.get(0).effectiveDate()).isEqualTo(LocalDate.of(2026,12,1));}
    @Test void dateBeforeStartIsRejected() throws Exception {var b=change("EXT");b.put("effectiveDate","2026-09-30");b.put("newEndDate","2027-01-31");change(b,400);noWrites();}
    @Test void memo201IsRejected() throws Exception {var b=change("EXTP");b.put("newExtensionPossibilityCode","H");b.put("memo","가".repeat(201));change(b,400);noWrites();}
    @Test void historicalChangeRequiresReason() throws Exception {var b=change("RATE");b.put("effectiveDate","2026-10-01");b.put("newMonthlyMm",0.8);change(b,400);noWrites();}
    @Test void monthFirstAllowsMidMonthStartAndRecordsRetroactive() throws Exception {var b=change("RATE");b.put("effectiveDate","2026-10-16");b.put("newMonthlyMm",0.8);b.put("reasonCode","PERSON");change(b,200);assertThat(histories).singleElement().satisfies(h -> {assertThat(h.effectiveDate()).isEqualTo(LocalDate.of(2026,10,1));assertThat(h.retroactiveYn()).isEqualTo("Y");});}
    @ParameterizedTest @ValueSource(doubles={0,1.01}) void invalidMmDoesNotMutate(double value) throws Exception {var b=change("RATE");b.put("newMonthlyMm",value);change(b,400);noWrites();}
    @Test void extensionIncrementsCountWithHistory() throws Exception {var b=change("EXT");b.put("effectiveDate","2027-01-01");b.put("newEndDate","2027-01-31");change(b,200);assertThat(rows.get("A-0001").extensionCount()).isEqualTo(1);assertThat(histories).singleElement().satisfies(h -> assertThat(h.type()).isEqualTo("EXT"));}
    @Test void extensionPastProjectContractIsRejected() throws Exception {var b=change("EXT");b.put("newEndDate","2028-01-01");change(b,400);noWrites();}
    @Test void earlyDateAfterEndIsRejected() throws Exception {var b=change("EARLY");b.put("reasonCode","PERSON");b.put("newEndDate","2027-01-01");change(b,400);noWrites();}
    @Test void earlyWithdrawalSetsEndAndReason() throws Exception {var b=change("EARLY");b.put("reasonCode","PERSON");b.put("newEndDate","2026-11-30");change(b,200);assertThat(rows.get("A-0001").endDate()).isEqualTo(LocalDate.of(2026,11,30));}
    @Test void unsupportedKindTransitionIsRejected() throws Exception {var b=change("KIND");b.put("newInputTypeCode","FREE");change(b,400);noWrites();}
    @Test void supportedKindTransitionHasNamesAndKeepsEndDate() throws Exception {var b=change("KIND");b.put("newInputTypeCode","ADD");change(b,200);assertThat(rows.get("A-0001").endDate()).isEqualTo(LocalDate.of(2026,12,31));assertThat(histories.get(0).beforeValue()).isEqualTo("신규");assertThat(histories.get(0).afterValue()).isEqualTo("추가투입");}
    @Test void missingVersionIsNeverAnUnrestrictedUpdate() throws Exception {var b=change("EXTP");b.remove("modifiedAt");b.put("newExtensionPossibilityCode","H");change(b,400);noWrites();}
    @Test void staleVersionIs409WithoutWrites() throws Exception {var b=change("EXTP");b.put("modifiedAt",now.toString());b.put("newExtensionPossibilityCode","H");change(b,409);noWrites();}
    @Test void sameExtensionIsInvalidOnSingleCommand() throws Exception {var b=change("EXTP");b.put("newExtensionPossibilityCode","");change(b,400);noWrites();}
    @Test void explicitEmptyExtensionClearsToNull() throws Exception {rows.put("A-0001",row("A-0001","developer","H","NEW","MO"));var b=change("EXTP");b.put("newExtensionPossibilityCode","");change(b,200);assertThat(rows.get("A-0001").extensionPossibilityCode()).isNull();}
    @Test void bulkOnlyChangesSelectedDistinctIdsAndNoOpHasNoHistory() throws Exception {rows.put("A-0002",row("A-0002","second",null,"NEW","MO"));var b=bulk("A-0001","A-0001");b.put("extensionPossibilityCode","H");mvc.perform(put("/api/common/assignments/extension-possibility").contentType("application/json").content(b.toString())).andExpect(status().isOk()).andExpect(response("PUT","/api/common/assignments/extension-possibility"));assertThat(rows.get("A-0002").extensionPossibilityCode()).isNull();assertThat(histories).hasSize(1);mvc.perform(put("/api/common/assignments/extension-possibility").contentType("application/json").content(b.toString())).andExpect(status().isOk());assertThat(histories).hasSize(1);}
    @Test void staleLaterBulkRowMeansZeroWrites() throws Exception {var b=bulk("A-0001","A-0002");rows.put("A-0002",row("A-0002","second",null,"NEW","MO"));((ObjectNode)b.get("assignments").get(1)).put("modifiedAt",now.toString());b.put("extensionPossibilityCode","H");mvc.perform(put("/api/common/assignments/extension-possibility").contentType("application/json").content(b.toString())).andExpect(status().isConflict());noWrites();}
    @Test void emptySelectionIsRejected() throws Exception {var b=bulk();b.put("extensionPossibilityCode","H");mvc.perform(put("/api/common/assignments/extension-possibility").contentType("application/json").content(b.toString())).andExpect(status().isBadRequest());noWrites();}
    @Test void cancellationIsPublicRetainsRowAndHistoryAndIsIdempotent() throws Exception {var b=bulk("A-0001");mvc.perform(post("/api/common/assignments/cancel").contentType("application/json").content(b.toString())).andExpect(status().isOk()).andExpect(response("POST","/api/common/assignments/cancel"));assertThat(rows.get("A-0001").statusCode()).isEqualTo("CNCL");assertThat(histories).singleElement().satisfies(h -> {assertThat(h.afterValue()).isEqualTo("배정 취소");assertThat(h.actor()).isEqualTo("admin");});mvc.perform(post("/api/common/assignments/cancel").contentType("application/json").content(b.toString())).andExpect(status().isOk());assertThat(histories).hasSize(1);mvc.perform(get("/api/common/assignments")).andExpect(jsonPath("$.data.items").isEmpty());}
    @Test void closedActualsDoNotBlockCancellation() throws Exception {when(mapper.closedMonths()).thenReturn(List.of("202610"));mvc.perform(post("/api/common/assignments/cancel").contentType("application/json").content(bulk("A-0001").toString())).andExpect(status().isOk());assertThat(rows.get("A-0001").statusCode()).isEqualTo("CNCL");verify(mapper,never()).saveMonth(any());}
    @Test void malformedBulkRowIsRejectedBeforeCancellation() throws Exception {var b=bulk("A-0001");((ObjectNode)b.get("assignments").get(0)).remove("assignmentId");mvc.perform(post("/api/common/assignments/cancel").contentType("application/json").content(b.toString())).andExpect(status().isBadRequest());noWrites();}
    HistoryRow history(String type,String corrected) {return new HistoryRow(1,"A-0001",type,today,"이전","이후","PERSON","메모",null,null,"N","N",corrected,null,"admin",now);}
    ObjectNode correction() {var b=json.createObjectNode();b.put("modifiedAt",now.toString());b.put("correctedValue","바른 내용");b.put("reason","정정 사유");return b;}
    void correction(ObjectNode b,int expected) throws Exception {mvc.perform(post("/api/common/assignments/A-0001/history/1/corrections").contentType("application/json").content(b.toString())).andExpect(status().is(expected));}
    @Test void allowedCorrectionOnlyChangesHistoryAndPreservesNumericState() throws Exception {var h=history("EARLY","N");histories.add(h);when(mapper.lockHistory("A-0001",1)).thenReturn(h);when(mapper.correct(any())).thenReturn(1);Row before=rows.get("A-0001");correction(correction(),200);assertThat(rows.get("A-0001")).isEqualTo(before);verify(mapper,never()).update(any());verify(mapper,never()).saveMonth(any());assertThat(histories).anySatisfy(x -> {if(x.type().equals("CRCT")){assertThat(x.target()).isEqualTo(1);assertThat(x.afterValue()).isEqualTo("바른 내용");}});}
    @Test void missingCorrectionReasonIsRejected() throws Exception {var b=correction();b.remove("reason");correction(b,400);verify(mapper,never()).correct(any());}
    @ParameterizedTest @ValueSource(strings={"INPUT","CRCT"}) void inputAndCorrectionCannotBeCorrected(String type) throws Exception {when(mapper.lockHistory("A-0001",1)).thenReturn(history(type,"N"));correction(correction(),400);verify(mapper,never()).correct(any());}
    @Test void alreadyCorrectedCannotBeCorrectedAgain() throws Exception {when(mapper.lockHistory("A-0001",1)).thenReturn(history("EARLY","Y"));correction(correction(),400);verify(mapper,never()).correct(any());}
    @Test void lostCorrectionRaceIs409() throws Exception {var h=history("EARLY","N");histories.add(h);when(mapper.lockHistory("A-0001",1)).thenReturn(h);when(mapper.correct(any())).thenReturn(0);correction(correction(),409);verify(mapper,never()).insertHistory(any());}
    @Test void historyFilterIncludesOnlyRequestedType() throws Exception {histories.add(history("CNCL","N"));histories.add(history("EARLY","N"));mvc.perform(get("/api/common/assignments/A-0001/history").param("changeTypeCode","CNCL")).andExpect(status().isOk()).andExpect(response("GET","/api/common/assignments/{assignmentId}/history")).andExpect(jsonPath("$.data.items.length()").value(1));}
    @Test void excelUsesSameFiltersAndWritesDnldAfterRealXlsx() throws Exception {var result=mvc.perform(get("/api/common/assignments/excel").param("year","2026").param("keyword","developer")).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn();try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(result.getResponse().getContentAsByteArray()))){assertThat(workbook.getSheetAt(0).getPhysicalNumberOfRows()).isGreaterThan(1);}verify(mapper).insertAccess("admin","투입 목록 2026년 1건");}
    @Test void contractEndErrorIncludesTheSourceDateAndRecoveryAction() throws Exception {
        var b=input();b.put("endDate","2028-01-01");
        mvc.perform(post("/api/common/assignments").contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("endDate"))
            .andExpect(jsonPath("$.errors[0].message").value("철수일이 프로젝트 계약 종료일(2027-12-31)보다 늦습니다. 「프로젝트·투입 › 프로젝트」에서 계약 기간을 먼저 연장해 주세요."));
        noWrites();
    }
    @Test void retirementErrorIncludesTheActualPersonAndDate() throws Exception {
        when(mapper.person(anyString(),anyString(),any(),any())).thenReturn(person("new-developer",today));
        mvc.perform(post("/api/common/assignments").contentType("application/json").content(input().toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("endDate"))
            .andExpect(jsonPath("$.errors[0].message").value("철수일이 개발자님의 퇴사일(2026-11-15)보다 늦습니다."));
        noWrites();
    }
    @Test void crossPartWarningUsesDatabaseLabelsAndStillSaves() throws Exception {
        project.salesPartId="other";project.salesPartName="프로젝트 영업파트";
        mvc.perform(post("/api/common/assignments").contentType("application/json").content(input().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.warnings[0]").value("영업 파트가 다릅니다: 개발자님은 영업파트 소속, 프로젝트는 프로젝트 영업파트입니다. 매출은 프로젝트 파트로, 이 사람 이익금은 소속 파트(본사면 파트 정산 제외)로 잡힙니다. 파트를 잘못 골랐다면 개발자 풀에서 고쳐 주세요."));
        assertThat(rows).containsKey("A-0002");
    }
    @Test void equalStartAssignmentIsNotOfferedForTogether() throws Exception {
        mvc.perform(get("/api/common/assignments/contract-developers").param("projectId","P-0001")
            .param("developerId","new-developer").param("startDate","2026-10-16"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].developerId").value("developer"))
            .andExpect(jsonPath("$.data.items[0].overlappingAssignments").isEmpty());
        noWrites();
    }
    @Test void dayAfterOriginalStartIsOfferedForTogether() throws Exception {
        mvc.perform(get("/api/common/assignments/contract-developers").param("projectId","P-0001")
            .param("developerId","new-developer").param("startDate","2026-10-17"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].overlappingAssignments[0].assignmentId").value("A-0001"));
        noWrites();
    }
    @Test void pmUsesOnlyServerStandardAndCannotWritePriceChanges() {
        var pm=mock(CurrentBusinessActor.class);when(pm.roles()).thenReturn(Set.of("PM"));when(pm.userId()).thenReturn("admin");
        var service=new CommonAssignmentService(mapper,projects,calendar,pm,rules,amounts);
        var b=input().put("billingPrice",99999999);
        service.postCommonAssignment(b);
        assertThat(rows.get("A-0002").billingPrice()).isEqualByComparingTo("8000000");
        assertThat(service.getCommonAssignment("A-0002").billingPrice()).isNull();
        clearInvocations(mapper);
        assertThatThrownBy(() -> service.postCommonAssignmentChange("A-0001",change("UPRC").put("newBillingPrice",8500000)))
            .isInstanceOf(com.company.common.support.service.CodeFailure.class);
        noWrites();
    }
    @Test void pmMissingStandardHasExactMessageButFreeSupportBypassesIt() {
        var pm=mock(CurrentBusinessActor.class);when(pm.roles()).thenReturn(Set.of("PM"));when(pm.userId()).thenReturn("admin");
        var service=new CommonAssignmentService(mapper,projects,calendar,pm,rules,amounts);
        when(mapper.person(anyString(),anyString(),any(),any())).thenReturn(new Person("new-developer","개발자","FT","part","영업파트",null,null,false));
        assertThatThrownBy(() -> service.postCommonAssignment(input()))
            .isInstanceOf(com.company.common.support.service.CodeFailure.class)
            .hasMessageContaining("월 단가가 없어 배정할 수 없습니다. 파트장·재무에게 요청해 주세요.");
        noWrites();
        service.postCommonAssignment(input().put("inputTypeCode","FREE"));
        assertThat(rows.get("A-0002").billingPrice()).isZero();
    }
}
