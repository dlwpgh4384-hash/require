package com.company.common.bills.service;

import com.company.common.bills.controller.CommonBillController;
import com.company.common.bills.mapper.BillMapper;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.*;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="NAVIGATION_TEST_DB_URL",matches="jdbc:postgresql:.*")
@WebMvcTest(CommonBillController.class)
@ContextConfiguration(classes={CommonBillController.class,CommonBillService.class,BillDunningService.class,
    BillLocalMailbox.class,BillingCalculationRules.class,BillingNumberAllocator.class,CalculationRuleService.class,ExcelExportService.class,
    CurrentBusinessActor.class,CodeExceptionHandler.class,ApiExceptionHandler.class,BillPersistenceTest.Persistence.class})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BillPersistenceTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired DataSource source; @Autowired Owned owned;
    @Autowired BillMapper mapper;
    JdbcTemplate db;
    record Owned(String name) {}
    @TestConfiguration(proxyBeanMethods=false) @EnableTransactionManagement
    static class Persistence {
        @Bean Owned owned(){return new Owned("billing_"+UUID.randomUUID().toString().replace("-",""));}
        @Bean DataSource dataSource(Owned owned) throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL"),user=System.getenv("NAVIGATION_TEST_DB_USER"),password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()){statement.execute("CREATE SCHEMA "+owned.name());}
            var source=new UnpooledDataSource("org.postgresql.Driver",url,user,password);
            var props=new Properties();props.setProperty("currentSchema",owned.name());source.setDriverProperties(props);
            var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory=new SqlSessionFactoryBean();factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/projects/ProjectMapper.xml"),new ClassPathResource("mapper/bills/BillMapper.xml"),new ClassPathResource("mapper/common/BillingNumberMapper.xml"),new ClassPathResource("mapper/CalculationRuleMapper.xml"),new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var config=new org.apache.ibatis.session.Configuration();config.setMapUnderscoreToCamelCase(false);config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config);return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory f){return new SqlSessionTemplate(f);}
        @Bean ProjectMapper projectMapper(SqlSessionTemplate s){return s.getMapper(ProjectMapper.class);}
        @Bean BillMapper billMapper(SqlSessionTemplate s){return s.getMapper(BillMapper.class);}
        @Bean BillingNumberMapper billingNumberMapper(SqlSessionTemplate s){return s.getMapper(BillingNumberMapper.class);}
        @Bean CalculationRuleMapper calculationRuleMapper(SqlSessionTemplate s){return s.getMapper(CalculationRuleMapper.class);}
        @Bean UserPreferencePeriodSourceMapper userPreferencePeriodSourceMapper(SqlSessionTemplate s){return s.getMapper(UserPreferencePeriodSourceMapper.class);}
        @Bean DataSourceTransactionManager transactionManager(DataSource d){return new DataSourceTransactionManager(d);}
        @Bean BusinessCalendar businessCalendar(){return new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T01:00:00Z"),ZoneOffset.UTC));}
    }
    @BeforeEach void setup() {
        db=new JdbcTemplate(source);
        db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES('bill-project','청구 프로젝트','MO','ING','2026-01-01','2027-12-31',100000000,0,0,'admin')");
    }
    @AfterEach void cleanup() throws Exception {try(var connection=source.getConnection();var statement=connection.createStatement()){statement.execute("DROP SCHEMA "+owned.name()+" CASCADE");}}
    ObjectNode values(){return json.createObjectNode().put("supplyAmount",2000).put("vatRate",10).put("billDate","2026-11-15").put("payDueDate","2026-12-25").putNull("taxInvoiceNo");}
    ObjectNode input(){return values().put("projectId","bill-project").put("billMonth","202611");}
    JsonNode data(ResultActions result) throws Exception {return json.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");}
    String create() throws Exception {return data(mvc.perform(post("/api/common/bills").contentType("application/json").content(input().toString())).andExpect(status().isOk())).path("bill").path("billId").asText();}
    String mod(String id) throws Exception {return data(mvc.perform(get("/api/common/bills/"+id)).andExpect(status().isOk())).path("bill").path("modifiedAt").asText();}
    ObjectNode edit(String id) throws Exception {return values().put("modifiedAt",mod(id));}
    String cancelBody(String id,String reason) throws Exception {return json.createObjectNode().put("modifiedAt",mod(id)).put("reason",reason).toString();}
    Object snapshot(){return db.queryForList("SELECT * FROM TB_BL_BILL ORDER BY BILL_ID");}
    @Test void publicCreateCommitsOneRowAndNewSessionReadsExactAmounts() throws Exception {
        long before=db.queryForObject("SELECT COUNT(*) FROM TB_BL_BILL",Long.class);String id=create();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_BL_BILL",Long.class)).isEqualTo(before+1);
        mvc.perform(get("/api/common/bills/"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.bill.supplyAmount").value(2000)).andExpect(jsonPath("$.data.bill.vatRate").value(10)).andExpect(jsonPath("$.data.bill.payDueDate").value("2026-12-25"));
        assertThat(db.queryForObject("SELECT REG_USER_ID FROM TB_BL_BILL WHERE BILL_ID=?",String.class,id)).isEqualTo("admin");
    }
    @Test void duplicateWarningLeavesRowsAndAllocationUnchanged() throws Exception {
        create();Object before=snapshot();mvc.perform(post("/api/common/bills").contentType("application/json").content(input().toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty()).andExpect(jsonPath("$.warning").isNotEmpty());assertThat(snapshot()).isEqualTo(before);
        var saved=data(mvc.perform(post("/api/common/bills").contentType("application/json").content(input().put("duplicateConfirmed",true).toString())).andExpect(status().isOk()));
        assertThat(saved.path("bill").path("billOrder").asInt()).isEqualTo(2);
    }
    @Test void cancelledNumberAndOrderRemainReserved() throws Exception {
        String id=create();mvc.perform(post("/api/common/bills/"+id+"/cancel").contentType("application/json").content(cancelBody(id,"등록 정정"))).andExpect(status().isOk());
        Object original=db.queryForList("SELECT * FROM TB_BL_BILL WHERE BILL_ID=?",id);String next=create();assertThat(next).isNotEqualTo(id);
        assertThat(mapper.invoice(next).billOrder()).isEqualTo(2);assertThat(db.queryForList("SELECT * FROM TB_BL_BILL WHERE BILL_ID=?",id)).isEqualTo(original);
    }
    @Test void updateRecomputesBalanceWithoutChangingReceiptOrIdentity() throws Exception {
        String id=create();db.update("INSERT INTO TB_BL_RCPT(BILL_ID,RCPT_DT,RCPT_AMT,REG_USER_ID) VALUES(?,'2026-11-15',500,'admin')",id);
        Object receipts=db.queryForList("SELECT * FROM TB_BL_RCPT");
        mvc.perform(put("/api/common/bills/"+id).contentType("application/json").content(edit(id).put("supplyAmount",3000).toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.bill.unpaidAmount").value(2800));
        assertThat(mapper.invoice(id).vatAmount()).isEqualByComparingTo("300");assertThat(mapper.invoice(id).billOrder()).isEqualTo(1);assertThat(mapper.invoice(id).billMonth()).isEqualTo("202611");assertThat(mapper.invoice(id).projectId()).isEqualTo("bill-project");
        assertThat(db.queryForObject("SELECT MOD_USER_ID FROM TB_BL_BILL WHERE BILL_ID=?",String.class,id)).isEqualTo("admin");assertThat(db.queryForList("SELECT * FROM TB_BL_RCPT")).isEqualTo(receipts);
        assertThat(mapper.receipts(id).get(0).receiptId()).matches("[0-9]+");
    }
    @Test void belowReceiptFloorRollsBackBill() throws Exception {
        String id=create();db.update("INSERT INTO TB_BL_RCPT(BILL_ID,RCPT_DT,RCPT_AMT,REG_USER_ID) VALUES(?,'2026-11-15',2000,'admin')",id);Object before=snapshot();
        mvc.perform(put("/api/common/bills/"+id).contentType("application/json").content(edit(id).put("supplyAmount",1000).toString())).andExpect(status().isBadRequest());assertThat(snapshot()).isEqualTo(before);
    }
    @Test void closedMonthRefusesCreateUpdateAndCancelWithoutMutation() throws Exception {
        String id=create();db.update("INSERT INTO TB_CL_CLOSE(CLOSE_YM,CLOSE_STAT_CD,REG_USER_ID) VALUES('202611','CLOSE','admin')");Object before=snapshot();
        mvc.perform(post("/api/common/bills").contentType("application/json").content(input().toString())).andExpect(status().isBadRequest());
        mvc.perform(put("/api/common/bills/"+id).contentType("application/json").content(edit(id).toString())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/common/bills/"+id+"/cancel").contentType("application/json").content(cancelBody(id,"사유"))).andExpect(status().isBadRequest());assertThat(snapshot()).isEqualTo(before);
    }
    @Test void activeReceiptPreventsBillCancellationAndPreservesBothRows() throws Exception {
        String id=create();db.update("INSERT INTO TB_BL_RCPT(BILL_ID,RCPT_DT,RCPT_AMT,REG_USER_ID) VALUES(?,'2026-11-15',100,'admin')",id);Object before=snapshot(),receipts=db.queryForList("SELECT * FROM TB_BL_RCPT");
        mvc.perform(post("/api/common/bills/"+id+"/cancel").contentType("application/json").content(cancelBody(id,"사유"))).andExpect(status().isBadRequest());assertThat(snapshot()).isEqualTo(before);assertThat(db.queryForList("SELECT * FROM TB_BL_RCPT")).isEqualTo(receipts);
    }
    @Test void cancellationIsPreservedInListButExcludedFromSummary() throws Exception {
        String id=create();mvc.perform(post("/api/common/bills/"+id+"/cancel").contentType("application/json").content(cancelBody(id,"시험 취소"))).andExpect(status().isOk()).andExpect(jsonPath("$.data.bill.cancelledBy").value("admin"));
        mvc.perform(get("/api/common/bills").param("year","2026").param("status","CNCL")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].billId").value(id)).andExpect(jsonPath("$.data.summary.yearBillCount").value(0)).andExpect(jsonPath("$.data.totals.totalAmount").value(0));
        Object before=snapshot();mvc.perform(post("/api/common/bills/"+id+"/cancel").contentType("application/json").content(cancelBody(id,"재취소"))).andExpect(status().isBadRequest());assertThat(snapshot()).isEqualTo(before);
    }
    @Test void resendCommitsNotificationAndHistoryAndSecondAttemptPreservesBoth() throws Exception {
        String id=create();
        db.update("UPDATE TB_BL_BILL SET PAY_DUE_DT='2026-10-31' WHERE BILL_ID=?",id);
        String hash=new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(UUID.randomUUID().toString());
        db.update("INSERT INTO TB_SY_USER(USER_ID,USER_NM,EMAIL_ADDR,PWD_HASH,TEMP_PWD_YN,PWD_ERR_CNT,USER_STAT_CD,MFA_USE_YN,MFA_ERR_CNT,REG_USER_ID) VALUES('bill-pm','청구 담당','billing@example.test',?,'N',0,'USE','N',0,'admin')",hash);
        db.update("INSERT INTO TB_SY_USER_ROLE(USER_ID,ROLE_ID,REG_USER_ID) VALUES('bill-pm','PM','admin')");
        db.update("INSERT INTO TB_SY_USER_SCOPE(USER_ID,SCOPE_SN,SCOPE_TYPE_CD,PROJ_ID,REG_USER_ID) VALUES('bill-pm',1,'PROJ','bill-project','admin')");
        // 예정일 10-31, 오늘 11-15 → 15일 지남: 기준값 DUN_D1=7·DUN_D2=30이라 1단계(PM)
        mvc.perform(post("/api/common/bills/"+id+"/dunning-resend")).andExpect(status().isOk()).andExpect(jsonPath("$.data.history.step").value(1));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_BL_DUN_HIST WHERE BILL_ID=? AND DUN_STEP=1 AND RESEND_YN='Y'",Long.class,id)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT NOTI_CN FROM TB_SY_NOTI WHERE LINK_KEY=?",String.class,id)).startsWith("미수 1단계 알림(다시): 청구 프로젝트 11월분 (15일 지남)");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_SY_NOTI WHERE LINK_KEY=? AND RCVR_USER_ID='bill-pm'",Long.class,id)).isEqualTo(1);
        Object histories=db.queryForList("SELECT * FROM TB_BL_DUN_HIST"),notices=db.queryForList("SELECT * FROM TB_SY_NOTI");
        mvc.perform(post("/api/common/bills/"+id+"/dunning-resend")).andExpect(status().isBadRequest());
        assertThat(db.queryForList("SELECT * FROM TB_BL_DUN_HIST")).isEqualTo(histories);assertThat(db.queryForList("SELECT * FROM TB_SY_NOTI")).isEqualTo(notices);
    }
    @Test void staleEditAndCancelAre409WithoutChange() throws Exception {
        String id=create();String token=mod(id);
        mvc.perform(put("/api/common/bills/"+id).contentType("application/json").content(values().put("modifiedAt",token).put("supplyAmount",2500).toString())).andExpect(status().isOk());
        Object before=snapshot();
        mvc.perform(put("/api/common/bills/"+id).contentType("application/json").content(values().put("modifiedAt",token).toString())).andExpect(status().isConflict());
        mvc.perform(post("/api/common/bills/"+id+"/cancel").contentType("application/json").content(json.createObjectNode().put("modifiedAt",token).put("reason","사유").toString())).andExpect(status().isConflict());
        assertThat(snapshot()).isEqualTo(before);
        assertThat(db.queryForObject("SELECT REG_DTTM IS NOT NULL AND MOD_DTTM>=REG_DTTM FROM TB_BL_BILL WHERE BILL_ID=?",Boolean.class,id)).isTrue();
    }
    @Test void excelWritesDownloadAccessLog() throws Exception {
        create();
        mvc.perform(get("/api/common/bills/excel").param("year","2026")).andExpect(status().isOk()).andExpect(header().string("X-Total-Count","1"));
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_SY_ACCS_LOG WHERE ACT_TYPE_CD='DNLD' AND SCRN_ID='UI-13'",Long.class)).isEqualTo(1);
    }
    @Test void nextMonthReadsMonthlyInputWithoutChangingIt() throws Exception {
        String id=create();
        db.update("INSERT INTO TB_BS_PART(PART_ID,PART_NM,HQ_YN,USE_YN,REG_USER_ID) VALUES('bill-part','청구 파트','N','Y','admin')");
        String job=db.queryForObject("SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB' AND USE_YN='Y'",String.class);
        db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,REG_USER_ID) VALUES('bill-person','bill-person','청구 인력','FT',?,'DIRECT','bill-part','IDLE','admin')",job);
        db.update("INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,CTRT_MM,BILL_UPRC,INPUT_TYPE_CD,EXT_CNT,EARLY_WTHDR_YN,ASGN_STAT_CD,REG_USER_ID) VALUES('bill-assignment','bill-project','bill-person',?,'2026-01-01','2027-12-31',1,8000000,'NEW',0,'N','ACT','admin')",job);
        db.update("INSERT INTO TB_PJ_ASGN_MON(ASGN_ID,INPUT_YM,DEV_ID,PROJ_ID,INPUT_DAY_CNT,MON_MM,APLY_UPRC,SALES_AMT,FIX_YN,REG_USER_ID) VALUES('bill-assignment','202612','bill-person','bill-project',31,1,8000000,8000000,'N','admin')");
        Object months=db.queryForList("SELECT * FROM TB_PJ_ASGN_MON"),original=db.queryForList("SELECT * FROM TB_BL_BILL WHERE BILL_ID=?",id);
        var detail=data(mvc.perform(post("/api/common/bills/"+id+"/next-month")).andExpect(status().isOk()));
        String next=detail.path("bill").path("billId").asText();assertThat(next).isNotEqualTo(id);assertThat(mapper.invoice(next).billOrder()).isEqualTo(2);assertThat(mapper.invoice(next).billMonth()).isEqualTo("202612");assertThat(mapper.invoice(next).billDate()).isEqualTo(LocalDate.of(2026,11,15));assertThat(mapper.invoice(next).payDueDate()).isEqualTo(LocalDate.of(2027,1,25));assertThat(mapper.invoice(next).taxInvoiceNo()).isNull();assertThat(mapper.invoice(next).supplyAmount()).isEqualByComparingTo("8000000");
        assertThat(db.queryForList("SELECT * FROM TB_PJ_ASGN_MON")).isEqualTo(months);assertThat(db.queryForList("SELECT * FROM TB_BL_BILL WHERE BILL_ID=?",id)).isEqualTo(original);
    }
}
