package com.company.common.commonops;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.manpowercomparisons.controller.CommonManpowerComparisonController;
import com.company.common.manpowercomparisons.mapper.ManpowerComparisonMapper;
import com.company.common.manpowercomparisons.service.*;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
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

@EnabledIfEnvironmentVariable(named="NAVIGATION_TEST_DB_URL", matches="jdbc:postgresql:.*")
@WebMvcTest(CommonManpowerComparisonController.class)
@ContextConfiguration(classes={CommonManpowerComparisonController.class, CommonManpowerComparisonService.class,
    ManpowerComparisonRules.class, CalculationRuleService.class, CodeExceptionHandler.class,
    ApiExceptionHandler.class, ManpowerComparisonPersistenceTest.Persistence.class,
    com.company.common.support.service.ExcelExportService.class,
    com.company.common.support.service.BusinessCalendar.class,
    com.company.common.support.service.CurrentBusinessActor.class})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ManpowerComparisonPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired Owned owned;
    @Autowired ManpowerComparisonWriteCounter writes;
    JdbcTemplate db;
    List<String> jobs;
    String sourceCode;
    record Owned(String name) {}

    @Intercepts(@Signature(type=Executor.class, method="update", args={MappedStatement.class,Object.class}))
    public static class ManpowerComparisonWriteCounter implements Interceptor {
        final AtomicInteger count = new AtomicInteger();
        @Override public Object intercept(Invocation invocation) throws Throwable {
            count.incrementAndGet();
            return invocation.proceed();
        }
    }

    @TestConfiguration(proxyBeanMethods=false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean Owned owned() { return new Owned("comparison_" + UUID.randomUUID().toString().replace("-", "")); }
        @Bean DataSource dataSource(Owned owned) throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL"), user=System.getenv("NAVIGATION_TEST_DB_USER"),
                password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var c=DriverManager.getConnection(url,user,password); var s=c.createStatement()) {
                s.execute("CREATE SCHEMA " + owned.name());
            }
            var source=new UnpooledDataSource("org.postgresql.Driver",url,user,password);
            var properties=new Properties();
            properties.setProperty("currentSchema",owned.name());
            source.setDriverProperties(properties);
            var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment(
                "postgresql",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();
            return source;
        }
        @Bean ManpowerComparisonWriteCounter writeCounter() { return new ManpowerComparisonWriteCounter(); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source, ManpowerComparisonWriteCounter writes) throws Exception {
            var factory=new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/manpowercomparisons/ManpowerComparisonMapper.xml"),
                new ClassPathResource("mapper/CalculationRuleMapper.xml"),
                new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var config=new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config);
            factory.setPlugins(writes);
            return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean ManpowerComparisonMapper comparisonMapper(SqlSessionTemplate s) { return s.getMapper(ManpowerComparisonMapper.class); }
        @Bean CalculationRuleMapper calculationRuleMapper(SqlSessionTemplate s) { return s.getMapper(CalculationRuleMapper.class); }
        @Bean UserPreferencePeriodSourceMapper periodMapper(SqlSessionTemplate s) { return s.getMapper(UserPreferencePeriodSourceMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }

    @BeforeEach void setup() {
        db=new JdbcTemplate(source);
        jobs=db.queryForList("SELECT CODE FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB' AND USE_YN='Y' AND DEL_YN='N' ORDER BY CODE LIMIT 3",String.class);
        assertThat(jobs).hasSize(3);
        sourceCode=db.queryForObject("SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='SRC' AND USE_YN='Y' AND DEL_YN='N'",String.class);
        assertThat(sourceCode).isNotBlank();
        db.update("INSERT INTO TB_BS_PART(PART_ID,PART_NM,HQ_YN,USE_YN,REG_USER_ID) VALUES('comparison-part','시험 파트','N','Y','admin')");
        db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,GRADE_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,REG_USER_ID) VALUES('comparison-person','comparison-person','시험 인력','FT',?,'MD','DRCT','comparison-part','IDLE','admin')",jobs.get(0));
        project("P-COMPARE","2026-01-01","2026-12-31","ING");
        plan("P-COMPARE",jobs.get(0),"3");
        plan("P-COMPARE",jobs.get(2),"0");
        assignment("A-COMPARE-1","P-COMPARE",jobs.get(1),"ACT");
        assignment("A-COMPARE-2","P-COMPARE",jobs.get(1),"ACT");
        assignment("A-COMPARE-X","P-COMPARE",jobs.get(1),"CNCL");
        month("A-COMPARE-1","P-COMPARE","202601","0.60");
        month("A-COMPARE-1","P-COMPARE","202612","0.70");
        month("A-COMPARE-1","P-COMPARE","202512","8.00");
        month("A-COMPARE-1","P-COMPARE","202701","9.00");
        month("A-COMPARE-2","P-COMPARE","202612","0.80");
        month("A-COMPARE-X","P-COMPARE","202612","7.00");
        writes.count.set(0);
    }
    @AfterEach void cleanup() throws Exception {
        try (var c=source.getConnection(); var s=c.createStatement()) { s.execute("DROP SCHEMA " + owned.name() + " CASCADE"); }
    }
    void project(String id,String start,String end,String state) {
        db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES(?,?,'IN',?,?::date,?::date,0,0,0,'admin')",id,id,state,start,end);
    }
    void plan(String project,String job,String mm) {
        db.update("INSERT INTO TB_PJ_MNPW_PLAN(PROJ_ID,JOB_CD,PLAN_MM,SRC_CD,REG_USER_ID) VALUES(?,?,?,?, 'admin')",project,job,new BigDecimal(mm),sourceCode);
    }
    void assignment(String id,String project,String job,String state) {
        db.update("INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,CTRT_MM,INPUT_TYPE_CD,EXT_CNT,ASGN_STAT_CD,REG_USER_ID) VALUES(?,?,'comparison-person',?,'2025-01-01','2027-12-31',1,'NEW',0,?,'admin')",id,project,job,state);
    }
    void month(String assignment,String project,String ym,String mm) {
        db.update("INSERT INTO TB_PJ_ASGN_MON(ASGN_ID,PROJ_ID,DEV_ID,INPUT_YM,INPUT_DAY_CNT,MON_MM,SALES_AMT,FIX_YN,REG_USER_ID) VALUES(?,?,'comparison-person',?,1,?,0,'N','admin')",assignment,project,ym,new BigDecimal(mm));
    }
    JsonNode data(String project,String status) throws Exception {
        var request=get("/api/common/manpower-comparisons").param("year","2026");
        if (project!=null) request.param("projectId",project);
        if (status!=null) request.param("differenceStatus",status);
        return json.readTree(mvc.perform(request).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.meta").isMap())
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }
    JsonNode job(JsonNode data,String job) {
        for (var row:data.path("items")) if (job.equals(row.path("jobCode").asText())) return row;
        throw new AssertionError("expected stored job: " + job);
    }

    @Test void happyUnionHasBothSidesAndStoredZeroWithoutPlanMultiplication() throws Exception {
        var result=data("P-COMPARE",null);
        // 계획 0이고 실제도 없는 직무(jobs[2])는 줄이 나오지 않는다(식-67).
        assertThat(result.path("items").size()).isEqualTo(2);
        assertThat(job(result,jobs.get(0)).path("actualMm").decimalValue()).isEqualByComparingTo("0");
        assertThat(job(result,jobs.get(0)).path("differenceStatus").asText()).isEqualTo("부족");
        assertThat(job(result,jobs.get(1)).path("plannedMm").decimalValue()).isEqualByComparingTo("0");
        assertThat(job(result,jobs.get(1)).path("differenceStatus").asText()).isEqualTo("초과");
        for (var row:result.path("items")) assertThat(row.path("jobCode").asText()).isNotEqualTo(jobs.get(2));
        for (String field:List.of("plannedMm","actualMm","differenceMm")) {
            BigDecimal sum=BigDecimal.ZERO;
            for (var row:result.path("items")) sum=sum.add(row.path(field).decimalValue());
            assertThat(result.path("totals").path(field).decimalValue()).isEqualByComparingTo(sum);
        }
        assertThat(result.path("totals").path("plannedMm").decimalValue()).isEqualByComparingTo("3");
    }

    @Test void actualRecalculatesFromPersistedPastAndFutureMonthsAndExcludesOtherYearsAndCancelled() throws Exception {
        BigDecimal expected=db.queryForObject("SELECT SUM(m.MON_MM) FROM TB_PJ_ASGN_MON m JOIN TB_PJ_ASGN a ON a.ASGN_ID=m.ASGN_ID WHERE a.PROJ_ID='P-COMPARE' AND a.JOB_CD=? AND a.ASGN_STAT_CD<>'CNCL' AND m.INPUT_YM>='202601' AND m.INPUT_YM<='202612'",BigDecimal.class,jobs.get(1));
        var actual=job(data("P-COMPARE",null),jobs.get(1));
        assertThat(actual.path("actualMm").decimalValue()).isEqualByComparingTo(expected);
        assertThat(expected).isEqualByComparingTo(new BigDecimal("0.60").add(new BigDecimal("0.70")).add(new BigDecimal("0.80")));
        assertThat(actual.path("differenceStatus").asText()).isEqualTo("초과");
    }

    @Test void annualRatioUsesOriginalPlanAcrossInclusivePartialMonths() throws Exception {
        project("P-ANNUAL","2025-12-31","2026-02-01","END");
        plan("P-ANNUAL",jobs.get(0),"1");
        BigDecimal original=db.queryForObject("SELECT PLAN_MM FROM TB_PJ_MNPW_PLAN WHERE PROJ_ID='P-ANNUAL'",BigDecimal.class);
        BigDecimal expected=original.multiply(BigDecimal.valueOf(2)).divide(BigDecimal.valueOf(3),2,java.math.RoundingMode.HALF_UP);
        assertThat(data("P-ANNUAL",null).path("items").get(0).path("plannedMm").decimalValue()).isEqualByComparingTo(expected);
    }

    @Test void scopeIsOnlyYearOverlapAndNotProjectStatus() throws Exception {
        project("P-OUTSIDE","2024-01-01","2024-12-31","ING");
        plan("P-OUTSIDE",jobs.get(0),"10");
        project("P-HISTORY","2026-12-31","2027-01-01","LOSE");
        plan("P-HISTORY",jobs.get(0),"2");
        var result=data(null,null);
        var ids=new ArrayList<String>();
        result.path("items").forEach(row -> ids.add(row.path("projectId").asText()));
        assertThat(ids).contains("P-HISTORY").doesNotContain("P-OUTSIDE");
        assertThat(result.path("totals").path("plannedMm").decimalValue()).isEqualByComparingTo("4");
    }

    @Test void zeroIsMatchedButNonzeroCentIsNotAndTotalsFollowStatusFilter() throws Exception {
        assignment("A-ZERO","P-COMPARE",jobs.get(2),"ACT");
        month("A-ZERO","P-COMPARE","202612","0.01");
        var matched=data("P-COMPARE","맞음");
        assertThat(matched.path("items").size()).isZero();
        var excess=data("P-COMPARE","초과");
        assertThat(excess.path("items").size()).isEqualTo(2);
        assertThat(excess.path("totals").path("plannedMm").decimalValue()).isEqualByComparingTo("0");
        assertThat(excess.path("totals").path("actualMm").decimalValue()).isEqualByComparingTo("2.11");
    }

    Map<String,Long> counts() {
        var result=new TreeMap<String,Long>();
        for (String table:db.queryForList("SELECT tablename FROM pg_tables WHERE schemaname=current_schema() ORDER BY tablename",String.class)) {
            if (!table.matches("[a-z0-9_]+")) throw new AssertionError("unexpected table name");
            result.put(table,db.queryForObject("SELECT count(*) FROM " + table,Long.class));
        }
        return result;
    }

    @Test void successfulAndEmptyReadsLeaveSnapshotsCountsAndSchemaUntouchedAndIssueZeroWrites() throws Exception {
        var plans=db.queryForList("SELECT * FROM TB_PJ_MNPW_PLAN ORDER BY PROJ_ID,JOB_CD");
        var months=db.queryForList("SELECT * FROM TB_PJ_ASGN_MON ORDER BY ASGN_ID,INPUT_YM");
        var counts=counts();
        data("P-COMPARE",null);
        data("unknown",null);
        assertThat(writes.count.get()).isZero();
        assertThat(db.queryForList("SELECT * FROM TB_PJ_MNPW_PLAN ORDER BY PROJ_ID,JOB_CD")).isEqualTo(plans);
        assertThat(db.queryForList("SELECT * FROM TB_PJ_ASGN_MON ORDER BY ASGN_ID,INPUT_YM")).isEqualTo(months);
        assertThat(counts()).isEqualTo(counts);
    }

    @Test void unknownProjectIsEmptyWithAllNumericZeroTotals() throws Exception {
        var result=data("unknown",null);
        assertThat(result.path("items").size()).isZero();
        for (String field:List.of("plannedMm","actualMm","differenceMm"))
            assertThat(result.path("totals").path(field).decimalValue()).isEqualByComparingTo("0");
    }

    @Test void multipleAssignmentMonthsDoNotMultiplyTheSameJobPlan() throws Exception {
        plan("P-COMPARE",jobs.get(1),"2.10");
        var row=job(data("P-COMPARE",null),jobs.get(1));
        assertThat(row.path("plannedMm").decimalValue()).isEqualByComparingTo("2.10");
        assertThat(row.path("actualMm").decimalValue()).isEqualByComparingTo("2.10");
        assertThat(row.path("differenceMm").decimalValue()).isEqualByComparingTo("0");
        assertThat(row.path("differenceStatus").asText()).isEqualTo("맞음");
    }

    @Test void fixedPastAndScheduledDecemberMonthsAreBothAuthoritative() throws Exception {
        db.update("UPDATE TB_PJ_ASGN_MON SET FIX_YN='Y' WHERE ASGN_ID='A-COMPARE-1' AND INPUT_YM='202601'");
        var row=job(data("P-COMPARE",null),jobs.get(1));
        assertThat(row.path("actualMm").decimalValue()).isEqualByComparingTo("2.10");
        assertThat(writes.count.get()).isZero();
    }

    @Test void planZeroAndActualZeroMakeNoRowAndUnusedCatalogJobsAreNotExpanded() throws Exception {
        // 식-67: 계획 0(빈 값)·실제 0이면 UI-11에 줄이 나오지 않는다. 투입 줄만 있고 월 MM이 0이어도 같다.
        project("P-ZERO","2026-06-01","2026-06-30","END");
        plan("P-ZERO",jobs.get(1),"0");
        assignment("A-ONLY-ZERO","P-ZERO",jobs.get(0),"ACT");
        month("A-ONLY-ZERO","P-ZERO","202606","0");
        assignment("A-ZERO-BOTH","P-ZERO",jobs.get(1),"ACT");
        month("A-ZERO-BOTH","P-ZERO","202606","0");
        var result=data("P-ZERO",null);
        assertThat(result.path("items").size()).isZero();
        for (String field:List.of("plannedMm","actualMm","differenceMm"))
            assertThat(result.path("totals").path(field).decimalValue()).isEqualByComparingTo("0");
    }

    @Test void planWithoutActualIsShortAndActualWithoutPlanIsExcessInTheSameProject() throws Exception {
        project("P-SIDES","2026-01-01","2026-12-31","ING");
        plan("P-SIDES",jobs.get(0),"1.50");
        assignment("A-SIDES","P-SIDES",jobs.get(1),"ACT");
        month("A-SIDES","P-SIDES","202603","0.40");
        var result=data("P-SIDES",null);
        assertThat(result.path("items").size()).isEqualTo(2);
        assertThat(result.path("items").get(0).path("jobCode").asText()).isEqualTo(jobs.get(0));
        assertThat(result.path("items").get(0).path("differenceStatus").asText()).isEqualTo("부족");
        assertThat(result.path("items").get(0).path("differenceMm").decimalValue()).isEqualByComparingTo("-1.50");
        assertThat(result.path("items").get(1).path("differenceStatus").asText()).isEqualTo("초과");
        assertThat(result.path("items").get(1).path("plannedMm").decimalValue()).isEqualByComparingTo("0");
    }

    @Test void emptyDatabaseYearReturnsZeroRowsAndZeroTotals() throws Exception {
        db.update("DELETE FROM TB_PJ_ASGN_MON");
        db.update("DELETE FROM TB_PJ_ASGN");
        db.update("DELETE FROM TB_PJ_MNPW_PLAN");
        var result=data(null,null);
        assertThat(result.path("items").isArray()).isTrue();
        assertThat(result.path("items").size()).isZero();
        for (String field:List.of("plannedMm","actualMm","differenceMm"))
            assertThat(result.path("totals").path(field).decimalValue()).isEqualByComparingTo("0");
    }

    @Test void projectsTouchingEitherYearBoundaryRemainEligible() throws Exception {
        project("P-FIRST","2025-12-31","2026-01-01","END");
        project("P-LAST","2026-12-31","2027-01-01","PRE");
        plan("P-FIRST",jobs.get(0),"2");
        plan("P-LAST",jobs.get(0),"2");
        assertThat(data("P-FIRST",null).path("totals").path("plannedMm").decimalValue())
            .isEqualByComparingTo("1");
        assertThat(data("P-LAST",null).path("totals").path("plannedMm").decimalValue())
            .isEqualByComparingTo("1");
    }
}
