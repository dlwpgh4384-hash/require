package com.company.common.raterenegotiations;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.RateRenegotiationDefinitions;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitMapper;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.raterenegotiations.controller.CommonRateRenegotiationController;
import com.company.common.raterenegotiations.mapper.RateRenegotiationMapper;
import com.company.common.raterenegotiations.service.*;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.*;
import java.io.ByteArrayInputStream;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(CommonRateRenegotiationController.class)
@ContextConfiguration(classes = {CommonRateRenegotiationController.class, CommonRateRenegotiationService.class,
    RateRenegotiationSource.class, RateRenegotiationExcelService.class, RateRenegotiationDefinitions.class,
    CalculationRuleService.class, UserPreferencePeriodBasis.class, CodeExceptionHandler.class, ApiExceptionHandler.class,
    RateRenegotiationPersistenceTest.Persistence.class, ExcelExportService.class, CurrentBusinessActor.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RateRenegotiationPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired Owned owned;
    @Autowired RateRenegotiationMapper rateMapper;
    @MockBean BusinessCalendar calendar;
    JdbcTemplate db;
    String job;
    record Owned(String name) {}
    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean Owned owned() { return new Owned("raterenegotiation_" + UUID.randomUUID().toString().replace("-", "")); }
        @Bean DataSource dataSource(Owned owned) throws Exception {
            String url = System.getenv("NAVIGATION_TEST_DB_URL"), user = System.getenv("NAVIGATION_TEST_DB_USER"), password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var c = DriverManager.getConnection(url, user, password); var s = c.createStatement()) { s.execute("CREATE SCHEMA " + owned.name()); }
            var ds = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new Properties(); properties.setProperty("currentSchema", owned.name()); ds.setDriverProperties(properties);
            var config = new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), ds));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();
            return ds;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/raterenegotiations/RateRenegotiationMapper.xml"),
                new ClassPathResource("mapper/personalcostprofits/PersonalCostProfitMapper.xml"),
                new ClassPathResource("mapper/CalculationRuleMapper.xml"), new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var config = new org.apache.ibatis.session.Configuration(); config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean PersonalCostProfitMapper personalMapper(SqlSessionTemplate s) { return s.getMapper(PersonalCostProfitMapper.class); }
        @Bean RateRenegotiationMapper rateMapper(SqlSessionTemplate s) { return s.getMapper(RateRenegotiationMapper.class); }
        @Bean CalculationRuleMapper calculationMapper(SqlSessionTemplate s) { return s.getMapper(CalculationRuleMapper.class); }
        @Bean UserPreferencePeriodSourceMapper periodMapper(SqlSessionTemplate s) { return s.getMapper(UserPreferencePeriodSourceMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void setup() {
        db = new JdbcTemplate(source);
        when(calendar.today()).thenReturn(LocalDate.of(2026, 2, 1));
        when(calendar.now()).thenReturn(Instant.parse("2026-02-01T00:00:00Z"));
        when(calendar.firstOpenMonth(any(), any())).thenCallRealMethod();
        db.update("UPDATE TB_SY_OPER_STD SET STD_VAL=202602 WHERE STD_CD='CASH_BASE_YM'");
        db.update("DELETE FROM TB_CL_CLOSE");
        job = db.queryForObject("SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB' AND USE_YN='Y' AND DEL_YN='N'", String.class);
        db.update("INSERT INTO TB_BS_PART(PART_ID,PART_NM,HQ_YN,USE_YN,REG_USER_ID) VALUES('rate-part','시험 영업파트','N','Y','admin')");
        db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,GRADE_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,JNCMP_DT,REG_USER_ID) VALUES('rate-dev','rate-number','김개발','CT',?,'MD','DRCT','rate-part','INPUT','2026-01-01','admin')", job);
        db.update("INSERT INTO TB_HR_MON_PAY(PAY_YM,DEV_ID,PAY_AMT,ALWNC_AMT,INSR_CMPNY_AMT,RETIRE_RSV_AMT,EMP_TYPE_CD,DRCT_CLS_CD,REG_USER_ID) VALUES('202602','rate-dev',1800000,0,0,0,'CT','DRCT','admin')");
        project("rate-project", "MO");
        assignment("rate-a", "rate-project", "NEW", 30, "0.5", "1000000");
        assignment("rate-b", "rate-project", "NEW", 92, "0.5", "1000000");
        assignment("rate-free", "rate-project", "FREE", 30, "1", "0");
    }
    void project(String id, String type) {
        db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES(?,? ,?,'ING','2026-01-01','2027-12-31',10000000,0,0,'admin')", id, id, type);
    }
    void assignment(String id, String project, String input, int days, String mm, String price) {
        db.update("INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,CTRT_MM,BILL_UPRC,INPUT_TYPE_CD,EXT_CNT,ASGN_STAT_CD,REG_USER_ID) VALUES(?,?,'rate-dev',?,'2026-01-01',?,1,?,?,0,'ACT','admin')",
            id, project, job, LocalDate.of(2026, 2, 1).plusDays(days), new java.math.BigDecimal(price), input);
        db.update("INSERT INTO TB_PJ_ASGN_MON(ASGN_ID,PROJ_ID,DEV_ID,INPUT_YM,INPUT_DAY_CNT,MON_MM,APLY_UPRC,SALES_AMT,FIX_YN,REG_USER_ID) VALUES(?,?,'rate-dev','202602',14,?,?,0,'N','admin')", id, project, new java.math.BigDecimal(mm), new java.math.BigDecimal(price));
    }
    @AfterEach void cleanup() throws Exception {
        try (var c = source.getConnection(); var s = c.createStatement()) { s.execute("DROP SCHEMA " + owned.name() + " CASCADE"); }
    }
    JsonNode read(String rate) throws Exception {
        var response = mvc.perform(get("/api/common/rate-renegotiations").param("increaseRate", rate))
            .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }
    List<String> snapshot(boolean audit) {
        List<String> rows = new ArrayList<>();
        var tables = new ArrayList<>(List.of("TB_PJ_ASGN", "TB_PJ_PROJ", "TB_HR_DEV_PRC", "TB_CO_MON_COST", "TB_PJ_ASGN_MON", "TB_HR_MON_PAY"));
        if (audit) tables.add("TB_SY_ACCS_LOG");
        for (String table : tables) rows.addAll(db.queryForList("SELECT row_to_json(t)::text FROM " + table + " t ORDER BY row_to_json(t)::text", String.class));
        return rows;
    }
    long logs() { return db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-32' AND ACT_TYPE_CD='DNLD'", Long.class); }
    @Test void liveAllocationIncludesFreeSupportBeforeSelectionAndDoesNotWriteAnySource() throws Exception {
        var before = snapshot(true);
        var result = read("5");
        assertThat(result.path("items")).hasSize(2);
        assertThat(result.path("summary").path("targetCount").asInt()).isEqualTo(2);
        assertThat(result.path("items").get(0).path("monthlyCost").decimalValue()).isEqualByComparingTo("450000");
        assertThat(result.path("items").get(0).path("targetUnitPrice").decimalValue()).isEqualByComparingTo("1130000");
        assertThat(result.path("items").get(0).path("annualProfitIncrease").decimalValue()).isEqualByComparingTo("600000");
        assertThat(read("-1").path("totalAnnualProfitIncrease").decimalValue()).isZero();
        assertThat(read("51").path("totalAnnualProfitIncrease").decimalValue()).isEqualByComparingTo("12000000");
        assertThat(snapshot(true)).isEqualTo(before);
    }
    @Test void beforePayrollUploadUsesPoolSalaryAndTurnkeyWithoutBudgetDoesNotStop() throws Exception {
        db.update("DELETE FROM TB_HR_MON_PAY");
        db.update("INSERT INTO TB_HR_DEV_PRC(DEV_ID,APLY_BGN_YM,MON_PAY_AMT,REG_USER_ID) VALUES('rate-dev','202601',1800000,'admin')");
        db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,GRADE_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,JNCMP_DT,REG_USER_ID) VALUES('rate-tk-dev','rate-number2','박턴키','FT',?,'MD','DRCT','rate-part','INPUT','2026-01-01','admin')", job);
        project("rate-turnkey", "TK");
        db.update("INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,CTRT_MM,BILL_UPRC,INPUT_TYPE_CD,EXT_CNT,ASGN_STAT_CD,REG_USER_ID) VALUES('rate-tk','rate-turnkey','rate-tk-dev',?,'2026-01-01','2026-03-03',1,NULL,'NEW',0,'ACT','admin')", job);
        db.update("INSERT INTO TB_PJ_ASGN_MON(ASGN_ID,PROJ_ID,DEV_ID,INPUT_YM,INPUT_DAY_CNT,MON_MM,APLY_UPRC,SALES_AMT,FIX_YN,REG_USER_ID) VALUES('rate-tk','rate-turnkey','rate-tk-dev','202602',20,1,0,0,'N','admin')");
        var result = read("5");
        // 계약직 풀 급여 1,800,000 + 4대보험(회사) 9.4% 169,200 = 1,969,200, 이 달 MM 2(0.5·0.5·무상 1) 중 0.5
        assertThat(result.path("items")).hasSize(2);
        assertThat(result.path("items").get(0).path("monthlyCost").decimalValue()).isEqualByComparingTo("492300");
        assertThat(result.path("salaryEstimatedDeveloperIds")).hasSize(1);
        assertThat(result.path("salaryEstimatedDeveloperIds").get(0).asText()).isEqualTo("rate-dev");
    }
    @Test void invalidQueryAndUnknownColumnHaveNoPersistenceSideEffects() throws Exception {
        var before = snapshot(true);
        mvc.perform(get("/api/common/rate-renegotiations").param("renewalMonths", "5")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("renewalMonths"));
        mvc.perform(get("/api/common/rate-renegotiations/excel").param("columns", "unknown")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("columns"));
        assertThat(snapshot(true)).isEqualTo(before);
    }
    @Test void workbookMatchesGetAndExactlyOneDatabaseTimestampedDownloadIsCommitted() throws Exception {
        var before = snapshot(false);
        var result = read("5");
        long oldLogs = logs();
        var bytes = mvc.perform(get("/api/common/rate-renegotiations/excel").param("increaseRate", "5")
            .param("columns", "annualProfitIncrease", "monthlyMm", "developerName"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = book.getSheetAt(0);
            assertThat(sheet.getRow(2).getLastCellNum()).isEqualTo((short) 3);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("5% 올리면 연 이익");
            for (int i = 0; i < result.path("items").size(); i++) {
                assertThat(sheet.getRow(i + 3).getCell(0).getNumericCellValue()).isEqualTo(result.path("items").get(i).path("annualProfitIncrease").asDouble());
                assertThat(sheet.getRow(i + 3).getCell(1).getNumericCellValue()).isEqualTo(0.5);
            }
        }
        assertThat(logs()).isEqualTo(oldLogs + 1);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-32' AND USER_ID='admin' AND ACT_TYPE_CD='DNLD' AND ACT_DTTM IS NOT NULL AND REG_DTTM IS NOT NULL", Long.class)).isEqualTo(1);
        assertThat(snapshot(false)).isEqualTo(before);
    }
    @Test void databaseFailureRollsBackSuccessfulDownloadAudit() throws Exception {
        db.execute("ALTER TABLE TB_SY_ACCS_LOG ADD CONSTRAINT rate_export_rejected CHECK(SCRN_ID<>'UI-32')");
        var before = snapshot(true);
        mvc.perform(get("/api/common/rate-renegotiations/excel").param("columns", "developerName"))
            .andExpect(status().isInternalServerError());
        assertThat(snapshot(true)).isEqualTo(before);
    }
    @Test void closedMonthProjectionUsesMatchingAssignmentMmLaborAndAppliedPrice() {
        db.update("INSERT INTO TB_CO_MON_COST(COST_YM,DEV_ID,PROJ_ID,ASGN_ID,ASGN_MM,FREE_MM,SALES_AMT,LABOR_ALLOC_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,DRCT_EXPS_AMT,COST_CLS_CD,CTRT_TYPE_CD,FIX_YN,REG_USER_ID) VALUES('202602','rate-dev','rate-project','rate-a',0.5,0.25,500000,450000,99000,88000,77000,'ALLOC','MO','Y','admin')");
        var costs = rateMapper.selectMonthlyCosts("202602");
        assertThat(costs).hasSize(1);
        assertThat(costs.get(0).mm()).isEqualByComparingTo("0.5");
        assertThat(costs.get(0).labor()).isEqualByComparingTo("450000");
        assertThat(rateMapper.selectMonthlyCosts("202601")).isEmpty();
        assertThat(rateMapper.selectAssignmentInfo("202602", true).stream()
            .filter(a -> a.assignmentId().equals("rate-a")).findFirst().orElseThrow().currentUnitPrice())
            .isEqualByComparingTo("1000000");
    }
}
