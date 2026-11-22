package com.company.common.operatingprofitstatements;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.operatingprofitstatements.controller.CommonOperatingProfitStatementController;
import com.company.common.operatingprofitstatements.mapper.OperatingProfitStatementMapper;
import com.company.common.operatingprofitstatements.service.*;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.profitstructures.service.*;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.company.common.uploads.controller.CommonUploadController;
import com.company.common.uploads.mapper.UploadMapper;
import com.company.common.uploads.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="NAVIGATION_TEST_DB_URL",matches="jdbc:postgresql:.*")
@WebMvcTest({CommonOperatingProfitStatementController.class,CommonUploadController.class})
@ContextConfiguration(classes={CommonOperatingProfitStatementController.class,CommonUploadController.class,
    CommonOperatingProfitStatementService.class,OperatingProfitStatementExcelService.class,OperatingProfitStatementUploadForm.class,
    ProfitStructureMonthlyProfitService.class,CommonProfitStructureService.class,ProfitStructureDefinitions.class,
    CalculationRuleService.class,UserPreferencePeriodBasis.class,CommonUploadService.class,UploadWorkbookService.class,
    ExcelExportService.class,BusinessCalendar.class,CurrentBusinessActor.class,CodeExceptionHandler.class,ApiExceptionHandler.class,
    OperatingProfitStatementPersistenceTest.Database.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperatingProfitStatementPersistenceTest {
    static final String URL=System.getenv("NAVIGATION_TEST_DB_URL"), USER=System.getenv("NAVIGATION_TEST_DB_USER"), PASSWORD=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
    static final String SCHEMA="operatingprofit_"+UUID.randomUUID().toString().replace("-","");
    static final Path FILES=Path.of(System.getProperty("java.io.tmpdir"),SCHEMA);
    static final String REPORT="/api/common/operating-profit-statements";
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CommonProfitStructureService structures;
    @Autowired ProfitStructureMonthlyProfitService monthly;
    @Autowired UploadWorkbookService workbook;
    @BeforeAll static void prepare() throws Exception {
        Assumptions.assumeTrue(URL!=null && !URL.isBlank());
        try(var c=java.sql.DriverManager.getConnection(URL,USER,PASSWORD);var s=c.createStatement()) { s.execute("CREATE SCHEMA "+SCHEMA); }
        var ds=data();
        var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",
            new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),ds));
        new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();
    }
    static DriverManagerDataSource data() {
        var ds=new DriverManagerDataSource(URL,USER,PASSWORD);var properties=new Properties(); properties.setProperty("currentSchema",SCHEMA); ds.setConnectionProperties(properties);return ds;
    }
    @AfterAll static void cleanup() throws Exception {
        if(URL!=null && !URL.isBlank()) try(var c=java.sql.DriverManager.getConnection(URL,USER,PASSWORD);var s=c.createStatement()){s.execute("DROP SCHEMA IF EXISTS "+SCHEMA+" CASCADE");}
        if(Files.exists(FILES))try(var entries=Files.walk(FILES)){for(var p:entries.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
    }
    @BeforeEach void fixture() {
        jdbc.execute("TRUNCATE TB_SY_ACCS_LOG,TB_SY_UPLD,TB_CO_MON_PL,TB_CO_PREV_ADJ,TB_FI_ITEM,TB_CL_CLOSE CASCADE");
        jdbc.update("UPDATE TB_SY_OPER_STD SET STD_VAL=202601 WHERE STD_CD='CASH_BASE_YM'");
        jdbc.update("INSERT INTO TB_CL_CLOSE(CLOSE_YM,CLOSE_STAT_CD,REG_USER_ID) VALUES ('202601','CLOSE','admin')");
        jdbc.update("INSERT INTO TB_CO_MON_PL(PL_YM,SALES_AMT,SALES_COST_AMT,GROSS_PRFT_AMT,SGA_TOT_AMT,OPRT_PRFT_AMT,SGA_INPUT_AMT,INDRCT_LABOR_AMT,RND_AMT,FIX_YN,REG_USER_ID) VALUES ('202601',1000000,400000,600000,100000,500000,50000,30000,20000,'Y','admin')");
        for(String month:List.of("202501","202502")) jdbc.update("INSERT INTO TB_CO_MON_PL(PL_YM,SALES_AMT,SALES_COST_AMT,GROSS_PRFT_AMT,SGA_TOT_AMT,OPRT_PRFT_AMT,MIGR_YN,FIX_YN,REG_USER_ID) VALUES (?,1000000,400000,600000,100000,500000,'Y','Y','admin')",month);
        jdbc.update("UPDATE TB_CO_MON_PL SET GROSS_PRFT_AMT=NULL WHERE PL_YM='202502'");
        jdbc.update("INSERT INTO TB_CO_PREV_ADJ(APLY_YM,TRGT_YM,ADJ_ITEM_CD,ADJ_AMT,ADJ_RSN,REG_USER_ID) VALUES ('202602','202601','SGA',-100000,'비용 누락','admin')");
    }
    String business(){return jdbc.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY PL_YM),'[]'::jsonb)::text FROM TB_CO_MON_PL t",String.class);}
    long logs(){return jdbc.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG",Long.class);}
    @Test void reportSharesUi07SourceAndMakesNoBusinessOrLogChanges() throws Exception {
        String before=business();long count=logs();
        var source=monthly.year(2026,2,"202601");
        var ui07=structures.getCommonProfitStructures(new com.company.common.profitstructures.dto.ProfitStructureContracts.Filter(2026,"year-to-date","202602"));
        mvc.perform(get(REPORT).param("year","2026").param("baseMonth","202602")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.summary.cumulativeOperatingProfit.amount").value(ui07.data().summary().operatingProfit().intValueExact()))
            .andExpect(jsonPath("$.data.summary.baseMonthOperatingProfit.amount").value(source.get("202602").values().operatingProfit().intValueExact()))
            .andExpect(jsonPath("$.data.numericTable.rows[0].values[13].value").value(2000000))
            .andExpect(jsonPath("$.data.numericTable.rows[5].values[13].value").isEmpty())
            .andExpect(jsonPath("$.data.numericTable.rows[10].values[1].value").value(-100000));
        assertThat(ui07.data().comparison().previousYear().grossProfit()).isNull();
        assertThat(source.get("202601").values().sellingAdministrativeInput()).isEqualByComparingTo("50000");
        assertThat(source.get("202601").values().indirectLaborCost()).isEqualByComparingTo("30000");
        assertThat(source.get("202601").values().internalResearchCost()).isEqualByComparingTo("20000");
        assertThat(business()).isEqualTo(before); assertThat(logs()).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM TB_CO_MON_PL WHERE PL_YM='202602'",Integer.class)).isZero();
    }
    @Test void adjustmentChangesOnlyAppliedMonthAndPreservesOriginalFixedMonth() throws Exception {
        String before=business();
        jdbc.update("UPDATE TB_CO_PREV_ADJ SET ADJ_AMT=100000 WHERE APLY_YM='202602'");
        mvc.perform(get(REPORT).param("year","2026").param("baseMonth","202602")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.numericTable.rows[10].values[1].value").value(100000))
            .andExpect(jsonPath("$.data.numericTable.rows[11].values[0].value").value(500000));
        assertThat(business()).isEqualTo(before);assertThat(logs()).isZero();
    }
    @Test void receivedWorkbookLogsUi08DbClockAndLeavesProfitUnchanged() throws Exception {
        String before=business();long count=logs();
        mvc.perform(get(REPORT+"/excel").param("year","2026").param("baseMonth","202602").param("amountUnit","천 원"))
            .andExpect(status().isOk()).andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertThat(logs()).isEqualTo(count+1);assertThat(business()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-08' AND ACT_TYPE_CD='DNLD' AND ACT_DTTM IS NOT NULL AND ACT_DTTM <= clock_timestamp()",Integer.class)).isEqualTo(1);
    }
    @Test void invalidExportDoesNotWriteLogOrProfit() throws Exception {
        String before=business();long count=logs();
        mvc.perform(get(REPORT+"/excel").param("year","2026").param("baseMonth","202613").param("amountUnit","원"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("baseMonth"));
        assertThat(business()).isEqualTo(before); assertThat(logs()).isEqualTo(count);
    }
    List<String> row(String month,String amount){return List.of(month,amount,"400000","100000","123456","","0","","","","","","-100000","","","","");}
    String inspect(List<List<String>> rows) throws Exception {
        var file=new MockMultipartFile("file","previous.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            workbook.tabularFile(OperatingProfitStatementUploadForm.HEADERS,rows,null));
        var response=mvc.perform(multipart("/api/common/uploads").file(file).param("formTypeCode","B11")).andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsString()).path("data").path("uploadId").asText();
    }
    @Test void b11AtomicReplacementPreservesOtherMonthsAndAppliedReplayIsReadOnly() throws Exception {
        var untouched=jdbc.queryForMap("SELECT * FROM TB_CO_MON_PL WHERE PL_YM='202502'");
        String id=inspect(List.of(row("202501","777777")));
        mvc.perform(post("/api/common/uploads/"+id+"/apply").contentType("application/json").content("{\"modifiedAt\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCount").value(0));
        var applied=jdbc.queryForMap("SELECT * FROM TB_CO_MON_PL WHERE PL_YM='202501'");
        assertThat(applied.get("sales_amt").toString()).isEqualTo("777777");assertThat(applied.get("gross_prft_amt")).isNull();
        assertThat(applied.get("idle_cost_amt").toString()).isEqualTo("0"); assertThat(applied.get("migr_yn")).isEqualTo("Y");assertThat(applied.get("fix_yn")).isEqualTo("Y");
        mvc.perform(post("/api/common/uploads/"+id+"/apply").contentType("application/json").content("{\"modifiedAt\":null}")).andExpect(status().isOk());
        assertThat(jdbc.queryForMap("SELECT * FROM TB_CO_MON_PL WHERE PL_YM='202501'")).isEqualTo(applied);
        assertThat(jdbc.queryForMap("SELECT * FROM TB_CO_MON_PL WHERE PL_YM='202502'")).isEqualTo(untouched);
    }
    @Test void missingVersionKeyAndStaleVersionCannotApplyAnyMonth() throws Exception {
        String id=inspect(List.of(row("202501","42"))),before=business();
        mvc.perform(post("/api/common/uploads/"+id+"/apply").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("modifiedAt"));
        mvc.perform(post("/api/common/uploads/"+id+"/apply").contentType("application/json").content("{\"modifiedAt\":\"2025-01-01T00:00:00Z\"}"))
            .andExpect(status().isConflict());
        assertThat(business()).isEqualTo(before);
    }
    @Test void duplicateFileAppliesNoMonthAndRetainsRowErrors() throws Exception {
        String before=business(),id=inspect(List.of(row("202501","9"),row("202501","10"),row("202503","11")));
        mvc.perform(get("/api/common/uploads/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCount").value(2));
        mvc.perform(post("/api/common/uploads/"+id+"/apply").contentType("application/json").content("{\"modifiedAt\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCount").value(2));
        assertThat(business()).isEqualTo(before);
    }
    @Test void failedSecondMonthRollsBackFirstMonthAndUploadCompletion() throws Exception {
        String id=inspect(List.of(row("202501","77"),row("202502","88"))),before=business();
        jdbc.execute("CREATE FUNCTION reject_operating_month() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.PL_YM='202502' THEN RAISE EXCEPTION 'month rejected'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER reject_operating_month BEFORE INSERT OR UPDATE ON TB_CO_MON_PL FOR EACH ROW EXECUTE FUNCTION reject_operating_month()");
        try { mvc.perform(post("/api/common/uploads/"+id+"/apply").contentType("application/json").content("{\"modifiedAt\":null}")).andExpect(status().is5xxServerError()); }
        finally { jdbc.execute("DROP TRIGGER reject_operating_month ON TB_CO_MON_PL");jdbc.execute("DROP FUNCTION reject_operating_month()"); }
        assertThat(business()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT UPLD_STAT_CD FROM TB_SY_UPLD WHERE UPLD_ID=?",String.class,Long.valueOf(id))).isEqualTo("CHECKED");
    }
    @TestConfiguration @EnableTransactionManagement
    @MapperScan(basePackageClasses={ProfitStructureMapper.class,OperatingProfitStatementMapper.class,CalculationRuleMapper.class,UserPreferencePeriodSourceMapper.class,UploadMapper.class},annotationClass=Mapper.class)
    static class Database {
        @Bean DataSource dataSource(){return data();}
        @Bean org.apache.ibatis.session.SqlSessionFactory sessions(DataSource ds)throws Exception {
            var factory=new SqlSessionFactoryBean();factory.setDataSource(ds);var config=new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(false);config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config);factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml"));return factory.getObject();
        }
        @Bean org.springframework.transaction.PlatformTransactionManager transactions(DataSource ds){return new DataSourceTransactionManager(ds);}
        @Bean JdbcTemplate jdbc(DataSource ds){return new JdbcTemplate(ds);}
        @Bean UploadFileStore files(){return new UploadFileStore(FILES);}
    }
}
