package com.company.common.assignments.service;

import com.company.common.assignments.controller.CommonAssignmentController;
import com.company.common.assignments.mapper.AssignmentMapper;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.developers.service.DeveloperManMonthRules;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.projects.service.ProjectAmountRules;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
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
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="NAVIGATION_TEST_DB_URL",matches="jdbc:postgresql:.*")
@WebMvcTest(CommonAssignmentController.class)
@ContextConfiguration(classes={CommonAssignmentController.class,CommonAssignmentService.class,AssignmentMonthRules.class,
    DeveloperManMonthRules.class,ProjectAmountRules.class,CalculationRuleService.class,CurrentBusinessActor.class,
    ExcelExportService.class,CodeExceptionHandler.class,ApiExceptionHandler.class,AssignmentPersistenceTest.Persistence.class})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AssignmentPersistenceTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired DataSource source;@Autowired Owned owned;@Autowired CommonAssignmentService assignments;
    @Autowired ProjectMapper projects;@Autowired AssignmentMapper assignmentMapper;@Autowired CalculationRuleMapper calculationRules;
    JdbcTemplate db;String job;
    record Owned(String name) {}
    @TestConfiguration(proxyBeanMethods=false) @EnableTransactionManagement
    static class Persistence {
        @Bean Owned owned(){return new Owned("assignment_"+UUID.randomUUID().toString().replace("-",""));}
        @Bean DataSource dataSource(Owned owned)throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL"),user=System.getenv("NAVIGATION_TEST_DB_USER"),password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("CREATE SCHEMA "+owned.name());}
            var source=new UnpooledDataSource("org.postgresql.Driver",url,user,password);var props=new Properties();props.setProperty("currentSchema",owned.name());source.setDriverProperties(props);
            var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source)throws Exception {
            var f=new SqlSessionFactoryBean();f.setDataSource(source);f.setMapperLocations(new ClassPathResource("mapper/projects/ProjectMapper.xml"),new ClassPathResource("mapper/assignments/AssignmentMapper.xml"),new ClassPathResource("mapper/CalculationRuleMapper.xml"),new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var c=new org.apache.ibatis.session.Configuration();c.setMapUnderscoreToCamelCase(false);c.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);f.setConfiguration(c);return f.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory f){return new SqlSessionTemplate(f);}
        @Bean ProjectMapper projectMapper(SqlSessionTemplate s){return s.getMapper(ProjectMapper.class);}
        @Bean AssignmentMapper assignmentMapper(SqlSessionTemplate s){return s.getMapper(AssignmentMapper.class);}
        @Bean CalculationRuleMapper calculationRuleMapper(SqlSessionTemplate s){return s.getMapper(CalculationRuleMapper.class);}
        @Bean UserPreferencePeriodSourceMapper userPreferencePeriodSourceMapper(SqlSessionTemplate s){return s.getMapper(UserPreferencePeriodSourceMapper.class);}
        @Bean DataSourceTransactionManager transactionManager(DataSource d){return new DataSourceTransactionManager(d);}
        @Bean BusinessCalendar businessCalendar(){return new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"),ZoneOffset.UTC));}
    }
    @BeforeEach void setup() {
        db=new JdbcTemplate(source);job=db.queryForObject("SELECT min(code) FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB' AND USE_YN='Y' AND DEL_YN='N'",String.class);
        db.update("INSERT INTO TB_BS_PART(PART_ID,PART_NM,HQ_YN,USE_YN,REG_USER_ID) VALUES('assignment-part','시험 영업파트','N','Y','admin')");
        db.update("INSERT INTO TB_BS_CLNT(CLNT_ID,CLNT_NM,CLNT_TYPE_CD,BIZ_REG_NO,USE_YN,REG_USER_ID) VALUES('assignment-customer','시험 고객','CUST','123-45-67890','Y','admin')");
        db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CUST_CLNT_ID,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,ORGNL_CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES('P-TEST','시험 프로젝트','assignment-customer','MO','ING','2026-01-01','2027-12-31',100000000,100000000,0,0,'admin')");
        db.update("INSERT INTO TB_PJ_PROJ_PART(PROJ_ID,APLY_BGN_YM,PART_ID,REG_USER_ID) VALUES('P-TEST','202601','assignment-part','admin')");
        for(String id:List.of("assignment-dev-a","assignment-dev-b")) {
            db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,REG_USER_ID) SELECT ?,?,?,'FT',?,(SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='DRCT_CLS'),'assignment-part','IDLE','admin'",id,id,id,job);
            db.update("INSERT INTO TB_HR_DEV_PRC(DEV_ID,APLY_BGN_YM,MON_UPRC,REG_USER_ID) VALUES(?,'202601',8000000,'admin')",id);
        }
    }
    @AfterEach void cleanup()throws Exception {try(var c=source.getConnection();var s=c.createStatement()){s.execute("DROP SCHEMA "+owned.name()+" CASCADE");}}
    ObjectNode input(String dev){return json.createObjectNode().put("projectId","P-TEST").put("developerId",dev).put("jobCode",job).put("startDate","2026-10-16").put("endDate","2026-12-31").put("monthlyMm",1).put("inputTypeCode","NEW").put("billingPrice",8000000);}
    JsonNode data(ResultActions result)throws Exception{return json.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");}
    JsonNode create(String dev)throws Exception{return data(mvc.perform(post("/api/common/assignments").contentType("application/json").content(input(dev).toString())).andExpect(status().isOk()));}
    JsonNode detail(String id)throws Exception{return data(mvc.perform(get("/api/common/assignments/"+id)).andExpect(status().isOk()));}
    ObjectNode version(JsonNode d){var b=json.createObjectNode();if(d.path("modifiedAt").isNull())b.putNull("modifiedAt");else b.put("modifiedAt",d.path("modifiedAt").asText());return b;}
    ObjectNode bulk(JsonNode... rows){var b=json.createObjectNode();var a=b.putArray("assignments");for(var r:rows){var v=version(r);v.put("assignmentId",r.path("assignmentId").asText());a.add(v);}return b;}
    long count(String table){return db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    Object snapshot(String table,String id){return db.queryForList("SELECT * FROM "+table+" WHERE ASGN_ID=? ORDER BY 1,2",id);}
    String reason(){return db.queryForObject("SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='ASGN_CHG_RSN'",String.class);}
    @Test void createMaterializesRowsHistoryAndYearMmThroughRealPostgresql()throws Exception {
        var created=create("assignment-dev-a");String id=created.path("assignmentId").asText();assertThat(id).matches("A-[0-9]{4,}");
        assertThat(detail(id).path("monthlyMm").decimalValue()).isEqualByComparingTo("1");assertThat(detail(id).path("yearMm").decimalValue()).isEqualByComparingTo("2.52");
        assertThat(db.queryForObject("SELECT MON_MM FROM TB_PJ_ASGN_MON WHERE ASGN_ID=? AND INPUT_YM='202610'",java.math.BigDecimal.class,id)).isEqualByComparingTo("0.52");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_PJ_ASGN_HIST WHERE ASGN_ID=? AND ASGN_CHG_TYPE_CD='INPUT' AND NEW_UPRC=8000000 AND NEW_CTRT_MM=1",Long.class,id)).isEqualTo(1);
    }
    @Test void futureHistoryPreservesPriorAmountsAndCorrectionCannotChangeNumericEffect()throws Exception {
        var row=create("assignment-dev-a");String id=row.path("assignmentId").asText();Object before=db.queryForList("SELECT * FROM TB_PJ_ASGN_MON WHERE ASGN_ID=? AND INPUT_YM<'202612' ORDER BY INPUT_YM",id);
        var b=version(row).put("changeTypeCode","UPRC").put("effectiveDate","2026-12-15").put("newBillingPrice",8500000);
        mvc.perform(post("/api/common/assignments/"+id+"/changes").contentType("application/json").content(b.toString())).andExpect(status().isOk());
        assertThat(detail(id).path("billingPrice").decimalValue()).isEqualByComparingTo("8000000");
        assertThat(db.queryForList("SELECT * FROM TB_PJ_ASGN_MON WHERE ASGN_ID=? AND INPUT_YM<'202612' ORDER BY INPUT_YM",id)).isEqualTo(before);
        assertThat(db.queryForObject("SELECT APLY_UPRC FROM TB_PJ_ASGN_MON WHERE ASGN_ID=? AND INPUT_YM='202612'",java.math.BigDecimal.class,id)).isEqualByComparingTo("8500000");
        var h=data(mvc.perform(get("/api/common/assignments/"+id+"/history").param("changeTypeCode","UPRC")).andExpect(status().isOk())).path("items").get(0);Object state=snapshot("TB_PJ_ASGN",id),monthly=snapshot("TB_PJ_ASGN_MON",id);
        assertThat(h.path("beforeValue").asText()).isEqualTo("8,000,000");assertThat(h.path("afterValue").asText()).isEqualTo("8,500,000");
        var correction=version(h).put("correctedValue","표시 내용 정정").put("reason","오타 정정");
        mvc.perform(post("/api/common/assignments/"+id+"/history/"+h.path("historyId").asText()+"/corrections").contentType("application/json").content(correction.toString())).andExpect(status().isOk());
        assignments.refreshCurrentMonth();assertThat(snapshot("TB_PJ_ASGN",id)).isEqualTo(state);assertThat(snapshot("TB_PJ_ASGN_MON",id)).isEqualTo(monthly);
        assertThat(db.queryForObject("SELECT CRCT_YN FROM TB_PJ_ASGN_HIST WHERE ASGN_ID=? AND HIST_SN=?",String.class,id,Integer.parseInt(h.path("historyId").asText()))).isEqualTo("Y");
    }
    @Test void closedSnapshotsSurviveChangeAndCancellationAndNoAdjustmentIsGenerated()throws Exception {
        var row=create("assignment-dev-a");String id=row.path("assignmentId").asText();
        db.update("UPDATE TB_PJ_ASGN_MON SET FIX_YN='Y' WHERE ASGN_ID=? AND INPUT_YM='202610'",id);
        var closedMonth=db.queryForList("SELECT * FROM TB_PJ_ASGN_MON WHERE ASGN_ID=? AND INPUT_YM='202610'",id);
        var b=version(row).put("changeTypeCode","RATE").put("effectiveDate","2026-10-01").put("newMonthlyMm",0.8).put("reasonCode",reason());
        mvc.perform(post("/api/common/assignments/"+id+"/changes").contentType("application/json").content(b.toString())).andExpect(status().isOk());
        mvc.perform(post("/api/common/assignments/cancel").contentType("application/json").content(bulk(detail(id)).toString())).andExpect(status().isOk());
        assertThat(db.queryForList("SELECT * FROM TB_PJ_ASGN_MON WHERE ASGN_ID=? AND INPUT_YM='202610'",id)).isEqualTo(closedMonth);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_PJ_ASGN_MON WHERE ASGN_ID=? AND INPUT_YM>'202610'",Long.class,id)).isZero();assertThat(count("TB_CO_MON_COST")).isZero();assertThat(db.queryForObject("SELECT ASGN_STAT_CD FROM TB_PJ_ASGN WHERE ASGN_ID=?",String.class,id)).isEqualTo("CNCL");
    }
    @Test void overloadWarningOnlyCountsMonthsTheAssignmentCovers()throws Exception {
        String first=data(mvc.perform(post("/api/common/assignments").contentType("application/json").content(input("assignment-dev-a").put("startDate","2026-10-01").put("endDate","2026-11-30").toString())).andExpect(status().isOk())).path("assignmentId").asText();
        String second=data(mvc.perform(post("/api/common/assignments").contentType("application/json").content(input("assignment-dev-a").put("startDate","2026-11-01").put("endDate","2026-11-30").put("monthlyMm",0.5).toString())).andExpect(status().isOk())).path("assignmentId").asText();
        String third=data(mvc.perform(post("/api/common/assignments").contentType("application/json").content(input("assignment-dev-a").put("startDate","2026-12-01").put("endDate","2026-12-31").toString())).andExpect(status().isOk())).path("assignmentId").asText();
        var items=data(mvc.perform(get("/api/common/assignments").param("year","2026")).andExpect(status().isOk())).path("items");
        var byId=new HashMap<String,JsonNode>();items.forEach(i -> byId.put(i.path("assignmentId").asText(),i));
        assertThat(byId.get(first).path("warnings").toString()).contains("MM 1.0 초과");
        assertThat(byId.get(second).path("monthlyMmTotal").decimalValue()).isEqualByComparingTo("1.5");
        assertThat(byId.get(third).path("warnings").toString()).doesNotContain("MM 1.0 초과");
        assertThat(byId.get(third).path("monthlyMmTotal").decimalValue()).isEqualByComparingTo("1");
        assertThat(byId.get(first).path("historyCount").asInt()).isEqualTo(1);
        assertThat(count("TB_CO_MON_COST")).isZero();
    }
    @Test void staleBulkLaterRowRollsBackEntireRequest()throws Exception {
        var first=create("assignment-dev-a");var second=create("assignment-dev-b");Object one=snapshot("TB_PJ_ASGN",first.path("assignmentId").asText());Object two=snapshot("TB_PJ_ASGN",second.path("assignmentId").asText());long history=count("TB_PJ_ASGN_HIST");
        var b=bulk(first,second);((ObjectNode)b.path("assignments").get(1)).put("modifiedAt","2000-01-01T00:00:00Z");b.put("extensionPossibilityCode","H");mvc.perform(put("/api/common/assignments/extension-possibility").contentType("application/json").content(b.toString())).andExpect(status().isConflict());
        assertThat(snapshot("TB_PJ_ASGN",first.path("assignmentId").asText())).isEqualTo(one);assertThat(snapshot("TB_PJ_ASGN",second.path("assignmentId").asText())).isEqualTo(two);assertThat(count("TB_PJ_ASGN_HIST")).isEqualTo(history);
    }
    @Test void downstreamCalculationFailureRollsBackAssignmentAndHistory()throws Exception {
        var row=create("assignment-dev-a");String id=row.path("assignmentId").asText();Object before=snapshot("TB_PJ_ASGN",id);Object monthly=snapshot("TB_PJ_ASGN_MON",id);long history=count("TB_PJ_ASGN_HIST");
        db.update("UPDATE TB_SY_CALC_RULE SET FRML_CN='unsupported definition' WHERE CALC_ID='식-01'");
        var b=version(row).put("changeTypeCode","RATE").put("effectiveDate","2026-12-01").put("newMonthlyMm",0.8);
        mvc.perform(post("/api/common/assignments/"+id+"/changes").contentType("application/json").content(b.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("calculation"));
        assertThat(snapshot("TB_PJ_ASGN",id)).isEqualTo(before);assertThat(snapshot("TB_PJ_ASGN_MON",id)).isEqualTo(monthly);assertThat(count("TB_PJ_ASGN_HIST")).isEqualTo(history);
    }
    @Test void excelAuditCommitsAfterWorkbookGeneration()throws Exception {create("assignment-dev-a");mvc.perform(get("/api/common/assignments/excel").param("year","2026")).andExpect(status().isOk());assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-12' AND ACT_TYPE_CD='DNLD'",Long.class)).isEqualTo(1);}
    @Test void changedDatabaseRoundingIsConsumedAndPreparationNeverOverwritesIt() throws Exception {
        db.update("UPDATE TB_SY_CALC_RULE SET FRML_CN=replace(FRML_CN,'2자리','1자리') WHERE CALC_ID='식-58'");
        var definitions=db.queryForList("SELECT * FROM TB_SY_CALC_RULE ORDER BY CALC_ID");
        var factory=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment(
            "postgresql",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),source));
        new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(factory)).prepare();
        var row=create("assignment-dev-a");
        assertThat(db.queryForObject(
            "SELECT MON_MM FROM TB_PJ_ASGN_MON WHERE ASGN_ID=? AND INPUT_YM='202610'",
            java.math.BigDecimal.class,row.path("assignmentId").asText())).isEqualByComparingTo("0.50");
        assertThat(db.queryForList("SELECT * FROM TB_SY_CALC_RULE ORDER BY CALC_ID")).isEqualTo(definitions);
    }
    @Test void staleDetailVersionCannotOverwriteTheLatestAssignmentOrItsHistory() throws Exception {
        var original=create("assignment-dev-a");
        String id=original.path("assignmentId").asText();
        String extension=db.queryForObject(
            "SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='EXT_PSBL' AND USE_YN='Y' AND DEL_YN='N'",
            String.class);
        var body=bulk(original).put("extensionPossibilityCode",extension);
        mvc.perform(put("/api/common/assignments/extension-possibility")
            .contentType("application/json").content(body.toString())).andExpect(status().isOk());
        var latest=detail(id);
        assertThat(Instant.parse(latest.path("modifiedAt").asText()))
            .isNotEqualTo(Instant.parse(original.path("modifiedAt").asText()));
        var assignment=snapshot("TB_PJ_ASGN",id);
        var history=snapshot("TB_PJ_ASGN_HIST",id);
        var monthly=snapshot("TB_PJ_ASGN_MON",id);
        mvc.perform(post("/api/common/assignments/cancel")
            .contentType("application/json").content(bulk(original).toString()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.errors[0].field").value("modifiedAt"))
            .andExpect(jsonPath("$.errors[0].message")
                .value("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."));
        assertThat(snapshot("TB_PJ_ASGN",id)).isEqualTo(assignment);
        assertThat(snapshot("TB_PJ_ASGN_HIST",id)).isEqualTo(history);
        assertThat(snapshot("TB_PJ_ASGN_MON",id)).isEqualTo(monthly);
    }
    @Test void assignmentAndHistoryDisplayNamesComeFromTheActiveCommonCodes() throws Exception {
        String jobName=db.queryForObject(
            "SELECT CODE_NM FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB' AND CODE=?",String.class,job);
        var row=create("assignment-dev-a");
        String id=row.path("assignmentId").asText();
        String inputName=db.queryForObject(
            "SELECT CODE_NM FROM TB_SY_CODE WHERE CODE_GRP_ID='INPUT_TYPE' AND CODE=?",
            String.class,row.path("inputTypeCode").asText());
        assertThat(row.path("jobName").asText()).isEqualTo(jobName);
        assertThat(row.path("inputTypeName").asText()).isEqualTo(inputName);
        String reasonCode=reason();
        var change=version(row).put("changeTypeCode","EARLY").put("effectiveDate","2026-11-15")
            .put("newEndDate","2026-11-30").put("reasonCode",reasonCode);
        mvc.perform(post("/api/common/assignments/"+id+"/changes")
            .contentType("application/json").content(change.toString())).andExpect(status().isOk());
        var history=data(mvc.perform(get("/api/common/assignments/"+id+"/history")
            .param("changeTypeCode","EARLY")).andExpect(status().isOk())).path("items").get(0);
        assertThat(history.path("reason").asText()).isEqualTo(db.queryForObject(
            "SELECT CODE_NM FROM TB_SY_CODE WHERE CODE_GRP_ID='ASGN_CHG_RSN' AND CODE=?",
            String.class,reasonCode));
        assertThat(history.path("changeTypeName").asText()).isEqualTo(db.queryForObject(
            "SELECT CODE_NM FROM TB_SY_CODE WHERE CODE_GRP_ID='ASGN_CHG_TYPE' AND CODE='EARLY'",
            String.class));
    }
    @Test void requiredCreationPropertiesCannotDefaultOrWriteHistory() throws Exception {
        for(String field:List.of("monthlyMm","inputTypeCode")) {
            var b=input("assignment-dev-a");b.remove(field);
            long assignments=count("TB_PJ_ASGN"),events=count("TB_PJ_ASGN_HIST");
            mvc.perform(post("/api/common/assignments").contentType("application/json").content(b.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
            assertThat(count("TB_PJ_ASGN")).isEqualTo(assignments);
            assertThat(count("TB_PJ_ASGN_HIST")).isEqualTo(events);
        }
    }
    @Test void equalStartContractPersonCanBeAssignedWithoutWithdrawingTheOriginal() throws Exception {
        var original=create("assignment-dev-b");String id=original.path("assignmentId").asText();
        var before=snapshot("TB_PJ_ASGN",id);
        var people=data(mvc.perform(get("/api/common/assignments/contract-developers")
            .param("projectId","P-TEST").param("developerId","assignment-dev-a").param("startDate","2026-10-16"))
            .andExpect(status().isOk())).path("items");
        assertThat(people.size()).isEqualTo(1);
        assertThat(people.get(0).path("overlappingAssignments").isEmpty()).isTrue();
        var b=input("assignment-dev-a").put("inputTypeCode","SUB")
            .put("contractDeveloperId","assignment-dev-b").put("replacementAction","assignment-only");
        mvc.perform(post("/api/common/assignments").contentType("application/json").content(b.toString()))
            .andExpect(status().isOk());
        assertThat(snapshot("TB_PJ_ASGN",id)).isEqualTo(before);
        assertThat(count("TB_PJ_ASGN")).isEqualTo(2);
    }
    @Test void equalStartTogetherCannotCreateAnInvalidOriginalPeriod() throws Exception {
        var original=create("assignment-dev-b");String id=original.path("assignmentId").asText();
        var before=snapshot("TB_PJ_ASGN",id);long events=count("TB_PJ_ASGN_HIST");
        var b=input("assignment-dev-a").put("inputTypeCode","SUB")
            .put("contractDeveloperId","assignment-dev-b").put("replacementAction","together").put("reasonCode",reason());
        b.putArray("earlyWithdrawalAssignmentIds").add(id);
        mvc.perform(post("/api/common/assignments").contentType("application/json").content(b.toString()))
            .andExpect(status().isBadRequest());
        assertThat(snapshot("TB_PJ_ASGN",id)).isEqualTo(before);
        assertThat(count("TB_PJ_ASGN")).isEqualTo(1);
        assertThat(count("TB_PJ_ASGN_HIST")).isEqualTo(events);
    }
    @Test void turnkeyReplacementKeepsDistributionAndReturnsTheExactReuploadNotice() throws Exception {
        db.update("UPDATE TB_PJ_PROJ SET CTRT_TYPE_CD='TK' WHERE PROJ_ID='P-TEST'");
        create("assignment-dev-b");
        db.update("INSERT INTO TB_PJ_TK_DSTRB(PROJ_ID,DSTRB_STEP_CD,ROW_SN,DSTRB_YM,ROW_TYPE_CD,DEV_ID,DSTRB_AMT,REG_USER_ID) VALUES('P-TEST','PLAN',1,'202611','DEV','assignment-dev-b',1000000,'admin')");
        var before=db.queryForList("SELECT * FROM TB_PJ_TK_DSTRB ORDER BY ROW_SN,DSTRB_YM");
        var b=input("assignment-dev-a").put("inputTypeCode","SUB").put("contractDeveloperId","assignment-dev-b")
            .put("replacementAction","assignment-only");
        var created=data(mvc.perform(post("/api/common/assignments").contentType("application/json").content(b.toString()))
            .andExpect(status().isOk()));
        String notice="턴키 배분표는 다시 옮겨 올려 주셔야 합니다. 그 전까지는 배분 불일치 경고가 뜹니다.";
        assertThat(created.path("warnings").toString()).contains(notice);
        assertThat(detail(created.path("assignmentId").asText()).path("warnings").toString()).contains(notice);
        assertThat(db.queryForList("SELECT * FROM TB_PJ_TK_DSTRB ORDER BY ROW_SN,DSTRB_YM")).isEqualTo(before);
    }
    /** 진짜 DB 공통코드로: 사람이 적는 「높음·보통·낮음」이 「높음 (80%)」 등 EXT_PSBL 코드로 읽힌다. */
    @Test void uploadExtensionTextsResolveAgainstRealCommonCodes() throws Exception {
        var resolver=new com.company.common.codegroups.service.CodeTextResolver(calculationRules);
        var expected=new LinkedHashMap<String,String>();
        for(var row:db.queryForList("SELECT CODE,CODE_NM FROM TB_SY_CODE WHERE CODE_GRP_ID='EXT_PSBL' AND USE_YN='Y' AND DEL_YN='N' ORDER BY SORT_ORD"))
            expected.put(((String)row.get("CODE_NM")).replaceAll("\\s*\\([^()]*\\)\\s*$",""),(String)row.get("CODE"));
        assertThat(expected.keySet()).containsExactly("높음","보통","낮음");
        for(var entry:expected.entrySet()) {
            var resolved=resolver.resolveLabel("EXT_PSBL",entry.getKey());
            assertThat(resolved.code()).isEqualTo(entry.getValue());assertThat(resolved.warning()).isNull();
        }
        var form=new AssignmentUploadForm(assignments,projects,assignmentMapper,resolver);
        var workbook=new com.company.common.uploads.service.UploadWorkbookService(new ExcelExportService());
        var rows=new ArrayList<List<String>>();
        for(String text:List.of("높음","보통","낮음","미정",""))
            rows.add(List.of("P-TEST","assignment-dev-a",job,"2026-11-16","2026-12-31","","8000000","신규","",text));
        byte[] bytes=new ExcelExportService().write("투입 배정",form.headers(),rows,"admin",Instant.EPOCH);
        var report=form.inspect(workbook.readTabular("ext.xlsx",bytes,form.headers()),null);
        assertThat(report.errorCount()).as(report.issues().toString()).isZero();
        assertThat(report.records()).extracting(r -> ((AssignmentUploadForm.UploadRow)r).extension())
            .containsExactly(expected.get("높음"),expected.get("보통"),expected.get("낮음"),"","");
    }
    void candidateSource(String tentId,String developer) {
        if(db.queryForObject("SELECT COUNT(*) FROM TB_SL_OPP WHERE OPP_ID='O-TEST'",Long.class)==0) {
            db.update("INSERT INTO TB_SL_OPP(OPP_ID,OPP_NM,CUST_CLNT_ID,PART_ID,SALES_STEP_CD,WIN_PRBL_RT,EXPCT_AMT,BGN_PLAN_YM,OPP_STAT_CD,APRV_STAT_CD,PROJ_ID,REG_USER_ID) VALUES('O-TEST','시험 기회','assignment-customer','assignment-part','PRE',90,1000000,'202611','WIN','NONE','P-TEST','admin')");
            db.update("INSERT INTO TB_SL_OPP_NEED(OPP_ID,NEED_SN,JOB_CD,NEED_CNT,BGN_YM,PRD_MCNT,MON_MM,REG_USER_ID) VALUES('O-TEST',1,?,2,'202611',1,1,'admin')",job);
        }
        db.update("INSERT INTO TB_SL_TENT_ASGN(TENT_ID,OPP_ID,NEED_SN,DEV_ID,BGN_YM,TENT_STAT_CD,SRC_SCRN_ID,REG_USER_ID) VALUES(CAST(? AS numeric),'O-TEST',1,?,'202611','CNVT','UI-29','admin')",tentId,developer);
    }
    /** [빼기]: 목록의 modifiedAt과 다르면 409, 맞으면 뺌, 이미 뺀 후보를 또 빼면 409. 간접 인력 후보는 목록에 없다. */
    @Test void candidateRemovalChecksVersionAndStatusAndIndirectPeopleAreNotCandidates() throws Exception {
        db.update("UPDATE TB_HR_DEV SET DRCT_CLS_CD='INDRCT' WHERE DEV_ID='assignment-dev-b'");
        candidateSource("901","assignment-dev-a");candidateSource("902","assignment-dev-b");
        var items=data(mvc.perform(get("/api/common/assignments/candidates")).andExpect(status().isOk())).path("items");
        assertThat(items.size()).isEqualTo(1);assertThat(items.get(0).path("candidateId").asText()).isEqualTo("901");
        String version=items.get(0).path("modifiedAt").asText();assertThat(version).isNotBlank();
        mvc.perform(post("/api/common/assignments/candidates/901/remove").contentType("application/json").content("{\"modifiedAt\":\"2000-01-01T00:00:00Z\"}")).andExpect(status().isConflict());
        assertThat(db.queryForObject("SELECT TENT_STAT_CD FROM TB_SL_TENT_ASGN WHERE TENT_ID=901",String.class)).isEqualTo("CNVT");
        mvc.perform(post("/api/common/assignments/candidates/901/remove").contentType("application/json").content(json.createObjectNode().put("modifiedAt",version).toString())).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT TENT_STAT_CD FROM TB_SL_TENT_ASGN WHERE TENT_ID=901",String.class)).isEqualTo("DROP");
        mvc.perform(post("/api/common/assignments/candidates/901/remove")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.errors[0].message").value("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."));
        var indirect=input("assignment-dev-b");
        mvc.perform(post("/api/common/assignments").contentType("application/json").content(indirect.toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("developerId"));
        assertThat(count("TB_PJ_ASGN")).isZero();
    }
    /** 시안대로 [확정] 창에서 다른 개발자로 바꿔 저장해도 배정되고, 그 후보는 확정되어 빠진다(사용자 결정 2026-11-18). */
    @Test void confirmingACandidateWithAnotherDeveloperSavesAndConfirmsTheCandidate() throws Exception {
        candidateSource("903","assignment-dev-a");
        var b=input("assignment-dev-b").put("candidateId","903");
        mvc.perform(post("/api/common/assignments").contentType("application/json").content(b.toString())).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT TENT_STAT_CD FROM TB_SL_TENT_ASGN WHERE TENT_ID=903",String.class)).isEqualTo("DONE");
        assertThat(db.queryForObject("SELECT DEV_ID FROM TB_PJ_ASGN",String.class)).isEqualTo("assignment-dev-b");
        mvc.perform(post("/api/common/assignments").contentType("application/json").content(input("assignment-dev-a").put("candidateId","903").toString())).andExpect(status().isConflict());
    }
    @Test void projectPersonnelCountsAndDistributionWarningsReadTheSameAssignmentsAfterSaving() throws Exception {
        db.update("UPDATE TB_PJ_PROJ SET CTRT_TYPE_CD='TK' WHERE PROJ_ID='P-TEST'");
        db.update("INSERT INTO TB_PJ_TK_DSTRB(PROJ_ID,DSTRB_STEP_CD,ROW_SN,DSTRB_YM,ROW_TYPE_CD,DEV_ID,DSTRB_AMT,REG_USER_ID) VALUES('P-TEST','PLAN',1,'202611','DEV','assignment-dev-b',1000000,'admin')");
        var distribution=db.queryForList("SELECT * FROM TB_PJ_TK_DSTRB ORDER BY ROW_SN,DSTRB_YM");
        assertThat(projects.mismatches("P-TEST")).anySatisfy(warning -> {
            assertThat(warning.name).isEqualTo("assignment-dev-b");
            assertThat(warning.kind).isEqualTo("배정 없음");
        });
        var first=create("assignment-dev-a");
        assertThat(projects.selectProject("P-TEST",LocalDate.of(2026,11,15),"202611").assignedPeopleCount)
            .isEqualTo(1);
        assertThat(projects.assignments("P-TEST")).extracting(person -> person.name)
            .containsExactly("assignment-dev-a");
        assertThat(projects.mismatches("P-TEST")).anySatisfy(warning -> {
            assertThat(warning.name).isEqualTo("assignment-dev-a");
            assertThat(warning.kind).isEqualTo("배분표 없음");
        });
        create("assignment-dev-b");
        assertThat(projects.selectProject("P-TEST",LocalDate.of(2026,11,15),"202611").assignedPeopleCount)
            .isEqualTo(2);
        assertThat(projects.mismatches("P-TEST"))
            .noneSatisfy(warning -> assertThat(warning.kind).isEqualTo("배정 없음"));
        mvc.perform(post("/api/common/assignments/cancel")
            .contentType("application/json").content(bulk(first).toString())).andExpect(status().isOk());
        assertThat(projects.selectProject("P-TEST",LocalDate.of(2026,11,15),"202611").assignedPeopleCount)
            .isEqualTo(1);
        assertThat(projects.assignments("P-TEST")).extracting(person -> person.name)
            .containsExactly("assignment-dev-b");
        assertThat(projects.mismatches("P-TEST"))
            .noneSatisfy(warning -> assertThat(warning.name).isEqualTo("assignment-dev-a"));
        assertThat(db.queryForList("SELECT * FROM TB_PJ_TK_DSTRB ORDER BY ROW_SN,DSTRB_YM"))
            .isEqualTo(distribution);
    }
}
