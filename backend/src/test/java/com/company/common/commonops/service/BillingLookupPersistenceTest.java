package com.company.common.commonops.service;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.BillingCalculationRules;
import com.company.common.commonops.controller.BillingLookupController;
import com.company.common.commonops.mapper.BillingLookupMapper;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.BusinessCalendar;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
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

@EnabledIfEnvironmentVariable(named="NAVIGATION_TEST_DB_URL",matches="jdbc:postgresql:.*")
@WebMvcTest(BillingLookupController.class)
@ContextConfiguration(classes={BillingLookupController.class,BillingLookupService.class,BillingCalculationRules.class,
    CalculationRuleService.class,CodeExceptionHandler.class,ApiExceptionHandler.class,BillingLookupPersistenceTest.Persistence.class})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BillingLookupPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired Owned owned;
    JdbcTemplate db;
    record Owned(String name) {}
    @TestConfiguration(proxyBeanMethods=false) @EnableTransactionManagement
    static class Persistence {
        @Bean Owned owned() { return new Owned("billing_lookup_"+UUID.randomUUID().toString().replace("-","")); }
        @Bean DataSource dataSource(Owned owned)throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL"),user=System.getenv("NAVIGATION_TEST_DB_USER"),password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()) { s.execute("CREATE SCHEMA "+owned.name()); }
            var source=new UnpooledDataSource("org.postgresql.Driver",url,user,password);
            var props=new Properties();props.setProperty("currentSchema",owned.name());source.setDriverProperties(props);
            var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();
            return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source)throws Exception {
            var factory=new SqlSessionFactoryBean();factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/commonops/BillingLookupMapper.xml"),
                new ClassPathResource("mapper/CalculationRuleMapper.xml"),new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var config=new org.apache.ibatis.session.Configuration();config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config);return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean BillingLookupMapper billingLookupMapper(SqlSessionTemplate s) { return s.getMapper(BillingLookupMapper.class); }
        @Bean CalculationRuleMapper calculationRuleMapper(SqlSessionTemplate s) { return s.getMapper(CalculationRuleMapper.class); }
        @Bean UserPreferencePeriodSourceMapper userPreferencePeriodSourceMapper(SqlSessionTemplate s) { return s.getMapper(UserPreferencePeriodSourceMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean BusinessCalendar businessCalendar() { return new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T01:00:00Z"),ZoneOffset.UTC)); }
    }
    @BeforeEach void setup() {
        db=new JdbcTemplate(source);
        db.update("INSERT INTO TB_BS_CLNT(CLNT_ID,CLNT_NM,CLNT_TYPE_CD,BIZ_REG_NO,USE_YN,REG_USER_ID) VALUES('lookup-customer','조회 고객','CUST','123-45-67890','Y','admin')");
        for(String type:List.of("MO","DP","TK","IN")) db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CUST_CLNT_ID,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES(?,?, 'lookup-customer',?,'ING','2025-01-01','2027-12-31',100000,0,0,'admin')","lookup-"+type,type,type);
    }
    @AfterEach void cleanup()throws Exception {
        try(var c=source.getConnection();var s=c.createStatement()) { s.execute("DROP SCHEMA "+owned.name()+" CASCADE"); }
    }
    void bill(String id,int order,String date,String due,int unpaid,String cancelled) {
        db.update("INSERT INTO TB_BL_BILL(BILL_ID,PROJ_ID,BILL_ORD,BILL_YM,BILL_DT,SPLY_AMT,VAT_RT,VAT_AMT,TOT_AMT,RCPT_SUM_AMT,UNPAID_AMT,PAY_DUE_DT,CNCL_YN,REG_USER_ID) VALUES(?,'lookup-MO',?,to_char(?::date,'YYYYMM'),?::date,10000,10,1000,11000,11000-?, ?,?::date,?,'admin')",id,order,date,date,unpaid,unpaid,due,cancelled);
    }
    @Test void realPostgresOptionsExcludeInternalAndNonRunningAndFilterSelectedProject()throws Exception {
        db.update("UPDATE TB_PJ_PROJ SET PROJ_STAT_CD='END' WHERE PROJ_ID='lookup-DP'");
        mvc.perform(get("/api/common/bill-options")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projects.length()").value(2))
            .andExpect(jsonPath("$.data.estimatedSupplyAmount").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/common/bill-options").param("projectId","lookup-TK").param("billMonth","202612"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.projects.length()").value(1))
            .andExpect(jsonPath("$.data.projects[0].customerName").value("조회 고객"))
            .andExpect(jsonPath("$.data.defaultPayDueDate").value("2027-01-25"));
        mvc.perform(get("/api/common/bill-options").param("projectId","lookup-IN")).andExpect(status().isBadRequest());
    }
    @Test void realPostgresDunningKeepsOldBillsFiltersBalancesAndReadsHistoryDateThenSequence()throws Exception {
        bill("old-open",1,"2025-01-01","2025-01-25",11000,"N");
        bill("cancelled",2,"2026-10-01","2026-10-25",11000,"Y");
        bill("paid",3,"2026-10-01","2026-10-25",0,"N");
        bill("not-due",4,"2026-11-01","2026-11-25",11000,"N");
        db.update("INSERT INTO TB_BL_DUN_HIST(BILL_ID,DUN_SN,DUN_STEP,SEND_DT,RCVR_DESC,SEND_MTHD_CD,RESEND_YN,REG_USER_ID) VALUES('old-open',2,3,'2026-11-10','재무','BOTH','Y','admin'),('old-open',1,3,'2026-11-10','경영진','BOTH','N','admin')");
        mvc.perform(get("/api/common/bill-dunning")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1)).andExpect(jsonPath("$.data.items[0].billId").value("old-open"))
            .andExpect(jsonPath("$.data.items[0].history[0].recipientDescription").value("경영진"))
            .andExpect(jsonPath("$.data.items[0].history[1].resend").value(true))
            .andExpect(jsonPath("$.data.items[0].canResend").value(false));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_BL_DUN_HIST",Integer.class)).isEqualTo(2);
        db.update("UPDATE TB_BL_BILL SET RCPT_SUM_AMT=TOT_AMT,UNPAID_AMT=0 WHERE BILL_ID='old-open'");
        mvc.perform(get("/api/common/bill-dunning")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_BL_DUN_HIST",Integer.class)).isEqualTo(2);
    }
    @Test void realPostgresMonthEstimateUsesOnlySelectedMonthAndExcludesFreeSupport()throws Exception {
        db.update("INSERT INTO TB_BS_PART(PART_ID,PART_NM,HQ_YN,USE_YN,REG_USER_ID) VALUES('lookup-part','조회 파트','N','Y','admin')");
        db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,REG_USER_ID) SELECT 'lookup-dev','lookup-dev','조회 인력','FT',(SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB'),(SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='DRCT_CLS'),'lookup-part','IDLE','admin'");
        for(String kind:List.of("NEW","FREE")) {
            String id="lookup-"+kind;
            db.update("INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,CTRT_MM,BILL_UPRC,INPUT_TYPE_CD,EXT_CNT,ASGN_STAT_CD,REG_USER_ID) SELECT ?,'lookup-MO','lookup-dev',min(CODE),'2026-01-01','2026-12-31',1,10000,?,0,'ACT','admin' FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB'",id,kind);
            db.update("INSERT INTO TB_PJ_ASGN_MON(ASGN_ID,INPUT_YM,DEV_ID,PROJ_ID,INPUT_DAY_CNT,MON_MM,APLY_UPRC,SALES_AMT,FIX_YN,REG_USER_ID) VALUES(?,'202610','lookup-dev','lookup-MO',15,0.5,12345,6172,'Y','admin'),(?,'202611','lookup-dev','lookup-MO',30,1,10000,10000,'N','admin')",id,id);
        }
        mvc.perform(get("/api/common/bill-options").param("projectId","lookup-MO").param("billMonth","202610"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.estimatedSupplyAmount").value(6172));
        mvc.perform(get("/api/common/bill-options").param("projectId","lookup-MO"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.estimatedSupplyAmount").value(10000));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_PJ_ASGN_MON",Integer.class)).isEqualTo(4);
    }
}
