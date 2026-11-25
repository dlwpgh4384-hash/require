package com.company.common.airecommendations.service;

import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.airecommendations.controller.AiRecommendationOptionsController;
import com.company.common.airecommendations.mapper.AiRecommendationOptionsMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.users.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;
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

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(AiRecommendationOptionsController.class)
@ContextConfiguration(classes = {AiRecommendationOptionsController.class, AiRecommendationOptionsService.class,
    AiRecommendationCodes.class, CommonCodeService.class, BusinessCalendar.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class, AiRecommendationOptionsPersistenceTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AiRecommendationOptionsPersistenceTest {
    private static final String PATH = "/api/common/ai-recommendation-options";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired Owned owned;
    @MockBean UserMapper management;
    JdbcTemplate db;
    record Owned(String name) {}

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean Owned owned() { return new Owned("recommendation_options_" + UUID.randomUUID().toString().replace("-", "")); }
        @Bean DataSource dataSource(Owned owned) throws Exception {
            String url = System.getenv("NAVIGATION_TEST_DB_URL");
            String user = System.getenv("NAVIGATION_TEST_DB_USER");
            String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var connection = DriverManager.getConnection(url, user, password);
                var statement = connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
                statement.execute("CREATE SCHEMA " + owned.name());
            }
            var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new Properties();
            properties.setProperty("currentSchema", owned.name());
            source.setDriverProperties(properties);
            var configuration = new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment(
                "postgresql", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration)).prepare();
            return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/airecommendations/AiRecommendationOptionsMapper.xml"),
                new ClassPathResource("mapper/CodeGroupMapper.xml"));
            var configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(false);
            configuration.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean AiRecommendationOptionsMapper aiRecommendationOptionsMapper(SqlSessionTemplate session) {
            return session.getMapper(AiRecommendationOptionsMapper.class);
        }
        @Bean CodeGroupMapper codeGroupMapper(SqlSessionTemplate session) { return session.getMapper(CodeGroupMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }

    @BeforeEach void setup() {
        db = new JdbcTemplate(source);
        db.update("INSERT INTO TB_BS_PART(PART_ID,PART_NM,REG_USER_ID) VALUES('options-part','조건 시험 파트','admin')");
        db.update("INSERT INTO TB_BS_CLNT(CLNT_ID,CLNT_TYPE_CD,CLNT_NM,BIZ_REG_NO,REG_USER_ID) VALUES('options-customer','CUST','조건 시험 고객','1234567890','admin')");
        for (var item : List.of(new String[]{"options-a", "ING", "MO"}, new String[]{"options-b", "ING", "DP"},
            new String[]{"options-c", "PRE", "MO"}, new String[]{"options-d", "END", "MO"},
            new String[]{"options-e", "LOSE", "MO"}, new String[]{"options-f", "ING", "IN"})) {
            db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES(?,?,?,?,'2026-12-15','2027-07-14',100000000,0,0,'admin')",
                item[0], item[0], item[2], item[1]);
        }
        for (var status : List.of("ING", "WIN", "LOSE", "STOP")) {
            db.update("INSERT INTO TB_SL_OPP(OPP_ID,OPP_NM,CUST_CLNT_ID,PART_ID,SALES_STEP_CD,WIN_PRBL_RT,EXPCT_AMT,BGN_PLAN_YM,OPP_STAT_CD,APRV_STAT_CD,REG_USER_ID) VALUES(?,?,'options-customer','options-part','FIND',10,100000000,'202612',?,'NONE','admin')",
                "options-" + status, "options-" + status, status);
        }
    }

    @AfterEach void cleanup() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + owned.name() + " CASCADE");
        }
    }

    @Test void actualPostgresReadFiltersTerminalInternalAndExpectedTargetsWithoutWrites() throws Exception {
        var before = db.queryForList("SELECT * FROM TB_PJ_PROJ ORDER BY PROJ_ID");
        var opportunities = db.queryForList("SELECT * FROM TB_SL_OPP ORDER BY OPP_ID");
        var result = mvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();
        var targets = json.readTree(result.getResponse().getContentAsByteArray()).path("data").path("targets");
        var ownIds = new java.util.ArrayList<String>();
        targets.forEach(target -> {
            if (target.path("targetId").asText().startsWith("options-")) ownIds.add(target.path("targetId").asText());
        });
        assertThat(ownIds).containsExactlyInAnyOrder("options-ING", "options-a", "options-b");
        assertThat(db.queryForList("SELECT * FROM TB_PJ_PROJ ORDER BY PROJ_ID")).isEqualTo(before);
        assertThat(db.queryForList("SELECT * FROM TB_SL_OPP ORDER BY OPP_ID")).isEqualTo(opportunities);
    }

    @Test void currentNeedRowsAndCodeNamesAreReadAgainInCompositeKeyOrder() throws Exception {
        String job = db.queryForObject("SELECT CODE FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB' AND USE_YN='Y' AND DEL_YN='N' ORDER BY SORT_ORD,CODE LIMIT 1", String.class);
        for (int sn : List.of(9, 2)) {
            db.update("INSERT INTO TB_SL_OPP_NEED(OPP_ID,NEED_SN,JOB_CD,NEED_CNT,BGN_YM,PRD_MCNT,MON_MM,REG_USER_ID) VALUES('options-ING',?,?,3,'202701',6,1,'admin')", sn, job);
        }
        mvc.perform(get(PATH).param("targetType", "opportunity").param("targetId", "options-ING"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.targets[0].needs[0].needSn").value(2))
            .andExpect(jsonPath("$.data.targets[0].needs[1].needSn").value(9))
            .andExpect(jsonPath("$.data.targets[0].needs[0].startMonth").value("2027-01"));
        db.update("UPDATE TB_SL_OPP_NEED SET NEED_CNT=7,BGN_YM='202702' WHERE OPP_ID='options-ING' AND NEED_SN=2");
        mvc.perform(get(PATH).param("targetId", "options-ING"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.targets[0].needs[0].requiredCount").value(7))
            .andExpect(jsonPath("$.data.targets[0].needs[0].startMonth").value("2027-02"));
    }

    @Test void invalidOrClosedSelectionIs400AndEmptyNeedsAreNotAValidationError() throws Exception {
        for (String id : List.of("options-WIN", "options-STOP", "options-f", "missing")) {
            mvc.perform(get(PATH).param("targetId", id)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetId"));
        }
        mvc.perform(get(PATH).param("targetId", "options-ING"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.targets[0].needs").isEmpty());
    }

    @Test void choicesExcludeOnlyInactiveAndDeletedCodesAndPreserveDatabaseOrdering() throws Exception {
        for (var item : List.of(new String[]{"OPT_ACTIVE", "Y", "N"}, new String[]{"OPT_INACTIVE", "N", "N"},
            new String[]{"OPT_DELETED", "Y", "Y"})) {
            db.update("INSERT INTO TB_SY_CODE(CODE_GRP_ID,CODE,CODE_NM,SORT_ORD,USE_YN,DEL_YN,REG_USER_ID) VALUES('JOB',?,'조건 시험 직무',999,?,?,'admin')", item[0], item[1], item[2]);
        }
        var result = mvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();
        var jobs = json.readTree(result.getResponse().getContentAsByteArray()).path("data").path("jobs");
        assertThat(jobs.findValuesAsText("code")).contains("OPT_ACTIVE").doesNotContain("OPT_INACTIVE", "OPT_DELETED");
    }
}
