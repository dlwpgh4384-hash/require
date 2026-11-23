package com.company.common.cashflowstatements;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.cashflowstatements.controller.CommonCashFlowStatementController;
import com.company.common.cashflowstatements.mapper.CashFlowStatementMapper;
import com.company.common.cashflowstatements.service.*;
import com.company.common.common.CashFlowStatementDefinitions;
import com.company.common.financeitems.mapper.FinanceItemMapper;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
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
@WebMvcTest(CommonCashFlowStatementController.class)
@ContextConfiguration(classes = {CommonCashFlowStatementController.class, CommonCashFlowStatementService.class,
    CashFlowStatementExcelService.class, CashFlowStatementDefinitions.class, CalculationRuleService.class,
    ExcelExportService.class, BusinessCalendar.class, CurrentBusinessActor.class, CodeExceptionHandler.class,
    ApiExceptionHandler.class, CashFlowStatementPersistenceTest.Database.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CashFlowStatementPersistenceTest {
    static final String URL = System.getenv("NAVIGATION_TEST_DB_URL");
    static final String USER = System.getenv("NAVIGATION_TEST_DB_USER");
    static final String PASSWORD = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
    static final String SCHEMA = "cashflow_" + UUID.randomUUID().toString().replace("-", "");
    static final String PATH = "/api/common/cash-flow-statements";
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @BeforeAll static void prepare() throws Exception {
        Assumptions.assumeTrue(URL != null && !URL.isBlank());
        try (var c = java.sql.DriverManager.getConnection(URL, USER, PASSWORD); var s = c.createStatement()) { s.execute("CREATE SCHEMA " + SCHEMA); }
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
        if (URL != null && !URL.isBlank()) try (var c = java.sql.DriverManager.getConnection(URL, USER, PASSWORD); var s = c.createStatement()) { s.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE"); }
    }
    @BeforeEach void fixture() {
        jdbc.execute("TRUNCATE TB_SY_ACCS_LOG,TB_BL_RCPT,TB_BL_BILL,TB_HR_MON_PAY,TB_FI_ITEM,TB_FI_LOAN,TB_FI_XFER,TB_FI_CARD_MON,TB_FI_TAX_PAY,TB_CL_CLOSE CASCADE");
        jdbc.update("UPDATE TB_SY_OPER_STD SET STD_VAL=202601 WHERE STD_CD='CASH_BASE_YM'");
        jdbc.update("UPDATE TB_SY_OPER_STD SET STD_VAL=1000000000 WHERE STD_CD='MIN_CASH'");
        jdbc.update("DELETE FROM TB_SY_OPER_STD WHERE STD_CD='CASH_BASE_AMT'");
        jdbc.update("INSERT INTO TB_SY_OPER_STD(STD_CD,APLY_BGN_YM,STD_NM,STD_VAL,UNIT_NM,REG_USER_ID) VALUES('CASH_BASE_AMT','202601','기초 잔액',1000000000,'원','admin')");
        jdbc.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES('cash-project','자금 프로젝트','MO','ING','2025-01-01','2027-12-31',100000000,0,0,'admin') ON CONFLICT(PROJ_ID) DO NOTHING");
        jdbc.update("INSERT INTO TB_BL_BILL(BILL_ID,PROJ_ID,BILL_ORD,BILL_YM,BILL_DT,SPLY_AMT,VAT_RT,VAT_AMT,TOT_AMT,RCPT_SUM_AMT,UNPAID_AMT,PAY_DUE_DT,REG_USER_ID) VALUES('cash-bill','cash-project',1,'202601','2026-01-01',100,10,10,110,40,70,'2026-03-10','admin')");
        jdbc.update("INSERT INTO TB_BL_RCPT(BILL_ID,RCPT_DT,RCPT_AMT,REG_USER_ID) VALUES('cash-bill','2026-02-10',40,'admin')");
        jdbc.update("INSERT INTO TB_FI_ITEM(FIN_YM,ITEM_SN,FIN_CLS_CD,ITEM_NM,AMT,VAT_AMT,REG_USER_ID) VALUES('202602',1,'NOPR_IN','잡수익',100,10,'admin')");
        jdbc.update("INSERT INTO TB_FI_ITEM(FIN_YM,ITEM_SN,FIN_CLS_CD,ITEM_NM,AMT,VAT_AMT,IO_DT,REG_USER_ID) VALUES('202601',1,'SGA','경비',50,5,'2026-02-15','admin')");
        jdbc.update("INSERT INTO TB_FI_LOAN(LOAN_DT,IO_CD,AMT,REG_USER_ID) VALUES('2026-02-01','IN',20,'admin'),('2026-02-02','OUT',5,'admin')");
        jdbc.update("INSERT INTO TB_FI_XFER(XFER_DT,XFER_CLS_CD,ITEM_NM,AMT,REG_USER_ID) VALUES('2026-02-01','SPCL','특별비',7,'admin')");
        jdbc.update("INSERT INTO TB_CL_CLOSE(CLOSE_YM,CLOSE_STAT_CD,REG_USER_ID) VALUES('202602','CLOSE','admin')");
    }
    Map<String, String> sources() {
        var result = new LinkedHashMap<String, String>();
        for (String table : List.of("TB_BL_RCPT", "TB_BL_BILL", "TB_HR_MON_PAY", "TB_FI_ITEM", "TB_FI_LOAN", "TB_FI_XFER", "TB_FI_CARD_MON", "TB_FI_TAX_PAY", "TB_SY_OPER_STD", "TB_CL_CLOSE", "TB_SY_CALC_RULE", "TB_CO_MON_PL"))
            result.put(table, jdbc.queryForObject("SELECT COALESCE(jsonb_agg(v ORDER BY v::text),'[]'::jsonb)::text FROM (SELECT to_jsonb(t) v FROM " + table + " t) s", String.class));
        return result;
    }
    long logs() { return jdbc.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG", Long.class); }
    @Test void realSqlKeepsSourceDatesVatAndPartialRemainderWithoutWrites() throws Exception {
        var before = sources();
        long logs = logs();
        mvc.perform(get(PATH).param("year", "2026")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.months.length()").value(12))
            .andExpect(jsonPath("$.data.months[1].cashSales").value(40))
            .andExpect(jsonPath("$.data.months[1].miscIncome").value(110))
            .andExpect(jsonPath("$.data.months[1].loan").value(15))
            .andExpect(jsonPath("$.data.months[1].overhead").value(55))
            .andExpect(jsonPath("$.data.months[1].unpaidPayroll").value(7))
            .andExpect(jsonPath("$.data.months[2].cashSales").value(70));
        assertThat(sources()).isEqualTo(before);
        assertThat(logs()).isEqualTo(logs);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=? AND upper(table_name) IN ('TB_FI_CASH','TB_FI_PAY')", Integer.class, SCHEMA)).isZero();
    }
    @Test void closedDueMonthDropsUnpaidAndDoesNotRollItForward() throws Exception {
        jdbc.update("UPDATE TB_BL_BILL SET PAY_DUE_DT='2026-02-20'");
        mvc.perform(get(PATH).param("year", "2026")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.months[1].cashSales").value(40)).andExpect(jsonPath("$.data.months[2].cashSales").value(0));
    }
    @Test void cancelledReceiptIsExcluded() throws Exception {
        jdbc.update("UPDATE TB_BL_RCPT SET CNCL_YN='Y'");
        mvc.perform(get(PATH).param("year", "2026")).andExpect(status().isOk()).andExpect(jsonPath("$.data.months[1].cashSales").value(0));
    }
    @Test void cancelledBillExcludesActualAndPlannedCash() throws Exception {
        jdbc.update("UPDATE TB_BL_BILL SET CNCL_YN='Y'");
        mvc.perform(get(PATH).param("year", "2026")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.months[1].cashSales").value(0)).andExpect(jsonPath("$.data.months[2].cashSales").value(0));
    }
    @Test void invalidYearDoesNotChangeAnySourceOrAudit() throws Exception {
        var before = sources(); long logs = logs();
        mvc.perform(get(PATH).param("year", "invalid")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("year"));
        assertThat(sources()).isEqualTo(before); assertThat(logs()).isEqualTo(logs);
    }
    @Test void completedWorkbookCreatesExactlyOneUi21LogAndNoSourceChanges() throws Exception {
        var before = sources(); long count = logs();
        mvc.perform(get(PATH + "/excel").param("year", "2026").param("amountUnit", "원")).andExpect(status().isOk());
        assertThat(sources()).isEqualTo(before);
        assertThat(logs()).isEqualTo(count + 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-21' AND ACT_TYPE_CD='DNLD'", Long.class)).isEqualTo(1);
    }
    @Test void rejectedAuditRollsBackLogAndNoSuccessfulWorkbookEscapes() throws Exception {
        var before = sources(); long count = logs();
        jdbc.execute("CREATE FUNCTION reject_cash_download() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.SCRN_ID='UI-21' THEN RAISE EXCEPTION 'download rejected'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER reject_cash_download AFTER INSERT ON TB_SY_ACCS_LOG FOR EACH ROW EXECUTE FUNCTION reject_cash_download()");
        try {
            mvc.perform(get(PATH + "/excel").param("year", "2026").param("amountUnit", "원")).andExpect(status().is5xxServerError());
        } finally {
            jdbc.execute("DROP TRIGGER reject_cash_download ON TB_SY_ACCS_LOG");
            jdbc.execute("DROP FUNCTION reject_cash_download()");
        }
        assertThat(sources()).isEqualTo(before); assertThat(logs()).isEqualTo(count);
    }
    @TestConfiguration @EnableTransactionManagement
    @MapperScan(basePackageClasses = {ProfitStructureMapper.class, CashFlowStatementMapper.class, FinanceItemMapper.class,
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
        @Bean org.springframework.transaction.PlatformTransactionManager transactions(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
    }
}
