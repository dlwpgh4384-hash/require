package com.company.common.salesopportunities.controller;

import com.company.common.common.SalesPipelineCodes;
import com.company.common.common.SalesPipelineApprovalStandards;
import com.company.common.codegroups.dto.CodeContracts.Item;
import com.company.common.salesopportunities.dto.SalesOpportunityContracts.Need;
import com.company.common.salesopportunities.mapper.SalesOpportunityMapper;
import com.company.common.salesopportunities.service.*;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.*;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(CommonSalesOpportunityController.class)
@ContextConfiguration(classes = {CommonSalesOpportunityController.class, CommonSalesOpportunityService.class,
    CurrentBusinessActor.class, CodeExceptionHandler.class, ApiExceptionHandler.class, SalesOpportunityPersistenceTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SalesOpportunityPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired OpportunityDatabase owned;
    @MockBean SalesOpportunityCalculations calculations;
    @MockBean SalesOpportunityProjects projects;
    @MockBean SalesPipelineCodes codes;
    JdbcTemplate db;
    record OpportunityDatabase(String name) {}
    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean OpportunityDatabase opportunityDatabase() { return new OpportunityDatabase("opportunities_" + UUID.randomUUID().toString().replace("-", "")); }
        @Bean DataSource dataSource(OpportunityDatabase owned) throws Exception {
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
            factory.setMapperLocations(new ClassPathResource("mapper/salesopportunities/SalesOpportunityMapper.xml"));
            var configuration = new org.apache.ibatis.session.Configuration(); configuration.setMapUnderscoreToCamelCase(false);
            configuration.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(configuration); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean SalesOpportunityMapper salesOpportunityMapper(SqlSessionTemplate session) { return session.getMapper(SalesOpportunityMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean BusinessCalendar businessCalendar() { return new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"), ZoneOffset.UTC)); }
    }
    @BeforeEach void setup() {
        db = new JdbcTemplate(source);
        db.update("INSERT INTO TB_BS_PART(part_id,part_nm,reg_user_id) VALUES('test-sales','시험 영업파트','admin')");
        db.update("INSERT INTO TB_BS_CLNT(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES('test-customer','CUST','시험 고객','1234567890','admin')");
        when(codes.name(anyString(), anyString())).thenAnswer(c -> c.getArgument(1));
        when(codes.selectable(anyString())).thenAnswer(c -> {
            var values = switch ((String)c.getArgument(0)) { case "SALES_STEP" -> List.of("FIND", "PROP", "NEGO", "PRE"); case "JOB" -> List.of("BE"); case "CTRT_TYPE" -> List.of("TK"); case "LOSE_RSN" -> List.of("PRICE"); default -> List.<String>of(); };
            return values.stream().map(code -> { var item = new Item(); item.code = code; item.codeName = code; item.useYn = "Y"; return item; }).toList();
        });
        when(calculations.price(any())).thenReturn(new BigDecimal("10000000"));
        when(calculations.stageProbability(anyString())).thenReturn(BigDecimal.TEN);
        when(calculations.evaluate(anyList())).thenReturn(new SalesOpportunityCalculations.Evaluation(new BigDecimal("14"), new SalesPipelineApprovalStandards.Values(new BigDecimal("15"), new BigDecimal("0.5"))));
    }
    @AfterEach void cleanup() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) { statement.execute("DROP SCHEMA " + owned.name() + " CASCADE"); }
    }
    ObjectNode input() throws Exception { return (ObjectNode)json.readTree("""
        {"opportunityName":"영업 기회","customerId":"test-customer","salesPartId":"test-sales","stageCode":"FIND","winningProbability":10,"expectedAmount":60000000,"startMonth":"2026-11","needs":[{"jobCode":"BE","requiredCount":1,"startMonth":"2026-11","months":6,"monthlyMm":1}]}
        """); }
    String create() throws Exception {
        return json.readTree(mvc.perform(post("/api/common/sales-opportunities").contentType("application/json").content(input().toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("opportunityId").asText();
    }
    ObjectNode version(String id) throws Exception {
        String value = json.readTree(mvc.perform(get("/api/common/sales-opportunities/{id}", id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("modifiedAt").asText();
        return json.createObjectNode().put("modifiedAt", value);
    }
    @Test void createAndUpdatePersistStableCompositeNeedKeyAndVersion() throws Exception {
        var id = create();
        var before = db.queryForMap("SELECT * FROM TB_SL_OPP WHERE opp_id=?", id);
        var body = input(); body.put("modifiedAt", version(id).path("modifiedAt").asText());
        ((ObjectNode)body.path("needs").get(0)).put("needSn", 1).put("months", 8);
        mvc.perform(put("/api/common/sales-opportunities/{id}", id).contentType("application/json").content(body.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.needs[0].months").value(8));
        assertThat(db.queryForObject("SELECT prd_mcnt::int FROM TB_SL_OPP_NEED WHERE opp_id=? AND need_sn=1", Integer.class, id)).isEqualTo(8);
        assertThat(db.queryForMap("SELECT * FROM TB_SL_OPP WHERE opp_id=?", id).get("reg_user_id")).isEqualTo(before.get("reg_user_id"));
        mvc.perform(put("/api/common/sales-opportunities/{id}", id).contentType("application/json").content(body.toString())).andExpect(status().isConflict());
    }
    @Test void fullRejectionReasonPersistsAndSystemNotificationIsBounded() throws Exception {
        var id = create(); var reason = "가".repeat(300);
        mvc.perform(put("/api/common/sales-opportunities/{id}/reject", id).contentType("application/json").content(version(id).put("reason", reason).toString())).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT act_rsn FROM TB_SL_APRV_HIST WHERE opp_id=? AND aprv_act_cd='REJ'", String.class, id)).isEqualTo(reason);
        assertThat(db.queryForObject("SELECT char_length(noti_cn) FROM TB_SY_NOTI WHERE link_key=?", Integer.class, id)).isEqualTo(300);
        assertThat(db.queryForObject("SELECT send_mthd_cd FROM TB_SY_NOTI WHERE link_key=?", String.class, id)).isEqualTo("SYS");
    }
    @Test void notificationFailureRollsBackApprovalAndHistory() throws Exception {
        var id = create();
        db.execute("ALTER TABLE TB_SY_NOTI ADD CONSTRAINT notification_guard CHECK(noti_type_cd <> 'QUOTE_REJ')");
        mvc.perform(put("/api/common/sales-opportunities/{id}/reject", id).contentType("application/json").content(version(id).put("reason", "사유").toString())).andExpect(status().is5xxServerError());
        assertThat(db.queryForObject("SELECT aprv_stat_cd FROM TB_SL_OPP WHERE opp_id=?", String.class, id)).isEqualTo("WAIT");
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SL_APRV_HIST WHERE opp_id=? AND aprv_act_cd='REJ'", Integer.class, id)).isZero();
    }
    @Test void stopAndResumeKeepPhysicalRowAndClearResultDate() throws Exception {
        var id = create();
        mvc.perform(post("/api/common/sales-opportunities/{id}/stop", id).contentType("application/json").content(version(id).toString())).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT rslt_dt FROM TB_SL_OPP WHERE opp_id=?", LocalDate.class, id)).isEqualTo(LocalDate.of(2026, 11, 15));
        mvc.perform(post("/api/common/sales-opportunities/{id}/resume", id).contentType("application/json").content(version(id).toString())).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT opp_stat_cd FROM TB_SL_OPP WHERE opp_id=?", String.class, id)).isEqualTo("ING");
        assertThat(db.queryForObject("SELECT rslt_dt FROM TB_SL_OPP WHERE opp_id=?", LocalDate.class, id)).isNull();
    }
}
