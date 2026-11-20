package com.company.common.withdrawals.service;

import com.company.common.assignments.mapper.AssignmentMapper;
import com.company.common.assignments.service.*;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.developers.service.DeveloperManMonthRules;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.projects.service.ProjectAmountRules;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.company.common.users.mapper.UserMapper;
import com.company.common.withdrawals.controller.CommonWithdrawalController;
import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.fasterxml.jackson.databind.*;
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
import org.springframework.boot.test.mock.mockito.MockBean;
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

@EnabledIfEnvironmentVariable(named="NAVIGATION_TEST_DB_URL", matches="jdbc:postgresql:.*")
@WebMvcTest(CommonWithdrawalController.class)
@ContextConfiguration(classes={CommonWithdrawalController.class,CommonWithdrawalService.class,
    WithdrawalRecommendationService.class,WithdrawalTentativeService.class,WithdrawalCalculationRules.class,
    WithdrawalCodes.class,CommonCodeService.class,CommonAssignmentService.class,AssignmentMonthRules.class,
    DeveloperManMonthRules.class,ProjectAmountRules.class,CalculationRuleService.class,CurrentBusinessActor.class,
    ExcelExportService.class,CodeExceptionHandler.class,ApiExceptionHandler.class,WithdrawalPersistenceTest.Persistence.class})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class WithdrawalPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired Owned owned;
    @Autowired WithdrawalTentativeService tentative;
    @Autowired CommonAssignmentService assignments;
    @MockBean UserMapper management;
    JdbcTemplate db;
    String job;
    record Owned(String name) {}

    @TestConfiguration(proxyBeanMethods=false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean Owned owned(){return new Owned("withdrawal_"+UUID.randomUUID().toString().replace("-",""));}
        @Bean DataSource dataSource(Owned owned)throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL"),user=System.getenv("NAVIGATION_TEST_DB_USER"),password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("CREATE SCHEMA "+owned.name());}
            var source=new UnpooledDataSource("org.postgresql.Driver",url,user,password);var properties=new Properties();properties.setProperty("currentSchema",owned.name());source.setDriverProperties(properties);
            var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source)throws Exception {
            var factory=new SqlSessionFactoryBean();factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/projects/ProjectMapper.xml"),
                new ClassPathResource("mapper/assignments/AssignmentMapper.xml"),new ClassPathResource("mapper/withdrawals/WithdrawalMapper.xml"),
                new ClassPathResource("mapper/CalculationRuleMapper.xml"),new ClassPathResource("mapper/CodeGroupMapper.xml"),
                new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var config=new org.apache.ibatis.session.Configuration();config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config);return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory){return new SqlSessionTemplate(factory);}
        @Bean WithdrawalMapper withdrawalMapper(SqlSessionTemplate s){return s.getMapper(WithdrawalMapper.class);}
        @Bean AssignmentMapper assignmentMapper(SqlSessionTemplate s){return s.getMapper(AssignmentMapper.class);}
        @Bean ProjectMapper projectMapper(SqlSessionTemplate s){return s.getMapper(ProjectMapper.class);}
        @Bean CalculationRuleMapper calculationRuleMapper(SqlSessionTemplate s){return s.getMapper(CalculationRuleMapper.class);}
        @Bean CodeGroupMapper codeGroupMapper(SqlSessionTemplate s){return s.getMapper(CodeGroupMapper.class);}
        @Bean UserPreferencePeriodSourceMapper userPreferencePeriodSourceMapper(SqlSessionTemplate s){return s.getMapper(UserPreferencePeriodSourceMapper.class);}
        @Bean DataSourceTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
        @Bean BusinessCalendar businessCalendar(){return new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"),ZoneOffset.UTC));}
    }
    @BeforeEach void setup(){
        db=new JdbcTemplate(source);
        job=db.queryForObject("SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB' AND USE_YN='Y' AND DEL_YN='N'",String.class);
        db.update("INSERT INTO TB_BS_PART(PART_ID,PART_NM,HQ_YN,USE_YN,REG_USER_ID) VALUES('withdrawal-part','시험 영업파트','N','Y','admin')");
        db.update("INSERT INTO TB_BS_CLNT(CLNT_ID,CLNT_NM,CLNT_TYPE_CD,BIZ_REG_NO,USE_YN,REG_USER_ID) VALUES('withdrawal-client','시험 고객','CUST','123-45-67890','Y','admin')");
        db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CUST_CLNT_ID,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,ORGNL_CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES('P-WITHDRAWAL','시험 프로젝트','withdrawal-client','MO','ING','2026-01-01','2027-12-31',100000000,100000000,0,0,'admin')");
        for(String person:List.of("withdrawal-person-a","withdrawal-person-b")){
            db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,GRADE_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,REG_USER_ID) VALUES(?,?,?,'FT',?,'MD','DRCT','withdrawal-part','IDLE','admin')",person,person,person,job);
            db.update("INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,CTRT_MM,BILL_UPRC,INPUT_TYPE_CD,EXT_CNT,ASGN_STAT_CD,REG_USER_ID) VALUES(?,'P-WITHDRAWAL',?,?,'2026-01-01','2026-11-30',1,8000000,'NEW',0,'ACT','admin')",person.endsWith("a")?"A-WITHDRAWAL-A":"A-WITHDRAWAL-B",person,job);
        }
        db.update("INSERT INTO TB_SL_OPP(OPP_ID,OPP_NM,CUST_CLNT_ID,PART_ID,SALES_STEP_CD,WIN_PRBL_RT,EXPCT_AMT,BGN_PLAN_YM,OPP_STAT_CD,APRV_STAT_CD,REG_USER_ID) VALUES('O-WITHDRAWAL','시험 기회','withdrawal-client','withdrawal-part','LEAD',60,100000000,'202612','ING','DRAFT','admin')");
        need(1);need(2);
    }
    @AfterEach void cleanup()throws Exception {try(var c=source.getConnection();var statement=c.createStatement()){statement.execute("DROP SCHEMA "+owned.name()+" CASCADE");}}
    void need(int sn){db.update("INSERT INTO TB_SL_OPP_NEED(OPP_ID,NEED_SN,JOB_CD,GRADE_CD,NEED_CNT,BGN_YM,PRD_MCNT,MON_MM,REG_USER_ID) VALUES('O-WITHDRAWAL',?,?,'MD',1,'202612',3,1,'admin')",sn,job);}
    JsonNode getData(String path)throws Exception {return json.readTree(mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("data");}
    void save(String assignment,int sn)throws Exception {mvc.perform(put("/api/common/withdrawals/"+assignment+"/tentative-assignment").contentType("application/json").content("{\"opportunityId\":\"O-WITHDRAWAL\",\"needSn\":"+sn+"}")).andExpect(status().isOk());}
    int count(String sql){return db.queryForObject(sql,Integer.class);}

    @Test void extensionPersistsHistorySharedSourceAndSameValueIsNoOp()throws Exception {
        db.update("UPDATE TB_PJ_ASGN SET EXT_PSBL_CD='H' WHERE ASGN_ID='A-WITHDRAWAL-A'");
        var current=assignments.getCommonAssignment("A-WITHDRAWAL-A");
        var body=json.createObjectNode().put("extensionPossibilityCode","M").put("modifiedAt",current.modifiedAt().toString());
        mvc.perform(put("/api/common/withdrawals/A-WITHDRAWAL-A/extension-possibility").contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(OpenApiResponseContract.response("PUT","/api/common/withdrawals/{assignmentId}/extension-possibility","contracts/openapi.yaml"));
        assertThat(db.queryForObject("SELECT EXT_PSBL_CD FROM TB_PJ_ASGN WHERE ASGN_ID='A-WITHDRAWAL-A'",String.class)).isEqualTo("M");
        var history=assignments.getCommonAssignmentHistory("A-WITHDRAWAL-A",null).items();
        assertThat(history).singleElement().satisfies(h -> {assertThat(h.changeTypeCode()).isEqualTo("EXTP");assertThat(h.reason()).isEmpty();});
        var shared=getData("/api/common/withdrawals/tentative-assignments?developerId=withdrawal-person-a");
        assertThat(shared.path("items").get(0).path("extensionPossibilityCode").asText()).isEqualTo("M");
        body.put("modifiedAt",assignments.getCommonAssignment("A-WITHDRAWAL-A").modifiedAt().toString());
        mvc.perform(put("/api/common/withdrawals/A-WITHDRAWAL-A/extension-possibility").contentType("application/json").content(body.toString())).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM TB_PJ_ASGN_HIST WHERE ASGN_ID='A-WITHDRAWAL-A'")).isEqualTo(1);
    }
    @Test
    @DisplayName("happy auth side-effect: TB_SL_TENT_ASGN 교체·getCommonWithdrawalAssignments 읽기, TB_PJ_ASGN·TB_CO_MON_COST 보존")
    void saveThenReplaceRetainsHistoryAndConfirmedAssignmentsAndMonths()throws Exception {
        var confirmed=db.queryForList("SELECT * FROM TB_PJ_ASGN ORDER BY ASGN_ID");
        var months=db.queryForList("SELECT * FROM TB_PJ_ASGN_MON ORDER BY ASGN_ID,INPUT_YM");
        var cost=db.queryForList("SELECT * FROM TB_CO_MON_COST ORDER BY 1,2");
        mvc.perform(put("/api/common/withdrawals/A-WITHDRAWAL-A/tentative-assignment").contentType("application/json").content("{\"opportunityId\":\"O-WITHDRAWAL\",\"needSn\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.assignment.needSn").value(1));
        mvc.perform(put("/api/common/withdrawals/A-WITHDRAWAL-A/tentative-assignment").contentType("application/json").content("{\"opportunityId\":\"O-WITHDRAWAL\",\"needSn\":2}"))
            .andExpect(status().isOk()).andExpect(OpenApiResponseContract.response("PUT","/api/common/withdrawals/{assignmentId}/tentative-assignment"))
            .andExpect(jsonPath("$.data.assignment.needSn").value(2));
        var current=getData("/api/common/withdrawals/A-WITHDRAWAL-A/tentative-assignment");
        assertThat(current.path("assignment").path("needSn").asInt()).isEqualTo(2);
        assertThat(current.path("assignment").path("startMonth").asText()).isEqualTo("2026-12");
        assertThat(count("SELECT count(*) FROM TB_SL_TENT_ASGN WHERE TENT_STAT_CD='REL' AND NEED_SN=1")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM TB_SL_TENT_ASGN WHERE TENT_STAT_CD='ACT' AND NEED_SN=2")).isEqualTo(1);
        assertThat(db.queryForList("SELECT * FROM TB_PJ_ASGN ORDER BY ASGN_ID")).isEqualTo(confirmed);
        assertThat(db.queryForList("SELECT * FROM TB_PJ_ASGN_MON ORDER BY ASGN_ID,INPUT_YM")).isEqualTo(months);
        assertThat(db.queryForList("SELECT * FROM TB_CO_MON_COST ORDER BY 1,2")).isEqualTo(cost);
        mvc.perform(get("/api/common/withdrawals/tentative-assignments").param("developerId","withdrawal-person-a"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].tentativeAssignment.needSn").value(2));
    }
    @Test void listUsesSetQueriesForReservationsHistoryAndSeatNames()throws Exception {
        save("A-WITHDRAWAL-A",1);
        db.update("INSERT INTO TB_PJ_ASGN_HIST(ASGN_ID,HIST_SN,ASGN_CHG_TYPE_CD,APLY_DT,BFR_VAL,AFT_VAL,RTRO_YN,CLOSE_CROSS_YN,CRCT_YN,REG_USER_ID) VALUES('A-WITHDRAWAL-B',1,'EARLY','2026-10-01','2026-12-31','2026-11-30','N','N','N','admin')");
        var list=getData("/api/common/withdrawals?year=2026");
        var byId=new HashMap<String,JsonNode>();
        list.path("items").forEach(i -> byId.put(i.path("assignmentId").asText(),i));
        assertThat(byId.get("A-WITHDRAWAL-A").path("nextAssignment").path("hasTentativeAssignment").asBoolean()).isTrue();
        assertThat(byId.get("A-WITHDRAWAL-B").path("nextAssignment").path("recommendationCount").asInt()).isEqualTo(1);
        assertThat(byId.get("A-WITHDRAWAL-B").path("earlyWithdrawalHistory").get(0).asText()).isEqualTo("2026-10-01 · 2026-11-30");
        assertThat(list.path("banner").path("soonCount").asInt()).isEqualTo(2);
        assertThat(list.path("banner").path("unassignedCount").asInt()).isEqualTo(1);
        var recommendations=getData("/api/common/withdrawals/A-WITHDRAWAL-B/recommendations");
        assertThat(recommendations.path("items").get(0).path("needSn").asInt()).isEqualTo(2);
        assertThat(recommendations.path("items").get(0).path("customerName").asText()).isEqualTo("시험 고객");
        assertThat(recommendations.path("items").get(0).path("remainingCount").asInt()).isEqualTo(1);
    }
    @Test void failedInsertRollsBackReleaseOfOldReservation()throws Exception {
        save("A-WITHDRAWAL-A",1);
        db.execute("CREATE FUNCTION reject_withdrawal_reservation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.NEED_SN=2 THEN RAISE EXCEPTION 'reservation rejected'; END IF; RETURN NEW; END $$");
        db.execute("CREATE TRIGGER reject_withdrawal_reservation BEFORE INSERT ON TB_SL_TENT_ASGN FOR EACH ROW EXECUTE FUNCTION reject_withdrawal_reservation()");
        assertThatThrownBy(() -> tentative.save("A-WITHDRAWAL-A",json.createObjectNode().put("opportunityId","O-WITHDRAWAL").put("needSn",2))).isInstanceOf(RuntimeException.class);
        assertThat(count("SELECT count(*) FROM TB_SL_TENT_ASGN WHERE TENT_STAT_CD='ACT' AND NEED_SN=1")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM TB_SL_TENT_ASGN WHERE TENT_STAT_CD='REL'")).isZero();
    }
    List<Integer> concurrent(String first,int firstNeed,String second,int secondNeed)throws Exception {
        var executor=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try {
            var calls=new ArrayList<Future<Integer>>();
            for(var entry:List.of(Map.entry(first,firstNeed),Map.entry(second,secondNeed))){calls.add(executor.submit(() -> {
                ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("start not released");
                try{tentative.save(entry.getKey(),json.createObjectNode().put("opportunityId","O-WITHDRAWAL").put("needSn",entry.getValue()));return 200;}
                catch(CodeFailure failure){return failure.getStatus();}
            }));}
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            return List.of(calls.get(0).get(30,TimeUnit.SECONDS),calls.get(1).get(30,TimeUnit.SECONDS));
        } finally {start.countDown();executor.shutdownNow();}
    }
    @Test void concurrentLastSeatHasOneWinnerAndNoOverCapacity()throws Exception {
        assertThat(concurrent("A-WITHDRAWAL-A",1,"A-WITHDRAWAL-B",1)).containsExactlyInAnyOrder(200,409);
        assertThat(count("SELECT count(*) FROM TB_SL_TENT_ASGN WHERE NEED_SN=1 AND TENT_STAT_CD IN ('ACT','CNVT')")).isEqualTo(1);
    }
    @Test void firstReservationRacesForSamePersonLeaveOnlyOneCurrentReservation()throws Exception {
        assertThat(concurrent("A-WITHDRAWAL-A",1,"A-WITHDRAWAL-A",2)).containsExactly(200,200);
        assertThat(count("SELECT count(*) FROM TB_SL_TENT_ASGN WHERE DEV_ID='withdrawal-person-a' AND TENT_STAT_CD IN ('ACT','CNVT')")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM TB_SL_TENT_ASGN WHERE TENT_STAT_CD='REL'")).isEqualTo(1);
    }

    /** 엑셀을 받으면 실제 DB에 DNLD 기록이 한 줄 남는다(읽기 전용 트랜잭션 안에서 쓰기가 막히던 결함, 2026-11-20). */
    @Test void excelDownloadWritesExactlyOneDownloadAccessRow()throws Exception {
        String dnld="SELECT count(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-05' AND ACT_TYPE_CD='DNLD'";
        assertThat(count(dnld)).isZero();
        mvc.perform(get("/api/common/withdrawals/excel").param("year","2026")).andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count","2"));
        assertThat(count(dnld)).isEqualTo(1);
        var row=db.queryForMap("SELECT USER_ID,TRGT_DESC,ACT_DTTM FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-05' AND ACT_TYPE_CD='DNLD'");
        assertThat(row.get("trgt_desc")).isEqualTo("철수 리스트 2026년 2건");
        assertThat(row.get("act_dttm")).isNotNull();
    }
}
