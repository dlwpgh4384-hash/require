package com.company.common.airecommendations.controller;

import com.company.common.airecommendations.mapper.*;
import com.company.common.airecommendations.mapper.AiRecommendationRows.*;
import com.company.common.airecommendations.service.*;
import com.company.common.codegroups.dto.CodeContracts.Item;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.salesopportunities.mapper.SalesOpportunityMapper;
import com.company.common.salesopportunities.mapper.SalesOpportunityRows.NeedRow;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.company.common.withdrawals.mapper.WithdrawalRows.Reservation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
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

@WebMvcTest(CommonAiRecommendationController.class)
@ContextConfiguration(classes={CommonAiRecommendationController.class, CommonAiRecommendationService.class,
    AiRecommendationActionService.class, AiRecommendationResources.class, AiRecommendationLogCodec.class,
    CurrentBusinessActor.class, CodeExceptionHandler.class, ApiExceptionHandler.class})
class CommonAiRecommendationControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AiRecommendationLogCodec codec;
    @MockBean AiRecommendationMapper mapper;
    @MockBean SalesOpportunityMapper opportunities;
    @MockBean WithdrawalMapper withdrawals;
    @MockBean ProjectMapper projects;
    @MockBean BusinessCalendar calendar;
    @MockBean AiRecommendationCodes codes;
    @MockBean AiRecommendationDefinitions definitions;
    @MockBean PlatformTransactionManager transactions;
    final List<Log> logs=new ArrayList<>();
    final Instant now=Instant.parse("2026-11-25T00:00:00Z");
    long sequence=100;
    Item code(String value) { var c=new Item(); c.code=value; c.codeName=value; c.useYn="Y"; return c; }
    @BeforeEach void setup() {
        logs.clear();
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(calendar.today()).thenReturn(LocalDate.of(2026,11,25));
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026,11));
        when(calendar.fromDatabaseMonth(anyString())).thenAnswer(c->YearMonth.parse(((String)c.getArgument(0)).substring(0,4)+"-"+((String)c.getArgument(0)).substring(4)));
        when(calendar.toDatabaseMonth(any())).thenAnswer(c->c.getArgument(0).toString().replace("-",""));
        when(mapper.target(anyString(),anyString())).thenAnswer(c->new Target(c.getArgument(0),c.getArgument(1),"대상","202612","202712","ING","MO","customer","IT",null,"Java","project","고객사"));
        when(codes.selectable("JOB")).thenReturn(List.of(code("BE"),code("FE")));
        when(codes.selectable("GRADE")).thenReturn(List.of(code("MD"),code("SR")));
        when(codes.selectable("SKILL")).thenReturn(List.of());
        when(codes.selectable("RCMD_EXCL")).thenReturn(List.of(code("OTHER")));
        when(codes.name("JOB","BE")).thenReturn("백엔드");
        var maxima=new LinkedHashMap<String,BigDecimal>(); var values=List.of(25,20,15,10,5,5,10,10);
        for (int i=0;i<8;i++) maxima.put(AiRecommendationRules.DIMENSIONS.get(i),BigDecimal.valueOf(values.get(i)));
        when(definitions.currentMaxima()).thenReturn(maxima);
        when(mapper.nextId()).thenAnswer(c->Long.toString(++sequence));
        var person=new Person("developer","인력","M001","BE",null,"백엔드","SR","고급","FT","정규",null);
        when(mapper.person("developer")).thenReturn(person);
        when(mapper.people(any())).thenReturn(List.of(person));
        when(mapper.candidates(any())).thenReturn(List.of(person));
        when(mapper.evidence(any())).thenReturn(new Evidence(0,0,0,false,false,null,false));
        when(mapper.insert(any())).thenAnswer(c->{ LogWrite w=c.getArgument(0); logs.add(w.log()); return 1; });
        when(mapper.replay(anyString(),anyString(),anyString())).thenAnswer(c->logs.stream().filter(l->codec.decode(l.scoreDetail()).metadata().fingerprint().equals(c.getArgument(0))
            && l.targetType().equals(c.getArgument(1)) && l.targetId().equals(c.getArgument(2))).toList());
        when(mapper.candidate(anyString(),anyString())).thenAnswer(c->logs.stream().filter(l->l.recommendationId().equals(c.getArgument(0)) && l.developerId().equals(c.getArgument(1))).findFirst().orElse(null));
        when(mapper.lockCandidate(anyString(),anyString())).thenAnswer(c->mapper.candidate(c.getArgument(0),c.getArgument(1)));
        when(mapper.action(any())).thenAnswer(c->{ Action a=c.getArgument(0); Log old=mapper.candidate(a.recommendationId(),a.developerId());
            logs.set(logs.indexOf(old),new Log(old.recommendationId(),old.rankOrder(),old.targetType(),old.targetId(),old.jobCode(),old.startMonth(),old.gradeCode(),a.needSn(),old.developerId(),old.totalScore(),old.scoreDetail(),old.reason(),a.action(),a.reasonCode(),a.actor(),now)); return 1; });
        when(mapper.exclude(any())).thenAnswer(c->{ ExclusionWrite w=c.getArgument(0); var a=w.action(); Log old=mapper.candidate(a.recommendationId(),a.developerId());
            logs.set(logs.indexOf(old),new Log(old.recommendationId(),old.rankOrder(),old.targetType(),old.targetId(),old.jobCode(),old.startMonth(),old.gradeCode(),old.needSn(),old.developerId(),old.totalScore(),w.scoreDetail(),old.reason(),"EXCL",a.reasonCode(),a.actor(),now)); return 1; });
    }
    Map<String,Object> input(String type) {
        var result=new LinkedHashMap<String,Object>(); result.put("requestId",UUID.randomUUID().toString()); result.put("targetType",type);
        result.put("targetId","target");result.put("jobCode","BE");result.put("gradeCode","SR");result.put("startMonth","2026-11");return result;
    }
    String recommend(String type) throws Exception {
        String body=mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(json.writeValueAsBytes(input(type))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body).path("data").path("recommendationId").asText();
    }
    @Test void publicRecommendationNormalizesAndPersistsSelectedGradeAndEightScores() throws Exception {
        mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(json.writeValueAsBytes(input("project"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.condition.startMonth").value("2026-12"))
            .andExpect(jsonPath("$.data.condition.gradeCode").value("SR"))
            .andExpect(jsonPath("$.data.items[0].scores.workplace.score").value(5));
        assertThat(logs).hasSize(1); assertThat(logs.get(0).gradeCode()).isEqualTo("SR");
        assertThat(codec.decode(logs.get(0).scoreDetail()).scores()).hasSize(8);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"FL","OS"})
    void dispatchedOpportunityRejectsEachExternalEmploymentType(String employment) throws Exception {
        when(mapper.target("opportunity","target")).thenReturn(new Target("opportunity","target","파견","202612",null,"ING","DP",null,null,null,null,null,null));
        when(mapper.candidates(any())).thenReturn(List.of(new Person("external","외부","M002","BE",null,"백엔드","SR","고급",employment,"외부",null)));
        mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(json.writeValueAsBytes(input("opportunity"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
        assertThat(logs).isEmpty();
    }
    @Test void fractionalNeedIsNotCoercedToAnotherSeat() throws Exception {
        String id=recommend("opportunity");var before=List.copyOf(logs);
        mvc.perform(put("/api/common/ai-recommendations/{id}/tentative-assignment",id).contentType("application/json").content("{\"developerId\":\"developer\",\"needSn\":1.5}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("needSn"));
        assertThat(logs).isEqualTo(before);verify(withdrawals,never()).insertReservation(any());
    }
    @Test void invalidTargetHasFieldErrorAndNoLog() throws Exception {
        when(mapper.target(anyString(),anyString())).thenReturn(null);
        mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(json.writeValueAsBytes(input("project"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("targetId")); assertThat(logs).isEmpty();
    }
    @Test void impossibleMonthHasFieldErrorAndNoLog() throws Exception {
        var in=input("project");in.put("startMonth","2026-13");
        mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(json.writeValueAsBytes(in)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("startMonth")); assertThat(logs).isEmpty();
    }
    /** L-13: 프로젝트 시작월 = max(요청 월, 이번 달, 프로젝트 시작월). 지난(마감) 달로는 추천하지 않는다. 기회는 그대로. */
    @Test void projectStartMonthIsRaisedToThisMonthButOpportunityKeepsAskedMonth() throws Exception {
        when(mapper.target(anyString(),anyString())).thenAnswer(c->new Target(c.getArgument(0),c.getArgument(1),"대상","202603","202712","ING","MO","customer","IT",null,"Java","project","고객사"));
        var project=input("project");project.put("startMonth","2026-03");
        mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(json.writeValueAsBytes(project)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.condition.startMonth").value("2026-11"));
        assertThat(logs).extracting(Log::startMonth).containsOnly("202611");
        var captor=org.mockito.ArgumentCaptor.forClass(AiRecommendationRows.Filter.class);
        verify(mapper,atLeastOnce()).candidates(captor.capture());
        assertThat(captor.getValue().firstDay()).isEqualTo(LocalDate.of(2026,11,1));
        var opportunity=input("opportunity");opportunity.put("startMonth","2026-03");
        mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(json.writeValueAsBytes(opportunity)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.condition.startMonth").value("2026-03"));
    }
    @Test void afterProjectEndIsRejectedBeforeAnyWrite() throws Exception {
        var in=input("project");in.put("startMonth","2028-01");
        mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(json.writeValueAsBytes(in)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("시작월이 프로젝트 종료월 뒤입니다.")); assertThat(logs).isEmpty();
    }
    @Test void sameRequestReplaysButNewRequestCreatesAnotherExecution() throws Exception {
        var in=input("project");byte[] body=json.writeValueAsBytes(in);
        String first=mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(content().json(first));
        assertThat(logs).hasSize(1);
        in.put("requestId",UUID.randomUUID().toString());in.put("gradeCode","MD");
        mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(json.writeValueAsBytes(in))).andExpect(status().isOk());
        assertThat(logs).extracting(Log::gradeCode).containsExactly("SR","MD");
    }
    @Test void sameRequestWithChangedConditionIsRejected() throws Exception {
        var in=input("project");mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(json.writeValueAsBytes(in))).andExpect(status().isOk());
        in.put("jobCode","FE");mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(json.writeValueAsBytes(in)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("requestId"));assertThat(logs).hasSize(1);
    }
    @Test void completedScoreReceiptReplaysRepeatedlyWithoutRecalculationOrAdditionalWrites() throws Exception {
        byte[] body=json.writeValueAsBytes(input("project"));
        String first=mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].totalScore").value(62))
            .andExpect(jsonPath("$.data.items[0].scores.availability.score").value(10))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var saved=List.copyOf(logs);
        when(definitions.currentMaxima()).thenReturn(Map.of());
        when(mapper.candidates(any())).thenReturn(List.of());

        for(int attempt=0;attempt<2;attempt++) {
            mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(content().json(first))
                .andExpect(jsonPath("$.data.items[0].totalScore").value(62))
                .andExpect(jsonPath("$.data.items[0].scores.availability.score").value(10));
        }
        assertThat(logs).isEqualTo(saved).hasSize(1);
        verify(mapper,times(1)).nextId();
        verify(mapper,times(1)).insert(any());
        verify(mapper,times(1)).candidates(any());
        verify(definitions,times(1)).currentMaxima();
    }
    @Test void zeroResultsHaveSequenceIdentityWithoutSentinelOrLogRows() throws Exception {
        // 0명이면 남길 기록 줄이 없으므로 같은 요청을 다시 보내도 쓰기 없이 다시 계산한다(결과는 계속 0명).
        when(mapper.candidates(any())).thenReturn(List.of()); var in=input("project");byte[] body=json.writeValueAsBytes(in);
        mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(body)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.recommendationId").value("101"));
        mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        assertThat(logs).isEmpty();verify(mapper,never()).insert(any());verify(mapper,never()).evidences(any());
    }
    @Test void responseHasNoCostOrPriceNumbers() throws Exception {
        String body=mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(json.writeValueAsBytes(input("project"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].scores.cost.score").exists())
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("costBasis").doesNotContain("\"price\"");
    }
    @Test void candidateEvidenceIsReadOnceForAllCandidates() throws Exception {
        var people=new ArrayList<Person>();
        for(int i=0;i<4;i++)people.add(new Person("p"+i,"P"+i,"M00"+i,"BE",null,"백엔드","SR","고급","FT","정규",null));
        when(mapper.candidates(any())).thenReturn(people);
        mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(json.writeValueAsBytes(input("project")))).andExpect(status().isOk());
        verify(mapper,times(1)).evidences(any());verify(mapper,times(1)).payrolls(any(),anyString());verify(mapper,times(1)).prices(any(),anyString());
        verify(mapper,times(1)).skills(any());verify(mapper,times(1)).conversions(any());verify(opportunities,times(1)).developersReservations(any());
        verify(mapper,never()).evidence(any());verify(opportunities,never()).developerReservations(anyString());
    }
    @Test void noNeedOpportunityCanRecommendAndExcludeWithFixedActorAudit() throws Exception {
        String id=recommend("opportunity");
        mvc.perform(post("/api/common/ai-recommendations/{id}/exclusions",id).contentType("application/json").content("{\"developerId\":\"developer\",\"reasonCode\":\"OTHER\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.excluded").value(true)).andExpect(jsonPath("$.data.actedBy").value("admin"));
        assertThat(logs.get(0).reasonCode()).isEqualTo("OTHER");assertThat(logs.get(0).actedAt()).isEqualTo(now);
        assertThat(codec.decode(logs.get(0).scoreDetail()).metadata().exclusionActive()).isTrue();
        // 같은 후보를 다시 제외하면 처음 기록 그대로 성공(쓰기 없음).
        mvc.perform(post("/api/common/ai-recommendations/{id}/exclusions",id).contentType("application/json").content("{\"developerId\":\"developer\",\"reasonCode\":\"OTHER\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.excluded").value(true)).andExpect(jsonPath("$.data.reasonCode").value("OTHER"));
        verify(mapper,times(1)).exclude(any());
    }
    @Test void exclusionDoesNotOverwriteTentativeCandidate() throws Exception {
        String id=recommend("opportunity");Log old=logs.get(0);
        logs.set(0,new Log(old.recommendationId(),old.rankOrder(),old.targetType(),old.targetId(),old.jobCode(),old.startMonth(),old.gradeCode(),1,old.developerId(),old.totalScore(),old.scoreDetail(),old.reason(),"TENT",null,"admin",now));
        Log before=logs.get(0);
        mvc.perform(post("/api/common/ai-recommendations/{id}/exclusions",id).contentType("application/json").content("{\"developerId\":\"developer\",\"reasonCode\":\"OTHER\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("이미 처리한 추천 후보입니다."));
        assertThat(logs.get(0)).isEqualTo(before);verify(mapper,never()).exclude(any());
    }
    @Test void cancelledAssignmentCannotBeLinked() throws Exception { invalidAssignment("assignment",new Assignment("assignment","developer","target","CNCL")); }
    @Test void missingDeveloperForTentativeIsFieldErrorNotServerError() throws Exception {
        String id=recommend("opportunity");when(withdrawals.lockDeveloper("developer")).thenReturn("developer");
        when(mapper.person("developer")).thenReturn(null);
        mvc.perform(put("/api/common/ai-recommendations/{id}/tentative-assignment",id).contentType("application/json").content("{\"developerId\":\"developer\",\"needSn\":1}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("developerId"))
            .andExpect(jsonPath("$.message").value("추천 후보를 확인해 주세요."));
        verify(withdrawals,never()).insertReservation(any());
    }
    @Test void missingExclusionReasonDoesNotChangeAudit() throws Exception { invalidReason("{}"); }
    @Test void codeFromAnotherGroupDoesNotChangeAudit() throws Exception { invalidReason("{\"developerId\":\"developer\",\"reasonCode\":\"BE\"}"); }
    void invalidReason(String body) throws Exception {
        String id=recommend("opportunity");Log before=logs.get(0);
        mvc.perform(post("/api/common/ai-recommendations/{id}/exclusions",id).contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reasonCode"));assertThat(logs.get(0)).isEqualTo(before);
    }
    @Test void assignmentSelectionLinksOnlyMatchingSavedAssignmentAndDoesNotTouchTentative() throws Exception {
        String id=recommend("project");when(mapper.assignment("assignment")).thenReturn(new Assignment("assignment","developer","target","ACT"));
        mvc.perform(post("/api/common/ai-recommendations/{id}/assignment",id).contentType("application/json").content("{\"developerId\":\"developer\",\"assignmentId\":\"assignment\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.userActCode").value("ASGN"));
        assertThat(logs.get(0).actedBy()).isEqualTo("admin");assertThat(logs.get(0).userActCode()).isEqualTo("ASGN");verifyNoInteractions(withdrawals);
    }
    @Test void missingAssignmentIdIsFieldErrorAndUnchanged() throws Exception { invalidAssignment(null,null); }
    @Test void nonexistentAssignmentIsFieldErrorAndUnchanged() throws Exception { invalidAssignment("missing",null); }
    @Test void otherDeveloperAssignmentCannotBeLinked() throws Exception { invalidAssignment("assignment",new Assignment("assignment","other","target","ACT")); }
    @Test void otherProjectAssignmentCannotBeLinked() throws Exception { invalidAssignment("assignment",new Assignment("assignment","developer","other","ACT")); }
    void invalidAssignment(String assignment,Assignment row) throws Exception {
        String id=recommend("project");Log before=logs.get(0);if(assignment!=null)when(mapper.assignment(assignment)).thenReturn(row);
        var body=new HashMap<String,String>();body.put("developerId","developer");if(assignment!=null)body.put("assignmentId",assignment);
        mvc.perform(post("/api/common/ai-recommendations/{id}/assignment",id).contentType("application/json").content(json.writeValueAsBytes(body)))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("assignmentId"));assertThat(logs.get(0)).isEqualTo(before);
    }
    @Test void missingNeedDoesNotChangeCandidateOrReservations() throws Exception {
        String id=recommend("opportunity");Log before=logs.get(0);
        mvc.perform(put("/api/common/ai-recommendations/{id}/tentative-assignment",id).contentType("application/json").content("{\"developerId\":\"developer\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("needSn"));assertThat(logs.get(0)).isEqualTo(before);verifyNoInteractions(withdrawals);
    }
    @Test void foreignNeedDoesNotMutateReservation() throws Exception {
        String id=recommend("opportunity");when(withdrawals.lockDeveloper("developer")).thenReturn("developer");
        mvc.perform(put("/api/common/ai-recommendations/{id}/tentative-assignment",id).contentType("application/json").content("{\"developerId\":\"developer\",\"needSn\":99}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("needSn"));verify(withdrawals,never()).insertReservation(any());
    }
    @Test void convertedCandidateIsRejectedEvenForSameSeat() throws Exception {
        String id=recommend("opportunity");when(withdrawals.lockDeveloper("developer")).thenReturn("developer");
        when(withdrawals.reservations("developer")).thenReturn(List.of(new Reservation("1","developer","target","기회",1,"202612","CNVT")));
        mvc.perform(put("/api/common/ai-recommendations/{id}/tentative-assignment",id).contentType("application/json").content("{\"developerId\":\"developer\",\"needSn\":1}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("투입 배정으로 넘긴 후보입니다. 투입 상세에서 확인해 주세요."));verify(withdrawals,never()).insertReservation(any());
    }
    @Test void tiesUseAvailabilityThenManagementNumberAndOnlyFiveCardsAreSaved() throws Exception {
        var people=new ArrayList<Person>();
        people.add(new Person("a","A","M009","BE",null,"백엔드","MD","중급","FT","정규",null));
        people.add(new Person("b","B","M001","BE",null,"백엔드","SR","고급","FT","정규",null));
        people.add(new Person("c","C","M000","BE",null,"백엔드","MD","중급","FT","정규",null));
        for(int i=0;i<4;i++)people.add(new Person("extra"+i,"기타","Z00"+i,"BE",null,"백엔드","MD","중급","FT","정규",null));
        when(mapper.candidates(any())).thenReturn(people);
        when(mapper.evidences(any())).thenReturn(List.of(new EvidenceRow("b",5,2,0,false,false,LocalDate.of(2027,1,31),true)));
        var result=json.readTree(mvc.perform(post("/api/common/ai-recommendations").contentType("application/json").content(json.writeValueAsBytes(input("project"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data").path("items");
        assertThat(result.size()).isEqualTo(5);assertThat(logs).hasSize(5);
        assertThat(result.path(0).path("developerId").asText()).isEqualTo("c");
        assertThat(result.path(1).path("developerId").asText()).isEqualTo("a");
        assertThat(result.path(2).path("developerId").asText()).startsWith("extra");
    }
    @Test void publicTentativeTransfersUsingSnapshotMonthAndCurrentNeedWithOvercapacity() throws Exception {
        String id=recommend("opportunity");
        when(withdrawals.lockDeveloper("developer")).thenReturn("developer");
        when(withdrawals.reservations("developer")).thenReturn(List.of(new Reservation("9","developer","previous","이전 기회",1,"202612","ACT")));
        // 2명 자리에 3번째 사람: 넘은 수(overCapacityCount)는 1, 알림의 n은 자리 인원 2.
        when(opportunities.needs("target")).thenReturn(List.of(new NeedRow(2,"BE","SR",2,"202701",6,BigDecimal.ONE,null)));
        when(mapper.evidence(any())).thenReturn(new Evidence(0,0,0,false,false,LocalDate.of(2026,12,31),false));
        when(withdrawals.release("9","admin")).thenReturn(1);
        when(withdrawals.insertReservation(any())).thenReturn(1);
        var header=mock(com.company.common.salesopportunities.mapper.SalesOpportunityRows.Header.class);
        when(header.opportunityName()).thenReturn("새 기회");when(opportunities.select("target")).thenReturn(header);
        when(opportunities.reservations("target")).thenReturn(List.of(
            new com.company.common.salesopportunities.mapper.SalesOpportunityRows.Reservation(9,"target","새 기회",2,"another","202701","ACT"),
            new com.company.common.salesopportunities.mapper.SalesOpportunityRows.Reservation(10,"target","새 기회",2,"other","202701","ACT"),
            new com.company.common.salesopportunities.mapper.SalesOpportunityRows.Reservation(11,"target","새 기회",2,"developer","202701","ACT")));
        mvc.perform(put("/api/common/ai-recommendations/{id}/tentative-assignment",id).contentType("application/json").content("{\"developerId\":\"developer\",\"needSn\":2}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.assignment.needSn").value(2))
            .andExpect(jsonPath("$.data.assignment.startMonth").value("2027-01"))
            .andExpect(jsonPath("$.data.overCapacityCount").value(1))
            .andExpect(jsonPath("$.data.assignment.assignmentId").value("11"))
            // 시안 문구 그대로, 「1번 자리」는 NEED_SN(2)이 아니라 지금 줄 가운데 순서.
            .andExpect(jsonPath("$.data.message").value("인력님을 「새 기회」 1번 자리(백엔드)에 가배정했습니다. (이전 가배정 「이전 기회」은 풀렸습니다)"
                + " 영업 파이프라인 필요 인력과 인력 운영 보드에서 볼 수 있습니다. 이 자리는 2명을 넘었습니다."));
        var write=org.mockito.ArgumentCaptor.forClass(com.company.common.withdrawals.mapper.WithdrawalRows.ReservationWrite.class);
        verify(withdrawals).insertReservation(write.capture());
        assertThat(write.getValue().sourceScreenId()).isEqualTo("UI-30");
        assertThat(write.getValue().startMonth()).isEqualTo("202701");
        verify(withdrawals).touchOpportunity("previous","admin");verify(withdrawals).touchOpportunity("target","admin");
        assertThat(logs.get(0).needSn()).isEqualTo(2);assertThat(logs.get(0).userActCode()).isEqualTo("TENT");
    }
}
