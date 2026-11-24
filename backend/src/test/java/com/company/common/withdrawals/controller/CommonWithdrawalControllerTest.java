package com.company.common.withdrawals.controller;

import com.company.common.assignments.mapper.AssignmentMapper;
import com.company.common.assignments.mapper.AssignmentRows;
import com.company.common.assignments.service.*;
import com.company.common.calculation.mapper.*;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.codegroups.dto.CodeContracts;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.company.common.withdrawals.mapper.WithdrawalRows.*;
import com.company.common.withdrawals.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonWithdrawalController.class)
@ContextConfiguration(classes = {CommonWithdrawalController.class, CommonWithdrawalService.class,
    WithdrawalRecommendationService.class, WithdrawalTentativeService.class, WithdrawalCalculationRules.class,
    CalculationRuleService.class, CommonAssignmentService.class, CurrentBusinessActor.class,
    ExcelExportService.class, CodeExceptionHandler.class, ApiExceptionHandler.class})
class CommonWithdrawalControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean WithdrawalMapper mapper;
    @MockBean AssignmentMapper assignments;
    @MockBean ProjectMapper projects;
    @MockBean BusinessCalendar calendar;
    @MockBean WithdrawalCodes codes;
    @MockBean AssignmentMonthRules monthRules;
    @MockBean CalculationRuleMapper definitions;
    @MockBean UserPreferencePeriodSourceMapper periods;
    @MockBean PlatformTransactionManager transactions;
    final LocalDate today = LocalDate.of(2026,11,15);
    final Instant now = Instant.parse("2026-11-15T00:00:00Z");
    final Map<String, AssignmentRows.Row> sources = new LinkedHashMap<>();
    final List<AssignmentRows.HistoryRow> history = new ArrayList<>();
    final List<Reservation> reservations = new ArrayList<>();
    final List<Need> needs = new ArrayList<>();

    @BeforeEach void setup() throws Exception {
        sources.clear(); history.clear(); reservations.clear(); needs.clear();
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(calendar.today()).thenReturn(today);
        when(calendar.currentMonth()).thenReturn(YearMonth.from(today));
        when(calendar.now()).thenReturn(now);
        when(calendar.fromDatabaseMonth(anyString())).thenAnswer(c -> YearMonth.parse(((String)c.getArgument(0)).substring(0,4) + "-" + ((String)c.getArgument(0)).substring(4)));
        when(calendar.toDatabaseMonth(any())).thenAnswer(c -> c.getArgument(0).toString().replace("-", ""));
        for (String id : List.of("00","60","61","77","79","80","81","82")) {
            String seed = new ClassPathResource("db/migration/V8__calculation_rule_seed.sql").getContentAsString(StandardCharsets.UTF_8);
            var tuple = Pattern.compile("\\('식-" + id + "', (?:NULL|'[^']*'), '[^']*', '((?:[^']|'')*)', '((?:[^']|'')*)',").matcher(seed);
            assertThat(tuple.find()).isTrue();
            var row = new CalculationRuleRow(); row.calcId = "식-" + id;
            row.formula = tuple.group(1).replace("''", "'"); row.details = tuple.group(2).replace("''", "'");
            when(definitions.selectDefinition("식-" + id)).thenReturn(row);
        }
        var backend = code("BE", "백엔드", 1); backend.relatedJobs = "FE";
        when(codes.options("JOB")).thenReturn(List.of(backend, code("FE", "프론트엔드", 2)));
        when(codes.options("GRADE")).thenReturn(List.of(code("MD", "중급", 2), code("HI", "고급", 3)));
        when(codes.relatedJob("BE", "FE")).thenReturn(true);
        when(codes.extensionResponseCode(any())).thenAnswer(c -> Objects.requireNonNullElse(c.getArgument(0), ""));
        when(codes.extensionName(any())).thenAnswer(c -> name(c.getArgument(0)));
        when(codes.extensionStorageCode(any())).thenAnswer(c -> {String value=c.getArgument(0); if (!Set.of("H","M","L", "").contains(value)) throw CodeFailure.invalid("extensionPossibilityCode", "등록된 값을 골라 주세요."); return value.isEmpty()?null:value;});
        when(projects.codeName(anyString(), anyString())).thenAnswer(c -> name(c.getArgument(1)));
        when(projects.lockProject(anyString())).thenReturn("P-0001");
        when(assignments.row(anyString())).thenAnswer(c -> sources.get(c.getArgument(0)));
        when(assignments.lockRow(anyString())).thenAnswer(c -> sources.get(c.getArgument(0)));
        when(assignments.history(anyString())).thenAnswer(c -> history.stream().filter(h -> h.assignmentId().equals(c.getArgument(0))).toList());
        when(assignments.nextHistory(anyString())).thenReturn(1);
        when(assignments.update(any())).thenAnswer(c -> {AssignmentRows.Write w=c.getArgument(0); sources.put(w.row().assignmentId(), w.row()); return 1;});
        when(assignments.insertHistory(any())).thenAnswer(c -> {AssignmentRows.HistoryWrite w=c.getArgument(0); history.add(w.row()); return 1;});
        when(mapper.source(anyString())).thenAnswer(c -> source(sources.get(c.getArgument(0))));
        when(mapper.lockSource(anyString())).thenAnswer(c -> source(sources.get(c.getArgument(0))));
        when(mapper.lockDeveloper(anyString())).thenAnswer(c -> c.getArgument(0));
        when(mapper.reservations(anyString())).thenAnswer(c -> reservations.stream().filter(r -> r.developerId().equals(c.getArgument(0))).toList());
        when(mapper.needs()).thenAnswer(c -> new ArrayList<>(needs));
        when(mapper.reservationsFor(anyCollection())).thenAnswer(c -> {Collection<String> ids=c.getArgument(0); return reservations.stream().filter(r -> ids.contains(r.developerId())).toList();});
        when(mapper.earlyHistory(anyCollection())).thenAnswer(c -> {Collection<String> ids=c.getArgument(0); return history.stream().filter(h -> "EARLY".equals(h.type()) && ids.contains(h.assignmentId())).map(h -> new EarlyRow(h.assignmentId(), h.effectiveDate(), h.afterValue())).toList();});
        when(mapper.list(any())).thenAnswer(c -> {ListFilter f=c.getArgument(0); return sources.values().stream().map(this::source)
            .filter(s -> (f.year()==null || s.endDate().getYear()==f.year()) && "ACT".equals(s.statusCode()))
            .filter(s -> f.from()==null || !s.endDate().isBefore(f.from()))
            .filter(s -> f.through()==null || !s.endDate().isAfter(f.through()))
            .filter(s -> f.jobCode()==null || f.jobCode().equals(s.jobCode()))
            .filter(s -> f.extensionPossibilityCode()==null || f.extensionPossibilityCode().equals(Objects.requireNonNullElse(s.extensionPossibilityCode(), "")))
            .sorted(Comparator.comparing(Source::endDate).thenComparing(Source::assignmentId)).toList();});
        when(mapper.shared(any())).thenAnswer(c -> {SharedFilter f=c.getArgument(0);return sources.values().stream().map(this::source)
            .filter(s -> f.developerId()==null || f.developerId().equals(s.developerId()))
            .filter(s -> f.opportunityId()==null || reservations.stream().anyMatch(r -> r.developerId().equals(s.developerId()) && r.opportunityId().equals(f.opportunityId()))).toList();});
        when(mapper.release(anyString(), anyString())).thenAnswer(c -> {boolean removed=reservations.removeIf(r -> r.tentativeId().equals(c.getArgument(0)));return removed?1:0;});
        when(mapper.insertReservation(any())).thenAnswer(c -> {ReservationWrite w=c.getArgument(0); reservations.add(new Reservation("99", w.developerId(), w.opportunityId(), "기회 " + w.opportunityId(), w.needSn(), w.startMonth(), "ACT")); return 1;});
        sources.put("A-0001", assignment("A-0001", "person", today.plusDays(15), null));
        needs.add(need("O-0001", 1, "202612", "BE", "ING", 0));
    }
    CodeContracts.Item code(String id, String name, int order) {var item=new CodeContracts.Item();item.code=id;item.codeName=name;item.sortOrd=order;item.useYn="Y";return item;}
    String name(String code) {return code==null || code.isEmpty()?"미정":switch(code){case "H" -> "높음";case "M" -> "보통";case "L" -> "낮음";default -> code;};}
    AssignmentRows.Row assignment(String id,String person,LocalDate end,String extension) {return new AssignmentRows.Row(id,"P-0001",person,"BE",LocalDate.of(2026,1,1),end,BigDecimal.ONE,BigDecimal.TEN,"NEW",null,extension,2,"N","ACT",null,"프로젝트",person,"D-1","백엔드","MO","ING");}
    Source source(AssignmentRows.Row row) {return row==null?null:new Source(row.assignmentId(),row.developerId(),row.name(),row.projectName(),row.jobCode(),row.jobName(),row.endDate(),row.extensionPossibilityCode(),row.extensionCount(),row.modifiedAt(),row.statusCode(),"BE","MD","client","중급",row.projectId(),row.managementNumber());}
    Need need(String id,int sn,String start,String job,String status,int occupied) {return new Need(id,sn,"기회 " + id,job,job,"MD","중급",start,3,1,occupied,status,"client",new BigDecimal("60"),BigDecimal.ONE,"고객사","제안");}
    void noTentativeWrites() {verify(mapper,never()).release(anyString(),anyString());verify(mapper,never()).insertReservation(any());}
    void save(int sn,int status) throws Exception {mvc.perform(put("/api/common/withdrawals/A-0001/tentative-assignment").contentType("application/json").content("{\"opportunityId\":\"O-0001\",\"needSn\":"+sn+"}")).andExpect(status().is(status));}
    void extension(String body,int status) throws Exception {mvc.perform(put("/api/common/withdrawals/A-0001/extension-possibility").contentType("application/json").content(body)).andExpect(status().is(status));}
    static org.springframework.test.web.servlet.ResultMatcher contract(String method,String path) {return OpenApiResponseContract.response(method,path);}

    @Test void anonymousRecommendationsUseDbScoreReasonsWaitingAndPositiveDecimalShortages() throws Exception {
        when(mapper.opportunityDemand(any())).thenReturn(List.of(new OpportunityDemandRow(new BigDecimal("2"),new BigDecimal("0.5"),new BigDecimal("60"))));
        mvc.perform(get("/api/common/withdrawals/A-0001/recommendations")).andExpect(status().isOk())
            .andExpect(contract("GET","/api/common/withdrawals/{assignmentId}/recommendations"))
            .andExpect(jsonPath("$.data.items[0].needSn").value(1)).andExpect(jsonPath("$.data.items[0].score").value(100))
            .andExpect(jsonPath("$.data.items[0].waitingLabel").value("바로 이어서 투입"))
            .andExpect(jsonPath("$.data.shortages[0].shortageCount").value(0.6));
        verify(definitions,atLeastOnce()).selectDefinition("식-80");noTentativeWrites();
    }
    @Test void recommendationCardCarriesMockupReasonsSeatCustomerStageAndHeader() throws Exception {
        when(mapper.skills("person")).thenReturn(List.of("Java","Spring"));
        mvc.perform(get("/api/common/withdrawals/A-0001/recommendations")).andExpect(status().isOk())
            .andExpect(contract("GET","/api/common/withdrawals/{assignmentId}/recommendations"))
            .andExpect(jsonPath("$.data.items[0].reasons[0]").value("직무 일치"))
            .andExpect(jsonPath("$.data.items[0].reasons[1]").value("등급 일치"))
            .andExpect(jsonPath("$.data.items[0].reasons[2]").value("공백 없음"))
            .andExpect(jsonPath("$.data.items[0].reasons[3]").value("같은 고객사"))
            .andExpect(jsonPath("$.data.items[0].reasons[4]").value("수주 확률 60%"))
            .andExpect(jsonPath("$.data.items[0].remainingCount").value(1))
            .andExpect(jsonPath("$.data.items[0].customerName").value("고객사"))
            .andExpect(jsonPath("$.data.items[0].stageName").value("제안"))
            .andExpect(jsonPath("$.data.items[0].tentative").value(false))
            .andExpect(jsonPath("$.data.target.name").value("person"))
            .andExpect(jsonPath("$.data.target.gradeName").value("중급"))
            .andExpect(jsonPath("$.data.target.skills[1]").value("Spring"))
            .andExpect(jsonPath("$.data.target.extensionPossibilityName").value("미정"))
            .andExpect(jsonPath("$.data.target.waitingStartDate").value("2026-12-01"))
            .andExpect(jsonPath("$.data.target.tentativeOpportunityName").value(org.hamcrest.Matchers.nullValue()));
    }
    @Test void emptyRecommendationStillReturnsHeaderAndEmptyLists() throws Exception {
        needs.clear();
        mvc.perform(get("/api/common/withdrawals/A-0001/recommendations")).andExpect(status().isOk())
            .andExpect(contract("GET","/api/common/withdrawals/{assignmentId}/recommendations"))
            .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.shortages").isEmpty())
            .andExpect(jsonPath("$.data.target.name").value("person"));
    }
    @Test void smallShortageIsNotAnnouncedAndLargeOneHasOneDecimal() throws Exception {
        when(mapper.opportunityDemand(any())).thenReturn(List.of(new OpportunityDemandRow(BigDecimal.ONE,new BigDecimal("0.4"),new BigDecimal("100"))));
        mvc.perform(get("/api/common/withdrawals/A-0001/recommendations")).andExpect(jsonPath("$.data.shortages").isEmpty());
        when(mapper.opportunityDemand(any())).thenReturn(List.of(new OpportunityDemandRow(BigDecimal.ONE,new BigDecimal("0.46"),new BigDecimal("100"))));
        mvc.perform(get("/api/common/withdrawals/A-0001/recommendations")).andExpect(jsonPath("$.data.shortages[0].shortageCount").value(0.5));
    }
    @Test void fullOutsideWindowEndedAndUnrelatedNeedsAreExcludedWithoutReleasingOccupancy() throws Exception {
        needs.clear();needs.add(need("full",1,"202612","BE","ING",1));needs.add(need("late",2,"202704","BE","ING",0));
        needs.add(need("ended",3,"202612","BE","END",0));needs.add(need("other",4,"202612","PM","ING",0));
        needs.add(need("over",5,"202609","BE","ING",0));
        mvc.perform(get("/api/common/withdrawals/A-0001/recommendations")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());noTentativeWrites();
    }
    @Test void alreadyStartedSeatThatHasNotEndedIsRecommendedFromTheMonthAfterWithdrawal() throws Exception {
        // 식-79 조건 1: 이미 시작했으면 끝나기 전까지 대상. 시작월 = max(철수 다음 달, 자리 시작월).
        needs.clear();needs.add(need("started",1,"202610","BE","ING",0));
        mvc.perform(get("/api/common/withdrawals/A-0001/recommendations")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].opportunityId").value("started"))
            .andExpect(jsonPath("$.data.items[0].startMonth").value("2026-10"))
            .andExpect(jsonPath("$.data.items[0].reasons[2]").value("공백 없음"))
            .andExpect(jsonPath("$.data.items[0].waitingLabel").value("바로 이어서 투입"));
        mvc.perform(get("/api/common/withdrawals").param("year","2026"))
            .andExpect(jsonPath("$.data.items[0].nextAssignment.recommendationCount").value(1));
        mvc.perform(put("/api/common/withdrawals/A-0001/tentative-assignment").contentType("application/json")
            .content("{\"opportunityId\":\"started\",\"needSn\":1}")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assignment.startMonth").value("2026-12"));
        verify(mapper).insertReservation(argThat(w -> "started".equals(w.opportunityId()) && "202612".equals(w.startMonth())));
    }
    @Test void relatedJobUsesTwentyInsteadOfFortyAndProbabilitySixtyAddsFive() throws Exception {
        needs.add(need("O-0002",2,"202612","FE","ING",0));
        mvc.perform(get("/api/common/withdrawals/A-0001/recommendations")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].score").value(100)).andExpect(jsonPath("$.data.items[1].score").value(80));
    }
    @Test void recommendationLimitAndDeterministicTieOrdering() throws Exception {
        needs.clear();for(int i=7;i>0;i--)needs.add(need("O-000"+i,i,"202612","BE","ING",0));
        mvc.perform(get("/api/common/withdrawals/A-0001/recommendations")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(5)).andExpect(jsonPath("$.data.items[0].opportunityId").value("O-0001"));
        mvc.perform(get("/api/common/withdrawals").param("year","2026")).andExpect(jsonPath("$.data.items[0].nextAssignment.recommendationCount").value(7));
    }
    @Test void highPossibilityIsNotANewRecommendationTarget() throws Exception {
        sources.put("A-0001",assignment("A-0001","person",today.plusDays(15),"H"));
        mvc.perform(get("/api/common/withdrawals/A-0001/recommendations")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }
    @Test void currentReservationNeedsExplicitReplacementReadAndIsNeverReleasedOnRead() throws Exception {
        reservations.add(new Reservation("1","person","other","기존",8,"202612","ACT"));
        mvc.perform(get("/api/common/withdrawals/A-0001/recommendations")).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/common/withdrawals/A-0001/recommendations").param("replaceCurrent","true")).andExpect(jsonPath("$.data.items.length()").value(1));noTentativeWrites();
    }
    @Test
    @DisplayName("happy auth side-effect: 무인증 연장 가능성 저장은 TB_PJ_ASGN·TB_PJ_ASGN_HIST 한 줄")
    void anonymousExtensionChangesSourceAndOneDisplayNameHistoryWithoutReason() throws Exception {
        sources.put("A-0001",assignment("A-0001","person",today.plusDays(15),"H"));
        mvc.perform(put("/api/common/withdrawals/A-0001/extension-possibility").contentType("application/json")
            .content("{\"extensionPossibilityCode\":\"M\",\"modifiedAt\":null}"))
            .andExpect(status().isOk()).andExpect(contract("PUT","/api/common/withdrawals/{assignmentId}/extension-possibility"))
            .andExpect(jsonPath("$.data.assignmentId").value("A-0001"))
            .andExpect(jsonPath("$.data.extensionPossibilityCode").value("M"));
        assertThat(sources.get("A-0001").extensionPossibilityCode()).isEqualTo("M");
        assertThat(history).singleElement().satisfies(h -> {assertThat(h.type()).isEqualTo("EXTP");assertThat(h.beforeValue()).isEqualTo("높음");assertThat(h.afterValue()).isEqualTo("보통");assertThat(h.reasonCode()).isNull();});
        mvc.perform(get("/api/common/withdrawals").param("year","2026")).andExpect(jsonPath("$.data.items[0].extensionPossibilityCode").value("M"));
        mvc.perform(get("/api/common/withdrawals/tentative-assignments").param("developerId","person"))
            .andExpect(contract("GET","/api/common/withdrawals/tentative-assignments")).andExpect(jsonPath("$.data.items[0].extensionPossibilityCode").value("M"));
        verify(definitions,never()).selectDefinition("식-80");verify(assignments,never()).saveMonth(any());
    }
    @Test void missingExtensionIsFieldValidationAndDoesNotWrite() throws Exception {
        mvc.perform(put("/api/common/withdrawals/A-0001/extension-possibility").contentType("application/json").content("{\"modifiedAt\":null}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("extensionPossibilityCode"));
        verify(assignments,never()).update(any());assertThat(history).isEmpty();
    }
    @Test void missingVersionIsNotConfusedWithExplicitNull() throws Exception {extension("{\"extensionPossibilityCode\":\"M\"}",400);verify(assignments,never()).update(any());}
    @Test void unknownPossibilityIsRejectedWithoutSourceOrHistoryWrites() throws Exception {
        extension("{\"extensionPossibilityCode\":\"UNKNOWN\",\"modifiedAt\":null}",400);
        verify(assignments,never()).update(any());assertThat(history).isEmpty();
    }
    @Test void cancelledAssignmentCannotChangePossibility() throws Exception {
        var r=sources.get("A-0001");
        when(assignments.lockRow("A-0001")).thenReturn(new AssignmentRows.Row(r.assignmentId(),r.projectId(),r.developerId(),r.jobCode(),r.startDate(),r.endDate(),r.monthlyMm(),r.billingPrice(),r.inputTypeCode(),r.contractDeveloperId(),r.extensionPossibilityCode(),r.extensionCount(),r.earlyYn(),"CNCL",r.modifiedAt(),r.projectName(),r.name(),r.managementNumber(),r.jobName(),r.contractTypeCode(),r.projectStatusCode()));
        extension("{\"extensionPossibilityCode\":\"M\",\"modifiedAt\":null}",409);
        verify(assignments,never()).update(any());assertThat(history).isEmpty();
    }
    @Test
    @DisplayName("business: stale modifiedAt은 409, 기존 배정과 이력 보존")
    void staleExtensionVersionConflictsWithoutHistory() throws Exception {
        mvc.perform(put("/api/common/withdrawals/A-0001/extension-possibility").contentType("application/json")
            .content("{\"extensionPossibilityCode\":\"M\",\"modifiedAt\":\"2020-01-01T00:00:00Z\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0].field").value("modifiedAt"));
        verify(assignments,never()).update(any());assertThat(history).isEmpty();assertThat(sources.get("A-0001").extensionPossibilityCode()).isNull();
    }
    @Test void unknownExtensionReturnsEmptyStringAndSameValueCreatesNoHistory() throws Exception {
        mvc.perform(put("/api/common/withdrawals/A-0001/extension-possibility").contentType("application/json").content("{\"extensionPossibilityCode\":\"\",\"modifiedAt\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.extensionPossibilityCode").value(""));assertThat(history).isEmpty();
    }
    @Test void historicalCompletedDateStillAllowsExtensionWithoutReason() throws Exception {
        sources.put("A-0001",assignment("A-0001","person",LocalDate.of(2025,5,1),"H"));
        extension("{\"extensionPossibilityCode\":\"M\",\"modifiedAt\":null}",200);assertThat(history).hasSize(1);
    }
    @Test
    @DisplayName("happy auth side-effect: TB_SL_TENT_ASGN 저장·getCommonWithdrawalAssignments 재조회, TB_PJ_ASGN·TB_CO_MON_COST·TB_SL_RCMD_LOG 무변경")
    void anonymousTentativeSaveUsesServerIdentityAndNextMonthWithoutConfirmedWrites() throws Exception {
        var before=new LinkedHashMap<>(sources);
        mvc.perform(put("/api/common/withdrawals/A-0001/tentative-assignment").contentType("application/json").content("{\"opportunityId\":\"O-0001\",\"needSn\":1}"))
            .andExpect(status().isOk()).andExpect(contract("PUT","/api/common/withdrawals/{assignmentId}/tentative-assignment"))
            .andExpect(jsonPath("$.data.assignment.needSn").value(1))
            .andExpect(jsonPath("$.data.assignment.startMonth").value("2026-12"));
        mvc.perform(get("/api/common/withdrawals/A-0001/tentative-assignment")).andExpect(status().isOk())
            .andExpect(contract("GET","/api/common/withdrawals/{assignmentId}/tentative-assignment"))
            .andExpect(jsonPath("$.data.assignment.needSn").value(1)).andExpect(jsonPath("$.data.assignment.startMonth").value("2026-12"));
        assertThat(reservations).hasSize(1);assertThat(sources).isEqualTo(before);
        verify(assignments,never()).update(any());verify(assignments,never()).saveMonth(any());
        verify(assignments,never()).insert(any());verify(assignments,never()).insertHistory(any());
        verify(mapper).insertReservation(argThat(w -> "person".equals(w.developerId()) && "admin".equals(w.actor()) && w.needSn()==1));
        verify(mapper).touchOpportunity("O-0001","admin");
        mvc.perform(get("/api/common/withdrawals/tentative-assignments").param("developerId","person"))
            .andExpect(status().isOk()).andExpect(contract("GET","/api/common/withdrawals/tentative-assignments"))
            .andExpect(jsonPath("$.data.items[0].tentativeAssignment.opportunityId").value("O-0001"));
        String sql=new ClassPathResource("mapper/withdrawals/WithdrawalMapper.xml").getContentAsString(StandardCharsets.UTF_8);
        var writes=Pattern.compile("(?is)<(?:insert|update|delete)\\b[^>]*>(.*?)</(?:insert|update|delete)>").matcher(sql);
        int statements=0;
        while(writes.find()) {
            String write=writes.group(1).toUpperCase(Locale.ROOT);
            // 엑셀 다운로드 접근 기록(DNLD)은 업무 자료가 아니라서 따로 센다(2026-11-19).
            if (write.contains("TB_SY_ACCS_LOG")) { assertThat(write).contains("'DNLD'").doesNotContain("TB_PJ_ASGN","TB_SL_TENT_ASGN"); continue; }
            // 가배정·해제 때 영업 기회 수정 시각만 바꾼다(결정 C8, 2026-11-24). 다른 칸은 쓰지 않는다.
            if (write.contains("UPDATE TB_SL_OPP ")) { assertThat(write).contains("MOD_DTTM","MOD_USER_ID").doesNotContain("OPP_STAT_CD","SALES_STEP_CD","EXPCT_AMT","WIN_PRBL_RT"); continue; }
            assertThat(write).contains("TB_SL_TENT_ASGN")
                .doesNotContain("TB_PJ_ASGN","TB_CO_MON_COST","TB_SL_RCMD_LOG");statements++;
        }
        assertThat(statements).isEqualTo(2);
        var order=inOrder(projects,mapper);order.verify(projects).lockSources();order.verify(mapper).lockDeveloper("person");order.verify(mapper).lockSource("A-0001");order.verify(mapper).reservations("person");order.verify(mapper).lockNeeds();
    }
    @Test void replacementReleasesOnlyOldActAndSharedReadShowsOnlyNewOpportunity() throws Exception {
        reservations.add(new Reservation("1","person","old","이전",7,"202612","ACT"));save(1,200);
        assertThat(reservations).singleElement().satisfies(r -> assertThat(r.opportunityId()).isEqualTo("O-0001"));
        mvc.perform(get("/api/common/withdrawals/tentative-assignments").param("opportunityId","old")).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/common/withdrawals/tentative-assignments").param("opportunityId","O-0001")).andExpect(jsonPath("$.data.items[0].tentativeAssignment.needSn").value(1));
    }
    @Test void missingNeedIsFieldValidationWithExistingReservationUnchanged() throws Exception {
        mvc.perform(put("/api/common/withdrawals/A-0001/tentative-assignment").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("needSn"));noTentativeWrites();
    }
    @Test
    @DisplayName("business: 마지막 자리 소진 시 409, 기존 TB_SL_TENT_ASGN 점유 유지")
    void capacityLostAfterReadConflictsBeforeReleasingOldReservation() throws Exception {
        mvc.perform(get("/api/common/withdrawals/A-0001/recommendations")).andExpect(status().isOk());
        reservations.add(new Reservation("1","person","old","이전",7,"202612","ACT"));
        needs.set(0,need("O-0001",1,"202612","BE","ING",1));
        mvc.perform(put("/api/common/withdrawals/A-0001/tentative-assignment").contentType("application/json").content("{\"opportunityId\":\"O-0001\",\"needSn\":1}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0].field").value("needSn"));
        noTentativeWrites();assertThat(reservations).hasSize(1);assertThat(reservations.get(0).opportunityId()).isEqualTo("old");
    }
    @Test void sameNeedNumberInTwoOpportunitiesUsesTheRequestedOpportunity() throws Exception {
        // 필요 인력 줄의 키는 (OPP_ID, NEED_SN): 같은 NEED_SN이 두 기회에 있어도 보낸 기회의 줄만 차지한다.
        needs.add(need("O-0002",1,"202612","BE","ING",0));
        mvc.perform(put("/api/common/withdrawals/A-0001/tentative-assignment").contentType("application/json").content("{\"opportunityId\":\"O-0002\",\"needSn\":1}"))
            .andExpect(status().isOk()).andExpect(contract("PUT","/api/common/withdrawals/{assignmentId}/tentative-assignment"))
            .andExpect(jsonPath("$.data.assignment.opportunityId").value("O-0002")).andExpect(jsonPath("$.data.assignment.needSn").value(1));
        verify(mapper).insertReservation(argThat(w -> "O-0002".equals(w.opportunityId()) && w.needSn()==1));
        assertThat(reservations).singleElement().satisfies(r -> assertThat(r.opportunityId()).isEqualTo("O-0002"));
    }
    @Test void missingOrUnknownOpportunityIsFieldValidationWithoutWrites() throws Exception {
        mvc.perform(put("/api/common/withdrawals/A-0001/tentative-assignment").contentType("application/json").content("{\"needSn\":1}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("opportunityId"));
        mvc.perform(put("/api/common/withdrawals/A-0001/tentative-assignment").contentType("application/json").content("{\"opportunityId\":\"O-9999\",\"needSn\":1}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("needSn"));
        noTentativeWrites();
    }
    @Test void otherPersonsSeatCountsButOwnSeatDoesNotWhenCountingRemainingSlots() throws Exception {
        // 식-79 조건 2: 남은 자리 = 필요 인원 − 그 줄의 ACT·CNVT. 이 사람 자신의 자리는 빼고 센다(시안 nextSlots).
        reservations.add(new Reservation("1","person","O-0001","기회 O-0001",1,"202612","ACT"));
        needs.set(0,need("O-0001",1,"202612","BE","ING",1));
        mvc.perform(get("/api/common/withdrawals/A-0001/recommendations").param("replaceCurrent","true"))
            .andExpect(jsonPath("$.data.items[0].remainingCount").value(1)).andExpect(jsonPath("$.data.items[0].tentative").value(true));
        reservations.clear();reservations.add(new Reservation("2","someone","O-0001","기회 O-0001",1,"202612","CNVT"));
        mvc.perform(get("/api/common/withdrawals/A-0001/recommendations")).andExpect(jsonPath("$.data.items").isEmpty());
        save(1,409);noTentativeWrites();
    }
    @Test void afterTentativeTheReplacementReadShowsTheSameOpportunityAsAlreadyReserved() throws Exception {
        needs.add(need("O-0002",2,"202612","BE","ING",0));
        save(1,200);
        var occupied=needs.get(0);needs.set(0,need(occupied.opportunityId(),occupied.needSn(),occupied.startMonth(),"BE","ING",1));
        mvc.perform(get("/api/common/withdrawals/A-0001/recommendations").param("replaceCurrent","true")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[?(@.opportunityId=='O-0001')].tentative").value(org.hamcrest.Matchers.contains(true)))
            .andExpect(jsonPath("$.data.items[?(@.opportunityId=='O-0002')].tentative").value(org.hamcrest.Matchers.contains(false)))
            .andExpect(jsonPath("$.data.target.tentativeOpportunityName").value("기회 O-0001"))
            .andExpect(jsonPath("$.data.target.tentativeStartMonth").value("2026-12"));
    }
    @Test void sameTargetIsIdempotent() throws Exception {reservations.add(new Reservation("1","person","O-0001","기회",1,"202612","ACT"));save(1,200);noTentativeWrites();}
    @Test void handedOffCandidateCannotBeReplaced() throws Exception {reservations.add(new Reservation("1","person","old","이전",7,"202612","CNVT"));save(1,409);noTentativeWrites();}
    @Test void currentTentativeMayBeNull() throws Exception {mvc.perform(get("/api/common/withdrawals/A-0001/tentative-assignment")).andExpect(status().isOk()).andExpect(contract("GET","/api/common/withdrawals/{assignmentId}/tentative-assignment")).andExpect(jsonPath("$.data.assignment").value(org.hamcrest.Matchers.nullValue()));}
    @Test void missingAssignmentIsValidationNotAnEmptyRecommendation() throws Exception {mvc.perform(get("/api/common/withdrawals/missing/recommendations")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("assignmentId"));}
    @Test void missingYearAndUnknownPeriodAreValidationErrors() throws Exception {
        mvc.perform(get("/api/common/withdrawals")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.errors[0].field").value("year"));
        mvc.perform(get("/api/common/withdrawals").param("year","2026").param("period","unknown")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("period"));
        verify(mapper,never()).list(any());noTentativeWrites();
    }
    @Test void emptyWithdrawalListIsSuccessfulAndNeverWritesSources() throws Exception {
        sources.clear();
        mvc.perform(get("/api/common/withdrawals").param("year","2026"))
            .andExpect(status().isOk()).andExpect(contract("GET","/api/common/withdrawals"))
            .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.totalCount").value(0));
        verify(mapper,atLeastOnce()).list(any());noTentativeWrites();
        verify(assignments,never()).update(any());verify(assignments,never()).insertHistory(any());
    }
    @Test void bannerCountsAssignmentsLikeTheMockupAndDoesNotExistForOtherYears() throws Exception {
        // 시안 nextSummary: 배정(ASSIGN) 단위로 센다. 같은 사람의 두 배정은 두 건이다.
        sources.put("A-0002",assignment("A-0002","person",today.plusDays(20),null));
        mvc.perform(get("/api/common/withdrawals").param("year","2026")).andExpect(status().isOk())
            .andExpect(contract("GET","/api/common/withdrawals")).andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.banner.soonCount").value(2))
            .andExpect(jsonPath("$.data.banner.unassignedCount").value(2)).andExpect(jsonPath("$.data.banner.matchedCount").value(2));
        mvc.perform(get("/api/common/withdrawals").param("year","2025")).andExpect(jsonPath("$.data.banner").value(org.hamcrest.Matchers.nullValue()));
    }
    @Test void bannerIgnoresListFiltersAndSeparatesHighTentativeAndNoSeat() throws Exception {
        sources.put("A-0002",assignment("A-0002","high",today.plusDays(20),"H"));
        sources.put("A-0003",assignment("A-0003","reserved",today.plusDays(25),"M"));
        sources.put("A-0004",assignment("A-0004","later",LocalDate.of(2026,12,31),"L"));
        sources.put("A-0005",assignment("A-0005","gone",today.minusDays(3),null));
        reservations.add(new Reservation("1","reserved","O-0001","기회 O-0001",1,"202612","ACT"));
        needs.set(0,need("O-0001",1,"202612","BE","ING",1));
        // 조건(직무 FE·이미 철수)으로 목록이 비어도 띠 숫자는 그대로다.
        mvc.perform(get("/api/common/withdrawals").param("year","2026").param("jobCode","FE")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.banner.soonCount").value(4))
            .andExpect(jsonPath("$.data.banner.unassignedCount").value(2))
            .andExpect(jsonPath("$.data.banner.matchedCount").value(0));
        mvc.perform(get("/api/common/withdrawals").param("year","2026").param("period","이미 철수"))
            .andExpect(jsonPath("$.data.totalCount").value(1)).andExpect(jsonPath("$.data.banner.soonCount").value(4));
    }
    @Test void bannerIsZeroNotMissingWhenNothingWithdrawsSoon() throws Exception {
        sources.clear();
        mvc.perform(get("/api/common/withdrawals").param("year","2026")).andExpect(status().isOk())
            .andExpect(contract("GET","/api/common/withdrawals"))
            .andExpect(jsonPath("$.data.banner.soonCount").value(0)).andExpect(jsonPath("$.data.banner.unassignedCount").value(0))
            .andExpect(jsonPath("$.data.banner.matchedCount").value(0));
    }
    @Test void nextAssignmentCellExplainsNonTargetsWithMockupWords() throws Exception {
        sources.put("A-0002",assignment("A-0002","high",today.plusDays(20),"H"));
        sources.put("A-0003",assignment("A-0003","later",LocalDate.of(2027,3,31),"M"));
        sources.put("A-0004",assignment("A-0004","nothing",today.plusDays(25),"L"));
        sources.put("A-0005",assignment("A-0005","gone",today.minusDays(3),null));
        sources.put("A-0006",assignment("A-0006","reserved-gone",today.minusDays(2),null));
        reservations.add(new Reservation("1","reserved-gone","O-0001","기회 O-0001",1,"202612","ACT"));
        reservations.add(new Reservation("2","nothing","O-0001","기회 O-0001",1,"202612","ACT"));
        needs.set(0,need("O-0001",1,"202612","BE","ING",1));
        sources.put("A-0007",assignment("A-0007","free",today.plusDays(26),"M"));
        var byId=new HashMap<String,com.fasterxml.jackson.databind.JsonNode>();
        for (String year : List.of("2026","2027")) {
            String body=mvc.perform(get("/api/common/withdrawals").param("year",year)).andExpect(status().isOk())
                .andExpect(contract("GET","/api/common/withdrawals")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            json.readTree(body).path("data").path("items").forEach(i -> byId.put(i.path("assignmentId").asText(), i.path("nextAssignment")));
        }
        assertThat(byId.get("A-0002").path("recommendationTarget").asBoolean()).isFalse();
        assertThat(byId.get("A-0002").path("note").asText()).isEqualTo("연장 가능성 높음");
        assertThat(byId.get("A-0003").path("recommendationTarget").asBoolean()).isFalse();
        assertThat(byId.get("A-0003").path("note").asText()).isEqualTo("3개월 뒤 철수");
        assertThat(byId.get("A-0004").path("hasTentativeAssignment").asBoolean()).isTrue();
        assertThat(byId.get("A-0004").path("note").isNull()).isTrue();
        assertThat(byId.get("A-0005").path("note").isNull()).isTrue();
        assertThat(byId.get("A-0006").path("hasTentativeAssignment").asBoolean()).isTrue();
        assertThat(byId.get("A-0006").path("opportunityName").asText()).isEqualTo("기회 O-0001");
        assertThat(byId.get("A-0007").path("recommendationTarget").asBoolean()).isTrue();
        assertThat(byId.get("A-0007").path("recommendationCount").asInt()).isZero();
        assertThat(byId.get("A-0007").path("note").asText()).isEqualTo("맞는 자리 없음");
        var labels=new ArrayList<String>();
        for (String year : List.of("2026","2027")) {
            byte[] bytes=mvc.perform(get("/api/common/withdrawals/excel").param("year",year)).andReturn().getResponse().getContentAsByteArray();
            try(var book=new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))){
                for(var row:book.getSheetAt(0)) if(row.getCell(7)!=null) labels.add(row.getCell(7).toString());
            }
        }
        assertThat(labels).contains("– · 연장 가능성 높음","– · 3개월 뒤 철수","가배정 · 기회 O-0001","없음 · 맞는 자리 없음","–");
    }
    @Test void listReadsReservationsHistoryCodesFormulasAndNeedsOncePerRequest() throws Exception {
        // 줄마다 묻지 않는다: 가배정·조기 철수 이력은 묶음 조회 한 번, 식·공통코드·필요 인력 줄은 요청마다 한 번.
        for(int i=2;i<=30;i++)sources.put("A-"+i,assignment("A-"+i,"person"+i,today.plusDays(10),i%2==0?"M":null));
        history.add(new AssignmentRows.HistoryRow(1,"A-2","EARLY",LocalDate.of(2026,10,1),"","2026-11-25",null,"",null,null,"N","N","N",null,"admin",now));
        mvc.perform(get("/api/common/withdrawals").param("year","2026")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalCount").value(30))
            .andExpect(jsonPath("$.data.items[?(@.assignmentId=='A-2')].earlyWithdrawalHistory[0]").value(org.hamcrest.Matchers.contains("2026-10-01 · 2026-11-25")));
        verify(mapper,never()).reservations(anyString());
        verify(mapper,times(1)).reservationsFor(anyCollection());
        verify(mapper,times(1)).earlyHistory(anyCollection());
        verify(assignments,never()).history(anyString());
        verify(mapper,times(1)).needs();
        verify(codes,atMost(1)).options("JOB");
        verify(codes,atMost(1)).options("GRADE");
        verify(codes,atMost(2)).extensionName(any());
        for (String id : List.of("식-77","식-79","식-82")) verify(definitions,times(1)).selectDefinition(id);
        verify(definitions,times(1)).selectDefinition("식-00");
        verify(mapper,times(2)).list(any());
    }
    @Test void periodAndJobFiltersShareTheTotalAndStableOrder() throws Exception {
        sources.put("A-0002",assignment("A-0002","other",today.minusDays(1),null));
        mvc.perform(get("/api/common/withdrawals").param("year","2026").param("period","이미 철수").param("jobCode","BE"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalCount").value(1)).andExpect(jsonPath("$.data.items[0].withdrawalCompleted").value(true));
    }
    @Test void exportContainsAllFilteredRowsAndOnlyVisibleBusinessColumns() throws Exception {
        for(int i=2;i<=30;i++)sources.put("A-"+i,assignment("A-"+i,"person"+i,today.plusDays(10),"H"));
        byte[] bytes=mvc.perform(get("/api/common/withdrawals/excel").param("year","2026"))
            .andExpect(status().isOk()).andExpect(header().string("X-Total-Count","30"))
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn().getResponse().getContentAsByteArray();
        try(var book=new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))){
            assertThat(book.getNumberOfSheets()).isEqualTo(1);
            var sheet=book.getSheetAt(0);assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(30);
            boolean header=false;for(var row:sheet)if("이름".equals(row.getCell(0)==null?"":row.getCell(0).toString())) {assertThat(row.getLastCellNum()).isEqualTo((short)8);header=true;}
            assertThat(header).isTrue();
        }
        verify(assignments,never()).insertAccess(anyString(),anyString());noTentativeWrites();
    }
}
