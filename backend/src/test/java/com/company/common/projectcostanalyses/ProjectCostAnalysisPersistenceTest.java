package com.company.common.projectcostanalyses;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.ProjectCostAnalysisDefinitions;
import com.company.common.projectcostanalyses.controller.CommonProjectCostAnalysisController;
import com.company.common.projectcostanalyses.mapper.ProjectCostAnalysisMapper;
import com.company.common.projectcostanalyses.service.*;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.*;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="NAVIGATION_TEST_DB_URL",matches="jdbc:postgresql:.*")
@WebMvcTest(CommonProjectCostAnalysisController.class)
@ContextConfiguration(classes={CommonProjectCostAnalysisController.class,CommonProjectCostAnalysisService.class,
    ProjectCostAnalysisExcelService.class,ProjectCostAnalysisDefinitions.class,CalculationRuleService.class,
    UserPreferencePeriodBasis.class,CodeExceptionHandler.class,ApiExceptionHandler.class,
    ProjectCostAnalysisPersistenceTest.Persistence.class,ExcelExportService.class,BusinessCalendar.class,CurrentBusinessActor.class})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProjectCostAnalysisPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired Owned owned;
    @Autowired ProjectCostAnalysisWriteCounter writes;
    JdbcTemplate db;
    record Owned(String name) {}
    @Intercepts(@Signature(type=Executor.class,method="update",args={MappedStatement.class,Object.class}))
    public static class ProjectCostAnalysisWriteCounter implements Interceptor {
        final AtomicInteger count=new AtomicInteger();
        @Override public Object intercept(Invocation invocation) throws Throwable {count.incrementAndGet();return invocation.proceed();}
    }
    @TestConfiguration(proxyBeanMethods=false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean Owned owned() {return new Owned("projectanalysis_"+UUID.randomUUID().toString().replace("-",""));}
        @Bean DataSource dataSource(Owned owned) throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL"),user=System.getenv("NAVIGATION_TEST_DB_USER"),password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("CREATE SCHEMA "+owned.name());}
            var ds=new UnpooledDataSource("org.postgresql.Driver",url,user,password);
            var properties=new Properties();properties.setProperty("currentSchema",owned.name());ds.setDriverProperties(properties);
            var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),ds));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();return ds;
        }
        @Bean ProjectCostAnalysisWriteCounter counter(){return new ProjectCostAnalysisWriteCounter();}
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source,ProjectCostAnalysisWriteCounter counter) throws Exception {
            var factory=new SqlSessionFactoryBean();factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/personalcostprofits/PersonalCostProfitMapper.xml"),
                new ClassPathResource("mapper/projectcostanalyses/ProjectCostAnalysisMapper.xml"),
                new ClassPathResource("mapper/CalculationRuleMapper.xml"),new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var config=new org.apache.ibatis.session.Configuration();config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config);factory.setPlugins(counter);return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory){return new SqlSessionTemplate(factory);}
        @Bean ProjectCostAnalysisMapper analysisMapper(SqlSessionTemplate s){return s.getMapper(ProjectCostAnalysisMapper.class);}
        @Bean CalculationRuleMapper calculationMapper(SqlSessionTemplate s){return s.getMapper(CalculationRuleMapper.class);}
        @Bean UserPreferencePeriodSourceMapper periodMapper(SqlSessionTemplate s){return s.getMapper(UserPreferencePeriodSourceMapper.class);}
        @Bean DataSourceTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void setup(){
        db=new JdbcTemplate(source);
        db.update("UPDATE TB_SY_CODE SET CODE_VAL=202501 WHERE CODE='CASH_BASE_YM'");
        db.update("INSERT INTO TB_CL_CLOSE(CLOSE_YM,CLOSE_STAT_CD,REG_USER_ID) VALUES('202601','CLOSE','admin')");
        String job=db.queryForObject("SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB' AND USE_YN='Y' AND DEL_YN='N'",String.class);
        db.update("INSERT INTO TB_BS_PART(PART_ID,PART_NM,HQ_YN,USE_YN,REG_USER_ID) VALUES('analysis-part','시험파트','N','Y','admin')");
        db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,GRADE_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,JNCMP_DT,REG_USER_ID) VALUES('analysis-a','analysis-a','분석 인력','CT',?,'MD','DRCT','analysis-part','IDLE','2025-01-01','admin')",job);
        db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,ORGNL_CTRT_AMT,CTRT_AMT,BDGT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES('analysis-p','분석 프로젝트','MO','ING','2025-12-01','2026-12-31',10000,10000,10000,10,20,'admin')");
        db.update("INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,CTRT_MM,INPUT_TYPE_CD,EXT_CNT,ASGN_STAT_CD,BILL_UPRC,REG_USER_ID) VALUES('analysis-paid','analysis-p','analysis-a',?,'2025-12-01','2026-12-31',0.5,'NEW',0,'ACT',2000,'admin')",job);
        for(String month:List.of("202512","202601","202602")) {
            db.update("INSERT INTO TB_HR_MON_PAY(PAY_YM,DEV_ID,PAY_AMT,ALWNC_AMT,INSR_CMPNY_AMT,RETIRE_RSV_AMT,EMP_TYPE_CD,DRCT_CLS_CD,REG_USER_ID) VALUES(?,'analysis-a',1000,0,100,0,'CT','DRCT','admin')",month);
            db.update("INSERT INTO TB_PJ_ASGN_MON(ASGN_ID,PROJ_ID,DEV_ID,INPUT_YM,INPUT_DAY_CNT,MON_MM,SALES_AMT,FIX_YN,REG_USER_ID) VALUES('analysis-paid','analysis-p','analysis-a',?,14,0.5,1000,'N','admin')",month);
        }
        db.update("INSERT INTO TB_CO_MON_COST(COST_YM,DEV_ID,PROJ_ID,ASGN_ID,ASGN_MM,FREE_MM,SALES_AMT,LABOR_ALLOC_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,DRCT_EXPS_AMT,COST_CLS_CD,CTRT_TYPE_CD,FIX_YN,REG_USER_ID) VALUES('202601','analysis-a','analysis-p','analysis-paid',0.5,0,1000,400,10,20,30,'ALLOC','MO','Y','admin')");
        writes.count.set(0);
    }
    @AfterEach void cleanupSchema() throws Exception {
        try(var c=source.getConnection();var s=c.createStatement()){s.execute("DROP SCHEMA "+owned.name()+" CASCADE");}
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String suffix,int month){
        return get("/api/common/project-cost-analyses"+suffix).param("year","2026").param("baseMonth",Integer.toString(month))
            .param("projectId","analysis-p").param("headcountChange","1").param("monthlyCostPerPerson","100")
            .param("monthlyPricePerPerson","200").param("months","1");
    }
    JsonNode read(String suffix,int month) throws Exception {
        return json.readTree(mvc.perform(request(suffix,month)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }
    List<String> snapshot(){
        List<String> rows=new ArrayList<>();
        for(String table:List.of("TB_CO_MON_COST","TB_PJ_PROJ","TB_PJ_ASGN","TB_PJ_ASGN_MON","TB_HR_MON_PAY","TB_PJ_PROJ_CHG","TB_CL_CLOSE"))
            rows.addAll(db.queryForList("SELECT row_to_json(t)::text FROM "+table+" t ORDER BY row_to_json(t)::text",String.class));
        return rows;
    }
    @ParameterizedTest @ValueSource(strings={"","/analysis-p/monthly-costs","/analysis-p/workforce","/analysis-p/causes","/analysis-p/simulation"})
    void allReadOperationsHaveZeroBusinessWritesAndZeroDownloadRecords(String suffix) throws Exception {
        var before=snapshot();read(suffix,1);
        assertThat(snapshot()).isEqualTo(before);assertThat(writes.count).hasValue(0);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-04'",Long.class)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"","/excel","/analysis-p/monthly-costs","/analysis-p/workforce","/analysis-p/causes","/analysis-p/simulation"})
    void invalidMonthNeverWritesAnything(String suffix) throws Exception {
        var before=snapshot();mvc.perform(request(suffix,13)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
        assertThat(snapshot()).isEqualTo(before);assertThat(writes.count).hasValue(0);
    }
    @Test void closedMonthSurvivesCurrentAssignmentPayrollAndContractChangesWhileLiveMonthChanges() throws Exception {
        var closed=read("/analysis-p/monthly-costs",1);
        var live=read("",2).path("items").get(0).path("totalCost");
        db.update("UPDATE TB_PJ_PROJ SET CTRT_AMT=20000 WHERE PROJ_ID='analysis-p'");
        db.update("UPDATE TB_PJ_ASGN SET BILL_UPRC=3000,CTRT_MM=0.8 WHERE ASGN_ID='analysis-paid'");
        db.update("UPDATE TB_HR_MON_PAY SET PAY_AMT=2000 WHERE PAY_YM='202602'");
        assertThat(read("/analysis-p/monthly-costs",1)).isEqualTo(closed);
        assertThat(read("",2).path("items").get(0).path("totalCost").decimalValue()).isGreaterThan(live.decimalValue());
    }
    @Test void additionalExpenseAndStoredCombinedDirectExpenseAreNotDoubleCounted() throws Exception {
        var before=read("",1).path("items").get(0).path("totalCost").decimalValue();
        db.update("INSERT INTO TB_PJ_DRCT_EXPS(PROJ_ID,EXPS_YM,EXPS_SN,DRCT_EXPS_ITEM_CD,EXPS_AMT,REG_USER_ID) VALUES('analysis-p','202601',1,(SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='DRCT_EXPS_ITEM'),90,'admin')");
        assertThat(read("",1).path("items").get(0).path("totalCost").decimalValue().subtract(before)).isEqualByComparingTo("90");
    }
    @Test void successfulWorkbookCommitsOneDatabaseTimestampRecordAndNoBusinessChanges() throws Exception {
        var before=snapshot();
        var file=mvc.perform(request("/excel",1)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(file))){assertThat(workbook.getNumberOfSheets()).isEqualTo(1);}
        assertThat(snapshot()).isEqualTo(before);assertThat(writes.count).hasValue(1);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-04' AND ACT_TYPE_CD='DNLD' AND USER_ID='admin' AND ACT_DTTM IS NOT NULL",Long.class)).isEqualTo(1);
    }
    @Test void failedAuditInsertionDoesNotDeliverAWorkbookOrCommitAnyRecord() throws Exception {
        var before=snapshot();
        db.execute("ALTER TABLE TB_SY_ACCS_LOG ADD CONSTRAINT analysis_download_rejected CHECK (SCRN_ID <> 'UI-04')");
        mvc.perform(request("/excel",1)).andExpect(status().is5xxServerError());
        assertThat(snapshot()).isEqualTo(before);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-04'",Long.class)).isZero();
    }
}
