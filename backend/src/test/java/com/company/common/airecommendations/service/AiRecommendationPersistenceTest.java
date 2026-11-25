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
    @ParameterizedTest @ValueSource(strings={"disabled","indirect","retire","oneDayLeave","openLeave","sameOpportunity"})
    void eachExclusionRemovesOnlyItsOwnCandidate(String rule) {
        String opportunity=fixture.forecastSources(), excluded=fixture.person("제외 시험"), retained=fixture.person("유지 시험");
        switch(rule) {
            case "disabled" -> fixture.db().update("UPDATE TB_HR_DEV SET USE_YN='N' WHERE DEV_ID=?",excluded);
            case "indirect" -> fixture.db().update("UPDATE TB_HR_DEV SET DRCT_CLS_CD='INDRCT' WHERE DEV_ID=?",excluded);
            case "retire" -> fixture.db().update("UPDATE TB_HR_DEV SET RETIRE_DT=DATE '2026-12-31' WHERE DEV_ID=?",excluded);
            case "oneDayLeave" -> fixture.db().update("INSERT INTO TB_HR_DEV_STAT(DEV_ID,STAT_SN,WORK_STAT_CD,BGN_DT,END_DT,REG_USER_ID) VALUES(?,1,'LEAVE',DATE '2026-11-20',DATE '2026-12-01','admin')",excluded);
            case "openLeave" -> fixture.db().update("INSERT INTO TB_HR_DEV_STAT(DEV_ID,STAT_SN,WORK_STAT_CD,BGN_DT,REG_USER_ID) VALUES(?,1,'LEAVE',DATE '2026-11-20','admin')",excluded);
            case "sameOpportunity" -> fixture.db().update("INSERT INTO TB_SL_TENT_ASGN(OPP_ID,NEED_SN,DEV_ID,BGN_YM,TENT_STAT_CD,SRC_SCRN_ID,REG_USER_ID) VALUES(?,1,?,'202612','ACT','UI-30','admin')",opportunity,excluded);
        }
        try(var session=fixture.factory().openSession()) {
            assertThat(session.getMapper(AiRecommendationMapper.class).candidates(filter(opportunity,"BE"))).extracting(Person::developerId).contains(retained).doesNotContain(excluded);
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
        String fingerprint=AiRecommendationExecutionJournal.fingerprint(UUID.randomUUID().toString());
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
            assertThat(mapper.replay(fingerprint)).hasSize(1);
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
