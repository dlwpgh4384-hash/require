package com.company.common.netprofitstatements;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.NetProfitStatementDefinitions;
import com.company.common.netprofitstatements.controller.CommonNetProfitStatementController;
import com.company.common.netprofitstatements.mapper.NetProfitStatementMapper;
import com.company.common.netprofitstatements.service.*;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
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
@WebMvcTest(CommonNetProfitStatementController.class)
@ContextConfiguration(classes = {CommonNetProfitStatementController.class, CommonNetProfitStatementService.class,
    NetProfitStatementExcelService.class, NetProfitStatementDefinitions.class, ProfitStructureMonthlyProfitService.class,
    CalculationRuleService.class, UserPreferencePeriodBasis.class, ExcelExportService.class, BusinessCalendar.class,
    CurrentBusinessActor.class, CodeExceptionHandler.class, ApiExceptionHandler.class,
    NetProfitStatementPersistenceTest.Database.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NetProfitStatementPersistenceTest {
    static final String URL = System.getenv("NAVIGATION_TEST_DB_URL");
    static final String USER = System.getenv("NAVIGATION_TEST_DB_USER");
    static final String PASSWORD = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
    static final String SCHEMA = "netprofit_" + UUID.randomUUID().toString().replace("-", "");
    static final String REPORT = "/api/common/net-profit-statements";
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ProfitStructureMonthlyProfitService monthly;

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
        if (URL != null && !URL.isBlank()) {
            try (var c = java.sql.DriverManager.getConnection(URL, USER, PASSWORD); var s = c.createStatement()) {
                s.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            }
        }
    }
    @BeforeEach void fixture() {
        jdbc.execute("TRUNCATE TB_SY_ACCS_LOG,TB_CO_MON_PL,TB_CO_PREV_ADJ,TB_FI_ITEM,TB_CL_CLOSE,TB_FI_CORP_TAX CASCADE");
        jdbc.update("UPDATE TB_SY_OPER_STD SET STD_VAL=202601 WHERE STD_CD='CASH_BASE_YM'");
        jdbc.update("INSERT INTO TB_CL_CLOSE(CLOSE_YM,CLOSE_STAT_CD,REG_USER_ID) VALUES ('202601','CLOSE','admin')");
        for (String month : List.of("202501", "202502", "202601")) {
            jdbc.update("INSERT INTO TB_CO_MON_PL(PL_YM,SALES_AMT,SALES_COST_AMT,GROSS_PRFT_AMT,SGA_TOT_AMT,OPRT_PRFT_AMT,NOPR_INCM_AMT,NOPR_EXPS_AMT,CORP_TAX_AMT,NET_PRFT_AMT,MIGR_YN,FIX_YN,REG_USER_ID) VALUES (?,1000000,400000,600000,100000,500000,70000,20000,0,550000,?,'Y','admin')",
                month, month.startsWith("2025") ? "Y" : "N");
        }
        jdbc.update("UPDATE TB_CO_MON_PL SET NET_PRFT_AMT=NULL WHERE PL_YM='202502'");
        jdbc.update("INSERT INTO TB_FI_ITEM(FIN_YM,ITEM_SN,FIN_CLS_CD,ITEM_NM,AMT,VAT_AMT,REG_USER_ID) VALUES ('202602',1,'NOPR_IN','영업외수익',70000,0,'admin')");
    }
    Map<String, String> sources() {
        var result = new LinkedHashMap<String, String>();
        for (String table : List.of("TB_CO_MON_PL", "TB_CO_MON_COST", "TB_FI_ITEM")) {
            result.put(table, jdbc.queryForObject("SELECT COALESCE(jsonb_agg(v ORDER BY v::text),'[]'::jsonb)::text FROM (SELECT to_jsonb(t) v FROM " + table + " t) s", String.class));
        }
        return result;
    }
    long logs() { return jdbc.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG", Long.class); }
    @Test void fixedAndLiveReadsMatchSharedYearWithoutChangingAnySourceOrAccessRow() throws Exception {
        var before = sources();
        long count = logs();
        var expected = monthly.year(2026, 2, "202601");
        mvc.perform(get(REPORT).param("year", "2026").param("baseMonth", "202601"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.provisional").value(false));
        mvc.perform(get(REPORT).param("year", "2026").param("baseMonth", "202602"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.provisional").value(true))
            .andExpect(jsonPath("$.data.summary.baseMonthNetProfit").value(expected.get("202602").values().netProfit().intValueExact()))
            .andExpect(jsonPath("$.data.numericTable.rows[0].values[13].value").value(2000000))
            .andExpect(jsonPath("$.data.numericTable.rows[16].values[13].value").isEmpty());
        assertThat(sources()).isEqualTo(before);
        assertThat(logs()).isEqualTo(count);
    }
    @Test void completedWorkbookAddsExactlyOneUi09DbClockLogOnly() throws Exception {
        var before = sources();
        long count = logs();
        mvc.perform(get(REPORT + "/excel").param("year", "2026").param("baseMonth", "202602").param("amountUnit", "천 원"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertThat(sources()).isEqualTo(before);
        assertThat(logs()).isEqualTo(count + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-09' AND ACT_TYPE_CD='DNLD' AND ACT_DTTM IS NOT NULL AND REG_DTTM IS NOT NULL AND ACT_DTTM<=clock_timestamp()", Integer.class)).isEqualTo(1);
    }
    @Test void invalidExportDoesNotChangeSourcesOrLog() throws Exception {
        var before = sources();
        long count = logs();
        mvc.perform(get(REPORT + "/excel").param("year", "2026").param("baseMonth", "202613").param("amountUnit", "원"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
        assertThat(sources()).isEqualTo(before);
        assertThat(logs()).isEqualTo(count);
    }
    @Test void rejectedAuditRollsBackInsertedLogAndReturnsNoSuccessfulWorkbook() throws Exception {
        var before = sources();
        long count = logs();
        jdbc.execute("CREATE FUNCTION reject_netprofit_download() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.SCRN_ID='UI-09' THEN RAISE EXCEPTION 'download rejected'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER reject_netprofit_download AFTER INSERT ON TB_SY_ACCS_LOG FOR EACH ROW EXECUTE FUNCTION reject_netprofit_download()");
        try {
            mvc.perform(get(REPORT + "/excel").param("year", "2026").param("baseMonth", "202602").param("amountUnit", "원"))
                .andExpect(status().is5xxServerError());
        } finally {
            jdbc.execute("DROP TRIGGER reject_netprofit_download ON TB_SY_ACCS_LOG");
            jdbc.execute("DROP FUNCTION reject_netprofit_download()");
        }
        assertThat(sources()).isEqualTo(before);
        assertThat(logs()).isEqualTo(count);
    }
    @TestConfiguration @EnableTransactionManagement
    @MapperScan(basePackageClasses = {ProfitStructureMapper.class, NetProfitStatementMapper.class,
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
