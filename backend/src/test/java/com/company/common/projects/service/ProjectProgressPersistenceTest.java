package com.company.common.projects.service;

import com.company.common.projects.controller.ProjectProgressController;
import com.company.common.projects.mapper.ProjectProgressMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="NAVIGATION_TEST_DB_URL",matches="jdbc:postgresql:.*")
@WebMvcTest(ProjectProgressController.class)
@ContextConfiguration(classes={ProjectProgressController.class,ProjectProgressService.class,
    ProjectStageService.class,CurrentBusinessActor.class,CodeExceptionHandler.class,ApiExceptionHandler.class,
    ProjectProgressPersistenceTest.Persistence.class})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProjectProgressPersistenceTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired DataSource source;
    @Autowired Owned owned; @Autowired ProjectStageService stages; @Autowired ProjectProgressService memos;
    JdbcTemplate db;
    record Owned(String name) {}
    @TestConfiguration(proxyBeanMethods=false) @EnableTransactionManagement
    static class Persistence {
        @Bean Owned owned(){return new Owned("progress_"+UUID.randomUUID().toString().replace("-",""));}
        @Bean DataSource dataSource(Owned owned)throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL"),user=System.getenv("NAVIGATION_TEST_DB_USER"),password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("CREATE SCHEMA "+owned.name());}
            var d=new UnpooledDataSource("org.postgresql.Driver",url,user,password);var properties=new Properties();properties.setProperty("currentSchema",owned.name());d.setDriverProperties(properties);
            var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),d));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();return d;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource d)throws Exception {
            var f=new SqlSessionFactoryBean();f.setDataSource(d);f.setMapperLocations(new ClassPathResource("mapper/projects/ProjectProgressMapper.xml"));
            var c=new org.apache.ibatis.session.Configuration();c.setMapUnderscoreToCamelCase(false);c.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);f.setConfiguration(c);return f.getObject();
        }
        @Bean SqlSessionTemplate template(SqlSessionFactory f){return new SqlSessionTemplate(f);}
        @Bean ProjectProgressMapper mapper(SqlSessionTemplate s){return s.getMapper(ProjectProgressMapper.class);}
        @Bean DataSourceTransactionManager transactionManager(DataSource d){return new DataSourceTransactionManager(d);}
        @Bean BusinessCalendar calendar(){return new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"),ZoneOffset.UTC));}
    }
    @BeforeEach void setup(){db=new JdbcTemplate(source);db.update("INSERT INTO TB_PJ_PROJ (PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES ('P-PROGRESS','진행 시험','TK','ING','2026-11-01','2027-01-31',1000000,0,0,'admin')");}
    @AfterEach void cleanup()throws Exception{try(var c=source.getConnection();var s=c.createStatement()){s.execute("DROP SCHEMA "+owned.name()+" CASCADE");}}
    String root(){return "/api/common/projects/P-PROGRESS";}
    List<Map<String,Object>> project(){return db.queryForList("SELECT * FROM TB_PJ_PROJ WHERE PROJ_ID='P-PROGRESS'");}
    @Test void concurrentEnsureAndMemosSerializeWithoutDuplicates()throws Exception {
        var pool=Executors.newFixedThreadPool(4);
        try {
            var calls=new ArrayList<Callable<Object>>();for(int i=0;i<4;i++)calls.add(()->stages.postCommonProjectBoardStepsEnsure("P-PROGRESS"));
            for(var future:pool.invokeAll(calls))future.get();
            assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_PJ_STEP WHERE PROJ_ID='P-PROGRESS'",Integer.class)).isEqualTo(7);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_PJ_STEP WHERE CMPL_DT IS NOT NULL",Integer.class)).isZero();
            calls.clear();for(int i=0;i<4;i++)calls.add(()->memos.postCommonProjectBoardWeeklyMemo("P-PROGRESS",json.readTree("{\"requests\":\"병행 등록\"}")));
            for(var future:pool.invokeAll(calls))future.get();
            assertThat(db.queryForObject("SELECT COUNT(DISTINCT MEMO_SN) FROM TB_PJ_WEEK_MEMO WHERE PROJ_ID='P-PROGRESS'",Integer.class)).isEqualTo(4);
            assertThat(db.queryForList("SELECT DISTINCT REG_USER_ID FROM TB_PJ_WEEK_MEMO",String.class)).containsExactly("admin");
        } finally {pool.shutdownNow();}
    }
    @Test void memoEditPreservesWrittenDateAndCreationAndOnlyTouchesSelectedRow()throws Exception {
        db.update("INSERT INTO TB_PJ_WEEK_MEMO (PROJ_ID,MEMO_SN,WRT_DT,REQ_CN,REG_USER_ID,REG_DTTM) VALUES ('P-PROGRESS',1,'2026-10-01','원문','other','2026-10-01T00:00:00Z'),('P-PROGRESS',2,'2026-10-02','다른 행','other','2026-10-02T00:00:00Z')");
        var before=project();var other=db.queryForMap("SELECT * FROM TB_PJ_WEEK_MEMO WHERE MEMO_SN=2");
        mvc.perform(put(root()+"/weekly-memos/1").contentType("application/json").content("{\"requests\":\"새 요청\",\"modifiedAt\":\"2026-10-01T00:00:00Z\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.updatedBy").value("admin"))
            .andExpect(jsonPath("$.data.writtenDate").value("2026-10-01"));
        mvc.perform(put(root()+"/weekly-memos/1").contentType("application/json").content("{\"requests\":\"늦은 수정\",\"modifiedAt\":\"2026-10-01T00:00:00Z\"}")).andExpect(status().isConflict());
        assertThat(db.queryForObject("SELECT REQ_CN FROM TB_PJ_WEEK_MEMO WHERE MEMO_SN=1",String.class)).isEqualTo("새 요청");
        assertThat(db.queryForObject("SELECT WRT_DT FROM TB_PJ_WEEK_MEMO WHERE MEMO_SN=1",LocalDate.class)).isEqualTo(LocalDate.of(2026,10,1));
        assertThat(db.queryForObject("SELECT MAX(WRT_DT) FROM TB_PJ_WEEK_MEMO",LocalDate.class)).isEqualTo(LocalDate.of(2026,10,2));
        assertThat(db.queryForMap("SELECT * FROM TB_PJ_WEEK_MEMO WHERE MEMO_SN=2")).isEqualTo(other);assertThat(project()).isEqualTo(before);
        mvc.perform(post(root()+"/weekly-memos").contentType("application/json").content("{}")).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT MAX(WRT_DT) FROM TB_PJ_WEEK_MEMO",LocalDate.class)).isEqualTo(LocalDate.of(2026,11,15));
    }
    @Test void completeReadbackRetryAndInvalidDateRollbackPreserveProjectMoney()throws Exception {
        var before=project();stages.postCommonProjectBoardStepsEnsure("P-PROGRESS");
        mvc.perform(post(root()+"/steps/1/complete")).andExpect(status().isOk()).andExpect(jsonPath("$.data.completedDate").value("2026-11-15"));
        var audit=db.queryForMap("SELECT * FROM TB_PJ_STEP WHERE STEP_SN=1");
        mvc.perform(post(root()+"/steps/1/complete")).andExpect(status().isOk());assertThat(db.queryForMap("SELECT * FROM TB_PJ_STEP WHERE STEP_SN=1")).isEqualTo(audit);
        var snapshot=db.queryForList("SELECT * FROM TB_PJ_STEP ORDER BY STEP_SN");
        var list=stages.getCommonProjectBoardSteps("P-PROGRESS");var body=json.createObjectNode();body.put("modifiedAt",list.modifiedAt().toString());var items=body.putArray("items");for(var s:list.items())items.addObject().put("stepId",s.stepId()).put("plannedStartDate",s.plannedStartDate().toString()).put("plannedEndDate",s.plannedEndDate().toString());
        ((com.fasterxml.jackson.databind.node.ObjectNode)items.get(6)).put("plannedEndDate","2026-11-01");
        mvc.perform(put(root()+"/steps").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        assertThat(db.queryForList("SELECT * FROM TB_PJ_STEP ORDER BY STEP_SN")).isEqualTo(snapshot);assertThat(project()).isEqualTo(before);
    }
    @Test void boardEnsureCreatesStagesOnceForExistingProjectsAndSkipsClosed()throws Exception {
        db.update("INSERT INTO TB_PJ_PROJ (PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES ('P-LOST','실주','TK','LOSE','2026-11-01','2027-01-31',0,0,0,'admin'),('P-OLD','끝남','MO','ING','2026-01-01','2026-11-14',0,0,0,'admin'),('P-BAD','유형 없음','XX','ING','2026-11-01','2027-01-31',0,0,0,'admin')");
        stages.ensureMissingStages();
        stages.ensureMissingStages();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_PJ_STEP WHERE PROJ_ID='P-PROGRESS'",Integer.class)).isEqualTo(7);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_PJ_STEP WHERE PROJ_ID<>'P-PROGRESS'",Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_PJ_STEP WHERE REG_DTTM IS NULL OR MOD_DTTM IS NOT NULL",Integer.class)).isZero();
    }

}
