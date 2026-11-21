package com.company.common.profitstructures;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.profitstructures.controller.CommonProfitStructureController;
import com.company.common.profitstructures.dto.ProfitStructureContracts.Filter;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.profitstructures.service.*;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.ibatis.annotations.Mapper;
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
@WebMvcTest(CommonProfitStructureController.class)
@ContextConfiguration(classes={CommonProfitStructureController.class,CommonProfitStructureService.class,
    ProfitStructureMonthlyProfitService.class,ProfitStructureDefinitions.class,ProfitStructureExcelService.class,
    CalculationRuleService.class,UserPreferencePeriodBasis.class,ExcelExportService.class,BusinessCalendar.class,
    CurrentBusinessActor.class,CodeExceptionHandler.class,ApiExceptionHandler.class,ProfitStructurePersistenceTest.Database.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProfitStructurePersistenceTest {
    static final String URL=System.getenv("NAVIGATION_TEST_DB_URL"),USER=System.getenv("NAVIGATION_TEST_DB_USER"),PASSWORD=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
    static final String SCHEMA="profitstructures_"+UUID.randomUUID().toString().replace("-","");
    @Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;
    @Autowired ProfitStructureMonthlyProfitService monthly;
    @Autowired ProfitStructureExcelService exports;
    @BeforeAll static void prepare() throws Exception {
        Assumptions.assumeTrue(URL!=null && !URL.isBlank(),"Runner PostgreSQL is optional");
        try(var c=java.sql.DriverManager.getConnection(URL,USER,PASSWORD);var s=c.createStatement()) {
            s.execute("CREATE SCHEMA "+SCHEMA);
        }
        var ds=new DriverManagerDataSource(URL,USER,PASSWORD);
        var properties=new Properties();properties.setProperty("currentSchema",SCHEMA);ds.setConnectionProperties(properties);
        var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",
            new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),ds));
        new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();
    }
    @AfterAll static void cleanup() throws Exception {
        if(URL!=null && !URL.isBlank())try(var c=java.sql.DriverManager.getConnection(URL,USER,PASSWORD);var s=c.createStatement()){s.execute("DROP SCHEMA IF EXISTS "+SCHEMA+" CASCADE");}
    }
    @BeforeEach void fixture() {
        jdbc.execute("TRUNCATE TB_SY_ACCS_LOG,TB_CO_MON_PL,TB_CO_MON_COST,TB_FI_ITEM,TB_CO_PREV_ADJ,TB_FI_CORP_TAX,TB_BL_BILL,TB_PJ_DRCT_EXPS,TB_CL_CLOSE,TB_PJ_TK_DSTRB,TB_PJ_PROJ_PART,TB_PJ_PROJ CASCADE");
        jdbc.update("UPDATE TB_SY_OPER_STD SET STD_VAL=202601 WHERE STD_CD='CASH_BASE_YM'");
        jdbc.update("INSERT INTO TB_CL_CLOSE(CLOSE_YM,CLOSE_STAT_CD,REG_USER_ID) VALUES ('202601','CLOSE','admin')");
        jdbc.update("INSERT INTO TB_CO_MON_PL(PL_YM,SALES_AMT,ALLOC_COST_AMT,IDLE_COST_AMT,DRCT_EXPS_AMT,SALES_COST_AMT,GROSS_PRFT_AMT,SGA_TOT_AMT,PREV_ADJ_AMT,OPRT_PRFT_AMT,NOPR_INCM_AMT,NOPR_EXPS_AMT,CORP_TAX_AMT,NET_PRFT_AMT,FIX_YN,REG_USER_ID,MOD_DTTM) VALUES ('202601',1000,500,150,50,700,300,100,10,210,20,5,10,215,'Y','admin',clock_timestamp())");
        jdbc.update("INSERT INTO TB_CO_MON_PL(PL_YM,SALES_AMT,SALES_COST_AMT,SGA_TOT_AMT,OPRT_PRFT_AMT,MIGR_YN,FIX_YN,REG_USER_ID) VALUES ('202501',1000,700,100,200,'Y','Y','admin')");
        jdbc.update("INSERT INTO TB_FI_ITEM(FIN_YM,ITEM_SN,FIN_CLS_CD,ITEM_NM,AMT,VAT_AMT,REG_USER_ID) VALUES ('202602',1,'SGA','판관비',100,0,'admin')");
        jdbc.update("INSERT INTO TB_FI_CORP_TAX(TAX_YR,EST_TAX_AMT,REG_USER_ID) VALUES ('2026',120,'admin')");
        jdbc.update("INSERT INTO TB_CO_PREV_ADJ(APLY_YM,TRGT_YM,ADJ_ITEM_CD,ADJ_AMT,ADJ_RSN,REG_USER_ID) VALUES ('202602','202601','SGA',-5,'전월 비용 조정','admin')");
        jdbc.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES ('P','턴키','TK','PRG','2026-01-01','2026-12-31',10000,0,0,'admin')");
        jdbc.update("INSERT INTO TB_PJ_PROJ_PART(PROJ_ID,APLY_BGN_YM,PART_ID,REG_USER_ID) VALUES ('P','202601',NULL,'admin')");
        jdbc.update("INSERT INTO TB_BL_BILL(BILL_ID,PROJ_ID,BILL_ORD,BILL_YM,BILL_DT,SPLY_AMT,VAT_RT,VAT_AMT,TOT_AMT,RCPT_SUM_AMT,UNPAID_AMT,PAY_DUE_DT,CNCL_YN,REG_USER_ID) VALUES ('B1','P',1,'202601','2026-01-15',9999,0,0,9999,0,9999,'2026-02-15','N','admin'),('B2','P',2,'202602','2026-02-15',900,0,0,900,0,900,'2026-03-15','N','admin'),('B3','P',3,'202602','2026-02-16',9999,0,0,9999,0,9999,'2026-03-16','Y','admin')");
        jdbc.update("INSERT INTO TB_PJ_DRCT_EXPS(PROJ_ID,EXPS_YM,EXPS_SN,DRCT_EXPS_ITEM_CD,EXPS_AMT,REG_USER_ID) VALUES ('P','202602',1,(SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='DRCT_EXPS_ITEM'),50,'admin')");
    }
    Map<String,String> business() {
        var values=new TreeMap<String,String>();
        for(var table:List.of("TB_CO_MON_PL","TB_CO_MON_COST","TB_FI_ITEM","TB_CO_PREV_ADJ","TB_FI_CORP_TAX","TB_BL_BILL","TB_PJ_DRCT_EXPS"))
            values.put(table,jdbc.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM "+table+" t",String.class));
        return values;
    }
    long downloads(){return jdbc.queryForObject("SELECT COUNT(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-07' AND ACT_TYPE_CD='DNLD'",Long.class);}
    @Test void mixedReadKeepsSevenBusinessTablesModifiedTimesAndLogsUnchanged() throws Exception {
        var before=business();var logs=downloads();
        mvc.perform(get("/api/common/profit-structures").param("year","2026").param("periodUnit","quarter").param("baseMonth","202602"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.revenue").value(1900))
            .andExpect(jsonPath("$.data.numericRows[0].summary.revenue").value(1900));
        assertThat(business()).isEqualTo(before);assertThat(downloads()).isEqualTo(logs);
    }
    @Test void missingSnapshotUsesSharedLiveMonthAndDoesNotMaterializeIt() throws Exception {
        var before=business();var expected=monthly.year(2026,2,"202601").get("202602").values();
        mvc.perform(get("/api/common/profit-structures").param("year","2026").param("periodUnit","month").param("baseMonth","202602"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.summary.operatingProfit").value(expected.operatingProfit().intValueExact()))
            .andExpect(jsonPath("$.data.waterfall.netProfit").value(expected.netProfit().intValueExact()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TB_CO_MON_PL WHERE PL_YM='202602'",Integer.class)).isZero();assertThat(business()).isEqualTo(before);
    }
    @Test void successfulExcelWritesExactlyOneDownloadAndNoBusinessChange() {
        var before=business();var logs=downloads();var bytes=exports.getCommonProfitStructuresExcel(new Filter(2026,"month","202602"));
        assertThat(bytes.bytes()).isNotEmpty();assertThat(downloads()).isEqualTo(logs+1);assertThat(business()).isEqualTo(before);
    }
    @Test void invalidMonthWritesNothing() {
        var before=business();var logs=downloads();
        assertThatThrownBy(()->exports.getCommonProfitStructuresExcel(new Filter(2026,"month","202613"))).isInstanceOf(CodeFailure.class);
        assertThat(downloads()).isEqualTo(logs);assertThat(business()).isEqualTo(before);
    }
    @Test void failedDownloadInsertRollsBackExportTransaction() {
        jdbc.execute("CREATE FUNCTION reject_profit_download() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'download rejected'; END $$");
        jdbc.execute("CREATE TRIGGER reject_profit_download BEFORE INSERT ON TB_SY_ACCS_LOG FOR EACH ROW EXECUTE FUNCTION reject_profit_download()");
        var before=business();var logs=downloads();
        try { assertThatThrownBy(()->exports.getCommonProfitStructuresExcel(new Filter(2026,"month","202602"))).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("DROP TRIGGER reject_profit_download ON TB_SY_ACCS_LOG");jdbc.execute("DROP FUNCTION reject_profit_download()"); }
        assertThat(downloads()).isEqualTo(logs);assertThat(business()).isEqualTo(before);
    }
    @Test void futureRoleProjectionUsesExistingSeedsWithoutHttpEnforcement() {
        assertThat(jdbc.queryForList("SELECT ROLE_ID FROM TB_SY_ROLE WHERE ROLE_ID IN ('FIN','EXEC','ADMIN')",String.class))
            .containsExactlyInAnyOrder("FIN","EXEC","ADMIN");
    }
    @TestConfiguration @EnableTransactionManagement
    @MapperScan(basePackageClasses={ProfitStructureMapper.class,CalculationRuleMapper.class,UserPreferencePeriodSourceMapper.class},annotationClass=Mapper.class)
    static class Database {
        @Bean DataSource dataSource(){var ds=new DriverManagerDataSource(URL,USER,PASSWORD);var p=new Properties();p.setProperty("currentSchema",SCHEMA);ds.setConnectionProperties(p);return ds;}
        @Bean org.apache.ibatis.session.SqlSessionFactory sessions(DataSource ds)throws Exception{var f=new SqlSessionFactoryBean();f.setDataSource(ds);var c=new org.apache.ibatis.session.Configuration();c.setMapUnderscoreToCamelCase(true);c.setArgNameBasedConstructorAutoMapping(true);f.setConfiguration(c);f.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml"));return f.getObject();}
        @Bean org.springframework.transaction.PlatformTransactionManager transactions(DataSource ds){return new DataSourceTransactionManager(ds);}
        @Bean JdbcTemplate jdbc(DataSource ds){return new JdbcTemplate(ds);}
    }
}
