package com.company.common.airecommendations.service;

import com.company.common.airecommendations.dto.AiRecommendationContracts.Condition;
import com.company.common.airecommendations.mapper.*;
import com.company.common.airecommendations.mapper.AiRecommendationRows.*;
import com.company.common.common.*;
import com.company.common.salesopportunities.mapper.SalesOpportunityMapper;
import com.company.common.salesopportunities.mapper.SalesOpportunityRows.ResultWrite;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="NAVIGATION_TEST_DB_URL",matches="jdbc:postgresql:.*")
class AiRecommendationPersistenceTest {
    ManpowerBoardFixture fixture;
    @BeforeEach void independentSchema() throws Exception { fixture=new ManpowerBoardFixture(); }
    @AfterEach void dropOwnedSchema() throws Exception { if(fixture!=null)fixture.close(); }
    Filter filter(String opportunity,String job) { return new Filter(new Condition("opportunity",opportunity,job,"2026-12","SR",null),LocalDate.of(2026,12,1),LocalDate.of(2026,12,31),LocalDate.of(2026,11,25),false); }
    Filter projectFilter(String project,String job) { return new Filter(new Condition("project",project,job,"2026-12","SR",null),LocalDate.of(2026,12,1),LocalDate.of(2026,12,31),LocalDate.of(2026,11,25),false); }
    String dispatchProject() {
        String project=UUID.randomUUID().toString().replace("-","").substring(0,20);
        fixture.db().update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES(?,?,'DP','ING',DATE '2026-01-01',DATE '2027-05-31',0,0,0,'admin')",project,"파견 시험 "+project);
        return project;
    }
    /** 빼는 사람 규칙 하나씩: 그 사람만 빠지고 같은 조건의 다른 사람은 남는다. */
    @ParameterizedTest @ValueSource(strings={"disabled","indirect","retire","retireBefore","oneDayLeave","openLeave","sameOpportunity",
        "dispatchFreelancerOpportunity","dispatchOutsourceOpportunity","dispatchFreelancerProject","dispatchOutsourceProject","excludedSameTargetJob","retireStatusWithoutDate"})
    void eachExclusionRemovesOnlyItsOwnCandidate(String rule) {
        String opportunity=fixture.forecastSources(), excluded=fixture.person("제외 시험"), retained=fixture.person("유지 시험");
        Filter filter=filter(opportunity,"BE");
        switch(rule) {
            case "disabled" -> fixture.db().update("UPDATE TB_HR_DEV SET USE_YN='N' WHERE DEV_ID=?",excluded);
            case "indirect" -> fixture.db().update("UPDATE TB_HR_DEV SET DRCT_CLS_CD='INDRCT' WHERE DEV_ID=?",excluded);
            case "retire" -> fixture.db().update("UPDATE TB_HR_DEV SET RETIRE_DT=DATE '2026-12-31' WHERE DEV_ID=?",excluded);
            case "retireBefore" -> fixture.db().update("UPDATE TB_HR_DEV SET RETIRE_DT=DATE '2026-11-30' WHERE DEV_ID=?",excluded);
            case "oneDayLeave" -> fixture.db().update("INSERT INTO TB_HR_DEV_STAT(DEV_ID,STAT_SN,WORK_STAT_CD,BGN_DT,END_DT,REG_USER_ID) VALUES(?,1,'LEAVE',DATE '2026-11-20',DATE '2026-12-01','admin')",excluded);
            case "openLeave" -> fixture.db().update("INSERT INTO TB_HR_DEV_STAT(DEV_ID,STAT_SN,WORK_STAT_CD,BGN_DT,REG_USER_ID) VALUES(?,1,'LEAVE',DATE '2026-11-20','admin')",excluded);
            case "sameOpportunity" -> fixture.db().update("INSERT INTO TB_SL_TENT_ASGN(OPP_ID,NEED_SN,DEV_ID,BGN_YM,TENT_STAT_CD,SRC_SCRN_ID,REG_USER_ID) VALUES(?,1,?,'202612','ACT','UI-30','admin')",opportunity,excluded);
            case "dispatchFreelancerOpportunity","dispatchOutsourceOpportunity" -> {
                fixture.db().update("UPDATE TB_SL_OPP SET CTRT_TYPE_CD='DP' WHERE OPP_ID=?",opportunity);
                fixture.db().update("UPDATE TB_HR_DEV SET EMP_TYPE_CD=? WHERE DEV_ID=?",rule.contains("Freelancer")?"FL":"OS",excluded);
            }
            case "dispatchFreelancerProject","dispatchOutsourceProject" -> {
                filter=projectFilter(dispatchProject(),"BE");
                fixture.db().update("UPDATE TB_HR_DEV SET EMP_TYPE_CD=? WHERE DEV_ID=?",rule.contains("Freelancer")?"FL":"OS",excluded);
            }
            case "excludedSameTargetJob" -> fixture.db().update("""
                INSERT INTO TB_SL_RCMD_LOG(RCMD_ID,RANK_ORD,RCMD_TYPE_CD,OPP_ID,JOB_CD,BGN_YM,REQ_GRADE_CD,DEV_ID,TOT_SCORE,SCORE_DTL,USER_ACT_CD,RCMD_EXCL_CD,REG_USER_ID)
                VALUES(nextval('SQ_TB_SL_RCMD_LOG'),1,'AI',?,'BE','202612','SR',?,8,'{"v":1,"e":true}','EXCL','OTHER','admin')""",opportunity,excluded);
            // 퇴사 상태인데 퇴사일이 비어 있고, 예전 휴직 이력(끝남)이 있다: 휴직 이력과 상관없이 뺀다.
            case "retireStatusWithoutDate" -> {
                fixture.db().update("UPDATE TB_HR_DEV SET WORK_STAT_CD='RETIRE',RETIRE_DT=NULL WHERE DEV_ID=?",excluded);
                fixture.db().update("INSERT INTO TB_HR_DEV_STAT(DEV_ID,STAT_SN,WORK_STAT_CD,BGN_DT,END_DT,REG_USER_ID) VALUES(?,1,'LEAVE',DATE '2025-01-01',DATE '2025-03-31','admin')",excluded);
            }
            default -> throw new IllegalArgumentException(rule);
        }
        try(var session=fixture.factory().openSession()) {
            assertThat(session.getMapper(AiRecommendationMapper.class).candidates(filter)).extracting(Person::developerId).contains(retained).doesNotContain(excluded);
        }
    }
    /** 추천 이유의 철수일 = 오늘 진행 중인 투입의 철수일. 진행 중 투입이 없으면 null(「지금 대기 중입니다」), 지난 투입 철수일은 쓰지 않는다. */
    @Test void withdrawalDateIsOnlyTheInProgressAssignmentEnd() {
        String project=dispatchProject(),idle=fixture.person("대기 시험"),busy=fixture.person("투입 시험");
        String insert="INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,CTRT_MM,BILL_UPRC,INPUT_TYPE_CD,EXT_CNT,EARLY_WTHDR_YN,ASGN_STAT_CD,REG_USER_ID) VALUES(?,?,?,'BE',?,?,1,8000000,'NEW',0,'N','ACT','admin')";
        fixture.db().update(insert,"old-idle",project,idle,LocalDate.of(2026,1,1),LocalDate.of(2026,6,30));
        fixture.db().update(insert,"old-busy",project,busy,LocalDate.of(2025,1,1),LocalDate.of(2025,12,31));
        fixture.db().update(insert,"now-busy",project,busy,LocalDate.of(2026,7,1),LocalDate.of(2026,11,30));
        try(var session=fixture.factory().openSession()) {
            var rows=new HashMap<String,LocalDate>();
            session.getMapper(AiRecommendationMapper.class).evidences(new EvidencesFilter(List.of(idle,busy),null,null,
                LocalDate.of(2026,11,25),LocalDate.of(2026,12,1))).forEach(r->rows.put(r.developerId(),r.withdrawalDate()));
            assertThat(rows).containsKeys(idle,busy);
            assertThat(rows.get(idle)).isNull();
            assertThat(rows.get(busy)).isEqualTo(LocalDate.of(2026,11,30));
        }
    }
    @Test void dispatchRuleKeepsExternalPeopleForOtherContractTypes() {
        String opportunity=fixture.forecastSources(),external=fixture.person("외주 시험");
        fixture.db().update("UPDATE TB_HR_DEV SET EMP_TYPE_CD='OS' WHERE DEV_ID=?",external);
        try(var session=fixture.factory().openSession()) {
            assertThat(session.getMapper(AiRecommendationMapper.class).candidates(filter(opportunity,"BE"))).extracting(Person::developerId).contains(external);
        }
    }
    @Test void insertStampsRegisteredTimeWithClockTimestampNotTransactionStart() throws Exception {
        String opportunity=fixture.forecastSources(),developer=fixture.person("등록 시각 시험");
        try(var session=fixture.factory().openSession()) {
            var mapper=session.getMapper(AiRecommendationMapper.class);var connection=session.getConnection();
            try(var sleep=connection.createStatement()) { sleep.execute("SELECT pg_sleep(0.05)"); }
            String id=mapper.nextId();
            mapper.insert(new LogWrite(new Log(id,1,"opportunity",opportunity,"BE","202612","SR",null,developer,new BigDecimal("8"),detail(),"근거",null,null,null,null),"admin"));
            try(var check=connection.prepareStatement("SELECT REG_DTTM > transaction_timestamp() FROM TB_SL_RCMD_LOG WHERE RCMD_ID=CAST(? AS numeric)")) {
                check.setString(1,id);
                try(var rows=check.executeQuery()) { assertThat(rows.next()).isTrue(); assertThat(rows.getBoolean(1)).isTrue(); }
            }
            session.commit();
        }
    }
    String detail() {
        var scores=new LinkedHashMap<String,AiRecommendationLogCodec.Score>();
        AiRecommendationRules.DIMENSIONS.forEach(d->scores.put(d,new AiRecommendationLogCodec.Score(BigDecimal.ONE,BigDecimal.TEN)));
        return new AiRecommendationLogCodec(new com.fasterxml.jackson.databind.ObjectMapper()).encode(new AiRecommendationLogCodec.Snapshot(scores,
            new AiRecommendationLogCodec.Metadata(AiRecommendationLogCodec.fingerprint(UUID.randomUUID().toString()),null,null)));
    }
    /** 제외는 처리 전 줄에만, 가배정·배정은 제외한 줄을 덮지 않는다. */
    @Test void userActionsKeepPreviousStateGuard() {
        String opportunity=fixture.forecastSources(),tentative=fixture.person("가배정 후보"),excluded=fixture.person("제외 후보");
        try(var session=fixture.factory().openSession()) {
            var mapper=session.getMapper(AiRecommendationMapper.class);String id=mapper.nextId();
            mapper.insert(new LogWrite(new Log(id,1,"opportunity",opportunity,"BE","202612","SR",null,tentative,new BigDecimal("8"),detail(),"근거",null,null,null,null),"admin"));
            mapper.insert(new LogWrite(new Log(id,2,"opportunity",opportunity,"BE","202612","SR",null,excluded,new BigDecimal("7"),detail(),"근거",null,null,null,null),"admin"));
            assertThat(mapper.action(new Action(id,tentative,"TENT",1,null,"admin"))).isEqualTo(1);
            assertThat(mapper.exclude(new ExclusionWrite(new Action(id,tentative,"EXCL",1,"OTHER","admin"),detail()))).isZero();
            assertThat(mapper.action(new Action(id,tentative,"TENT",1,null,"admin"))).isEqualTo(1);
            assertThat(mapper.exclude(new ExclusionWrite(new Action(id,excluded,"EXCL",null,"OTHER","admin"),detail()))).isEqualTo(1);
            assertThat(mapper.action(new Action(id,excluded,"TENT",1,null,"admin"))).isZero();
            assertThat(mapper.action(new Action(id,excluded,"ASGN",null,null,"admin"))).isZero();
            assertThat(mapper.candidate(id,tentative).userActCode()).isEqualTo("TENT");
            assertThat(mapper.candidate(id,excluded).userActCode()).isEqualTo("EXCL");
            session.commit();
        }
    }
    @Test void replayFindsOnlyTheSameRequestOnTheSameTarget() {
        String opportunity=fixture.forecastSources(),other=fixture.forecastSources(),developer=fixture.person("다시 보내기 시험");
        String fingerprint=AiRecommendationLogCodec.fingerprint(UUID.randomUUID().toString());
        var scores=new LinkedHashMap<String,AiRecommendationLogCodec.Score>();
        AiRecommendationRules.DIMENSIONS.forEach(d->scores.put(d,new AiRecommendationLogCodec.Score(BigDecimal.ONE,BigDecimal.TEN)));
        String detail=new AiRecommendationLogCodec(new com.fasterxml.jackson.databind.ObjectMapper()).encode(new AiRecommendationLogCodec.Snapshot(scores,new AiRecommendationLogCodec.Metadata(fingerprint,null,null)));
        try(var session=fixture.factory().openSession()) {
            var mapper=session.getMapper(AiRecommendationMapper.class);String id=mapper.nextId();
            mapper.insert(new LogWrite(new Log(id,1,"opportunity",opportunity,"BE","202612","SR",null,developer,new BigDecimal("8"),detail,"근거",null,null,null,null),"admin"));
            mapper.insert(new LogWrite(new Log(mapper.nextId(),1,"opportunity",opportunity,"BE","202612","SR",null,developer,new BigDecimal("8"),detail(),"근거",null,null,null,null),"admin"));
            session.commit();
            assertThat(mapper.replay(fingerprint,"opportunity",opportunity)).extracting(Log::recommendationId).containsExactly(id);
            assertThat(mapper.replay(fingerprint,"opportunity",other)).isEmpty();
        }
    }
    @Test void largestNeedNumberCountsCurrentRowsAndRecommendationLogs() {
        String opportunity=fixture.forecastSources(),developer=fixture.person("번호 시험");
        try(var session=fixture.factory().openSession()) {
            var sales=session.getMapper(SalesOpportunityMapper.class);
            assertThat(sales.maxNeedSn(opportunity)).isEqualTo(1);
            assertThat(sales.maxNeedSn("missing")).isZero();
            fixture.db().update("""
                INSERT INTO TB_SL_RCMD_LOG(RCMD_ID,RANK_ORD,RCMD_TYPE_CD,OPP_ID,NEED_SN,JOB_CD,BGN_YM,REQ_GRADE_CD,DEV_ID,TOT_SCORE,SCORE_DTL,USER_ACT_CD,REG_USER_ID)
                VALUES(nextval('SQ_TB_SL_RCMD_LOG'),1,'AI',?,7,'BE','202612','SR',?,8,'{"v":1}','TENT','admin')""",opportunity,developer);
            session.clearCache();
            assertThat(sales.maxNeedSn(opportunity)).isEqualTo(7);
        }
    }
    @Test void confirmedOneIsExcludedButHalfAndOtherOpportunityTentativeRemain() {
        var people=fixture.boardSources();String opportunity=fixture.forecastSources();
        fixture.db().update("UPDATE TB_PJ_ASGN_MON SET MON_MM=0.5 WHERE DEV_ID=?",people.get("partial"));
        try(var session=fixture.factory().openSession()) {
            var ids=session.getMapper(AiRecommendationMapper.class).candidates(filter(opportunity,"BE")).stream().map(Person::developerId).toList();
            assertThat(ids).doesNotContain(people.get("full"),people.get("concurrent"),people.get("excess"),people.get("leave"),people.get("retired"));
            assertThat(ids).contains(people.get("partial"),people.get("tentative"),people.get("idle"));
        }
    }
    @Test void snapshotInsertReplayAndExpiryPreserveScoresAndAuditAcrossReopen() {
        String opportunity=fixture.forecastSources(),developer=fixture.person("기록 시험");
        var codec=new AiRecommendationLogCodec(new com.fasterxml.jackson.databind.ObjectMapper());
        var scores=new LinkedHashMap<String,AiRecommendationLogCodec.Score>();
        AiRecommendationRules.DIMENSIONS.forEach(d->scores.put(d,new AiRecommendationLogCodec.Score(BigDecimal.ONE,BigDecimal.TEN)));
        String fingerprint=AiRecommendationLogCodec.fingerprint(UUID.randomUUID().toString());
        String detail=codec.encode(new AiRecommendationLogCodec.Snapshot(scores,new AiRecommendationLogCodec.Metadata(fingerprint,null,true)));
        String id;
        try(var session=fixture.factory().openSession()) {
            var mapper=session.getMapper(AiRecommendationMapper.class);mapper.lockRequest(fingerprint);id=mapper.nextId();
            mapper.insert(new LogWrite(new Log(id,1,"opportunity",opportunity,"BE","202612","SR",null,developer,new BigDecimal("8"),detail,"근거",null,null,null,null),"admin"));
            mapper.exclude(new ExclusionWrite(new Action(id,developer,"EXCL",null,"OTHER","admin"),detail));session.commit();
        }
        try(var session=fixture.factory().openSession()) {
            var mapper=session.getMapper(AiRecommendationMapper.class);var original=mapper.candidate(id,developer);
            assertThat(original.actedAt()).isNotNull();assertThat(original.actedBy()).isEqualTo("admin");
            assertThat(mapper.replay(fingerprint,"opportunity",opportunity)).hasSize(1);
            assertThat(mapper.candidates(filter(opportunity,"BE"))).extracting(Person::developerId).doesNotContain(developer);
            assertThat(mapper.candidates(filter(opportunity,"FE"))).extracting(Person::developerId).contains(developer);
            var sales=session.getMapper(SalesOpportunityMapper.class);
            sales.result(new ResultWrite(opportunity,"STOP",LocalDate.of(2026,11,25),null,null,"admin"));
            sales.result(new ResultWrite(opportunity,"ING",null,null,null,"admin"));session.commit();
            var closed=mapper.candidate(id,developer);
            assertThat(codec.decode(closed.scoreDetail()).metadata().exclusionActive()).isFalse();
            assertThat(codec.decode(closed.scoreDetail()).scores()).isEqualTo(scores);
            assertThat(closed.actedAt()).isEqualTo(original.actedAt());assertThat(closed.reasonCode()).isEqualTo(original.reasonCode());
            assertThat(mapper.candidates(filter(opportunity,"BE"))).extracting(Person::developerId).contains(developer);
        }
    }
    @Test void noNeedsStillPermitsCandidateReadsAndLogs() {
        String opportunity=fixture.forecastSources(),developer=fixture.person("자리 없는 기회");
        fixture.db().update("DELETE FROM TB_SL_OPP_NEED WHERE OPP_ID=?",opportunity);
        try(var session=fixture.factory().openSession()) {
            var mapper=session.getMapper(AiRecommendationMapper.class);
            assertThat(mapper.target("opportunity",opportunity).targetId()).isEqualTo(opportunity);
            assertThat(mapper.candidates(filter(opportunity,"BE"))).extracting(Person::developerId).contains(developer);
        }
    }
}
