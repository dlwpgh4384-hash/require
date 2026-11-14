package com.company.common.service;

import com.company.common.controller.*;
import com.company.common.mapper.*;
import com.company.common.schema.SchemaPreparation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(CommonClientController.class)
@ContextConfiguration(classes = {CommonClientController.class, CommonClientService.class, CurrentBusinessActor.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class, ExcelExportService.class,
    CommonClientIntegrationTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CommonClientIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired OwnedSchema owned;
    @Autowired ClientMapper mapper;
    JdbcTemplate db;
    record OwnedSchema(String name) {}
    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean OwnedSchema ownedSchema() { return new OwnedSchema("clients_" + UUID.randomUUID().toString().replace("-", "")); }
        @Bean DataSource dataSource(OwnedSchema owned) throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL"), user=System.getenv("NAVIGATION_TEST_DB_USER"), password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var connection=DriverManager.getConnection(url,user,password); var statement=connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
                statement.execute("CREATE SCHEMA " + owned.name());
            }
            var source = new UnpooledDataSource("org.postgresql.Driver",url,user,password);
            var properties=new Properties(); properties.setProperty("currentSchema",owned.name()); source.setDriverProperties(properties);
            var configuration = new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration)).prepare();
            return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory=new SqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/ClientMapper.xml"),new ClassPathResource("mapper/UserMapper.xml"),
                new ClassPathResource("mapper/CodeGroupMapper.xml"));
            var config=new org.apache.ibatis.session.Configuration(); config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean ClientMapper clientMapper(SqlSessionTemplate session) { return session.getMapper(ClientMapper.class); }
        @Bean UserMapper userMapper(SqlSessionTemplate session) { return session.getMapper(UserMapper.class); }
        @Bean CodeGroupMapper codeGroupMapper(SqlSessionTemplate session) { return session.getMapper(CodeGroupMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean BusinessCalendar businessCalendar() { return new BusinessCalendar(java.time.Clock.fixed(java.time.Instant.parse("2030-01-01T15:30:00Z"),java.time.ZoneOffset.UTC)); }
        @Bean PaymentAccountCipher paymentAccountCipher() { return new PaymentAccountCipher(java.util.Base64.getEncoder().encodeToString(new byte[32])); }
    }
    @BeforeEach void setup() { db=new JdbcTemplate(source); }
    @AfterEach void cleanup() throws Exception {
        try (var connection=source.getConnection(); var statement=connection.createStatement()) { statement.execute("DROP SCHEMA " + owned.name() + " CASCADE"); }
    }
    private ObjectNode input(String type, String number) {
        String bank=db.queryForObject("SELECT code FROM TB_SY_CODE WHERE code_grp_id='BANK' AND use_yn='Y' AND del_yn='N' ORDER BY sort_ord LIMIT 1",String.class);
        return json.createObjectNode().put("clientTypeCode",type).put("clientName","독립 거래처").put("businessNumber",number)
            .put("taxEmail","client@example.test").put("bankCode",bank).put("accountNumber","12345678").put("accountHolder","독립 예금주");
    }
    private String create(String type, String number) throws Exception {
        var result=mvc.perform(post("/api/common/clients").contentType("application/json").content(input(type,number).toString())).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).at("/data/client/clientId").asText();
    }
    private String snapshot() { return db.queryForObject("SELECT jsonb_build_array((SELECT jsonb_agg(to_jsonb(c) ORDER BY clnt_id) FROM TB_BS_CLNT c),(SELECT jsonb_agg(to_jsonb(h) ORDER BY hist_id) FROM TB_SY_CHG_HIST h WHERE trgt_tbl_nm='TB_BS_CLNT'))::text",String.class); }
    private ObjectNode update(String id) {
        var body=input("BOTH","123-45-67890"); body.remove("businessNumber"); var token=mapper.selectClient(id,java.time.LocalDate.of(2030,1,2)).modifiedAt;
        if(token==null) body.putNull("modifiedAt"); else body.put("modifiedAt",token.toString()); return body;
    }
    private ObjectNode state(String use, String... ids) {
        var body=json.createObjectNode().put("useYn",use); var array=body.putArray("items");
        for(String id:ids) { var item=array.addObject().put("clientId",id); var token=mapper.selectClient(id,java.time.LocalDate.of(2030,1,2)).modifiedAt;
            if(token==null)item.putNull("modifiedAt");else item.put("modifiedAt",token.toString()); }
        return body;
    }
    @Test void encryptedCreationReadbackAndHistoryArePersistentAndMasked() throws Exception {
        String id=create("BOTH","123-45-67890");
        assertThat(db.queryForObject("SELECT acct_no_enc FROM TB_BS_CLNT WHERE clnt_id=?",String.class,id)).doesNotContain("12345678");
        mvc.perform(get("/api/common/clients")).andExpect(jsonPath("$.data.items[0].clientId").value(id)).andExpect(jsonPath("$.data.items[0].paymentAccountSummary").value(org.hamcrest.Matchers.endsWith("5678")));
        mvc.perform(get("/api/common/clients/"+id+"/change-history")).andExpect(jsonPath("$.data.items[0].fieldName").value("신규 등록")).andExpect(jsonPath("$.data.items[0].changedBy").value("admin"));
        mvc.perform(get("/api/common/clients/"+id+"/payment-account")).andExpect(jsonPath("$.data.accountNumber").value("12345678"));
        var reopened=new JdbcTemplate(source); assertThat(reopened.queryForObject("SELECT use_yn FROM TB_BS_CLNT WHERE clnt_id=?",String.class,id)).isEqualTo("Y");
    }
    @Test void excelDownloadKeepsVisibleColumnsAndLeavesAccessLogs() throws Exception {
        String id=create("VNDR","123-45-67890");
        var response=mvc.perform(get("/api/common/clients/excel").param("columns","name","type","account"))
            .andExpect(status().isOk()).andReturn().getResponse();
        assertThat(response.getHeader("Content-Disposition")).contains("attachment");
        try (var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet=workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("admin");
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("상호");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("구분");
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("협력사");
            assertThat(sheet.getRow(3).getCell(2).getStringCellValue()).endsWith("5678");
        }
        mvc.perform(get("/api/common/clients/excel").param("columns","unknown")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/common/clients/"+id+"/payment-account")).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE scrn_id='UI-15' AND act_type_cd='DNLD' AND user_id='admin'",Long.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE scrn_id='UI-15' AND act_type_cd='ACCT_VIEW'",Long.class)).isEqualTo(1);
    }
    @Test void anUnreadableAccountDoesNotBreakTheList() throws Exception {
        String id=create("VNDR","123-45-67890");
        db.update("UPDATE TB_BS_CLNT SET acct_no_enc=? WHERE clnt_id=?", java.util.Base64.getEncoder().encodeToString(new byte[40]), id);
        mvc.perform(get("/api/common/clients")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].paymentAccountSummary").value(org.hamcrest.Matchers.endsWith("확인 필요")));
    }
    @Test void duplicateAndMalformedRequestsHaveZeroNewRows() throws Exception {
        create("BOTH","123-45-67890"); String before=snapshot();
        mvc.perform(post("/api/common/clients").contentType("application/json").content(input("VNDR","123-45-67890").toString())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/common/clients").contentType("application/json").content(input("VNDR","987-65-43210").put("accountNumber","1234567").toString())).andExpect(status().isBadRequest());
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void updateWritesOnlyChangedFieldsAndStaleRollsBack() throws Exception {
        String id=create("BOTH","123-45-67890"); var body=update(id).put("contactName","새 담당자");
        mvc.perform(put("/api/common/clients/"+id).contentType("application/json").content(body.toString())).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key=? AND chg_item_nm='담당자' AND bfr_val IS NULL AND aft_val='새 담당자'",Long.class,id)).isEqualTo(1);
        String before=snapshot(); mvc.perform(put("/api/common/clients/"+id).contentType("application/json").content(body.put("contactName","오래된 저장").toString())).andExpect(status().isConflict());
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void historyFailureRollsBackMasterUpdate() throws Exception {
        String id=create("BOTH","123-45-67890"); String before=snapshot();
        db.execute("ALTER TABLE TB_SY_CHG_HIST ADD CONSTRAINT reject_contact CHECK (chg_item_nm <> '담당자')");
        try { mvc.perform(put("/api/common/clients/"+id).contentType("application/json").content(update(id).put("contactName","실패").toString())); }
        catch (jakarta.servlet.ServletException expected) { assertThat(expected.getCause()).isNotNull(); }
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void stoppedClientsLeaveNewSelectionButRetainPastProjectReferences() throws Exception {
        String id=create("CUST","123-45-67890"); project("past",id,"END");
        mvc.perform(put("/api/common/clients/use-status").contentType("application/json").content(state("N",id).toString())).andExpect(status().isOk());
        assertThat(mapper.selectSelectableClientIds()).doesNotContain(id);
        assertThat(db.queryForObject("SELECT cust_clnt_id FROM TB_PJ_PROJ WHERE proj_id='past'",String.class)).isEqualTo(id);
        mvc.perform(put("/api/common/clients/use-status").contentType("application/json").content(state("Y",id).toString())).andExpect(status().isOk());
        assertThat(mapper.selectSelectableClientIds()).contains(id);
    }
    private void project(String id,String client,String status) {
        db.update("INSERT INTO TB_PJ_PROJ(proj_id,proj_nm,cust_clnt_id,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES(?,?,?,'TM',?,'2030-01-01','2030-12-31',0,0,0,'admin')",id,id,client,status);
    }
    private void developer(String id,String client) {
        db.update("INSERT INTO TB_HR_DEV(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,blng_clnt_id,reg_user_id) VALUES(?,?,?,'FT','BE','DRCT','HQ','WORK',?,'admin')",id,id,id,client);
    }
    @Test void filteredListsIncludeBothAndTodayUnionDeduplicatesPeopleNotAssignmentLines() throws Exception {
        String both=create("BOTH","123-45-67890"), vendor=create("VNDR","987-65-43210");
        project("p1",both,"ING"); project("p2",both,"ING"); developer("d1",both);
        db.update("INSERT INTO TB_PJ_ASGN(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES('a1','p1','d1','BE','2030-01-01','2030-01-02',1,'NEW',0,'ACT','admin'),('a2','p2','d1','BE','2030-01-02','2030-01-03',1,'NEW',0,'ACT','admin')");
        mvc.perform(get("/api/common/clients").param("clientTypeCode","CUST")).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].clientId").value(both)).andExpect(jsonPath("$.data.items[0].personCount").value(1)).andExpect(jsonPath("$.data.items[0].projectCount").value(2));
        mvc.perform(get("/api/common/clients/"+both+"/assignments")).andExpect(jsonPath("$.data.items.length()").value(2));
        mvc.perform(get("/api/common/clients").param("clientTypeCode","VNDR")).andExpect(jsonPath("$.data.items.length()").value(2));
        assertThat(mapper.selectClient(vendor,java.time.LocalDate.of(2030,1,2)).personCount).isZero();
    }
    @Test void staleBatchHasPriorityOverRelationshipFailuresAndZeroWrites() throws Exception {
        String a=create("CUST","123-45-67890"), b=create("VNDR","987-65-43210"); project("ongoing",a,"ING"); developer("affiliated",b);
        var body=state("N",a,b); db.update("UPDATE TB_BS_CLNT SET mod_dttm=clock_timestamp() WHERE clnt_id=?",b); String before=snapshot();
        mvc.perform(put("/api/common/clients/use-status").contentType("application/json").content(body.toString())).andExpect(status().isConflict()); assertThat(snapshot()).isEqualTo(before);
        mvc.perform(put("/api/common/clients/use-status").contentType("application/json").content(state("N",a,b).toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.failed.length()").value(2));
        assertThat(snapshot()).isEqualTo(before);
    }
}
