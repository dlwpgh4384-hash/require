package com.company.common.salespipeline.controller;

import com.company.common.calculation.dto.CalculationRuleContracts.Standard;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.*;
import com.company.common.codegroups.dto.CodeContracts.Item;
import com.company.common.salesopportunities.mapper.SalesOpportunityMapper;
import com.company.common.salespipeline.mapper.SalesPipelineMapper;
import com.company.common.salespipeline.service.*;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.company.common.withdrawals.service.WithdrawalCalculationRules;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@WebMvcTest(CommonSalesPipelineController.class)
@ContextConfiguration(classes = {CommonSalesPipelineController.class, CommonSalesPipelineService.class,
    SalesPipelineCalculationRules.class, SalesPipelineForecastService.class, SalesPipelineExcelService.class,
    SalesPipelineDefinitions.class, SalesPipelineApprovalStandards.class, WithdrawalCalculationRules.class,
    CurrentBusinessActor.class, ExcelExportService.class, CodeExceptionHandler.class, ApiExceptionHandler.class,
    SalesPipelinePersistenceTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SalesPipelinePersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired PipelineDatabase owned;
    @MockBean CalculationRuleService metadata;
    @MockBean SalesPipelineCodes codes;
    JdbcTemplate db;
    record PipelineDatabase(String name) {}
    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean PipelineDatabase pipelineDatabase() { return new PipelineDatabase("pipeline_" + UUID.randomUUID().toString().replace("-", "")); }
        @Bean DataSource dataSource(PipelineDatabase owned) throws Exception {
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
            factory.setMapperLocations(new ClassPathResource("mapper/salespipeline/SalesPipelineMapper.xml"),
                new ClassPathResource("mapper/salesopportunities/SalesOpportunityMapper.xml"),
                new ClassPathResource("mapper/withdrawals/WithdrawalMapper.xml"));
            var configuration = new org.apache.ibatis.session.Configuration(); configuration.setMapUnderscoreToCamelCase(false);
            configuration.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(configuration); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean SalesPipelineMapper salesPipelineMapper(SqlSessionTemplate session) { return session.getMapper(SalesPipelineMapper.class); }
        @Bean SalesOpportunityMapper salesOpportunityMapper(SqlSessionTemplate session) { return session.getMapper(SalesOpportunityMapper.class); }
        @Bean WithdrawalMapper withdrawalMapper(SqlSessionTemplate session) { return session.getMapper(WithdrawalMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean BusinessCalendar businessCalendar() { return new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC)); }
    }
    @BeforeEach void setup() throws Exception {
        db = new JdbcTemplate(source);
        String seed = new ClassPathResource("db/migration/V8__calculation_rule_seed.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        when(metadata.definition(anyString())).thenAnswer(call -> CommonSalesPipelineControllerTest.definition(seed, call.getArgument(0)));
        when(metadata.effectiveStandard(anyString(), any())).thenAnswer(call -> new Standard(call.getArgument(0), YearMonth.of(2026, 1), new BigDecimal(switch ((String)call.getArgument(0)) {
            case "QUOTE_APRV_RT" -> "15"; case "QUOTE_REAPRV_GAP" -> "0.5"; case "TGT_MRGN_RT" -> "20";
            case "SHORT_PERSON" -> "0.4"; case "HIRE_LEAD_MON" -> "2";
            case "STEP_PRBL_1" -> "10"; case "STEP_PRBL_2" -> "30"; case "STEP_PRBL_3" -> "60"; case "STEP_PRBL_4" -> "90";
            default -> "50";
        })));
        when(codes.selectable(anyString())).thenAnswer(call -> {
            var values = switch ((String)call.getArgument(0)) {
                case "JOB" -> List.of("BE"); case "GRADE" -> List.of("MD");
                case "SALES_STEP" -> List.of("FIND", "PROP", "NEGO", "PRE"); default -> List.<String>of();
            };
            return values.stream().map(code -> { var item = new Item(); item.code = code; item.codeName = code; return item; }).toList();
        });
        db.update("INSERT INTO TB_BS_PART(part_id,part_nm,reg_user_id) VALUES('pipeline-part','시험 영업파트','admin')");
        db.update("INSERT INTO TB_BS_CLNT(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES('pipeline-customer','CUST','시험 고객','1234567890','admin')");
        db.update("INSERT INTO TB_SL_OPP(opp_id,opp_nm,cust_clnt_id,part_id,sales_step_cd,win_prbl_rt,expct_amt,bgn_plan_ym,expct_prd_mcnt,opp_stat_cd,aprv_stat_cd,reg_user_id,reg_dttm) VALUES('pipeline-ongoing','시험 기회','pipeline-customer','pipeline-part','PROP',30,300000000,'202612',6,'ING','WAIT','admin',TIMESTAMPTZ '2026-11-01 00:00:00Z')");
        db.update("INSERT INTO TB_SL_OPP_NEED(opp_id,need_sn,job_cd,grade_cd,need_cnt,bgn_ym,prd_mcnt,mon_mm,expct_uprc,reg_user_id) VALUES('pipeline-ongoing',1,'BE','MD',2,'202612',6,1,8000000,'admin')");
    }
    @AfterEach void cleanup() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) { statement.execute("DROP SCHEMA " + owned.name() + " CASCADE"); }
    }
    void payroll(String id, String job, long cost) {
        db.update("INSERT INTO TB_HR_DEV(dev_id,mng_no,dev_nm,emp_type_cd,grade_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES(?,?,?,'EMP','MD',?,'DIRECT','pipeline-part','WORK','admin')", id, id, id, job);
        db.update("INSERT INTO TB_HR_MON_PAY(pay_ym,dev_id,pay_amt,alwnc_amt,insr_cmpny_amt,retire_rsv_amt,emp_type_cd,drct_cls_cd,reg_user_id) VALUES('202610',?,?,0,0,0,'EMP','DIRECT','admin')", id, cost);
        db.update("INSERT INTO TB_CO_MON_COST(cost_ym,dev_id,proj_id,asgn_id,asgn_mm,free_mm,sales_amt,labor_alloc_amt,team_exps_amt,proj_exps_amt,drct_exps_amt,cost_cls_cd,reg_user_id) VALUES('202610',?,'IDLE','IDLE',0,0,0,?,0,0,0,'IDLE','admin')", id, cost);
    }
    @Test void quoteUsesLatestPayrollCostsAndPreservesAllOpportunityRowsAndTimestamps() throws Exception {
        payroll("pipeline-dev-a", "BE", 6700000); payroll("pipeline-dev-b", "BE", 6900000);
        var opportunities = db.queryForList("SELECT * FROM TB_SL_OPP ORDER BY opp_id");
        var needs = db.queryForList("SELECT * FROM TB_SL_OPP_NEED ORDER BY opp_id,need_sn");
        mvc.perform(post("/api/common/sales-pipeline/quote").contentType("application/json").content("""
            {"needs":[{"jobCode":"BE","gradeCode":"MD","requiredCount":2,"startMonth":"2026-12","months":6,"monthlyMm":1,"estimatedUnitPrice":8000000}]}
            """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.expectedRevenue").value(96000000))
            .andExpect(jsonPath("$.data.expectedCost").value(81600000)).andExpect(jsonPath("$.data.profitRate").value(15));
        assertThat(db.queryForList("SELECT * FROM TB_SL_OPP ORDER BY opp_id")).isEqualTo(opportunities);
        assertThat(db.queryForList("SELECT * FROM TB_SL_OPP_NEED ORDER BY opp_id,need_sn")).isEqualTo(needs);
    }
    @Test void pipelineExcludesClosedRowsAndFourteenDaysIsNotStale() throws Exception {
        db.update("INSERT INTO TB_SL_OPP SELECT 'pipeline-stopped',opp_nm,cust_clnt_id,part_id,sales_step_cd,win_prbl_rt,prbl_manual_yn,expct_amt,bgn_plan_ym,ctrt_type_cd,expct_prd_mcnt,next_todo_cn,next_todo_ddln_dt,work_plc_addr,rmrk,'STOP',expct_prft_rt,aprv_stat_cd,aprv_prft_rt,CURRENT_DATE,lose_rsn_cd,lose_memo,proj_id,reg_user_id,reg_dttm,mod_user_id,mod_dttm FROM TB_SL_OPP WHERE opp_id='pipeline-ongoing'");
        mvc.perform(get("/api/common/sales-pipeline")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.ongoingCount").value(1)).andExpect(jsonPath("$.data.summary.staleCount").value(0))
            // 종료 탭 건수는 사용 중지를 빼고 센다(종료 목록 기본 조회와 같은 수).
            .andExpect(jsonPath("$.data.summary.closedCount").value(0));
        mvc.perform(get("/api/common/sales-pipeline/closed")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(0));
        mvc.perform(get("/api/common/sales-pipeline/closed").param("includeStopped", "true")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].result").value("STOP"));
    }
    @Test void forecastExcelUsesSameMonthRecordsAndPersistsDnldAfterSuccess() throws Exception {
        var response = mvc.perform(get("/api/common/sales-pipeline/forecast")).andExpect(status().isOk()).andReturn().getResponse();
        var months = json.readTree(response.getContentAsString()).path("data").path("months");
        assertThat(months).hasSize(6); assertThat(months.get(0).path("month").asText()).isEqualTo("2026-12");
        var file = mvc.perform(get("/api/common/sales-pipeline/forecast/excel")).andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "6")).andReturn().getResponse();
        try (var book = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(file.getContentAsByteArray()))) {
            for (int i = 0; i < 6; i++) {
                var row = book.getSheetAt(0).getRow(i + 3);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo(months.get(i).path("month").asText());
                assertThat(row.getCell(2).getNumericCellValue()).isEqualTo(months.get(i).path("weightedOpportunityRevenue").asDouble());
            }
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_SY_ACCS_LOG WHERE scrn_id='UI-29' AND act_type_cd='DNLD'", Integer.class)).isEqualTo(1);
    }
}
