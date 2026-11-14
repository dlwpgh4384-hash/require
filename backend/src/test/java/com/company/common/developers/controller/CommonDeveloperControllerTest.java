package com.company.common.developers.controller;

import com.company.common.developers.mapper.*;
import com.company.common.developers.dto.DeveloperContracts.*;
import com.company.common.developers.service.*;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.support.service.*;
import com.company.common.users.mapper.UserMapper;
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
import static com.company.common.support.controller.OpenApiResponseContract.response;

@WebMvcTest(CommonDeveloperController.class)
@ContextConfiguration(classes={CommonDeveloperController.class,CommonDeveloperService.class,CodeExceptionHandler.class,
    ApiExceptionHandler.class,CurrentBusinessActor.class,ExcelExportService.class})
class CommonDeveloperControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean DeveloperMapper mapper;
    @MockBean UserMapper management;
    @MockBean BusinessCalendar calendar;
    @MockBean DeveloperNumberReservations reservations;
    @MockBean DeveloperManMonthRules calculations;
    @MockBean PlatformTransactionManager transactions;
    Map<String,DeveloperRows.Developer> developers;
    Map<String,List<DeveloperRows.State>> states;
    Map<String,List<String>> skills;
    List<Audit> audits;
    final LocalDate today=LocalDate.of(2026,11,15);
    @BeforeEach void setup() {
        developers=new LinkedHashMap<>(); states=new HashMap<>(); skills=new HashMap<>(); audits=new ArrayList<>();
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(calendar.today()).thenReturn(today); when(calendar.currentMonth()).thenReturn(YearMonth.from(today));
        when(calendar.now()).thenReturn(Instant.parse("2026-11-14T16:00:00Z"));
        when(calendar.toDatabaseMonth(any())).thenAnswer(c -> ((YearMonth)c.getArgument(0)).toString().replace("-",""));
        when(reservations.numbers()).thenReturn(Set.of("K-0001"));
        when(calculations.prorate(any(),anyInt())).thenAnswer(c -> ((BigDecimal)c.getArgument(0)).divide(BigDecimal.valueOf((int)c.getArgument(1)),2,java.math.RoundingMode.HALF_UP));
        when(calculations.over(any())).thenAnswer(c -> ((BigDecimal)c.getArgument(0)).compareTo(BigDecimal.ONE)>0);
        when(mapper.selectCodes(anyString())).thenAnswer(c -> {
            String group=c.getArgument(0); var values=switch(group) {
                case "EMP_TYPE" -> Map.of("FT","정규직"); case "JOB" -> Map.of("DEV","개발","PM","PM");
                case "DRCT_CLS" -> Map.of("DRCT","직접","INDRCT","간접(관리직)");
                case "JBPS" -> Map.of("MEMBER","팀원"); case "SKILL" -> Map.of("JAVA","Java"); default -> Map.<String,String>of();
            };
            return values.entrySet().stream().map(e -> {var r=new DeveloperRows.Code(); r.code=e.getKey(); r.name=e.getValue(); return r;}).toList();
        });
        when(mapper.selectablePart("part")).thenReturn(true);
        when(mapper.selectableOrganization("vendor")).thenReturn(true);
        when(mapper.selectManagementNumbers()).thenAnswer(c -> developers.values().stream().map(d -> d.managementNumber).toList());
        when(mapper.lockDeveloper(anyString(),anyString())).thenAnswer(c -> developers.get(c.getArgument(0)));
        when(mapper.selectDeveloper(anyString(),anyString())).thenAnswer(c -> developers.get(c.getArgument(0)));
        when(mapper.selectDevelopers(any(),anyString())).thenAnswer(c -> new ArrayList<>(developers.values()));
        when(mapper.selectStatuses(anyString())).thenAnswer(c -> states.getOrDefault(c.getArgument(0),List.of()).stream().map(DeveloperRows.State::copy).toList());
        when(mapper.selectSkills(anyString())).thenAnswer(c -> new ArrayList<>(skills.getOrDefault(c.getArgument(0),List.of())));
        when(mapper.selectAssignments(anyString())).thenReturn(List.of());
        when(mapper.insertDeveloper(any())).thenAnswer(c -> { Write w=c.getArgument(0); var d=new DeveloperRows.Developer();
            d.developerId=w.developerId(); d.managementNumber=w.input().managementNumber(); d.useYn="Y"; apply(d,w.input());
            developers.put(d.developerId,d); return 1; });
        when(mapper.updateDeveloper(any())).thenAnswer(c -> {Write w=c.getArgument(0); apply(developers.get(w.developerId()),w.input()); return 1;});
        doAnswer(c -> { skills.put(c.getArgument(0),new ArrayList<>()); return null; }).when(mapper).deleteSkills(anyString());
        doAnswer(c -> { skills.get((String)c.getArgument(0)).add(c.getArgument(1)); return null; }).when(mapper).insertSkill(anyString(),anyString(),anyInt());
        when(mapper.insertStatus(any())).thenAnswer(c -> {DeveloperRows.State s=c.getArgument(0); states.computeIfAbsent(s.developerId,k -> new ArrayList<>()).add(s.copy()); return 1;});
        when(mapper.updateStatus(any())).thenAnswer(c -> {DeveloperRows.State s=c.getArgument(0); states.get(s.developerId).stream().filter(r -> r.sequence==s.sequence).forEach(r -> r.endDate=s.endDate); return 1;});
        when(mapper.deleteStatus(anyString(),anyInt())).thenAnswer(c -> {states.get((String)c.getArgument(0)).removeIf(s -> s.sequence==(int)c.getArgument(1)); return 1;});
        doAnswer(c -> {audits.add(c.getArgument(0)); return null;}).when(mapper).insertHistory(any());
        when(mapper.updateUse(anyString(),anyString(),anyString())).thenAnswer(c -> { developers.get((String)c.getArgument(0)).useYn=c.getArgument(1); return 1; });
        var a=new DeveloperRows.Address(); a.areaCode="1168010100"; a.parentAreaCode="1168000000"; a.address="서울 강남구 역삼동";
        when(mapper.selectAddress(a.areaCode)).thenReturn(a);
        existing("dev","K-0003");
    }
    private void apply(DeveloperRows.Developer d,Input i) {
        d.name=i.name(); d.employeeTypeCode=i.employeeTypeCode(); d.organizationId=i.organizationId(); d.gradeCode=i.gradeCode();
        d.primaryJobCode=i.primaryJobCode(); d.secondaryJobCode=i.secondaryJobCode(); d.positionCode=i.positionCode();
        d.directClassCode=i.directClassCode(); d.salesPartId=i.salesPartId(); d.hireDate=i.hireDate(); d.monthlyRate=i.monthlyRate();
        d.monthlyPay=i.monthlyPay(); d.homeAreaCode=i.homeAreaCode(); d.homeAddress=i.homeAddress();
        d.organizationName="자사"; d.employeeTypeName="정규직"; d.primaryJobName="개발";
    }
    private DeveloperRows.Developer existing(String id,String number) {
        var d=new DeveloperRows.Developer(); d.developerId=id; d.managementNumber=number; d.name="개발자"; d.employeeTypeCode="FT";
        d.organizationId=""; d.primaryJobCode="DEV"; d.positionCode="MEMBER"; d.directClassCode="DRCT"; d.salesPartId="part"; d.useYn="Y";
        d.organizationName="자사"; d.employeeTypeName="정규직"; d.primaryJobName="개발";
        developers.put(id,d); return d;
    }
    private ObjectNode input() {
        return json.createObjectNode().put("name","개발자").put("employeeTypeCode","FT").put("organizationId","")
            .put("primaryJobCode","DEV").put("directClassCode","DRCT").put("salesPartId","part");
    }
    private DeveloperRows.State state(String code,int sequence,LocalDate start,LocalDate end) {
        var s=new DeveloperRows.State(); s.developerId="dev"; s.code=code; s.sequence=sequence; s.startDate=start; s.endDate=end; s.registeredBy="admin";
        states.computeIfAbsent("dev",k -> new ArrayList<>()).add(s); return s;
    }
    private org.springframework.test.web.servlet.ResultActions create(ObjectNode body) throws Exception {
        return mvc.perform(post("/api/common/developers").contentType("application/json").content(body.toString()));
    }
    private org.springframework.test.web.servlet.ResultActions registerStatus(String name,String date) throws Exception {
        return mvc.perform(post("/api/common/developers/dev/statuses").contentType("application/json").content(json.createObjectNode().put("statusName",name).put("date",date).toString()));
    }
    @Test
    @DisplayName("auth happy side-effect: 무인증 등록은 admin으로 TB_HR_DEV·TB_HR_DEV_SKILL·TB_HR_DEV_PRC 저장")
    void publicCreateAllocatesUnusedUnreservedNumberAndReadsSkillsAddress() throws Exception {
        var body=input().put("managementNumber","").put("homeAreaCode","1168010100").put("homeAddress","클라이언트 문자열 무시"); body.putArray("skillCodes").add("JAVA");
        String raw=mvc.perform(post("/api/common/developers").contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(response("POST","/api/common/developers"))
            .andExpect(jsonPath("$.data.managementNumber").value("K-0002")).andExpect(jsonPath("$.data.salesPartId").value("part"))
            .andExpect(jsonPath("$.data.homeAddress").value("서울 강남구 역삼동")).andExpect(jsonPath("$.data.skillCodes[0]").value("JAVA")).andReturn().getResponse().getContentAsString();
        String id=json.readTree(raw).path("data").path("developerId").asText();
        mvc.perform(get("/api/common/developers/"+id)).andExpect(status().isOk()).andExpect(response("GET","/api/common/developers/{developerId}")).andExpect(jsonPath("$.data.name").value("개발자"));
        verify(mapper).insertDeveloper(argThat(w -> "admin".equals(w.actor())));
        verify(mapper).savePrice(argThat(w -> "202611".equals(w.month())));
        verify(mapper).insertSkill(id,"Java",1);
    }
    @Test
    @DisplayName("validation: 이름 누락은 400 errors.name, 개발자·단가·스킬 쓰기 없음")
    void missingNameWritesNothing() throws Exception {
        var b=input(); b.remove("name");
        mvc.perform(post("/api/common/developers").contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest()).andExpect(response("POST","/api/common/developers"))
            .andExpect(jsonPath("$.errors[0].field").value("name"));
        verify(mapper,never()).insertDeveloper(any()); verify(mapper,never()).savePrice(any());
        verify(mapper,never()).insertSkill(anyString(),anyString(),anyInt());
    }
    @Test void tooLongNumberWritesNothing() throws Exception {create(input().put("managementNumber","A".repeat(21))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("managementNumber"));verify(mapper,never()).insertDeveloper(any());}
    @Test
    @DisplayName("business: 중복 관리번호는 400이며 기존 TB_HR_DEV·단가·이력 불변")
    void duplicateNumberPreservesExisting() throws Exception {
        mvc.perform(post("/api/common/developers").contentType("application/json").content(input().put("managementNumber","K-0003").toString()))
            .andExpect(status().isBadRequest()).andExpect(response("POST","/api/common/developers"))
            .andExpect(jsonPath("$.message").value("이미 등록된 관리번호입니다."));
        assertThat(developers).hasSize(1); assertThat(developers.get("dev").managementNumber).isEqualTo("K-0003");
        verify(mapper,never()).insertDeveloper(any()); verify(mapper,never()).savePrice(any()); assertThat(audits).isEmpty();
    }
    @Test void stoppedVendorCannotBeNewlySelected() throws Exception {create(input().put("organizationId","stopped")).andExpect(status().isBadRequest());verify(mapper,never()).insertDeveloper(any());}
    @Test void missingPartWritesNothing() throws Exception {var b=input();b.remove("salesPartId");create(b).andExpect(status().isBadRequest());verify(mapper,never()).insertDeveloper(any());}
    @Test
    @DisplayName("auth happy side-effect: admin 수정은 TB_HR_DEV_PRC·TB_SY_CHG_HIST 기록, 과거 EMP_TYPE_CD 변경 없음")
    void publicUpdateRecordsEveryChangedFieldAndCurrentKoreaMonth() throws Exception {
        mvc.perform(put("/api/common/developers/dev").contentType("application/json").content(input().put("name","새 이름").put("primaryJobCode","PM").put("monthlyPay",100).put("monthlyRate",200).toString()))
            .andExpect(status().isOk()).andExpect(response("PUT","/api/common/developers/{developerId}")).andExpect(jsonPath("$.data.name").value("새 이름"));
        assertThat(audits).extracting(Audit::fieldName).contains("이름","주 직무","월 단가","월 급여");
        verify(mapper).savePrice(argThat(p -> p.month().equals("202611") && p.monthlyPay().equals(new BigDecimal("100"))));
        verify(mapper).updateDeveloper(argThat(w -> "admin".equals(w.actor()) && "FT".equals(w.input().employeeTypeCode())));
    }
    @Test void updateCannotChangeManagementNumber() throws Exception {
        mvc.perform(put("/api/common/developers/dev").contentType("application/json").content(input().put("managementNumber","OTHER").toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("managementNumber")); verify(mapper,never()).updateDeveloper(any());
    }
    @Test void longNamePreservesOldValue() throws Exception {
        mvc.perform(put("/api/common/developers/dev").contentType("application/json").content(input().put("name","가".repeat(51)).toString())).andExpect(status().isBadRequest()); assertThat(developers.get("dev").name).isEqualTo("개발자");
    }
    @Test
    @DisplayName("auth happy side-effect: 재직 등록은 TB_HR_DEV_STAT 휴직 종료·원종료일 보관과 TB_HR_DEV 상태 재판정")
    void publicReturnClosesLeaveAtPreviousDayAndKeepsOriginal() throws Exception {
        state("LEAVE",1,today.minusDays(5),today.plusDays(5));
        mvc.perform(post("/api/common/developers/dev/statuses").contentType("application/json")
            .content(json.createObjectNode().put("statusName","재직").put("date",today.toString()).toString()))
            .andExpect(status().isOk()).andExpect(response("POST","/api/common/developers/{developerId}/statuses"))
            .andExpect(jsonPath("$.data.items.length()").value(2));
        assertThat(states.get("dev")).hasSize(2); assertThat(states.get("dev").get(0).endDate).isEqualTo(today.minusDays(1));
        assertThat(states.get("dev").get(1).originalEndDate).isEqualTo(today.plusDays(5));
        verify(mapper).insertStatus(argThat(s -> "WORK".equals(s.code) && "admin".equals(s.registeredBy)));
        verify(mapper).updateDerived("dev",null,"IDLE",false,today);
    }
    @DisplayName("validation: 상태명·일자 누락은 필드 오류이고 TB_HR_DEV_STAT 추가 없음")
    @ParameterizedTest @ValueSource(strings={"statusName","date"}) void missingStatusFieldHasNoWrites(String field) throws Exception {
        var body=json.createObjectNode().put("statusName","휴직").put("date",today.toString());body.remove(field);
        mvc.perform(post("/api/common/developers/dev/statuses").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));verify(mapper,never()).insertStatus(any());
    }
    @Test void returnWithoutLeaveIsRefused() throws Exception {registerStatus("재직",today.toString()).andExpect(status().isBadRequest());verify(mapper,never()).insertStatus(any());}
    @Test void lateReturnIsRefused() throws Exception {state("LEAVE",1,today.minusDays(5),today.minusDays(2));registerStatus("재직",today.toString()).andExpect(status().isBadRequest());verify(mapper,never()).updateStatus(any());}
    @Test void overlappingLeaveHasNoWrites() throws Exception {state("LEAVE",1,today.minusDays(5),null); registerStatus("휴직",today.toString()).andExpect(status().isBadRequest());verify(mapper,never()).insertStatus(any());}
    @Test void retirementCopiesDateAndClosesLeave() throws Exception {
        state("LEAVE",1,today.minusDays(5),null); registerStatus("퇴사",today.toString()).andExpect(status().isOk());
        assertThat(states.get("dev").get(0).endDate).isEqualTo(today.minusDays(1)); assertThat(developers.get("dev").retirementDate).isEqualTo(today);
        assertThat(developers.get("dev").retirementScheduled).isTrue(); assertThat(developers.get("dev").useYn).isEqualTo("Y");
    }
    @Test
    @DisplayName("business: 퇴사일 뒤 배정은 등록 거부, TB_HR_DEV_STAT·TB_HR_DEV 불변")
    void retirementWithRemainingAssignmentIsRefused() throws Exception {
        var a=new DeveloperRows.Assignment();a.startDate=today.minusDays(1);a.endDate=today.plusDays(1); when(mapper.selectAssignments("dev")).thenReturn(List.of(a));
        mvc.perform(post("/api/common/developers/dev/statuses").contentType("application/json")
            .content(json.createObjectNode().put("statusName","퇴사").put("date",today.toString()).toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1건")));
        verify(mapper,never()).insertStatus(any());assertThat(developers.get("dev").retirementDate).isNull();
    }
    @Test void secondRetirementIsRefused() throws Exception {state("RETIRE",1,today.plusDays(1),null);registerStatus("퇴사",today.toString()).andExpect(status().isBadRequest());verify(mapper,never()).insertStatus(any());}
    @Test void closedRetirementHasNoWrites() throws Exception {when(mapper.closedMonth("202611")).thenReturn(true);registerStatus("퇴사",today.toString()).andExpect(status().isBadRequest());verify(mapper,never()).insertStatus(any());}
    @Test
    @DisplayName("auth happy side-effect: 퇴사 취소는 휴직 복원과 TB_HR_DEV 상태·사용 복구 및 사유 이력")
    void publicRetirementCancellationRestoresLeaveAndAuditsReason() throws Exception {
        state("LEAVE",1,today.minusDays(5),today.minusDays(1)); var r=state("RETIRE",2,today,null);r.closedLeaveSequence=1;r.originalEndDate=today.plusDays(5);
        mvc.perform(post("/api/common/developers/dev/statuses/2/retirement-cancel").contentType("application/json").content("{\"reason\":\"계속 근무\"}"))
            .andExpect(status().isOk()).andExpect(response("POST","/api/common/developers/{developerId}/statuses/{statusId}/retirement-cancel"));
        assertThat(states.get("dev")).hasSize(1);assertThat(states.get("dev").get(0).endDate).isEqualTo(today.plusDays(5));
        assertThat(developers.get("dev").retirementDate).isNull();assertThat(developers.get("dev").useYn).isEqualTo("Y");
        assertThat(audits).anyMatch(a -> a.type().equals("UPD") && a.fieldName().equals("퇴사 취소") && a.reason().equals("계속 근무"));
    }
    @Test
    @DisplayName("validation: 빈 취소 사유는 errors.reason이며 퇴사 삭제 없음")
    void emptyCancelReasonPreservesRetirement() throws Exception {
        state("RETIRE",1,today,null); mvc.perform(post("/api/common/developers/dev/statuses/1/retirement-cancel").contentType("application/json").content("{\"reason\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));verify(mapper,never()).deleteStatus(anyString(),anyInt());
    }
    @Test
    @DisplayName("business: 퇴사월 마감은 지정 안내와 함께 취소 거부·이력 불변")
    void closedRetirementCancellationHasExactMessageAndNoWrites() throws Exception {
        state("RETIRE",1,today,null); when(mapper.closedMonth("202611")).thenReturn(true);
        mvc.perform(post("/api/common/developers/dev/statuses/1/retirement-cancel").contentType("application/json").content("{\"reason\":\"취소\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("퇴사한 달(2026-11)이 마감되어 취소할 수 없습니다. 다시 쓰려면 새로 등록하세요."));verify(mapper,never()).deleteStatus(anyString(),anyInt());
    }
    @Test
    @DisplayName("auth happy side-effect: 재직 취소는 TB_HR_DEV_STAT 복원 종료일을 퇴사 전날로 제한하고 상태 재판정")
    void publicReturnCancellationHasNoBodyAndCapsRestorationBeforeRetirement() throws Exception {
        state("LEAVE",1,today.minusDays(5),today.minusDays(1));var w=state("WORK",2,today,null);w.closedLeaveSequence=1;w.originalEndDate=null;state("RETIRE",3,today.plusDays(5),null);
        mvc.perform(post("/api/common/developers/dev/statuses/2/return-cancel")).andExpect(status().isOk()).andExpect(response("POST","/api/common/developers/{developerId}/statuses/{statusId}/return-cancel"));
        assertThat(states.get("dev")).hasSize(2);assertThat(states.get("dev").get(0).endDate).isEqualTo(today.plusDays(4));
        verify(mapper).deleteStatus("dev",2);
        verify(mapper).updateDerived("dev",today.plusDays(5),"LEAVE",false,today);
    }
    @Test
    @DisplayName("validation: 재직 아닌 줄의 취소는 errors.statusId·휴직 불변")
    void wrongStatusCannotBeCancelledAsReturn() throws Exception {
        state("LEAVE",1,today,null);
        mvc.perform(post("/api/common/developers/dev/statuses/1/return-cancel"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("statusId"));
        verify(mapper,never()).deleteStatus(anyString(),anyInt()); assertThat(states.get("dev")).hasSize(1);
    }
    @Test
    @DisplayName("business: 복귀월 마감 뒤 취소는 400·재직과 휴직 불변")
    void closedReturnCannotBeCancelled() throws Exception {
        state("WORK",2,today,null); when(mapper.closedMonth("202611")).thenReturn(true);
        mvc.perform(post("/api/common/developers/dev/statuses/2/return-cancel"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("복귀한 달이 마감되어 취소할 수 없습니다."));
        verify(mapper,never()).deleteStatus(anyString(),anyInt()); verify(mapper,never()).updateStatus(any());
    }
    @Test
    @DisplayName("auth happy side-effect: 종료일 연장은 TB_HR_DEV_STAT 동일 줄과 TB_HR_DEV 파생 상태 갱신")
    void publicEndDateExtensionUpdatesSameRow() throws Exception {
        state("LEAVE",1,today.minusDays(1),today);mvc.perform(put("/api/common/developers/dev/statuses/1/end-date").contentType("application/json").content("{\"endDate\":\"2026-11-20\"}"))
            .andExpect(status().isOk()).andExpect(response("PUT","/api/common/developers/{developerId}/statuses/{statusId}/end-date"));assertThat(states.get("dev").get(0).endDate).isEqualTo(LocalDate.of(2026,11,20));assertThat(developers.get("dev").workStatusName).isEqualTo("휴직");
    }
    @Test void malformedEndDateIsFieldError() throws Exception {mvc.perform(put("/api/common/developers/dev/statuses/1/end-date").contentType("application/json").content("{\"endDate\":\"not-a-date\"}"))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("endDate"));verify(mapper,never()).updateStatus(any());}
    @Test
    @DisplayName("business: 재직으로 닫힌 휴직은 종료일 변경 거부·원본 보존")
    void returnClosedLeaveRequiresReturnCancellation() throws Exception {
        state("LEAVE",1,today.minusDays(5),today.minusDays(1));var w=state("WORK",2,today,null);w.closedLeaveSequence=1;
        mvc.perform(put("/api/common/developers/dev/statuses/1/end-date").contentType("application/json").content("{\"endDate\":null}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("재직을 먼저 취소하세요."));verify(mapper,never()).updateStatus(any());
    }
    @Test void endedLeaveCannotBeEdited() throws Exception {state("LEAVE",1,today.minusDays(5),today.minusDays(1));mvc.perform(put("/api/common/developers/dev/statuses/1/end-date").contentType("application/json").content("{\"endDate\":null}")).andExpect(status().isBadRequest());verify(mapper,never()).updateStatus(any());}
    @Test
    @DisplayName("auth happy side-effect: 혼합 중지는 가능한 TB_HR_DEV만 변경하며 거부 대상과 관계 보존")
    void bulkStopProcessesOnlyEligibleAndCanResume() throws Exception {
        existing("busy","K-0004");var a=new DeveloperRows.Assignment();a.startDate=today.minusDays(1);a.endDate=today;a.projectId="p";when(mapper.selectAssignments("busy")).thenReturn(List.of(a));
        mvc.perform(put("/api/common/developers/use-status").contentType("application/json").content("{\"developerIds\":[\"dev\",\"busy\"],\"useYn\":\"N\"}"))
            .andExpect(status().isOk()).andExpect(response("PUT","/api/common/developers/use-status")).andExpect(jsonPath("$.data.processedDeveloperIds[0]").value("dev"))
            .andExpect(jsonPath("$.data.rejected[0].developerId").value("busy"));
        assertThat(developers.get("dev").useYn).isEqualTo("N"); assertThat(developers.get("busy").useYn).isEqualTo("Y");
        verify(mapper).updateUse("dev","N","admin"); verify(mapper,never()).updateUse(eq("busy"),anyString(),anyString());
        mvc.perform(put("/api/common/developers/use-status").contentType("application/json").content("{\"developerIds\":[\"dev\"],\"useYn\":\"Y\"}")).andExpect(status().isOk());assertThat(developers.get("dev").useYn).isEqualTo("Y");
    }
    @Test void emptyBulkIdsHasNoWrites() throws Exception {mvc.perform(put("/api/common/developers/use-status").contentType("application/json").content("{\"developerIds\":[],\"useYn\":\"N\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("developerIds"));verify(mapper,never()).updateUse(anyString(),anyString(),anyString());}
    @Test void pastRetirementCannotResume() throws Exception {state("RETIRE",1,today.minusDays(1),null);mvc.perform(put("/api/common/developers/use-status").contentType("application/json").content("{\"developerIds\":[\"dev\"],\"useYn\":\"Y\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.rejected[0].reason").value("퇴사한 개발자는 다시 사용할 수 없습니다. 새로 등록하세요."));verify(mapper,never()).updateUse(anyString(),anyString(),anyString());}
    @Test void defaultListHidesStoppedButExplicitRetiredIncludesIt() throws Exception {
        state("RETIRE",1,today.minusDays(1),null);mvc.perform(get("/api/common/developers")).andExpect(status().isOk()).andExpect(response("GET","/api/common/developers")).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/common/developers").param("workStatusName","퇴사")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(1));
    }
    @Test void historyUsesApprovedShapeAndDatabaseResult() throws Exception {
        when(mapper.selectChanges("dev")).thenReturn(List.of());
        mvc.perform(get("/api/common/developers/dev/change-history")).andExpect(status().isOk()).andExpect(response("GET","/api/common/developers/{developerId}/change-history"));
        verify(mapper).selectChanges("dev");
    }
    @Test void assignmentsUseApprovedShapeAndDatabaseResult() throws Exception {
        mvc.perform(get("/api/common/developers/dev/assignments")).andExpect(status().isOk()).andExpect(response("GET","/api/common/developers/{developerId}/assignments"));
        verify(mapper,atLeastOnce()).selectAssignments("dev");
    }
    @Test void statusesUseApprovedShapeAndDatabaseResult() throws Exception {
        mvc.perform(get("/api/common/developers/dev/statuses")).andExpect(status().isOk()).andExpect(response("GET","/api/common/developers/{developerId}/statuses"));
        verify(mapper,atLeastOnce()).selectStatuses("dev");
    }
    @Test void hireDateDisplayRowOmitsStatusIdButKeepsRequiredNullableFields() throws Exception {
        developers.get("dev").hireDate=today.minusYears(1);
        state("LEAVE",1,today.minusDays(1),null);
        mvc.perform(get("/api/common/developers/dev/statuses"))
            .andExpect(status().isOk()).andExpect(response("GET","/api/common/developers/{developerId}/statuses"))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].statusId").value("1"))
            .andExpect(jsonPath("$.data.items[1].statusName").value("재직"))
            .andExpect(result -> {
                var hire=json.readTree(result.getResponse().getContentAsByteArray()).path("data").path("items").get(1);
                assertThat(hire.has("statusId")).isFalse();
                assertThat(hire.has("endDate")).isTrue(); assertThat(hire.get("endDate").isNull()).isTrue();
                assertThat(hire.has("reason")).isTrue(); assertThat(hire.get("reason").isNull()).isTrue();
            });
    }
    @Test void literalCollectionRouteReachesItsOwnHandlerAndMapper() throws Exception {
        mvc.perform(get("/api/common/developers"))
            .andExpect(handler().handlerType(CommonDeveloperController.class))
            .andExpect(handler().methodName("getCommonDevelopers"))
            .andExpect(status().isOk()).andExpect(response("GET","/api/common/developers"))
            .andExpect(jsonPath("$.data.items[0].developerId").value("dev"))
            .andExpect(jsonPath("$.data.items[0].name").value("개발자"));
        verify(mapper).selectDevelopers(any(),eq("202611"));
    }
    @Test void absentDeveloperReturnsDeclaredFieldErrorWithoutCreatingAnything() throws Exception {
        mvc.perform(get("/api/common/developers/missing"))
            .andExpect(status().isBadRequest()).andExpect(response("GET","/api/common/developers/{developerId}"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors[0].field").value("developerId"))
            .andExpect(jsonPath("$.message").value("개발자를 찾을 수 없습니다."));
        verify(mapper,never()).insertDeveloper(any()); verify(mapper,never()).updateDeveloper(any());
    }
    @Test void areasAreParentScoped() throws Exception {
        var a=new DeveloperRows.Area();a.areaCode="11";a.areaName="서울";when(mapper.selectAreas(null)).thenReturn(List.of(a));
        mvc.perform(get("/api/common/developers/areas")).andExpect(status().isOk()).andExpect(response("GET","/api/common/developers/areas")).andExpect(jsonPath("$.data.items[0].areaCode").value("11"));
        mvc.perform(get("/api/common/developers/areas").param("parentAreaCode","unknown")).andExpect(status().isBadRequest());
    }
    @Test void excelIsRealWorkbookContainsAdminTimestampAndFilteredRows() throws Exception {
        byte[] bytes=mvc.perform(get("/api/common/developers/excel")).andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse().getContentAsByteArray();
        try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            var sheet=workbook.getSheetAt(0);assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("admin","2026-11-15 01:00");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("개발자");
        }
        verify(mapper).insertAccess("admin",1);
    }
}
