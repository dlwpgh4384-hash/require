package com.company.common.commonops;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.BusinessPlanComparisonDefinitions;
import com.company.common.commonops.controller.CommonBusinessPlanComparisonController;
import com.company.common.commonops.mapper.BusinessPlanComparisonMapper;
import com.company.common.commonops.service.CommonBusinessPlanComparisonService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(CommonBusinessPlanComparisonController.class)
@ContextConfiguration(classes = {CommonBusinessPlanComparisonController.class, CommonBusinessPlanComparisonService.class,
    BusinessPlanComparisonDefinitions.class, ProfitStructureMonthlyProfitService.class, CalculationRuleService.class,
    UserPreferencePeriodBasis.class, CodeExceptionHandler.class, ApiExceptionHandler.class,
    BusinessPlanComparisonPersistenceTest.Database.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BusinessPlanComparisonPersistenceTest {
    static final String URL = System.getenv("NAVIGATION_TEST_DB_URL");
    static final String USER = System.getenv("NAVIGATION_TEST_DB_USER");
    static final String PASSWORD = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
    static final String SCHEMA = "businessplan_" + UUID.randomUUID().toString().replace("-", "");
    static final String PATH = "/api/common/business-plan-comparisons";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProfitStructureMonthlyProfitService monthly;
    @Autowired BusinessPlanComparisonMapper targets;

    @BeforeAll static void prepare() throws Exception {
        Assumptions.assumeTrue(URL != null && !URL.isBlank());
        try (var c = java.sql.DriverManager.getConnection(URL, USER, PASSWORD); var s = c.createStatement()) {
            s.execute("CREATE SCHEMA " + SCHEMA);
        }
        var config = new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",
            new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), data()));
        new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();
    }
    static DriverManagerDataSource data() {
        var ds = new DriverManagerDataSource(URL, USER, PASSWORD);
        var properties = new Properties();
        properties.setProperty("currentSchema", SCHEMA);
        ds.setConnectionProperties(properties);
        return ds;
    }
    @AfterAll static void cleanup() throws Exception {
        if (URL != null && !URL.isBlank())
            try (var c = java.sql.DriverManager.getConnection(URL, USER, PASSWORD); var s = c.createStatement()) {
                s.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            }
    }
    @BeforeEach void fixture() {
        jdbc.execute("TRUNCATE TB_FI_BIZ_PLAN,TB_CO_MON_PL,TB_FI_ITEM,TB_CL_CLOSE,TB_SY_ACCS_LOG CASCADE");
        jdbc.update("UPDATE TB_SY_OPER_STD SET STD_VAL=202601 WHERE STD_CD='CASH_BASE_YM'");
        for (int m = 1; m <= 12; m++) {
            String ym = "2026" + String.format("%02d", m);
            jdbc.update("INSERT INTO TB_FI_BIZ_PLAN(PLAN_YM,PART_ID,SALES_TGT_AMT,OPRT_PRFT_TGT_AMT,REG_USER_ID) VALUES (?,'ALL',?,?, 'admin')",
                ym, m == 1 ? 0 : 200, m == 1 ? 0 : 100);
            if (m <= 10) {
                jdbc.update("INSERT INTO TB_CL_CLOSE(CLOSE_YM,CLOSE_STAT_CD,REG_USER_ID) VALUES (?,'CLOSE','admin')", ym);
                jdbc.update("INSERT INTO TB_CO_MON_PL(PL_YM,SALES_AMT,SALES_COST_AMT,SGA_TOT_AMT,OPRT_PRFT_AMT,FIX_YN,REG_USER_ID) VALUES (?,300,200,150,-50,'Y','admin')", ym);
            }
        }
        jdbc.update("INSERT INTO TB_FI_ITEM(FIN_YM,ITEM_SN,FIN_CLS_CD,ITEM_NM,AMT,VAT_AMT,REG_USER_ID) VALUES ('202611',1,'SGA','조회 시험',50,0,'admin')");
        jdbc.update("INSERT INTO TB_FI_BIZ_PLAN(PLAN_YM,PART_ID,SALES_TGT_AMT,OPRT_PRFT_TGT_AMT,REG_USER_ID) VALUES ('202601','OTHER',999,888,'admin')");
    }
    Map<String, List<Map<String, Object>>> snapshot() {
        var result = new TreeMap<String, List<Map<String, Object>>>();
        result.put("targets", jdbc.queryForList("SELECT * FROM TB_FI_BIZ_PLAN ORDER BY PLAN_YM,PART_ID"));
        result.put("actuals", jdbc.queryForList("SELECT * FROM TB_CO_MON_PL ORDER BY PL_YM"));
        result.put("close", jdbc.queryForList("SELECT * FROM TB_CL_CLOSE ORDER BY CLOSE_YM"));
        result.put("access", jdbc.queryForList("SELECT * FROM TB_SY_ACCS_LOG ORDER BY LOG_ID"));
        return result;
    }
    @Test void publicReadMatchesUi08SourceAndPreservesFixedOpenTargetsCloseAndAccessRows() throws Exception {
        var before = snapshot();
        var source = monthly.year(2026, 11, "202601");
        var rows = targets.targets(2026);
        assertThat(rows).hasSize(12);
        assertThat(rows.get(0).revenueTarget()).isZero();
        mvc.perform(get(PATH).param("year", "2026").param("baseMonth", "202611"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.months.length()").value(12))
            .andExpect(jsonPath("$.data.months[0].achievementRate").isEmpty())
            .andExpect(jsonPath("$.data.months[0].profitRateDifference").isEmpty())
            .andExpect(jsonPath("$.data.summary.cumulativeRevenueActual").value(3000))
            .andExpect(jsonPath("$.data.months[10].revenueActual").value(source.get("202611").values().revenue().intValueExact()))
            .andExpect(jsonPath("$.data.months[10].operatingProfitActual").value(source.get("202611").values().operatingProfit().intValueExact()));
        mvc.perform(get(PATH).param("year", "2026").param("baseMonth", "202610"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.baseMonthClosed").value(true));
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void invalidMonthOrYearMismatchCannotChangeAnyBusinessRows() throws Exception {
        var before = snapshot();
        for (String month : List.of("202613", "202511"))
            mvc.perform(get(PATH).param("year", "2026").param("baseMonth", month))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
        assertThat(snapshot()).isEqualTo(before);
    }
    @TestConfiguration @EnableTransactionManagement
    @MapperScan(basePackageClasses = {BusinessPlanComparisonMapper.class, ProfitStructureMapper.class,
        CalculationRuleMapper.class, UserPreferencePeriodSourceMapper.class}, annotationClass = Mapper.class)
    static class Database {
        @Bean DataSource dataSource() { return data(); }
        @Bean org.apache.ibatis.session.SqlSessionFactory sessions(DataSource ds) throws Exception {
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(ds);
            var config = new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml"));
            return factory.getObject();
        }
        @Bean org.springframework.transaction.PlatformTransactionManager transactions(DataSource ds) {
            return new DataSourceTransactionManager(ds);
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
    }
}
