package com.company.common.manpowerforecasts;

import com.company.common.calculation.dto.CalculationRuleContracts.Standard;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.manpowerforecasts.controller.CommonManpowerForecastController;
import com.company.common.manpowerforecasts.mapper.ManpowerForecastMapper;
import com.company.common.manpowerforecasts.service.*;
import com.company.common.salespipeline.mapper.SalesPipelineMapper;
import com.company.common.salespipeline.service.*;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.company.common.withdrawals.service.WithdrawalCalculationRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
@WebMvcTest(CommonManpowerForecastController.class)
@ContextConfiguration(classes = {CommonManpowerForecastController.class, CommonManpowerForecastService.class,
    ManpowerForecastExcelService.class, SalesPipelineForecastService.class, WithdrawalCalculationRules.class,
    CurrentBusinessActor.class, ExcelExportService.class, CodeExceptionHandler.class, ApiExceptionHandler.class,
    ManpowerForecastPersistenceTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ManpowerForecastPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired ForecastDatabase owned;
    @Autowired SalesPipelineForecastService shared;
    @MockBean CalculationRuleService metadata;
    @MockBean SalesPipelineCalculationRules calculations;
    JdbcTemplate db;
    record ForecastDatabase(String name) {}
    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean ForecastDatabase forecastDatabase() { return new ForecastDatabase("forecast_" + UUID.randomUUID().toString().replace("-", "")); }
        @Bean DataSource dataSource(ForecastDatabase owned) throws Exception {
            String url = System.getenv("NAVIGATION_TEST_DB_URL"), user = System.getenv("NAVIGATION_TEST_DB_USER"), password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
                statement.execute("CREATE SCHEMA " + owned.name());
            }
            var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new Properties(); properties.setProperty("currentSchema", owned.name()); source.setDriverProperties(properties);
            var configuration = new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration)).prepare();
            return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/salespipeline/SalesPipelineMapper.xml"),
                new ClassPathResource("mapper/manpowerforecasts/ManpowerForecastMapper.xml"));
            var configuration = new org.apache.ibatis.session.Configuration(); configuration.setMapUnderscoreToCamelCase(false);
            configuration.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(configuration); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean SalesPipelineMapper salesPipelineMapper(SqlSessionTemplate session) { return session.getMapper(SalesPipelineMapper.class); }
        @Bean ManpowerForecastMapper manpowerForecastMapper(SqlSessionTemplate session) { return session.getMapper(ManpowerForecastMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean BusinessCalendar businessCalendar() { return new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC)); }
    }
    @BeforeEach void setup() throws Exception {
        db = new JdbcTemplate(source);
        ManpowerForecastTestData.definitions(metadata, calculations);
        doAnswer(call -> new Standard(call.getArgument(0), YearMonth.of(2026, 1),
            db.queryForObject("SELECT std_val FROM TB_SY_OPER_STD WHERE std_cd=? AND aply_bgn_ym<=? ORDER BY aply_bgn_ym DESC LIMIT 1", BigDecimal.class,
                call.getArgument(0), ((YearMonth) call.getArgument(1)).format(java.time.format.DateTimeFormatter.ofPattern("uuuuMM")))))
            .when(metadata).effectiveStandard(anyString(), any());
        db.update("INSERT INTO TB_BS_PART(part_id,part_nm,reg_user_id) VALUES('forecast-part','예측 시험 파트','admin')");
        db.update("INSERT INTO TB_PJ_PROJ(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES('forecast-project','예측 시험','MO','ING',DATE '2026-01-01',DATE '2027-05-31',0,0,0,'admin')");
        db.update("INSERT INTO TB_PJ_MNPW_PLAN(proj_id,job_cd,plan_mm,src_cd,reg_user_id) VALUES('forecast-project','BE',8,'MANUAL','admin')");
        developer("forecast-leave"); developer("forecast-extension"); developer("forecast-future");
        db.update("INSERT INTO TB_HR_DEV_STAT(dev_id,stat_sn,work_stat_cd,bgn_dt,end_dt,reg_user_id) VALUES('forecast-leave',1,'LEAVE',DATE '2026-11-01',DATE '2027-05-31','admin')");
        assignment("forecast-extension", "2026-01-01", "2026-11-30");
        assignment("forecast-future", "2026-12-01", "2027-05-31");
        db.update("INSERT INTO TB_PJ_ASGN_MON(asgn_id,input_ym,dev_id,proj_id,input_day_cnt,mon_mm,aply_uprc,sales_amt,reg_user_id) VALUES('forecast-future','202612','forecast-future','forecast-project',22,0.5,0,0,'admin')");
    }
    void developer(String id) {
        db.update("INSERT INTO TB_HR_DEV(dev_id,mng_no,dev_nm,emp_type_cd,grade_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES(?,?,?,'EMP','MD','BE','DRCT','forecast-part','WORK','admin')", id, id, id);
    }
    void assignment(String id, String from, String to) {
        db.update("INSERT INTO TB_PJ_ASGN(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,input_type_cd,ext_psbl_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES(?,'forecast-project',?,'BE',CAST(? AS date),CAST(? AS date),0.5,'NEW',NULL,0,'ACT','admin')", id, id, from, to);
    }
    @AfterEach void cleanup() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) { statement.execute("DROP SCHEMA " + owned.name() + " CASCADE"); }
    }
    List<Map<String, Object>> plans() { return db.queryForList("SELECT * FROM TB_PJ_MNPW_PLAN ORDER BY proj_id,job_cd"); }
    List<Map<String, Object>> assignments() { return db.queryForList("SELECT * FROM TB_PJ_ASGN ORDER BY asgn_id"); }
    List<Map<String, Object>> standards() { return db.queryForList("SELECT * FROM TB_SY_OPER_STD ORDER BY std_cd,aply_bgn_ym"); }
    @Test void realWorkbookEqualsQueryAndLeavesAllBusinessRowsUnchanged() throws Exception {
        var plans = plans(); var assignments = assignments(); var standards = standards();
        var response = mvc.perform(get("/api/common/manpower-forecasts").param("year", "2026")).andExpect(status().isOk()).andReturn().getResponse();
        var months = json.readTree(response.getContentAsString(StandardCharsets.UTF_8)).path("data").path("months");
        var file = mvc.perform(get("/api/common/manpower-forecasts/excel").param("year", "2026"))
            .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "3")).andReturn().getResponse();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(file.getContentAsByteArray()))) {
            assertThat(book.getNumberOfSheets()).isEqualTo(1);
            for (int row = 0; row < 3; row++) for (int col = 0; col < months.size(); col++) {
                var value = months.get(col).path(List.of("demand", "supply", "difference").get(row)).decimalValue();
                assertThat(book.getSheetAt(0).getRow(row + 3).getCell(col + 1).getNumericCellValue())
                    .isEqualTo(value.setScale(1, RoundingMode.HALF_UP).doubleValue());
            }
        }
        assertThat(plans()).isEqualTo(plans); assertThat(assignments()).isEqualTo(assignments); assertThat(standards()).isEqualTo(standards);
    }
    @Test void developmentDownloadWithoutAuthenticationHeaderReturnsBinary() throws Exception {
        mvc.perform(get("/api/common/manpower-forecasts/excel").param("year", "2026"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
    @Test void missingYearKeepsAssignmentsUnchangedAndCreatesNoAudit() throws Exception {
        var before = assignments();
        mvc.perform(get("/api/common/manpower-forecasts/excel")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("year"));
        assertThat(assignments()).isEqualTo(before);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_SY_ACCS_LOG WHERE scrn_id='UI-17'", Integer.class)).isZero();
    }
    @Test void leaveUnknownExtensionAndFutureConfirmedUseIdenticalSharedMonthValuesAndPreserveStandards() throws Exception {
        var before = standards();
        var legacy = shared.getCommonSalesPipelineForecast("2026-11").months();
        var response = mvc.perform(get("/api/common/manpower-forecasts").param("year", "2026")).andExpect(status().isOk()).andReturn().getResponse();
        var months = json.readTree(response.getContentAsString(StandardCharsets.UTF_8)).path("data").path("months");
        // 올해 조회(11·12월)는 인력 운영 보드가 쓰는 이번 달 시작 6개월 예측의 앞 두 달과 겹친다.
        assertThat(months.size()).isEqualTo(2);
        for (int i = 0; i < months.size(); i++) {
            assertThat(months.get(i).path("month").asText()).isEqualTo(legacy.get(i).month());
            assertThat(months.get(i).path("supply").decimalValue()).isEqualByComparingTo(legacy.get(i).supply());
            assertThat(months.get(i).path("demand").decimalValue()).isEqualByComparingTo(legacy.get(i).demand());
        }
        mvc.perform(get("/api/common/manpower-forecasts/excel").param("year", "2026")).andExpect(status().isOk());
        assertThat(standards()).isEqualTo(before);
    }
    @Test void realDatabaseMonthValuesMatchHandCalculatedExpectations() throws Exception {
        // 연장 확인용 사람의 11월 확정 투입 0.5(11-30 철수, 연장 가능성 미정 → 12월에 50% 반영).
        db.update("INSERT INTO TB_PJ_ASGN_MON(asgn_id,input_ym,dev_id,proj_id,input_day_cnt,mon_mm,aply_uprc,sales_amt,reg_user_id) VALUES('forecast-extension','202611','forecast-extension','forecast-project',21,0.5,0,0,'admin')");
        var response = mvc.perform(get("/api/common/manpower-forecasts").param("year", "2026")).andExpect(status().isOk()).andReturn().getResponse();
        var data = json.readTree(response.getContentAsString(StandardCharsets.UTF_8)).path("data");
        var months = data.path("months");
        assertThat(months.size()).isEqualTo(2);
        // 11월 가용: 휴직자 제외, 연장 확인 1 − 0.5 = 0.5, 향후 투입(12월부터) 사람은 11월 투입 없음 1 → 1.5.
        // 11월 필요: 미충원은 다음 달(12월)부터 → 0.
        assertMonth(months.get(0), "2026-11", "1.50", "0.00", "0.00", "1.5");
        // 12월 가용: 연장 확인 1 − 0.5×50% = 0.75, 향후 확정 1 − 0.5 = 0.5 → 1.25 → 식-60 한 자리 1.3.
        // 12월 필요(미충원): (계획 8 − 배정 1.0) ÷ 12월~2027-05 6개월 = 1.1667 → 1.17. 차이 1.3 − 1.2 = 0.1.
        assertMonth(months.get(1), "2026-12", "1.30", "1.17", "1.17", "0.1");
        assertThat(data.path("summary").path("lastMonthSupply").decimalValue()).isEqualByComparingTo("1.30");
        assertThat(data.path("summary").path("lastMonthDemand").decimalValue()).isEqualByComparingTo("1.17");
        assertThat(data.path("summary").path("mostShortage").isNull()).isTrue();
        var legacy = shared.getCommonSalesPipelineForecast("2026-11").months();
        for (int i = 0; i < months.size(); i++) {
            assertThat(months.get(i).path("supply").decimalValue()).isEqualByComparingTo(legacy.get(i).supply());
            assertThat(months.get(i).path("demand").decimalValue()).isEqualByComparingTo(legacy.get(i).demand());
        }
    }
    static void assertMonth(com.fasterxml.jackson.databind.JsonNode month, String name, String supply, String demand,
        String unfilled, String difference) {
        assertThat(month.path("month").asText()).isEqualTo(name);
        assertThat(month.path("supply").decimalValue()).isEqualByComparingTo(supply);
        assertThat(month.path("demand").decimalValue()).isEqualByComparingTo(demand);
        assertThat(month.path("unfilledDemand").decimalValue()).isEqualByComparingTo(unfilled);
        assertThat(month.path("difference").decimalValue()).isEqualByComparingTo(difference);
        assertThat(month.path("difference").decimalValue().scale()).isLessThanOrEqualTo(1);
    }
    @Test void successfulDownloadPersistsUi17DnldWithThreeRowsAndNoUi29Audit() throws Exception {
        mvc.perform(get("/api/common/manpower-forecasts/excel").param("year", "2026")).andExpect(status().isOk());
        var logs = db.queryForList("SELECT user_id,scrn_id,act_type_cd,trgt_desc,act_dttm,reg_dttm FROM TB_SY_ACCS_LOG WHERE scrn_id='UI-17'");
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0)).containsEntry("user_id", "admin").containsEntry("act_type_cd", "DNLD").containsEntry("trgt_desc", "인력 수요 예측 3건");
        assertThat(logs.get(0).get("act_dttm")).isNotNull(); assertThat(logs.get(0).get("reg_dttm")).isNotNull();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_SY_ACCS_LOG WHERE scrn_id='UI-29'", Integer.class)).isZero();
    }
    @Test void databaseAuditFailureRollsBackAndDoesNotReturnSuccessfulFile() throws Exception {
        db.execute("ALTER TABLE TB_SY_ACCS_LOG ADD CONSTRAINT forecast_download_rejected CHECK (scrn_id <> 'UI-17')");
        var before = plans();
        mvc.perform(get("/api/common/manpower-forecasts/excel").param("year", "2026")).andExpect(status().isInternalServerError());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_SY_ACCS_LOG WHERE scrn_id='UI-17'", Integer.class)).isZero();
        assertThat(plans()).isEqualTo(before);
    }
}
