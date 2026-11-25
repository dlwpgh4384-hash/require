package com.company.common.airecommendations.service;

import com.company.common.airecommendations.dto.AiRecommendationContracts.TentativeInput;
import com.company.common.airecommendations.mapper.*;
import com.company.common.airecommendations.mapper.AiRecommendationRows.*;
import com.company.common.common.*;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.salesopportunities.mapper.SalesOpportunityMapper;
import com.company.common.support.service.*;
import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="NAVIGATION_TEST_DB_URL",matches="jdbc:postgresql:.*")
class AiRecommendationTentativePersistenceTest {
    ManpowerBoardFixture fixture;
    SqlSessionTemplate sessions;
    DataSourceTransactionManager transactions;
    String source,target,developer,id;
    @BeforeEach void prepareOwnedDatabase() throws Exception {
        fixture=new ManpowerBoardFixture();source=fixture.forecastSources();target=fixture.forecastSources();developer=fixture.person("가배정 시험");
        fixture.db().update("INSERT INTO TB_SL_TENT_ASGN(OPP_ID,NEED_SN,DEV_ID,BGN_YM,TENT_STAT_CD,SRC_SCRN_ID,REG_USER_ID) VALUES(?,1,?,'202612','ACT','UI-29','admin')",source,developer);
        var configuration=fixture.factory().getConfiguration();var dataSource=configuration.getEnvironment().getDataSource();
        configuration.setEnvironment(new Environment("spring",new SpringManagedTransactionFactory(),dataSource));
        sessions=new SqlSessionTemplate(fixture.factory());transactions=new DataSourceTransactionManager(dataSource);
        var mapper=sessions.getMapper(AiRecommendationMapper.class);id=mapper.nextId();
        var scores=new LinkedHashMap<String,AiRecommendationLogCodec.Score>();AiRecommendationRules.DIMENSIONS.forEach(d->scores.put(d,new AiRecommendationLogCodec.Score(BigDecimal.ONE,BigDecimal.TEN)));
        String detail=new AiRecommendationLogCodec(new ObjectMapper()).encode(new AiRecommendationLogCodec.Snapshot(scores,new AiRecommendationLogCodec.Metadata(AiRecommendationLogCodec.fingerprint(UUID.randomUUID().toString()),null,null)));
        mapper.insert(new LogWrite(new Log(id,1,"opportunity",target,"BE","202612","MD",null,developer,new BigDecimal("8"),detail,"근거",null,null,null,null),"admin"));
    }
    @AfterEach void dropOwnedDatabase() throws Exception { if(fixture!=null)fixture.close(); }
    static <T> T failing(T delegate,Class<T> type,String method) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,called,args)->{
            if(called.getName().equals(method))throw new IllegalStateException("injected write failure");
            try{return called.invoke(delegate,args);}catch(InvocationTargetException error){throw error.getCause();}
        }));
    }
    AiRecommendationActionService service(String fail) {
        var mapper=sessions.getMapper(AiRecommendationMapper.class);var withdrawals=sessions.getMapper(WithdrawalMapper.class);
        if("action".equals(fail))mapper=failing(mapper,AiRecommendationMapper.class,fail);
        else if(!fail.isEmpty())withdrawals=failing(withdrawals,WithdrawalMapper.class,fail);
        var codes=mock(AiRecommendationCodes.class);when(codes.name("JOB","BE")).thenReturn("백엔드");
        var json=new ObjectMapper();var resources=new AiRecommendationResources(codes,mock(AiRecommendationDefinitions.class),new AiRecommendationLogCodec(json),json,new BusinessCalendar(ManpowerBoardFixture.CLOCK));
        var targetService=new AiRecommendationActionService(mapper,sessions.getMapper(SalesOpportunityMapper.class),withdrawals,sessions.getMapper(ProjectMapper.class),resources,new CurrentBusinessActor());
        var proxy=new ProxyFactory(targetService);proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactions,new AnnotationTransactionAttributeSource()));
        return (AiRecommendationActionService)proxy.getProxy();
    }
    @ParameterizedTest @ValueSource(strings={"release","insertReservation","touchOpportunity","action"})
    void allTransferWritesRollbackTogether(String failedWrite) {
        String before=fixture.businessSnapshot();
        var old=sessions.getMapper(AiRecommendationMapper.class).candidate(id,developer);
        assertThatThrownBy(()->service(failedWrite).tentative(id,new TentativeInput(developer,1))).isInstanceOf(IllegalStateException.class);
        assertThat(fixture.businessSnapshot()).isEqualTo(before);
        assertThat(sessions.getMapper(AiRecommendationMapper.class).candidate(id,developer)).isEqualTo(old);
    }
    @Test void committedTransferUsesSharedRowsAndNeverCreatesAnAssignment() {
        int assignments=fixture.db().queryForObject("SELECT COUNT(*) FROM TB_PJ_ASGN",Integer.class);
        var result=service("").tentative(id,new TentativeInput(developer,1));
        assertThat(result.assignment().startMonth()).isEqualTo("2026-12");
        assertThat(fixture.db().queryForObject("SELECT TENT_STAT_CD FROM TB_SL_TENT_ASGN WHERE OPP_ID=? AND DEV_ID=?",String.class,source,developer)).isEqualTo("REL");
        assertThat(fixture.db().queryForObject("SELECT SRC_SCRN_ID FROM TB_SL_TENT_ASGN WHERE OPP_ID=? AND DEV_ID=?",String.class,target,developer)).isEqualTo("UI-30");
        assertThat(sessions.getMapper(SalesOpportunityMapper.class).reservations(target)).extracting(r->r.developerId()).contains(developer);
        assertThat(sessions.getMapper(AiRecommendationMapper.class).candidate(id,developer).needSn()).isEqualTo(1);
        assertThat(fixture.db().queryForObject("SELECT COUNT(*) FROM TB_SL_OPP WHERE OPP_ID IN (?,?) AND MOD_DTTM IS NOT NULL",Integer.class,source,target)).isEqualTo(2);
        assertThat(fixture.db().queryForObject("SELECT COUNT(*) FROM TB_PJ_ASGN",Integer.class)).isEqualTo(assignments);
        // 결과의 id는 새 가배정 줄의 TENT_ID.
        assertThat(result.assignment().assignmentId()).isEqualTo(fixture.db().queryForObject(
            "SELECT TENT_ID::text FROM TB_SL_TENT_ASGN WHERE OPP_ID=? AND DEV_ID=? AND TENT_STAT_CD='ACT'",String.class,target,developer));
        assertThat(result.message()).isEqualTo("가배정 시험님을 「예측 시험 "+target+"」 1번 자리(백엔드)에 가배정했습니다. (이전 가배정 「예측 시험 "+source
            +"」은 풀렸습니다) 영업 파이프라인 필요 인력과 인력 운영 보드에서 볼 수 있습니다.");
    }
    @Test void seatNumberInNoticeIsOrderAmongCurrentRowsNotNeedNumber() {
        fixture.db().update("INSERT INTO TB_SL_OPP_NEED(OPP_ID,NEED_SN,JOB_CD,GRADE_CD,NEED_CNT,BGN_YM,PRD_MCNT,MON_MM,EXPCT_UPRC,REG_USER_ID) VALUES(?,3,'BE','MD',1,'202612',6,1,8000000,'admin')",target);
        var result=service("").tentative(id,new TentativeInput(developer,3));
        assertThat(result.assignment().needSn()).isEqualTo(3);
        assertThat(result.message()).startsWith("가배정 시험님을 「예측 시험 "+target+"」 2번 자리(백엔드)에 가배정했습니다.");
        assertThat(result.assignment().assignmentId()).isNotBlank();
    }
    @Test void sameSeatAgainReleasesNothingAndKeepsTheSameTentativeId() {
        var first=service("").tentative(id,new TentativeInput(developer,1));
        var again=service("").tentative(id,new TentativeInput(developer,1));
        assertThat(again.assignment().assignmentId()).isEqualTo(first.assignment().assignmentId());
        assertThat(again.releasedOpportunityName()).isNull();
        assertThat(again.message()).doesNotContain("이전 가배정");
    }
}
