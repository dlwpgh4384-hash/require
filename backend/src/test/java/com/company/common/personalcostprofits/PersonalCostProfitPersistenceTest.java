package com.company.common.personalcostprofits;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.PersonalCostProfitDefinitions;
import com.company.common.personalcostprofits.controller.CommonPersonalCostProfitController;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitMapper;
import com.company.common.personalcostprofits.service.*;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
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
@WebMvcTest(CommonPersonalCostProfitController.class)
@ContextConfiguration(classes={CommonPersonalCostProfitController.class, CommonPersonalCostProfitService.class,
    PersonalCostProfitExcelService.class, PersonalCostProfitDefinitions.class, CalculationRuleService.class,
    UserPreferencePeriodBasis.class, CodeExceptionHandler.class, ApiExceptionHandler.class,
    PersonalCostProfitPersistenceTest.Persistence.class, ExcelExportService.class, BusinessCalendar.class, CurrentBusinessActor.class})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PersonalCostProfitPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired Owned owned;
    @Autowired PersonalCostProfitWriteCounter writes;
    JdbcTemplate db;
    String job;
    record Owned(String name) {}
    @Intercepts(@Signature(type=Executor.class, method="update", args={MappedStatement.class,Object.class}))
    public static class PersonalCostProfitWriteCounter implements Interceptor {
        final AtomicInteger count = new AtomicInteger();
        @Override public Object intercept(Invocation invocation) throws Throwable { count.incrementAndGet(); return invocation.proceed(); }
    }
    @TestConfiguration(proxyBeanMethods=false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean Owned owned() { return new Owned("personalprofit_" + UUID.randomUUID().toString().replace("-", "")); }
        @Bean DataSource dataSource(Owned owned) throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL"), user=System.getenv("NAVIGATION_TEST_DB_USER"), password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var c=DriverManager.getConnection(url,user,password); var s=c.createStatement()) { s.execute("CREATE SCHEMA " + owned.name()); }
            var ds=new UnpooledDataSource("org.postgresql.Driver",url,user,password);
            var properties=new Properties(); properties.setProperty("currentSchema",owned.name()); ds.setDriverProperties(properties);
            var configuration=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),ds));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration)).prepare();
            return ds;
        }
        @Bean PersonalCostProfitWriteCounter writeCounter() { return new PersonalCostProfitWriteCounter(); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source, PersonalCostProfitWriteCounter writes) throws Exception {
            var factory=new SqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/personalcostprofits/PersonalCostProfitMapper.xml"),
                new ClassPathResource("mapper/CalculationRuleMapper.xml"), new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var config=new org.apache.ibatis.session.Configuration(); config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config); factory.setPlugins(writes); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean PersonalCostProfitMapper personalMapper(SqlSessionTemplate s) { return s.getMapper(PersonalCostProfitMapper.class); }
        @Bean CalculationRuleMapper calculationMapper(SqlSessionTemplate s) { return s.getMapper(CalculationRuleMapper.class); }
        @Bean UserPreferencePeriodSourceMapper periodMapper(SqlSessionTemplate s) { return s.getMapper(UserPreferencePeriodSourceMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void setup() {
        db=new JdbcTemplate(source);
        db.update("UPDATE TB_SY_CODE SET CODE_VAL=202601 WHERE CODE='CASH_BASE_YM'");
        db.update("INSERT INTO TB_CL_CLOSE(CLOSE_YM,CLOSE_STAT_CD,REG_USER_ID) VALUES('202601','CLOSE','admin')");
        job=db.queryForObject("SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB' AND USE_YN='Y' AND DEL_YN='N'",String.class);
        db.update("INSERT INTO TB_BS_PART(PART_ID,PART_NM,HQ_YN,USE_YN,REG_USER_ID) VALUES('personal-part','시험 영업파트','N','Y','admin')");
        person("personal-a", "검색%동일", "CT");
        db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES('personal-p','프로젝트','MO','ING','2026-01-01','2026-12-31',10000,10,20,'admin')");
        assignment("personal-paid", "NEW"); assignment("personal-free", "FREE");
        payroll("personal-a", "202601", 1000); payroll("personal-a", "202602", 1000);
        snapshot("personal-paid", "0.5", 900, 400); snapshot("personal-free", "0.5", 0, 600);
        db.update("INSERT INTO TB_PJ_ASGN_MON(ASGN_ID,PROJ_ID,DEV_ID,INPUT_YM,INPUT_DAY_CNT,MON_MM,SALES_AMT,FIX_YN,REG_USER_ID) VALUES('personal-paid','personal-p','personal-a','202602',14,0.5,800,'N','admin')");
        db.update("INSERT INTO TB_PJ_ASGN_MON(ASGN_ID,PROJ_ID,DEV_ID,INPUT_YM,INPUT_DAY_CNT,MON_MM,SALES_AMT,FIX_YN,REG_USER_ID) VALUES('personal-free','personal-p','personal-a','202602',0,0,0,'N','admin')");
        writes.count.set(0);
    }
    void person(String id,String name,String type) {
        db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,GRADE_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,JNCMP_DT,REG_USER_ID) VALUES(?,?,?,?,?,'MD','DRCT','personal-part','IDLE','2026-01-01','admin')",id,id,name,type,job);
    }
    void assignment(String id,String input) {
        db.update("INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,CTRT_MM,INPUT_TYPE_CD,EXT_CNT,ASGN_STAT_CD,REG_USER_ID) VALUES(?,'personal-p','personal-a',?,'2026-01-01','2026-12-31',0.5,?,0,'ACT','admin')",id,job,input);
    }
    void payroll(String id,String month,int salary) {
        db.update("INSERT INTO TB_HR_MON_PAY(PAY_YM,DEV_ID,PAY_AMT,ALWNC_AMT,INSR_CMPNY_AMT,RETIRE_RSV_AMT,EMP_TYPE_CD,DRCT_CLS_CD,REG_USER_ID) VALUES(?,?,?,0,100,0,'CT','DRCT','admin')",month,id,salary);
    }
    void snapshot(String assignment,String mm,int sales,int labor) {
        db.update("INSERT INTO TB_CO_MON_COST(COST_YM,DEV_ID,PROJ_ID,ASGN_ID,ASGN_MM,FREE_MM,SALES_AMT,LABOR_ALLOC_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,DRCT_EXPS_AMT,COST_CLS_CD,CTRT_TYPE_CD,FIX_YN,REG_USER_ID) VALUES('202601','personal-a','personal-p',?,?,0,?,?,10,20,30,'ALLOC','MO','Y','admin')",assignment,new BigDecimal(mm),sales,labor);
    }
    @AfterEach void cleanup() throws Exception {
        try (var c=source.getConnection(); var s=c.createStatement()) { s.execute("DROP SCHEMA " + owned.name() + " CASCADE"); }
    }
    JsonNode read(String mode,String name) throws Exception {
        var request=get("/api/common/personal-cost-profits").param("year","2026").param("baseMonth","2").param("displayMode",mode);
        if (name!=null) request.param("name",name);
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }
    List<String> businessSnapshot() {
        List<String> result=new ArrayList<>();
        for (String table:List.of("TB_CO_MON_COST","TB_HR_MON_PAY","TB_PJ_ASGN_MON")) {
            result.addAll(db.queryForList("SELECT row_to_json(t)::text FROM " + table + " t ORDER BY row_to_json(t)::text",String.class));
        }
        return result;
    }
    @Test void databaseAggregationUsesCompositeIdentityAndReadModesNeverWriteSourcesOrAudit() throws Exception {
        var before=businessSnapshot();
        var annual=read("연누계",null);
        assertThat(annual.path("items")).hasSize(1);
        // 투입ID마다 한 줄(1월 마감 + 2월 계산을 더함): 유상·무상지원·대기 3줄
        var allocations=annual.path("items").get(0).path("allocations");
        assertThat(allocations).hasSize(3);
        // 저장행 순서(투입ID 순): 무상지원(personal-free) → 유상(personal-paid)
        assertThat(allocations.get(0).path("assignmentId").asText()).isEqualTo("personal-free");
        assertThat(allocations.get(0).path("free").asBoolean()).isTrue();
        assertThat(allocations.get(1).path("assignmentId").asText()).isEqualTo("personal-paid");
        assertThat(allocations.get(1).path("mm").decimalValue()).isEqualByComparingTo("1.00");
        assertThat(allocations.get(2).path("assignmentId").asText()).isEqualTo("IDLE");
        assertThat(annual.path("items").get(0).path("managementNumber").asText()).isEqualTo("personal-a");
        read("월평균",null); read("연누계",null);
        assertThat(businessSnapshot()).isEqualTo(before);
        assertThat(writes.count).hasValue(0);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-03' AND ACT_TYPE_CD='DNLD'",Long.class)).isZero();
    }
    @Test void sourceMutationChangesLiveOnlyAndAdditionalDirectExpenseIsNotPersonal() throws Exception {
        var before=read("연누계",null).path("items").get(0);
        db.update("UPDATE TB_HR_MON_PAY SET PAY_AMT=2000 WHERE PAY_YM='202602'");
        db.update("UPDATE TB_PJ_PROJ SET TEAM_EXPS_AMT=100,PROJ_EXPS_AMT=200 WHERE PROJ_ID='personal-p'");
        db.update("INSERT INTO TB_PJ_DRCT_EXPS(PROJ_ID,EXPS_YM,EXPS_SN,DRCT_EXPS_ITEM_CD,EXPS_AMT,REG_USER_ID) VALUES('personal-p','202602',1,(SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='DRCT_EXPS_ITEM'),999999,'admin')");
        var after=read("연누계",null).path("items").get(0);
        // 1월(마감)만 있는 무상지원 줄은 저장값 그대로
        assertThat(after.path("allocations").get(0).path("assignmentId").asText()).isEqualTo("personal-free");
        assertThat(after.path("allocations").get(0)).isEqualTo(before.path("allocations").get(0));
        assertThat(after.path("totalCost").decimalValue()).isGreaterThan(before.path("totalCost").decimalValue());
        assertThat(after.path("totalCost").decimalValue()).isLessThan(new BigDecimal("999999"));
    }
    @Test void closedIdleAndIndirectRowsKeepTheLiveMmAndFreeMmIsNotCountedTwice() throws Exception {
        var before=read("연누계",null).path("items").get(0);
        // DDL: free rows carry FREE_MM = ASGN_MM; fixed idle rows carry 1.0 - month MM, indirect rows 1.0 (both shown as the idle line, MM 0).
        db.update("UPDATE TB_CO_MON_COST SET FREE_MM=ASGN_MM WHERE ASGN_ID='personal-free'");
        db.update("INSERT INTO TB_CO_MON_COST(COST_YM,DEV_ID,PROJ_ID,ASGN_ID,ASGN_MM,FREE_MM,SALES_AMT,LABOR_ALLOC_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,DRCT_EXPS_AMT,COST_CLS_CD,FIX_YN,REG_USER_ID) VALUES('202601','personal-a','IDLE','IDLE',0.25,0,0,0,0,0,0,'IDLE','Y','admin')");
        var after=read("연누계",null).path("items").get(0);
        assertThat(after.path("mm").decimalValue()).isEqualByComparingTo(before.path("mm").decimalValue());
        assertThat(after.path("allocations").get(0).path("mm")).isEqualTo(before.path("allocations").get(0).path("mm"));
        var idle=after.path("allocations").get(after.path("allocations").size()-1);
        assertThat(idle.path("assignmentId").asText()).isEqualTo("IDLE");
        assertThat(idle.path("mm").decimalValue()).isEqualByComparingTo("0");
        db.update("UPDATE TB_CO_MON_COST SET PROJ_ID='INDRCT',ASGN_ID='INDRCT',ASGN_MM=1,COST_CLS_CD='INDRCT' WHERE ASGN_ID='IDLE'");
        var indirect=read("연누계",null).path("items").get(0);
        assertThat(indirect.path("mm").decimalValue()).isEqualByComparingTo(before.path("mm").decimalValue());
        assertThat(indirect.path("allocations").get(indirect.path("allocations").size()-1).path("assignmentId").asText()).isEqualTo("IDLE");
    }
    @Test void literalSubstringReturnsEveryMatchingPersonWithoutSqlWildcardInterpretation() throws Exception {
        person("personal-b","검색%동일", "CT"); person("personal-c","검색다름", "CT");
        assertThat(read("연누계","%동일").path("items")).hasSize(2);
        // 앞뒤 공백은 빼고 찾는다(시안 trim)
        assertThat(read("연누계"," %동일 ").path("items")).hasSize(2);
        assertThat(read("연누계","없는 사람").path("items")).isEmpty();
    }
    @Test void selfAffiliationFindsPeopleWithoutClient() throws Exception {
        // 소속 「자사」 = 소속 거래처가 없는 사람(시험 인력은 BLNG_CLNT_ID 없음)
        var body=mvc.perform(get("/api/common/personal-cost-profits").param("year","2026").param("baseMonth","2")
            .param("displayMode","연누계").param("affiliationId","__self"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(json.readTree(body).path("data").path("items")).hasSize(1);
    }
    @Test void leavePayrollIsHiddenFromParentsAndIncludedInAnonymousIdleTotals() throws Exception {
        var base=read("연누계",null).path("totals").path("totalCost").decimalValue();
        person("personal-leave","휴직", "CT"); payroll("personal-leave","202602",200);
        db.update("INSERT INTO TB_HR_DEV_STAT(DEV_ID,STAT_SN,WORK_STAT_CD,BGN_DT,REG_USER_ID) VALUES('personal-leave',1,'LEAVE','2026-02-01','admin')");
        var result=read("연누계",null);
        assertThat(result.path("items")).hasSize(1);
        assertThat(result.path("summary").path("totalPeople").asInt()).isEqualTo(2);
        assertThat(result.path("summary").path("leavePeople").asInt()).isEqualTo(1);
        assertThat(result.path("totals").path("totalCost").decimalValue()).isEqualByComparingTo(base.add(new BigDecimal("300")));
    }
    @Test void successfulWorkbookCommitsExactlyOneDownloadWithDatabaseTimestampAndNoBusinessChanges() throws Exception {
        var before=businessSnapshot();
        mvc.perform(get("/api/common/personal-cost-profits/excel").param("year","2026").param("baseMonth","2").param("displayMode","연누계"))
            .andExpect(status().isOk());
        assertThat(businessSnapshot()).isEqualTo(before);
        assertThat(writes.count).hasValue(1);
        var rows=db.queryForList("SELECT ACT_DTTM FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-03' AND ACT_TYPE_CD='DNLD'");
        assertThat(rows).hasSize(1); assertThat(rows.get(0).get("act_dttm")).isNotNull();
    }
    @Test void rejectedDownloadDoesNotAudit() throws Exception {
        var before=businessSnapshot();
        mvc.perform(get("/api/common/personal-cost-profits/excel").param("year","2026").param("baseMonth","2").param("displayMode","연누계").param("columns","unknown"))
            .andExpect(status().isBadRequest());
        assertThat(businessSnapshot()).isEqualTo(before); assertThat(writes.count).hasValue(0);
    }
    @Test void failedAuditCannotReturnAWorkbookOrChangeBusinessSources() throws Exception {
        var before=businessSnapshot();
        db.execute("CREATE FUNCTION reject_personal_download() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'download audit rejected'; END $$");
        db.execute("CREATE TRIGGER reject_personal_download BEFORE INSERT ON TB_SY_ACCS_LOG FOR EACH ROW EXECUTE FUNCTION reject_personal_download()");
        mvc.perform(get("/api/common/personal-cost-profits/excel").param("year","2026").param("baseMonth","2").param("displayMode","연누계"))
            .andExpect(status().isInternalServerError());
        assertThat(businessSnapshot()).isEqualTo(before);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-03' AND ACT_TYPE_CD='DNLD'",Long.class)).isZero();
    }
}
