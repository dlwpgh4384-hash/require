package com.company.common.commonops;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.CandidateDefinitions;
import com.company.common.commonops.controller.CommonCandidateOverviewController;
import com.company.common.commonops.mapper.CandidateOverviewMapper;
import com.company.common.commonops.service.*;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.company.common.withdrawals.service.WithdrawalCalculationRules;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
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
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(CommonCandidateOverviewController.class)
@ContextConfiguration(classes = {CommonCandidateOverviewController.class, CandidateOverviewService.class,
    CandidateResumeExtractionService.class, CandidateResumeDocumentReader.class, CodeExceptionHandler.class,
    ApiExceptionHandler.class, CandidateOverviewPersistenceTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CandidateOverviewPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired DataSource source;
    @Autowired SqlSessionFactory sessions;
    @Autowired OwnedSchema owned;
    JdbcTemplate db;
    record OwnedSchema(String name) {}
    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean OwnedSchema ownedSchema() { return new OwnedSchema("candidate_overview_" + UUID.randomUUID().toString().replace("-", "")); }
        @Bean DataSource dataSource(OwnedSchema owned) throws Exception {
            String url = System.getenv("NAVIGATION_TEST_DB_URL");
            String user = System.getenv("NAVIGATION_TEST_DB_USER");
            String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
                statement.execute("CREATE SCHEMA " + owned.name());
            }
            var data = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new java.util.Properties(); properties.setProperty("currentSchema", owned.name());
            data.setDriverProperties(properties); return data;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/commonops/CandidateOverviewMapper.xml"),
                new ClassPathResource("mapper/withdrawals/WithdrawalMapper.xml"),
                new ClassPathResource("mapper/CalculationRuleMapper.xml"));
            var config = new org.apache.ibatis.session.Configuration(); config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean CandidateOverviewMapper candidateOverviewMapper(SqlSessionTemplate session) { return session.getMapper(CandidateOverviewMapper.class); }
        @Bean WithdrawalMapper withdrawalMapper(SqlSessionTemplate session) { return session.getMapper(WithdrawalMapper.class); }
        @Bean CalculationRuleService calculationRuleService(SqlSessionTemplate session) {
            return new CalculationRuleService(session.getMapper(CalculationRuleMapper.class), mock(UserPreferencePeriodSourceMapper.class));
        }
        @Bean WithdrawalCalculationRules withdrawalCalculationRules(CalculationRuleService rules) { return new WithdrawalCalculationRules(rules); }
        @Bean CandidateDefinitions candidateDefinitions(CalculationRuleService rules) { return new CandidateDefinitions(rules); }
        @Bean BusinessCalendar businessCalendar() { return new BusinessCalendar(Clock.fixed(Instant.parse("2026-12-31T15:00:00Z"), ZoneOffset.UTC)); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void setup() throws Exception {
        new SchemaPreparation(sessions).prepare(); db = new JdbcTemplate(source);
    }
    @AfterEach void cleanup() {
        new JdbcTemplate(source).execute("DROP SCHEMA " + owned.name() + " CASCADE");
    }
    private void candidate(String id, String status, String available, String contact) {
        db.update("INSERT INTO TB_HR_CAND(CAND_ID,CAND_NM,EMP_TYPE_CD,JOB_CD,GRADE_CD,HOPE_UPRC,INPUT_PSBL_DT,CNTC_STAT_CD,LAST_CNTC_DT,CAND_SRC_CD,REG_USER_ID) "
            + "VALUES (?,?,'FR','DEV','MD',6500000,CAST(? AS date),?,CAST(? AS date),'SITE','admin')", id, id, available, status, contact);
    }
    @Test void realSummaryExcludesTerminalAndProvesThirtyAndThirtyOneDaysAndKoreanMidnight() throws Exception {
        candidate("thirty", "BEFORE", "2027-02-01", "2026-12-02");
        candidate("thirty-one", "DONE", "2027-02-02", "2026-12-01");
        candidate("after-start", "ITVW", "2027-02-02", "2027-01-01");
        candidate("hold", "HOLD", "2026-11-01", "2026-11-01");
        candidate("contract", "CTRT", "2026-11-01", "2026-11-01");
        mvc.perform(get("/api/common/candidate-summary")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.activeCount").value(3))
            .andExpect(jsonPath("$.data.interviewScheduledCount").value(1))
            .andExpect(jsonPath("$.data.availableByNextMonthCount").value(1))
            .andExpect(jsonPath("$.data.overdueContactCount").value(1));
        mvc.perform(get("/api/common/candidate-shortages")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isArray());
    }
    @Test void actualExtractionHasNoCandidateContactConsentUploadOrHistorySideEffects() throws Exception {
        var before = counts();
        mvc.perform(multipart("/api/common/candidate-resume-extractions").file(new MockMultipartFile(
            "file", "resume.txt", "text/plain", "희망금액:650만원\n01012345678\nJava 개발".getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.fields[?(@.field == 'desiredMonthlyRate')].value").value(org.hamcrest.Matchers.hasItem("6500000")));
        assertThat(counts()).isEqualTo(before);
        mvc.perform(multipart("/api/common/candidate-resume-extractions").file(new MockMultipartFile(
            "file", "resume.jpg", "image/jpeg", new byte[]{1,2,3}))).andExpect(status().isBadRequest());
        assertThat(counts()).isEqualTo(before);
    }
    private java.util.List<Long> counts() {
        return java.util.List.of(db.queryForObject("SELECT COUNT(*) FROM TB_HR_CAND", Long.class),
            db.queryForObject("SELECT COUNT(*) FROM TB_HR_CAND_CNTC", Long.class),
            db.queryForObject("SELECT COUNT(*) FROM TB_SY_UPLD", Long.class),
            db.queryForObject("SELECT COUNT(*) FROM TB_SY_CHG_HIST", Long.class),
            db.queryForObject("SELECT COUNT(*) FROM TB_HR_CAND WHERE PRVC_AGRE_USER_ID IS NOT NULL", Long.class));
    }
}
