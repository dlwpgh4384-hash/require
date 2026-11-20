package com.company.common.partsettlements;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.PartSettlementDefinitions;
import com.company.common.partsettlements.controller.CommonPartSettlementController;
import com.company.common.partsettlements.mapper.PartSettlementMapper;
import com.company.common.partsettlements.service.*;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.company.common.users.mapper.UserMapper;
import com.fasterxml.jackson.databind.*;
import java.sql.DriverManager;
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
@WebMvcTest(CommonPartSettlementController.class)
@ContextConfiguration(classes={CommonPartSettlementController.class,CommonPartSettlementService.class,
    PartSettlementCloseService.class,PartSettlementExcelService.class,PartSettlementDefinitions.class,
    CalculationRuleService.class,CodeExceptionHandler.class,ApiExceptionHandler.class,
    PartSettlementPersistenceTest.Persistence.class,ExcelExportService.class,BusinessCalendar.class,CurrentBusinessActor.class})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PartSettlementPersistenceTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired DataSource source; @Autowired Owned owned;
    @Autowired PartSettlementCloseService close;
    JdbcTemplate db;
    record Owned(String name) {}
    @TestConfiguration(proxyBeanMethods=false) @EnableTransactionManagement
    static class Persistence {
        @Bean Owned owned() { return new Owned("partsettlement_"+UUID.randomUUID().toString().replace("-","")); }
        @Bean DataSource dataSource(Owned owned) throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL"),user=System.getenv("NAVIGATION_TEST_DB_USER"),password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()){s.execute("CREATE SCHEMA "+owned.name());}
            var ds=new UnpooledDataSource("org.postgresql.Driver",url,user,password);
            var properties=new Properties(); properties.setProperty("currentSchema",owned.name()); ds.setDriverProperties(properties);
            var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),ds));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare(); return ds;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory=new SqlSessionFactoryBean();factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/partsettlements/PartSettlementMapper.xml"),new ClassPathResource("mapper/UserMapper.xml"),
                new ClassPathResource("mapper/CalculationRuleMapper.xml"),new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var config=new org.apache.ibatis.session.Configuration();config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config);return factory.getObject();
        }
        @Bean SqlSessionTemplate template(SqlSessionFactory factory){return new SqlSessionTemplate(factory);}
        @Bean PartSettlementMapper settlementMapper(SqlSessionTemplate s){return s.getMapper(PartSettlementMapper.class);}
        @Bean UserMapper userMapper(SqlSessionTemplate s){return s.getMapper(UserMapper.class);}
        @Bean CalculationRuleMapper calculationMapper(SqlSessionTemplate s){return s.getMapper(CalculationRuleMapper.class);}
        @Bean UserPreferencePeriodSourceMapper periodMapper(SqlSessionTemplate s){return s.getMapper(UserPreferencePeriodSourceMapper.class);}
        @Bean DataSourceTransactionManager transactionManager(DataSource source){return new DataSourceTransactionManager(source);}
    }
    @BeforeEach void fixture() {
        db=new JdbcTemplate(source);
        String job=db.queryForObject("SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB' AND USE_YN='Y' AND DEL_YN='N'",String.class);
        for(String id:List.of("settlement-a","settlement-b","settlement-hq")) {
            db.update("INSERT INTO TB_BS_PART(PART_ID,PART_NM,HQ_YN,USE_YN,REG_USER_ID) VALUES(?,?,?,'Y','admin')",id,id,id.endsWith("hq")?"Y":"N");
            db.update("INSERT INTO TB_BS_PART_STD(PART_ID,APLY_BGN_YM,PART_LDR_USER_ID,PRFT_DSTRB_RT,SALES_EXPS_AMT,REG_USER_ID) VALUES(?,'202501','admin',40,1500000,'admin')",id);
        }
        db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,REG_USER_ID) VALUES('settlement-dev','settlement-dev','정산 인력','FT',?,'DRCT','settlement-a','IDLE','admin')",job);
        db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES('settlement-project','고정될 이름','MO','ING','2025-12-01','2026-12-31',100000000,0,0,'admin')");
        db.update("INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,CTRT_MM,INPUT_TYPE_CD,EXT_CNT,ASGN_STAT_CD,BILL_UPRC,REG_USER_ID) VALUES('settlement-assignment','settlement-project','settlement-dev',?,'2026-01-01','2026-12-31',1,'NEW',0,'ACT',7000000,'admin')",job);
        db.update("INSERT INTO TB_PJ_ASGN_MON(ASGN_ID,PROJ_ID,DEV_ID,INPUT_YM,INPUT_DAY_CNT,MON_MM,SALES_AMT,FIX_YN,REG_USER_ID) VALUES('settlement-assignment','settlement-project','settlement-dev','202601',31,1,7000000,'N','admin')");
        db.update("INSERT INTO TB_HR_MON_PAY(PAY_YM,DEV_ID,PAY_AMT,ALWNC_AMT,INSR_CMPNY_AMT,RETIRE_RSV_AMT,EMP_TYPE_CD,DRCT_CLS_CD,REG_USER_ID) VALUES('202601','settlement-dev',4000000,0,0,0,'FT','DRCT','admin')");
    }
    @AfterEach void removeOwnedSchema() throws Exception {
        try(var c=source.getConnection();var s=c.createStatement()){s.execute("DROP SCHEMA "+owned.name()+" CASCADE");}
    }
    String root(String action){return "/api/common/part-settlements/settlement-a/2026-01"+action;}
    JsonNode read() throws Exception {return json.readTree(mvc.perform(get(root(""))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("data");}
    String command(String action,Map<String,Object> fields) throws Exception {
        return mvc.perform(put(root(action)).contentType("application/json").content(json.writeValueAsString(fields))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    Map<String,Object> version(JsonNode row){var result=new LinkedHashMap<String,Object>();result.put("modifiedAt",row.path("modifiedAt").isNull()?null:row.path("modifiedAt").asText());return result;}
    int count(String table){return db.queryForObject("SELECT count(*) FROM "+table,Integer.class);}
    @Test void readsNeverMaterializeHeadersAndInvalidPeriodLeavesEverythingUnchanged() throws Exception {
        int before=count("TB_CO_PART_SETL");var row=read();assertThat(row.path("items").size()).isEqualTo(1);
        mvc.perform(get("/api/common/part-settlements").param("yearMonth","2026-01")).andExpect(status().isOk());
        mvc.perform(get(root("/trends"))).andExpect(status().isOk());assertThat(count("TB_CO_PART_SETL")).isEqualTo(before);
        mvc.perform(get("/api/common/part-settlements").param("yearMonth","2026-13")).andExpect(status().isBadRequest());assertThat(count("TB_CO_PART_SETL")).isEqualTo(before);
    }
    @Test void cardNullVersionCreatesAndStaleVersionDoesNotTouchDetailsOrAudit() throws Exception {
        var body=version(read());body.put("corpCardAmount",100000);String reply=command("/card",body);
        var data=json.readTree(reply).get("data");assertThat(data.path("corpCardAmount").asInt()).isEqualTo(100000);
        assertThat(db.queryForObject("SELECT CORP_CARD_AMT FROM TB_CO_PART_SETL WHERE PART_ID='settlement-a'",Long.class)).isEqualTo(100000L);
        assertThat(data.path("modifiedAt").asText()).isEqualTo(db.queryForObject("SELECT MOD_DTTM FROM TB_CO_PART_SETL WHERE PART_ID='settlement-a'",java.sql.Timestamp.class).toInstant().toString());
        int history=count("TB_SY_CHG_HIST");mvc.perform(put(root("/card")).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isConflict());
        assertThat(count("TB_CO_PART_SETL_DTL")).isZero();assertThat(count("TB_SY_CHG_HIST")).isEqualTo(history);
    }
    @Test void historicalMembershipSnapshotSurvivesSourceEditsAndCancelReleasesCurrentSources() throws Exception {
        db.update("UPDATE TB_HR_DEV SET PART_ID='settlement-b' WHERE DEV_ID='settlement-dev'");
        db.update("INSERT INTO TB_SY_CHG_HIST(TRGT_TBL_NM,TRGT_KEY,CHG_TYPE_CD,CHG_ITEM_NM,BFR_VAL,AFT_VAL,REG_USER_ID,REG_DTTM) VALUES('TB_HR_DEV','settlement-dev','UPD','영업 파트','settlement-a','settlement-b','admin','2026-02-01T00:00:00+09:00')");
        assertThat(read().path("items").size()).isZero();
        var confirmed=json.readTree(command("/confirm",version(read()))).get("data");assertThat(confirmed.path("items").size()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT EMP_TYPE_CD FROM TB_CO_PART_SETL_DTL WHERE PART_ID='settlement-a'",String.class)).isEqualTo("FT");
        db.update("UPDATE TB_PJ_PROJ SET PROJ_NM='바뀐 이름' WHERE PROJ_ID='settlement-project'");assertThat(read().path("items").get(0).path("projectNames").get(0).asText()).isEqualTo("고정될 이름");
        var cancel=version(confirmed);cancel.put("cancelReason"," 검증한 정정 사유 ");command("/cancel",cancel);
        assertThat(read().path("confirmed").asBoolean()).isFalse();assertThat(count("TB_CO_PART_SETL_DTL")).isEqualTo(1);
        assertThat(db.queryForObject("SELECT CHG_RSN FROM TB_SY_CHG_HIST WHERE TRGT_TBL_NM='TB_CO_PART_SETL' AND AFT_VAL='WORK'",String.class)).isEqualTo(" 검증한 정정 사유 ");
        db.update("UPDATE TB_HR_DEV SET PART_ID='settlement-a' WHERE DEV_ID='settlement-dev'");assertThat(read().path("items").get(0).path("projectNames").get(0).asText()).isEqualTo("바뀐 이름");
    }
    @Test void lockedCancelRollsBackThenUnclosedReasonCancelSucceeds() throws Exception {
        var row=json.readTree(command("/confirm",version(read()))).get("data");var body=version(row);body.put("cancelReason","정정");
        db.update("INSERT INTO TB_CL_CLOSE(CLOSE_YM,CLOSE_STAT_CD,REG_USER_ID) VALUES('202601','CLOSE','admin')");int audit=count("TB_SY_CHG_HIST");
        mvc.perform(put(root("/cancel")).contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isBadRequest());assertThat(count("TB_SY_CHG_HIST")).isEqualTo(audit);
        db.update("DELETE FROM TB_CL_CLOSE WHERE CLOSE_YM='202601'");command("/cancel",body);assertThat(read().path("confirmed").asBoolean()).isFalse();
    }
    @Test void closeBatchStaleTargetRollsBackWholeCallAndSuccessfulRetryUsesAuto() throws Exception {
        var check=close.getCommonPartSettlementCloseChecks("2026-01");var body=new LinkedHashMap<String,Object>();body.put("yearMonth","2026-01");body.put("items",check.items());
        var card=version(read());card.put("corpCardAmount",50);command("/card",card);int headers=count("TB_CO_PART_SETL");
        mvc.perform(put("/api/common/part-settlements/close-confirm").contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isConflict());
        assertThat(count("TB_CO_PART_SETL")).isEqualTo(headers);assertThat(count("TB_CO_PART_SETL_DTL")).isZero();
        body.put("items",close.getCommonPartSettlementCloseChecks("2026-01").items());mvc.perform(put("/api/common/part-settlements/close-confirm").contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isOk()).andExpect(jsonPath("$.data.unconfirmedCount").value(0));
        assertThat(read().path("confirmationNote").asText()).isEqualTo("마감 시 자동 확정");assertThat(read().path("confirmedBy").asText()).isEqualTo("admin");
        assertThat(db.queryForObject("SELECT count(*) FROM TB_CO_PART_SETL WHERE SETL_STAT_CD='AUTO'",Integer.class)).isEqualTo(check.items().size());assertThat(count("TB_CL_CLOSE")).isZero();
    }
    @Test void successfulXlsxWritesExactlyOneDownloadAndNoSettlement() throws Exception {
        int logs=count("TB_SY_ACCS_LOG"),headers=count("TB_CO_PART_SETL");
        var result=mvc.perform(get("/api/common/part-settlements/excel").param("partId","settlement-a").param("yearMonth","2026-01").param("view","monthly")).andExpect(status().isOk()).andReturn();
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(result.getResponse().getContentAsByteArray()))){assertThat(book.getSheetAt(0).getPhysicalNumberOfRows()).isGreaterThan(1);}
        assertThat(count("TB_SY_ACCS_LOG")).isEqualTo(logs+1);assertThat(count("TB_CO_PART_SETL")).isEqualTo(headers);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-19' AND ACCS_TYPE_CD='DNLD'",Integer.class)).isEqualTo(1);
    }
}
