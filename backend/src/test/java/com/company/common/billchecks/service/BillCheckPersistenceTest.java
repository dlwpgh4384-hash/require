package com.company.common.billchecks.service;

import com.company.common.billchecks.controller.BillCheckController;
import com.company.common.billchecks.mapper.BillCheckMapper;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.BillingCalculationRules;
import com.company.common.common.BillingNumberAllocator;
import com.company.common.common.BillingNumberMapper;
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
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="NAVIGATION_TEST_DB_URL",matches="jdbc:postgresql:.*")
@WebMvcTest(BillCheckController.class)
@ContextConfiguration(classes={BillCheckController.class,BillCheckService.class,BillingCalculationRules.class,BillingNumberAllocator.class,
    CalculationRuleService.class,CurrentBusinessActor.class,CodeExceptionHandler.class,ApiExceptionHandler.class,
    BillCheckPersistenceTest.Persistence.class})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BillCheckPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired Owned owned;
    @Autowired BillCheckMapper mapper;
    JdbcTemplate db;
    record Owned(String name) {}

    @TestConfiguration(proxyBeanMethods=false) @EnableTransactionManagement
    static class Persistence {
        @Bean Owned owned() { return new Owned("billcheck_"+UUID.randomUUID().toString().replace("-","")); }
        @Bean DataSource dataSource(Owned owned)throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL");
            String user=System.getenv("NAVIGATION_TEST_DB_USER"),password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try(var connection=DriverManager.getConnection(url,user,password);var statement=connection.createStatement()) {
                statement.execute("CREATE SCHEMA "+owned.name());
            }
            var source=new UnpooledDataSource("org.postgresql.Driver",url,user,password);
            var props=new Properties();props.setProperty("currentSchema",owned.name());source.setDriverProperties(props);
            var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();
            return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source)throws Exception {
            var factory=new SqlSessionFactoryBean();factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/projects/ProjectMapper.xml"),
                new ClassPathResource("mapper/billchecks/BillCheckMapper.xml"),new ClassPathResource("mapper/CalculationRuleMapper.xml"),
                new ClassPathResource("mapper/common/BillingNumberMapper.xml"),new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var config=new org.apache.ibatis.session.Configuration();config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config);return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean ProjectMapper projectMapper(SqlSessionTemplate session) { return session.getMapper(ProjectMapper.class); }
        @Bean BillCheckMapper billCheckMapper(SqlSessionTemplate session) { return session.getMapper(BillCheckMapper.class); }
        @Bean BillingNumberMapper billingNumberMapper(SqlSessionTemplate session) { return session.getMapper(BillingNumberMapper.class); }
        @Bean CalculationRuleMapper calculationRuleMapper(SqlSessionTemplate session) { return session.getMapper(CalculationRuleMapper.class); }
        @Bean com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper userPreferencePeriodSourceMapper(SqlSessionTemplate session) {
            return session.getMapper(com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper.class);
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean BusinessCalendar businessCalendar() {
            return new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T01:00:00Z"),ZoneOffset.UTC));
        }
    }
    @BeforeEach void setup() {
        db=new JdbcTemplate(source);
        db.update("INSERT INTO TB_BS_PART(PART_ID,PART_NM,HQ_YN,USE_YN,REG_USER_ID) VALUES('check-part','점검 파트','N','Y','admin')");
        db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES('check-project','점검 프로젝트','MO','ING','2025-01-01','2027-12-31',100000000,0,0,'admin')");
        String job=db.queryForObject("SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB' AND USE_YN='Y'",String.class);
        db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,REG_USER_ID) VALUES('check-person','check-person','점검 인력','FT',?,'DIRECT','check-part','IDLE','admin')",job);
        db.update("INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,CTRT_MM,BILL_UPRC,INPUT_TYPE_CD,EXT_CNT,EARLY_WTHDR_YN,ASGN_STAT_CD,REG_USER_ID) VALUES('check-assignment','check-project','check-person',?,'2025-01-01','2027-12-31',1,8000000,'NEW',0,'N','ACT','admin')",job);
        db.update("INSERT INTO TB_PJ_ASGN_MON(ASGN_ID,INPUT_YM,DEV_ID,PROJ_ID,INPUT_DAY_CNT,MON_MM,APLY_UPRC,SALES_AMT,FIX_YN,REG_USER_ID) VALUES('check-assignment','202611','check-person','check-project',30,1,8000000,8000000,'N','admin')");
    }
    @AfterEach void cleanup()throws Exception {
        try(var connection=source.getConnection();var statement=connection.createStatement()) {
            statement.execute("DROP SCHEMA "+owned.name()+" CASCADE");
        }
    }
    ObjectNode input() {
        return json.createObjectNode().put("projectId","check-project").put("checkMonth","202611")
            .put("vatRate",10).put("billDate","2026-11-15").put("payDueDate","2026-12-25");
    }
    void invoice(String id,int order,long supply) {
        db.update("INSERT INTO TB_BL_BILL(BILL_ID,PROJ_ID,BILL_ORD,BILL_YM,BILL_DT,SPLY_AMT,VAT_RT,VAT_AMT,TOT_AMT,RCPT_SUM_AMT,UNPAID_AMT,PAY_DUE_DT,REG_USER_ID) VALUES(?,'check-project',?,'202611','2026-11-15',?,10,?,?,0,?,'2026-12-25','admin')",
            id,order,supply,supply/10,supply+supply/10,supply+supply/10);
    }
    void closeMonth() {
        db.update("INSERT INTO TB_CL_CLOSE(CLOSE_YM,CLOSE_STAT_CD,REG_USER_ID) VALUES('202611','CLOSE','admin')");
        db.update("UPDATE TB_PJ_ASGN_MON SET FIX_YN='Y'");
    }
    void eligible(String action) {
        if (action.equals("additional")) { closeMonth();invoice("B-202611-001",1,3000000); }
        if (action.equals("correction")) { invoice("B-202611-001",1,1000000);invoice("B-202611-002",2,2000000); }
    }
    /** [청구 고치기] 화면처럼 고칠 청구(그 달 청구일·차수가 가장 늦은 것)의 수정 시각을 읽어 보낸다. */
    String readModifiedAt() {
        var ids=db.queryForList("SELECT BILL_ID FROM TB_BL_BILL WHERE BILL_YM='202611' AND CNCL_YN='N' ORDER BY BILL_DT DESC,BILL_ORD DESC,BILL_ID DESC LIMIT 1",String.class);
        return ids.isEmpty()?null:mapper.invoice(ids.get(0)).modifiedAt.toString();
    }
    ResultActions command(String action,ObjectNode body)throws Exception {
        if (action.equals("correction") && !body.has("modifiedAt")) body.put("modifiedAt",readModifiedAt());
        return mvc.perform(post("/api/common/bill-checks/"+action).contentType("application/json").content(body.toString()));
    }
    JsonNode data(ResultActions result)throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }
    Object bills() { return db.queryForList("SELECT * FROM TB_BL_BILL ORDER BY BILL_ID"); }
    Object months() { return db.queryForList("SELECT * FROM TB_PJ_ASGN_MON ORDER BY ASGN_ID,INPUT_YM"); }
    Object closes() { return db.queryForList("SELECT * FROM TB_CL_CLOSE ORDER BY CLOSE_YM"); }

    @ParameterizedTest @ValueSource(strings={"missing","additional","correction"})
    void happyCommandMaterializesStoredSupplyAndPreservesOtherBills(String action)throws Exception {
        eligible(action);
        var original=db.queryForList("SELECT * FROM TB_BL_BILL WHERE BILL_ID='B-202611-001'");
        var detail=data(command(action,input()).andExpect(status().isOk()));
        String id=detail.path("bill").path("billId").asText();
        BigDecimal expected=new BigDecimal(action.equals("additional")?"5000000":action.equals("correction")?"7000000":"8000000");
        assertThat(mapper.invoice(id).supplyAmount).isEqualByComparingTo(expected);
        assertThat(detail.path("bill").path("supplyAmount").decimalValue()).isEqualByComparingTo(expected);
        if (!original.isEmpty()) assertThat(db.queryForList("SELECT * FROM TB_BL_BILL WHERE BILL_ID='B-202611-001'")).isEqualTo(original);
        if(action.equals("correction")) {
            assertThat(id).isEqualTo("B-202611-002");
            assertThat(mapper.invoice(id).billOrder).isEqualTo(2);
            assertThat(mapper.invoice(id).billMonth).isEqualTo("202611");
        }
    }
    @ParameterizedTest @ValueSource(strings={"missing","additional","correction"})
    void publicCommandPersistsAdminAudit(String action)throws Exception {
        eligible(action);var result=data(command(action,input()).andExpect(status().isOk()));
        String id=result.path("bill").path("billId").asText();
        assertThat(db.queryForObject("SELECT REG_USER_ID FROM TB_BL_BILL WHERE BILL_ID=?",String.class,id)).isEqualTo("admin");
        assertThat(db.queryForObject("SELECT MOD_USER_ID FROM TB_BL_BILL WHERE BILL_ID=?",String.class,id)).isEqualTo("admin");
    }
    @ParameterizedTest @ValueSource(strings={"missing","additional","correction"})
    void malformedMonthReturnsFieldErrorAndZeroDatabaseChanges(String action)throws Exception {
        eligible(action);Object before=bills();
        command(action,input().put("checkMonth","2026-11")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("checkMonth"));
        assertThat(bills()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"missing","additional","correction"})
    void staleCheckCannotWriteWhenItsConditionIsResolved(String action)throws Exception {
        eligible(action);mvc.perform(get("/api/common/bill-checks").param("checkMonth","202611")).andExpect(status().isOk());
        if(action.equals("missing")) invoice("B-202611-001",1,8000000);
        else if(action.equals("additional")) db.update("UPDATE TB_BL_BILL SET SPLY_AMT=8000000,VAT_AMT=800000,TOT_AMT=8800000,UNPAID_AMT=8800000");
        else closeMonth();
        Object before=bills();command(action,input()).andExpect(status().isBadRequest());assertThat(bills()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings={"missing","additional","correction"})
    void successfulWriteRecomputesGetWhileMonthlyAndCloseSourcesRemainByteForByte(String action)throws Exception {
        eligible(action);Object monthly=months(),closure=closes();command(action,input()).andExpect(status().isOk());
        var check=data(mvc.perform(get("/api/common/bill-checks").param("checkMonth","202611")).andExpect(status().isOk()));
        assertThat(check.path("items").get(0).path("difference").decimalValue()).isEqualByComparingTo("0");
        assertThat(months()).isEqualTo(monthly);assertThat(closes()).isEqualTo(closure);
    }
    @Test void cancelledNumbersAndOrdersAreNotReused()throws Exception {
        invoice("B-202611-008",8,1000000);db.update("UPDATE TB_BL_BILL SET CNCL_YN='Y'");
        var result=data(command("missing",input()).andExpect(status().isOk()));
        assertThat(result.path("bill").path("billId").asText()).isEqualTo("B-202611-009");
        assertThat(result.path("bill").path("billOrder").asInt()).isEqualTo(9);
    }
    @Test void receiptFloorUsesNormalRowsAndRollsBackCorrection()throws Exception {
        invoice("B-202611-001",1,10000000);
        db.update("INSERT INTO TB_BL_RCPT(BILL_ID,RCPT_DT,RCPT_AMT,REG_USER_ID) VALUES('B-202611-001','2026-11-15',9000000,'admin')");
        Object before=bills();command("correction",input()).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("supplyAmount"))
            .andExpect(jsonPath("$.errors[0].message").value("합계가 이미 받은 금액(9,000,000원)보다 작습니다."));
        assertThat(bills()).isEqualTo(before);
        db.update("UPDATE TB_BL_RCPT SET CNCL_YN='Y'");
        var detail=data(command("correction",input()).andExpect(status().isOk()));
        assertThat(detail.path("bill").path("receiptSumAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(detail.path("receipts").get(0).path("receiptId").asText()).matches("[0-9]+");
    }
    @Test void databaseInsertFailureRollsBackWithoutChangingSourceOrOldInvoice()throws Exception {
        eligible("additional");
        db.execute("ALTER TABLE TB_BL_BILL ADD CONSTRAINT billcheck_reject_insert CHECK (BILL_ID='B-202611-001')");
        Object before=bills(),monthly=months();
        command("additional",input()).andExpect(status().is5xxServerError());
        assertThat(bills()).isEqualTo(before);assertThat(months()).isEqualTo(monthly);
    }
    @Test void correctionAfterSomeoneElseChangedTheBillIsCommonConflictAndWritesNothing()throws Exception {
        eligible("correction");
        String read=readModifiedAt();
        db.update("UPDATE TB_BL_BILL SET TAX_INVC_NO='OTHER',MOD_USER_ID='other',MOD_DTTM=clock_timestamp() WHERE BILL_ID='B-202611-002'");
        Object before=bills();
        command("correction",input().put("modifiedAt",read)).andExpect(status().isConflict())
            .andExpect(jsonPath("$.errors[0].field").value("modifiedAt"))
            .andExpect(jsonPath("$.errors[0].message").value("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."));
        assertThat(bills()).isEqualTo(before);
    }
    @Test void guardedCorrectUpdateChangesNoRowForAStaleTime()throws Exception {
        eligible("correction");
        var latest=mapper.invoice("B-202611-002");
        var stale=new com.company.common.billchecks.dto.BillCheckContracts.Input("check-project","202611",BigDecimal.TEN,
            LocalDate.of(2026,11,15),LocalDate.of(2026,12,25),null,new BigDecimal("7000000"),latest.modifiedAt.minusSeconds(1));
        var amounts=new BillingCalculationRules.Amounts(new BigDecimal("7000000"),BigDecimal.TEN,new BigDecimal("700000"),new BigDecimal("7700000"));
        Object before=bills();
        assertThat(mapper.correct(new com.company.common.billchecks.mapper.BillCheckSources.Write("B-202611-002",2,stale,amounts,BigDecimal.ZERO,"admin"))).isZero();
        assertThat(bills()).isEqualTo(before);
        var current=new com.company.common.billchecks.dto.BillCheckContracts.Input("check-project","202611",BigDecimal.TEN,
            LocalDate.of(2026,11,15),LocalDate.of(2026,12,25),null,new BigDecimal("7000000"),latest.modifiedAt);
        assertThat(mapper.correct(new com.company.common.billchecks.mapper.BillCheckSources.Write("B-202611-002",2,current,amounts,BigDecimal.ZERO,"admin"))).isEqualTo(1);
    }
    @Test void checkMonthButtonsUseTheYearRangeOfBillMonths()throws Exception {
        invoice("B-202611-001",1,1000000);
        db.update("INSERT INTO TB_BL_BILL(BILL_ID,PROJ_ID,BILL_ORD,BILL_YM,BILL_DT,SPLY_AMT,VAT_RT,VAT_AMT,TOT_AMT,RCPT_SUM_AMT,UNPAID_AMT,PAY_DUE_DT,REG_USER_ID) VALUES('B-202512-001','check-project',2,'202512','2025-12-15',1,10,0,1,0,1,'2026-01-25','admin'),('B-202601-001','check-project',3,'202601','2026-01-15',1,10,0,1,0,1,'2026-02-25','admin')");
        assertThat(mapper.billMonths("2026","202611")).containsExactly("202601","202611");
    }
    @Test void cancelledAssignmentIsLeftOutEvenInClosedMonth()throws Exception {
        // 사용자 결정(2026-11-19): 투입 기준 금액은 취소 안 된 배정(ASGN_STAT_CD='ACT')만, 마감한 달도 같다.
        String job=db.queryForObject("SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB' AND USE_YN='Y'",String.class);
        db.update("INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,CTRT_MM,BILL_UPRC,INPUT_TYPE_CD,EXT_CNT,EARLY_WTHDR_YN,ASGN_STAT_CD,REG_USER_ID) VALUES('check-cancelled','check-project','check-person',?,'2025-01-01','2027-12-31',1,5000000,'NEW',0,'N','CNCL','admin')",job);
        db.update("INSERT INTO TB_PJ_ASGN_MON(ASGN_ID,INPUT_YM,DEV_ID,PROJ_ID,INPUT_DAY_CNT,MON_MM,APLY_UPRC,SALES_AMT,FIX_YN,REG_USER_ID) VALUES('check-cancelled','202611','check-person','check-project',30,1,5000000,5000000,'N','admin')");
        closeMonth();
        var rows=data(mvc.perform(get("/api/common/bill-checks").param("checkMonth","202611")).andExpect(status().isOk())).path("items");
        assertThat(rows.get(0).path("referenceAmount").decimalValue()).isEqualByComparingTo("8000000");
    }
}
