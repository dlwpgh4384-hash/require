package com.company.common.candidates.controller;

import com.company.common.candidates.mapper.CandidateMapper;
import com.company.common.candidates.service.*;
import com.company.common.common.*;
import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.users.mapper.UserMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.BusinessCalendar;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(CommonCandidateController.class)
@ContextConfiguration(classes = {CommonCandidateController.class, CommonCandidateService.class,
    CandidateCalculationRules.class, CandidateCodes.class, CandidateDefinitions.class, CommonCodeService.class,
    CalculationRuleService.class, com.company.common.support.service.ExcelExportService.class,
    com.company.common.support.service.CurrentBusinessActor.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class, CandidatePersistenceTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CandidatePersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired CandidateDatabase owned;
    JdbcTemplate db;
    record CandidateDatabase(String name) {}
    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean CandidateDatabase candidateDatabase() { return new CandidateDatabase("candidates_" + UUID.randomUUID().toString().replace("-", "")); }
        @Bean DataSource dataSource(CandidateDatabase owned) throws Exception {
            String url = System.getenv("NAVIGATION_TEST_DB_URL"), user = System.getenv("NAVIGATION_TEST_DB_USER"), password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
                statement.execute("CREATE SCHEMA " + owned.name());
            }
            var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new Properties(); properties.setProperty("currentSchema", owned.name()); source.setDriverProperties(properties);
            var configuration = new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration)).prepare(); return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/candidates/CandidateMapper.xml"),
                new ClassPathResource("mapper/CodeGroupMapper.xml"), new ClassPathResource("mapper/UserMapper.xml"),
                new ClassPathResource("mapper/CalculationRuleMapper.xml"), new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var configuration = new org.apache.ibatis.session.Configuration(); configuration.setMapUnderscoreToCamelCase(false);
            configuration.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(configuration); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean CandidateMapper candidateMapper(SqlSessionTemplate session) { return session.getMapper(CandidateMapper.class); }
        @Bean CodeGroupMapper codeGroupMapper(SqlSessionTemplate session) { return session.getMapper(CodeGroupMapper.class); }
        @Bean UserMapper userMapper(SqlSessionTemplate session) { return session.getMapper(UserMapper.class); }
        @Bean CalculationRuleMapper calculationRuleMapper(SqlSessionTemplate session) { return session.getMapper(CalculationRuleMapper.class); }
        @Bean UserPreferencePeriodSourceMapper userPreferencePeriodSourceMapper(SqlSessionTemplate session) { return session.getMapper(UserPreferencePeriodSourceMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean BusinessCalendar businessCalendar() { return new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"), ZoneOffset.UTC)); }
    }
    @BeforeEach void setup() { db = new JdbcTemplate(source); }
    @AfterEach void cleanup() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) { statement.execute("DROP SCHEMA " + owned.name() + " CASCADE"); }
    }
    ObjectNode input() {
        var body = json.createObjectNode().put("name", "후보 " + UUID.randomUUID()).put("desiredMonthlyRate", 6500000)
            .put("employeeTypeCode", "FL").put("primaryJobCode", "BE").put("gradeCode", "MD")
            .put("availableDate", "2026-12-01").put("sourceCode", "SITE").put("phone", "02-123-4567")
            .put("email", "candidate@example.org").put("privacyAgreement", true).put("memo", "기존 메모");
        body.putArray("skills").add("Java").add("Java"); return body;
    }
    String create() throws Exception {
        var response = mvc.perform(post("/api/common/candidates").contentType("application/json").content(input().toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("candidateId").asText();
    }
    String version(String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/common/candidates/{id}", id)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString()).path("data").path("modifiedAt").asText(null);
    }
    ObjectNode update(String id) throws Exception {
        var body = input(); String version = version(id); if (version == null) body.putNull("modifiedAt"); else body.put("modifiedAt", version); return body;
    }
    int count(String table) { return db.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    @Test void seededAdminCreatesReadbackAndOnlyCandidateTablesChange() throws Exception {
        int developers = count("TB_HR_DEV"), uploads = count("TB_SY_UPLD"), history = count("TB_SY_CHG_HIST");
        String id = create();
        mvc.perform(get("/api/common/candidates/{id}", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.skills.length()").value(1)).andExpect(jsonPath("$.data.lastContactDate").value("2026-11-15"))
            .andExpect(jsonPath("$.data.contactStatusCode").value("BEFORE")).andExpect(jsonPath("$.data.privacyAgreementUserId").value("admin"));
        assertThat(count("TB_HR_CAND")).isEqualTo(1); assertThat(count("TB_HR_CAND_SKILL")).isEqualTo(1); assertThat(count("TB_HR_CAND_CNTC")).isEqualTo(1);
        assertThat(count("TB_HR_DEV")).isEqualTo(developers); assertThat(count("TB_SY_UPLD")).isEqualTo(uploads); assertThat(count("TB_SY_CHG_HIST")).isEqualTo(history);
        assertThat(db.queryForObject("SELECT reg_user_id FROM TB_HR_CAND_CNTC WHERE cand_id=?", String.class, id)).isEqualTo("SYSTEM");
    }
    @Test void rejectedInputPersistsNothing() throws Exception {
        var body = input().put("privacyAgreement", false);
        mvc.perform(post("/api/common/candidates").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
        assertThat(count("TB_HR_CAND")).isZero(); assertThat(count("TB_HR_CAND_SKILL")).isZero(); assertThat(count("TB_HR_CAND_CNTC")).isZero();
    }
    @Test void createHistoryFailureRollsBackMasterAndSkills() throws Exception {
        db.execute("ALTER TABLE TB_HR_CAND_CNTC ADD CONSTRAINT candidate_history_guard CHECK (reg_user_id <> 'SYSTEM')");
        mvc.perform(post("/api/common/candidates").contentType("application/json").content(input().toString())).andExpect(status().is5xxServerError());
        assertThat(count("TB_HR_CAND")).isZero(); assertThat(count("TB_HR_CAND_SKILL")).isZero(); assertThat(count("TB_HR_CAND_CNTC")).isZero();
    }
    @Test void updateHistoryFailureRollsBackMasterAndSkills() throws Exception {
        String id = create(); var old = db.queryForMap("SELECT * FROM TB_HR_CAND WHERE cand_id=?", id);
        db.execute("ALTER TABLE TB_HR_CAND_CNTC ADD CONSTRAINT candidate_history_guard CHECK(cntc_memo <> '정보 수정')");
        var body = update(id).put("name", "바뀌지 않음"); body.putArray("skills").add("Kotlin");
        mvc.perform(put("/api/common/candidates/{id}", id).contentType("application/json").content(body.toString())).andExpect(status().is5xxServerError());
        assertThat(db.queryForMap("SELECT * FROM TB_HR_CAND WHERE cand_id=?", id)).isEqualTo(old);
        assertThat(db.queryForList("SELECT skill_nm FROM TB_HR_CAND_SKILL WHERE cand_id=?", String.class, id)).containsExactly("Java");
        assertThat(count("TB_HR_CAND_CNTC")).isEqualTo(1);
    }
    @Test void matchingExecutesOriginalOpportunitySchemaAndRecalculatesAfterUpdate() throws Exception {
        int developersBefore = count("TB_HR_DEV"), assignmentsBefore = count("TB_SL_TENT_ASGN");
        String part = db.queryForObject("SELECT part_id FROM TB_BS_PART ORDER BY part_id LIMIT 1", String.class);
        db.update("INSERT INTO TB_BS_CLNT(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES('candidate-customer','CUST','후보 매칭 고객','123-45-67890','admin')");
        db.update("INSERT INTO TB_SL_OPP(opp_id,opp_nm,cust_clnt_id,part_id,sales_step_cd,win_prbl_rt,expct_amt,bgn_plan_ym,opp_stat_cd,aprv_stat_cd,reg_user_id) VALUES('candidate-opp','후보 매칭 기회','candidate-customer',?,'PROP',50,8000000,'202611','ING','NONE','admin')", part);
        db.update("INSERT INTO TB_SL_OPP_NEED(opp_id,need_sn,job_cd,grade_cd,need_cnt,bgn_ym,prd_mcnt,mon_mm,expct_uprc,reg_user_id) VALUES('candidate-opp',1,'BE','MD',1,'202611',2,1,8000000,'admin')");
        db.update("INSERT INTO TB_SL_OPP_NEED(opp_id,need_sn,job_cd,grade_cd,need_cnt,bgn_ym,prd_mcnt,mon_mm,expct_uprc,reg_user_id) VALUES('candidate-opp',2,'BE','MD',1,'202611',1,1,9000000,'admin')");
        db.update("INSERT INTO TB_SL_OPP_NEED(opp_id,need_sn,job_cd,grade_cd,need_cnt,bgn_ym,prd_mcnt,mon_mm,expct_uprc,reg_user_id) VALUES('candidate-opp',3,'BE','MD',1,'202612',2,1,NULL,'admin')");
        String id = create();
        mvc.perform(get("/api/common/candidates/{id}/matches", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].expectedProfitRate").value(17.5));
        var last = db.queryForObject("SELECT last_cntc_dt FROM TB_HR_CAND WHERE cand_id=?", java.sql.Date.class, id);
        mvc.perform(put("/api/common/candidates/{id}", id).contentType("application/json").content(update(id).put("desiredMonthlyRate", 7000000).toString()))
            .andExpect(status().isOk());
        mvc.perform(get("/api/common/candidates/{id}/matches", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].expectedProfitRate").value(11.25))
            .andExpect(jsonPath("$.data.items[0].lowMargin").value(true)).andExpect(jsonPath("$.data.items[0].lowMarginRate").value(15));
        assertThat(db.queryForObject("SELECT last_cntc_dt FROM TB_HR_CAND WHERE cand_id=?", java.sql.Date.class, id)).isEqualTo(last);
        db.update("UPDATE TB_HR_CAND SET cntc_stat_cd='CTRT' WHERE cand_id=?", id);
        mvc.perform(get("/api/common/candidates/{id}/matches", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1));
        assertThat(count("TB_HR_DEV")).isEqualTo(developersBefore); assertThat(count("TB_SL_TENT_ASGN")).isEqualTo(assignmentsBefore);
    }
    @Test void firstConsentIsPreservedAndUpdateDoesNotTouchLastContact() throws Exception {
        String id = create();
        db.update("UPDATE TB_HR_CAND SET prvc_agre_dt=DATE '2026-10-01',last_cntc_dt=DATE '2026-10-20' WHERE cand_id=?", id);
        var body = update(id).putNull("phone").putNull("email").put("privacyAgreement", false);
        mvc.perform(put("/api/common/candidates/{id}", id).contentType("application/json").content(body.toString())).andExpect(status().isOk());
        body = update(id).put("privacyAgreement", true);
        mvc.perform(put("/api/common/candidates/{id}", id).contentType("application/json").content(body.toString())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.privacyAgreementDate").value("2026-10-01")).andExpect(jsonPath("$.data.lastContactDate").value("2026-10-20"));
    }
    @Test void simultaneousNullVersionWritesHaveExactlyOneWinner() throws Exception {
        String id = create(); String body = update(id).toString();
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        Callable<Integer> write = () -> { start.await(); return mvc.perform(put("/api/common/candidates/{id}", id).contentType("application/json").content(body)).andReturn().getResponse().getStatus(); };
        try {
            var first = pool.submit(write); var second = pool.submit(write); start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        } finally { pool.shutdownNow(); }
        assertThat(count("TB_HR_CAND_CNTC")).isEqualTo(2);
    }
    @Test void contactsUseNullSafeVersionAndSameStatusRefreshesDate() throws Exception {
        String id = create(); db.update("UPDATE TB_HR_CAND SET last_cntc_dt=DATE '2026-09-01' WHERE cand_id=?", id);
        var body = json.createObjectNode().putNull("modifiedAt").put("contactStatusCode", "BEFORE").put("memo", "시스템이 등록");
        mvc.perform(post("/api/common/candidates/{id}/contacts", id).contentType("application/json").content(body.toString())).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.lastContactDate").value("2026-11-15"));
        mvc.perform(get("/api/common/candidates/{id}/contacts", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].systemRecord").value(false)).andExpect(jsonPath("$.data.items[0].recordedBy").value("admin"));
        var before = db.queryForMap("SELECT * FROM TB_HR_CAND WHERE cand_id=?", id);
        mvc.perform(post("/api/common/candidates/{id}/contacts", id).contentType("application/json").content(body.toString())).andExpect(status().isConflict());
        assertThat(db.queryForMap("SELECT * FROM TB_HR_CAND WHERE cand_id=?", id)).isEqualTo(before); assertThat(count("TB_HR_CAND_CNTC")).isEqualTo(2);
    }
    @Test void lastContactFallbackIsKoreanRegistrationDayWithoutGetWrites() throws Exception {
        String id = create(); db.update("UPDATE TB_HR_CAND SET last_cntc_dt=NULL,reg_dttm=TIMESTAMPTZ '2026-11-14 16:00:00+00' WHERE cand_id=?", id);
        mvc.perform(get("/api/common/candidates/{id}", id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.lastContactDate").value("2026-11-15"));
        assertThat(db.queryForObject("SELECT last_cntc_dt FROM TB_HR_CAND WHERE cand_id=?", Object.class, id)).isNull();
    }
    @Test void listOrdersTerminalLastThenAvailabilityThenIdentity() throws Exception {
        String first = create(), second = create(), terminal = create();
        db.update("UPDATE TB_HR_CAND SET input_psbl_dt=DATE '2026-12-15' WHERE cand_id=?", first);
        db.update("UPDATE TB_HR_CAND SET input_psbl_dt=DATE '2026-12-02' WHERE cand_id=?", second);
        db.update("UPDATE TB_HR_CAND SET input_psbl_dt=DATE '2026-11-01',cntc_stat_cd='HOLD' WHERE cand_id=?", terminal);
        mvc.perform(get("/api/common/candidates").param("jobCode", "BE").param("availabilityWindow", "next-month"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].candidateId").value(second))
            .andExpect(jsonPath("$.data.items[1].candidateId").value(first)).andExpect(jsonPath("$.data.items[2].candidateId").value(terminal));
    }
}
