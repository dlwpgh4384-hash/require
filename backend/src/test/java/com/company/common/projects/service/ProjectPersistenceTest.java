package com.company.common.projects.service;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.codegroups.service.CodeTextResolver;
import com.company.common.projects.service.ProjectAmountRules;
import com.company.common.projects.controller.CommonProjectController;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.support.service.*;
import com.company.common.uploads.controller.CommonUploadController;
import com.company.common.uploads.mapper.UploadMapper;
import com.company.common.uploads.service.*;
import com.company.common.users.mapper.UserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="NAVIGATION_TEST_DB_URL",matches="jdbc:postgresql:.*")
@WebMvcTest({CommonProjectController.class,CommonUploadController.class})
@ContextConfiguration(classes={CommonProjectController.class,CommonUploadController.class,CommonProjectService.class,
    CommonUploadService.class,ProjectUploadForm.class,ProjectDistributionUploadForm.class,ProjectProgressRules.class,com.company.common.common.ProjectBoardSignalRules.class,
    ProjectAmountRules.class,CalculationRuleService.class,CodeTextResolver.class,CurrentBusinessActor.class,
    UploadWorkbookService.class,ExcelExportService.class,CodeExceptionHandler.class,ApiExceptionHandler.class,
    ProjectPersistenceTest.Persistence.class})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProjectPersistenceTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired DataSource source;
    @Autowired Owned owned; @Autowired UploadWorkbookService workbooks;
    @Autowired CommonProjectService projects;
    JdbcTemplate db;
    record Owned(String name,Path directory) {}
    @TestConfiguration(proxyBeanMethods=false) @EnableTransactionManagement
    static class Persistence {
        @Bean Owned owned() { String name="project_"+UUID.randomUUID().toString().replace("-","");return new Owned(name,Path.of(System.getProperty("java.io.tmpdir"),name)); }
        @Bean DataSource dataSource(Owned owned) throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL"),user=System.getenv("NAVIGATION_TEST_DB_USER"),password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("CREATE SCHEMA "+owned.name());}
            var source=new UnpooledDataSource("org.postgresql.Driver",url,user,password);var props=new Properties();props.setProperty("currentSchema",owned.name());source.setDriverProperties(props);
            var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var f=new SqlSessionFactoryBean();f.setDataSource(source);
            f.setMapperLocations(new ClassPathResource("mapper/projects/ProjectMapper.xml"),new ClassPathResource("mapper/uploads/UploadMapper.xml"),new ClassPathResource("mapper/UserMapper.xml"),new ClassPathResource("mapper/CalculationRuleMapper.xml"),new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var c=new org.apache.ibatis.session.Configuration();c.setMapUnderscoreToCamelCase(false);c.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);f.setConfiguration(c);return f.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory f){return new SqlSessionTemplate(f);}
        @Bean ProjectMapper projectMapper(SqlSessionTemplate s){return s.getMapper(ProjectMapper.class);}
        @Bean UploadMapper uploadMapper(SqlSessionTemplate s){return s.getMapper(UploadMapper.class);}
        @Bean UserMapper userMapper(SqlSessionTemplate s){return s.getMapper(UserMapper.class);}
        @Bean CalculationRuleMapper calculationRuleMapper(SqlSessionTemplate s){return s.getMapper(CalculationRuleMapper.class);}
        @Bean UserPreferencePeriodSourceMapper userPreferencePeriodSourceMapper(SqlSessionTemplate s){return s.getMapper(UserPreferencePeriodSourceMapper.class);}
        @Bean DataSourceTransactionManager transactionManager(DataSource d){return new DataSourceTransactionManager(d);}
        @Bean BusinessCalendar businessCalendar(){return new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"),ZoneOffset.UTC));}
        @Bean UploadFileStore uploadFileStore(Owned o)throws Exception{Files.createDirectory(o.directory());return new UploadFileStore(o.directory().resolve("originals"));}
    }
    @BeforeEach void setup(){db=new JdbcTemplate(source);}
    @AfterEach void cleanup()throws Exception{
        try(var c=source.getConnection();var s=c.createStatement()){s.execute("DROP SCHEMA "+owned.name()+" CASCADE");}
        if(Files.exists(owned.directory()))try(var paths=Files.walk(owned.directory())){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
    }
    List<String> row(String name){return List.of("",name,"","IN","2026-11-01","2026-12-31","","","PRE","","","","","","특징");}
    JsonNode data(ResultActions a)throws Exception{return json.readTree(a.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");}
    JsonNode uploadProjects(List<List<String>> rows)throws Exception{
        return data(mvc.perform(multipart("/api/common/uploads").file(new MockMultipartFile("file","projects.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",workbooks.tabularFile(ProjectUploadForm.HEADERS,rows,null))).param("formTypeCode","B9")).andExpect(status().isOk()));
    }
    ResultActions apply(JsonNode checked)throws Exception{
        var body=json.createObjectNode();if(checked.path("modifiedAt").isNull())body.putNull("modifiedAt");else body.put("modifiedAt",checked.path("modifiedAt").asText());
        return mvc.perform(post("/api/common/uploads/"+checked.path("uploadId").asText()+"/apply").contentType("application/json").content(body.toString()));
    }
    long count(String table){return db.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    JsonNode registeredProject() throws Exception {
        db.update("INSERT INTO TB_BS_CLNT (CLNT_ID,CLNT_NM,CLNT_TYPE_CD,BIZ_REG_NO,USE_YN,REG_USER_ID) VALUES (?,?,?,?,?,?)",
            "project-customer", "프로젝트 고객사", "CUST", "123-45-67890", "Y", "admin");
        for (var id : List.of("project-part-a", "project-part-b")) {
            db.update("INSERT INTO TB_BS_PART (PART_ID,PART_NM,HQ_YN,USE_YN,REG_USER_ID) VALUES (?,?,?,?,?)",
                id, id.equals("project-part-a") ? "영업 가" : "영업 나", "N", "Y", "admin");
        }
        var body = json.createObjectNode();
        body.put("projectName", "등록 재조회 프로젝트").put("customerId", "project-customer")
            .put("contractTypeCode", "MO").put("salesPartId", "project-part-a")
            .put("startDate", "2026-11-01").put("endDate", "2026-12-31")
            .put("statusCode", "PRE").put("contractAmount", 1000000)
            .put("estimateAmount", 1200000).put("workplace", "서울").put("feature", "등록 특징");
        return data(mvc.perform(post("/api/common/projects").contentType("application/json")
            .content(body.toString())).andExpect(status().isOk()));
    }
    @Test void dueTodayIsNotOverdueAndClosedIssuesDoNotAffectTraffic() throws Exception {
        String id = registeredProject().path("projectId").asText();
        db.update("INSERT INTO TB_PJ_STEP (PROJ_ID,STEP_SN,PROJ_STEP_CD,STEP_WGHT_RT,PLAN_BGN_DT,PLAN_END_DT,REG_USER_ID) VALUES (?,1,'OP_RUN',100,'2026-11-01','2026-12-31','admin')", id);
        db.update("INSERT INTO TB_PJ_WEEK_MEMO (PROJ_ID,MEMO_SN,WRT_DT,ACTL_PRGRS_RT,REG_USER_ID) VALUES (?,1,'2026-11-15',20,'admin')", id);
        db.update("INSERT INTO TB_PJ_ISSUE (PROJ_ID,ISSUE_TTL,IMPR_CD,DDLN_DT,ISSUE_STAT_CD,REG_USER_ID) VALUES (?,'오늘 기한','HIGH','2026-11-15','OPEN','admin'), (?,'종료 이슈','HIGH','2026-11-01','CLOSE','admin')", id, id);
        mvc.perform(get("/api/common/projects/" + id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.trafficLight").value("정상"))
            .andExpect(jsonPath("$.data.issues.length()").value(2));
        db.update("UPDATE TB_PJ_ISSUE SET DDLN_DT='2026-11-14' WHERE PROJ_ID=? AND ISSUE_STAT_CD='OPEN'", id);
        mvc.perform(get("/api/common/projects/" + id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.trafficLight").value("위험"));
        var listed = data(mvc.perform(get("/api/common/projects")).andExpect(status().isOk()));
        assertThat(listed.path("items").get(0).path("trafficLight").asText()).isEqualTo("위험");
    }
    @Test void modifyingAnOldMemoDoesNotResetItsWrittenDateForTraffic() throws Exception {
        String id = registeredProject().path("projectId").asText();
        db.update("INSERT INTO TB_PJ_STEP (PROJ_ID,STEP_SN,PROJ_STEP_CD,STEP_WGHT_RT,PLAN_BGN_DT,PLAN_END_DT,REG_USER_ID) VALUES (?,1,'OP_RUN',100,'2026-11-01','2026-12-31','admin')", id);
        db.update("INSERT INTO TB_PJ_WEEK_MEMO (PROJ_ID,MEMO_SN,WRT_DT,ACTL_PRGRS_RT,REG_USER_ID,MOD_DTTM) VALUES (?,1,'2026-10-31',20,'admin',CURRENT_TIMESTAMP)", id);
        mvc.perform(get("/api/common/projects/" + id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.trafficLight").value("주의"));
    }
    @Test void lossReadsPreCostsAsIdleWithoutRewritingClosedSnapshots() throws Exception {
        String id = registeredProject().path("projectId").asText();
        db.update("INSERT INTO TB_CO_MON_COST (COST_YM,DEV_ID,PROJ_ID,ASGN_ID,ASGN_MM,FREE_MM,SALES_AMT,LABOR_ALLOC_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,DRCT_EXPS_AMT,COST_CLS_CD,FIX_YN,REG_USER_ID) VALUES ('202611','cost-person',?,'cost-assignment',1,0,0,120000,10000,10000,20000,'PRE','Y','admin')", id);
        db.update("INSERT INTO TB_CL_CLOSE (CLOSE_YM,CLOSE_STAT_CD,REG_USER_ID) VALUES ('202611','CLOSE','admin')");
        mvc.perform(post("/api/common/projects/" + id + "/lose").contentType("application/json")
            .content("{\"reasonCode\":\"ETC\",\"memo\":\"원가 보존\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.statusCode").value("LOSE"));
        var costs = projects.getProjectCosts(id);
        assertThat(costs).hasSize(1);
        assertThat(costs.get(0).costClassCode).isEqualTo("IDLE");
        assertThat(costs.get(0).totalAmount).isEqualByComparingTo("140000");
        assertThat(db.queryForObject("SELECT COST_CLS_CD FROM TB_CO_MON_COST WHERE PROJ_ID=?", String.class, id)).isEqualTo("PRE");
        assertThat(db.queryForObject("SELECT LABOR_ALLOC_AMT+DRCT_EXPS_AMT FROM TB_CO_MON_COST WHERE PROJ_ID=?", java.math.BigDecimal.class, id)).isEqualByComparingTo("140000");
    }
    @Test void registrationPersistsFieldsAndBothInitialHistories() throws Exception {
        var p = registeredProject();
        String id = p.path("projectId").asText();
        assertThat(id).matches("P-[0-9]{4,}");
        var read = data(mvc.perform(get("/api/common/projects/" + id)).andExpect(status().isOk()));
        assertThat(read.path("projectName").asText()).isEqualTo("등록 재조회 프로젝트");
        assertThat(read.path("statusCode").asText()).isEqualTo("PRE");
        assertThat(read.path("estimateAmount").decimalValue()).isEqualByComparingTo("1200000");
        assertThat(read.path("feature").asText()).isEqualTo("등록 특징");
        assertThat(read.path("originalContractAmount").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(db.queryForObject("SELECT PART_ID FROM TB_PJ_PROJ_PART WHERE PROJ_ID=? AND APLY_BGN_YM='202611'", String.class, id))
            .isEqualTo("project-part-a");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_PJ_PROJ_CHG WHERE PROJ_ID=? AND CHG_ITEM_CD='CTRT' AND REG_USER_ID='admin'", Long.class, id))
            .isEqualTo(1);
    }
    @Test void manualBudgetSurvivesContractUpdateAndHistoryIsReadable() throws Exception {
        String id = registeredProject().path("projectId").asText();
        mvc.perform(put("/api/common/projects/" + id + "/budget").contentType("application/json")
            .content("{\"budgetAmount\":650000}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.budgetManualYn").value("Y"));
        var update = json.createObjectNode();
        update.put("projectName", "등록 재조회 프로젝트").put("customerId", "project-customer")
            .put("startDate", "2026-11-01").put("endDate", "2026-12-31")
            .put("contractAmount", 2000000).put("estimateAmount", 2200000).put("workplace", "부산");
        update.put("modifiedAt", data(mvc.perform(get("/api/common/projects/" + id))).path("modifiedAt").asText());
        mvc.perform(put("/api/common/projects/" + id).contentType("application/json").content(update.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.budgetAmount").value(650000));
        assertThat(db.queryForObject("SELECT BDGT_MANUAL_YN FROM TB_PJ_PROJ WHERE PROJ_ID=?", String.class, id)).isEqualTo("Y");
        assertThat(db.queryForObject("SELECT ORGNL_CTRT_AMT FROM TB_PJ_PROJ WHERE PROJ_ID=?", java.math.BigDecimal.class, id))
            .isEqualByComparingTo("2000000");
        var history = data(mvc.perform(get("/api/common/projects/" + id + "/change-history")).andExpect(status().isOk()));
        var names = new ArrayList<String>();
        history.path("items").forEach(h -> names.add(h.path("itemName").asText()));
        assertThat(names).contains("예산(목표원가)", "견적 금액", "근무지");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_PJ_PROJ_CHG WHERE PROJ_ID=? AND CHG_ITEM_CD='BUDGET'", Long.class, id))
            .isEqualTo(1);
    }
    @Test void expenseDeletionUsesMonthAndSequenceAndPreservesClosedRow() throws Exception {
        String id = registeredProject().path("projectId").asText();
        long costs = count("TB_CO_MON_COST"), payments = count("TB_HR_MON_PAY");
        var ids = new ArrayList<String>();
        for (var month : List.of("2026-11", "2026-12")) {
            var body = json.createObjectNode().put("yearMonth", month).put("itemCode", "ETC")
                .put("amount", 15000).put("memo", "별도 직접경비");
            var e = data(mvc.perform(post("/api/common/projects/" + id + "/direct-expenses")
                .contentType("application/json").content(body.toString())).andExpect(status().isOk()));
            ids.add(e.path("expenseId").asText());
        }
        assertThat(ids).containsExactly("202611:1", "202612:1");
        db.update("INSERT INTO TB_CL_CLOSE (CLOSE_YM,CLOSE_STAT_CD,REG_USER_ID) VALUES ('202611','CLOSE','admin')");
        mvc.perform(post("/api/common/projects/" + id + "/direct-expenses/" + ids.get(0) + "/delete"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/common/projects/" + id + "/direct-expenses/" + ids.get(1) + "/delete"))
            .andExpect(status().isOk());
        var remaining = data(mvc.perform(get("/api/common/projects/" + id + "/direct-expenses")).andExpect(status().isOk()));
        assertThat(remaining.path("items").size()).isEqualTo(1);
        assertThat(remaining.path("items").get(0).path("expenseId").asText()).isEqualTo(ids.get(0));
        assertThat(count("TB_CO_MON_COST")).isEqualTo(costs);
        assertThat(count("TB_HR_MON_PAY")).isEqualTo(payments);
    }
    @Test void firstPartMonthIsPreservedAndNextMonthCanBeReplacedOrCancelled() throws Exception {
        String id = registeredProject().path("projectId").asText();
        var body = json.createObjectNode().put("salesPartId", "project-part-b").put("effectiveMonth", "2026-11");
        mvc.perform(put("/api/common/projects/" + id + "/sales-part")
            .contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_PJ_PROJ_PART WHERE PROJ_ID=?", Long.class, id)).isEqualTo(1);
        body.put("effectiveMonth", "2026-12").put("reason", "예정 변경");
        mvc.perform(put("/api/common/projects/" + id + "/sales-part")
            .contentType("application/json").content(body.toString())).andExpect(status().isOk());
        var parts = data(mvc.perform(get("/api/common/projects/" + id + "/sales-parts")).andExpect(status().isOk()));
        assertThat(parts.path("items").size()).isEqualTo(2);
        assertThat(parts.path("items").get(1).path("scheduled").asBoolean()).isTrue();
        assertThat(parts.path("items").get(1).path("reason").asText()).isEqualTo("예정 변경");
        body.put("salesPartId", "project-part-a");
        mvc.perform(put("/api/common/projects/" + id + "/sales-part")
            .contentType("application/json").content(body.toString())).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT PART_ID FROM TB_PJ_PROJ_PART WHERE PROJ_ID=?", String.class, id))
            .isEqualTo("project-part-a");
        var history = data(mvc.perform(get("/api/common/projects/" + id + "/change-history")).andExpect(status().isOk()));
        assertThat(history.path("items").get(0).path("beforeValue").asText()).contains("2026-12부터 영업 나");
        assertThat(history.path("items").get(0).path("afterValue").asText()).isEqualTo("변경 취소");
    }
    @Test void b9InspectionIsReceiptOnlyAndApplyPersistsMatchingIdsFirstHistoriesAndRetryReceipt()throws Exception{
        long before=count("TB_PJ_PROJ");var checked=uploadProjects(List.of(row("첫 프로젝트"),row("둘째 프로젝트")));
        assertThat(count("TB_PJ_PROJ")).isEqualTo(before);var applied=data(apply(checked).andExpect(status().isOk()));
        assertThat(count("TB_PJ_PROJ")).isEqualTo(before+2);assertThat(applied.path("plannedProjects").size()).isEqualTo(2);
        for(var assigned:applied.path("plannedProjects")){
            String id=assigned.path("projectId").asText();var p=data(mvc.perform(get("/api/common/projects/"+id)).andExpect(status().isOk()));
            assertThat(p.path("contractAmount").decimalValue()).isEqualByComparingTo("0");assertThat(p.path("budgetManualYn").asText()).isEqualTo("N");
            assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_PJ_PROJ_PART WHERE PROJ_ID=? AND APLY_BGN_YM='202611'",Long.class,id)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_PJ_PROJ_CHG WHERE PROJ_ID=? AND CHG_ITEM_CD='CTRT'",Long.class,id)).isEqualTo(1);
        }
        assertThat(data(apply(applied).andExpect(status().isOk())).path("plannedProjects").size()).isZero();
        assertThat(count("TB_PJ_PROJ")).isEqualTo(before+2);
    }
    @Test void b9FailureAfterFirstRowRollsBackEveryProjectHistoryAndAppliedReceipt()throws Exception{
        var checked=uploadProjects(List.of(row("정상"),row("거부")));long before=count("TB_PJ_PROJ"),history=count("TB_PJ_PROJ_CHG");
        db.execute("CREATE FUNCTION reject_project() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.PROJ_NM='거부' THEN RAISE EXCEPTION 'test rejection'; END IF; RETURN NEW; END $$");
        db.execute("CREATE TRIGGER reject_project BEFORE INSERT ON TB_PJ_PROJ FOR EACH ROW EXECUTE FUNCTION reject_project()");
        apply(checked).andExpect(status().is5xxServerError());assertThat(count("TB_PJ_PROJ")).isEqualTo(before);assertThat(count("TB_PJ_PROJ_CHG")).isEqualTo(history);
        assertThat(db.queryForObject("SELECT UPLD_STAT_CD FROM TB_SY_UPLD WHERE UPLD_ID=?",String.class,checked.path("uploadId").asLong())).isEqualTo("CHECKED");
        db.execute("DROP TRIGGER reject_project ON TB_PJ_PROJ");apply(checked).andExpect(status().isOk());assertThat(count("TB_PJ_PROJ")).isEqualTo(before+2);
    }
    @Test
    @DisplayName("side-effect: B6 반영 시 TB_SY_UPLD.TRGT_DSTRB_STEP_CD 이전 스냅샷 보존")
    void b6ReplaceIsStageScopedAndOldReceiptsKeepTheirStage()throws Exception{
        var p=data(apply(uploadProjects(List.of(row("배분 프로젝트")))).andExpect(status().isOk()));String id=p.path("plannedProjects").get(0).path("projectId").asText();
        db.update("UPDATE TB_PJ_PROJ SET CTRT_TYPE_CD='TK',CTRT_AMT=100 WHERE PROJ_ID=?",id);
        var headers=List.of("구분","관리번호","이름","항목명","2026-11","2026-12","합계");
        var uploadIds=new ArrayList<Long>();
        for(var stage:List.of("EST","CTRT","EST")){
            var checked=data(mvc.perform(multipart("/api/common/uploads").file(new MockMultipartFile("file","distribution.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",workbooks.tabularFile(headers,List.of(List.of("ITEM","","","장비","10","20","30")),null))).param("formTypeCode","B6").param("projectId",id).param("distributionStepCode",stage)).andExpect(status().isOk()));
            uploadIds.add(checked.path("uploadId").asLong());
            var body=json.createObjectNode();
            if(checked.path("modifiedAt").isNull()) body.putNull("modifiedAt");
            else body.put("modifiedAt",checked.path("modifiedAt").asText());
            mvc.perform(post("/api/common/uploads/{uploadId}/apply",checked.path("uploadId").asLong())
                .contentType("application/json").content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(com.company.common.support.controller.OpenApiResponseContract.response(
                    "POST", "/api/common/uploads/{uploadId}/apply"))
                .andExpect(jsonPath("$.data.statusName").value(db.queryForObject(
                    "SELECT CODE_NM FROM TB_SY_CODE WHERE CODE_GRP_ID='UPLD_STAT' AND CODE='APPLIED'",String.class)));
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_PJ_TK_DSTRB WHERE PROJ_ID=?",Long.class,id)).isEqualTo(4);
        assertThat(db.queryForList("SELECT TRGT_DSTRB_STEP_CD FROM TB_SY_UPLD WHERE TRGT_PROJ_ID=? ORDER BY UPLD_ID",String.class,id)).containsExactly("EST","CTRT","EST");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_PJ_TK_DSTRB WHERE PROJ_ID=? AND UPLD_ID=?",Long.class,id,uploadIds.get(2))).isEqualTo(2);
    }
}
