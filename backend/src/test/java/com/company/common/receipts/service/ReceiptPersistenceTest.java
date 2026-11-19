package com.company.common.receipts.service;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.BillingCalculationRules;
import com.company.common.commonops.controller.BillingLookupController;
import com.company.common.commonops.mapper.BillingLookupMapper;
import com.company.common.commonops.service.BillingLookupService;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.receipts.controller.ReceiptController;
import com.company.common.receipts.mapper.ReceiptMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
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
@WebMvcTest({ReceiptController.class,BillingLookupController.class})
@ContextConfiguration(classes={ReceiptController.class,ReceiptService.class,BillingLookupController.class,
    BillingLookupService.class,BillingCalculationRules.class,CalculationRuleService.class,CurrentBusinessActor.class,
    CodeExceptionHandler.class,ApiExceptionHandler.class,ReceiptPersistenceTest.Persistence.class})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReceiptPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired Owned owned;
    @Autowired ReceiptMapper mapper;
    @Autowired BillingCalculationRules rules;
    JdbcTemplate db;
    record Owned(String name) {}

    @TestConfiguration(proxyBeanMethods=false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean Owned owned() { return new Owned("receipt_"+UUID.randomUUID().toString().replace("-","")); }
        @Bean DataSource dataSource(Owned owned)throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL"),user=System.getenv("NAVIGATION_TEST_DB_USER"),password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()) {
                s.execute("CREATE SCHEMA "+owned.name());
            }
            var source=new UnpooledDataSource("org.postgresql.Driver",url,user,password);
            var props=new Properties();
            props.setProperty("currentSchema",owned.name());
            source.setDriverProperties(props);
            var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment(
                "postgresql",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();
            return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source)throws Exception {
            var f=new SqlSessionFactoryBean();
            f.setDataSource(source);
            f.setMapperLocations(new ClassPathResource("mapper/projects/ProjectMapper.xml"),
                new ClassPathResource("mapper/receipts/ReceiptMapper.xml"),
                new ClassPathResource("mapper/commonops/BillingLookupMapper.xml"),
                new ClassPathResource("mapper/CalculationRuleMapper.xml"),
                new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var c=new org.apache.ibatis.session.Configuration();
            c.setMapUnderscoreToCamelCase(false);
            c.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            f.setConfiguration(c);
            return f.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory f) { return new SqlSessionTemplate(f); }
        @Bean ProjectMapper projectMapper(SqlSessionTemplate s) { return s.getMapper(ProjectMapper.class); }
        @Bean ReceiptMapper receiptMapper(SqlSessionTemplate s) { return s.getMapper(ReceiptMapper.class); }
        @Bean BillingLookupMapper billingLookupMapper(SqlSessionTemplate s) { return s.getMapper(BillingLookupMapper.class); }
        @Bean CalculationRuleMapper calculationRuleMapper(SqlSessionTemplate s) { return s.getMapper(CalculationRuleMapper.class); }
        @Bean UserPreferencePeriodSourceMapper userPreferencePeriodSourceMapper(SqlSessionTemplate s) {
            return s.getMapper(UserPreferencePeriodSourceMapper.class);
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource d) { return new DataSourceTransactionManager(d); }
        @Bean BusinessCalendar businessCalendar() {
            return new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"),ZoneOffset.UTC));
        }
    }
    @BeforeEach void setup() {
        db=new JdbcTemplate(source);
        db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES('receipt-project','입금 시험','MO','ING','2025-01-01','2027-12-31',1000,0,0,'admin')");
        bill("old-bill","202509");
        bill("second-bill","202609");
    }
    void bill(String id,String month) {
        db.update("INSERT INTO TB_BL_BILL(BILL_ID,PROJ_ID,BILL_ORD,BILL_YM,BILL_DT,SPLY_AMT,VAT_RT,VAT_AMT,TOT_AMT,RCPT_SUM_AMT,UNPAID_AMT,PAY_DUE_DT,REG_USER_ID) VALUES(?,'receipt-project',1,?,'2025-09-01',1000,0,0,1000,0,1000,'2025-09-25','admin')",id,month);
    }
    @AfterEach void cleanup()throws Exception {
        try(var c=source.getConnection();var s=c.createStatement()) { s.execute("DROP SCHEMA "+owned.name()+" CASCADE"); }
    }
    String input(int amount) {
        return "{\"receiptDate\":\"2026-10-20\",\"items\":[{\"billId\":\"old-bill\",\"amount\":"+amount+"}]}";
    }
    ResultActions postReceipt(String body)throws Exception {
        return mvc.perform(post("/api/common/receipts").contentType("application/json").content(body));
    }
    JsonNode data(ResultActions result)throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }
    String create(int amount)throws Exception {
        return data(postReceipt(input(amount)).andExpect(status().isOk())).path("receipts").get(0).path("receiptId").asText();
    }
    ResultActions cancel(String id,String reason)throws Exception {
        return mvc.perform(post("/api/common/receipts/"+id+"/cancel").contentType("application/json")
            .content(json.createObjectNode().put("reason",reason).toString()));
    }
    Object bills() { return db.queryForList("SELECT * FROM TB_BL_BILL ORDER BY BILL_ID"); }
    Object receipts() { return db.queryForList("SELECT * FROM TB_BL_RCPT ORDER BY RCPT_ID"); }
    Object original() {
        return db.queryForList("SELECT BILL_ID,PROJ_ID,BILL_ORD,BILL_YM,BILL_DT,SPLY_AMT,VAT_RT,VAT_AMT,TOT_AMT,PAY_DUE_DT,TAX_INVC_NO,REG_USER_ID,REG_DTTM FROM TB_BL_BILL ORDER BY BILL_ID");
    }
    void balance(String sum,String unpaid) {
        assertThat(db.queryForObject("SELECT RCPT_SUM_AMT FROM TB_BL_BILL WHERE BILL_ID='old-bill'",BigDecimal.class)).isEqualByComparingTo(sum);
        assertThat(db.queryForObject("SELECT UNPAID_AMT FROM TB_BL_BILL WHERE BILL_ID='old-bill'",BigDecimal.class)).isEqualByComparingTo(unpaid);
    }
    void close(String month) {
        db.update("INSERT INTO TB_CL_CLOSE(CLOSE_YM,CLOSE_STAT_CD,REG_USER_ID) VALUES(?,'CLOSE','admin')",month);
    }
    @Test void anonymousOldBillPartialReceiptPersistsOneOriginalRowAndBalances()throws Exception {
        Object original=original();
        String id=create(400);
        assertThat(id).matches("[0-9]{1,12}");
        assertThat(mapper.receipt(id).amount()).isEqualByComparingTo("400");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_BL_RCPT",Long.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT REG_USER_ID FROM TB_BL_RCPT WHERE RCPT_ID=CAST(? AS numeric)",String.class,id)).isEqualTo("admin");
        balance("400","600");
        assertThat(original()).isEqualTo(original);
    }
    @Test void zeroPreservesEveryRowAndExactFieldMessage()throws Exception {
        Object before=bills();
        postReceipt(input(0)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("items.amount"))
            .andExpect(jsonPath("$.errors[0].message").value("입금액을 입력해 주세요"));
        assertThat(receipts()).isEqualTo(List.of());
        assertThat(bills()).isEqualTo(before);
    }
    @Test void overpaymentPreservesEveryRow()throws Exception {
        Object before=bills();
        postReceipt(input(1001)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].message").value("입금액이 미수금보다 큽니다"));
        assertThat(receipts()).isEqualTo(List.of());
        assertThat(bills()).isEqualTo(before);
    }
    @Test void currentMonthClosurePreservesEveryRow()throws Exception {
        close("202611");
        Object before=bills();
        postReceipt(input(400)).andExpect(status().isBadRequest());
        assertThat(receipts()).isEqualTo(List.of());
        assertThat(bills()).isEqualTo(before);
    }
    @Test void invalidLaterBatchRowNeverCommitsTheEarlierRow()throws Exception {
        Object before=bills();
        postReceipt("{\"receiptDate\":\"2026-10-20\",\"items\":[{\"billId\":\"old-bill\",\"amount\":400},{\"billId\":\"second-bill\",\"amount\":1001}]}")
            .andExpect(status().isBadRequest());
        assertThat(receipts()).isEqualTo(List.of());
        assertThat(bills()).isEqualTo(before);
    }
    @Test void successfulBatchStoresSeparateRowsWithOneDateAndSeparateBalances()throws Exception {
        var result=data(postReceipt("{\"receiptDate\":\"2026-10-20\",\"items\":[{\"billId\":\"old-bill\",\"amount\":400},{\"billId\":\"second-bill\",\"amount\":700}]}")
            .andExpect(status().isOk()));
        assertThat(result.path("receipts")).hasSize(2);
        assertThat(result.path("receipts").get(0).path("receiptId").asText())
            .isNotEqualTo(result.path("receipts").get(1).path("receiptId").asText());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_BL_RCPT WHERE RCPT_DT='2026-10-20' AND CNCL_YN='N'",Long.class)).isEqualTo(2);
        balance("400","600");
        assertThat(db.queryForObject("SELECT UNPAID_AMT FROM TB_BL_BILL WHERE BILL_ID='second-bill'",BigDecimal.class)).isEqualByComparingTo("300");
    }
    @Test void receiptMonthClosureBlocksBackdatedRegistrationWithoutWrites()throws Exception {
        close("202610");
        Object before=bills();
        postReceipt(input(400)).andExpect(status().isBadRequest());
        assertThat(receipts()).isEqualTo(List.of());
        assertThat(bills()).isEqualTo(before);
    }
    @Test void fullPaymentStopsRealDunningReadAndKeepsNormalReceipt()throws Exception {
        var before=data(mvc.perform(get("/api/common/bill-dunning")).andExpect(status().isOk()));
        assertThat(before.path("items").findValuesAsText("billId")).contains("old-bill");
        String id=create(1000);
        balance("1000","0");
        var after=data(mvc.perform(get("/api/common/bill-dunning")).andExpect(status().isOk()));
        assertThat(after.path("items").findValuesAsText("billId")).doesNotContain("old-bill");
        assertThat(mapper.receipt(id).cancelled()).isFalse();
        assertThat(rules.status(false,BigDecimal.ZERO,LocalDate.of(2025,9,25),LocalDate.of(2026,11,15))).isEqualTo("PAID");
    }
    @Test void closedBillMonthDoesNotBlockOpenReceiptCancellationAndMetadataSurvives()throws Exception {
        close("202509");
        close("202609");
        Object original=original();
        String id=create(1000);
        cancel(id,"  금액 정정  ").andExpect(status().isOk());
        var row=mapper.receipt(id);
        assertThat(row.cancelled()).isTrue();
        assertThat(row.cancelReason()).isEqualTo("금액 정정");
        assertThat(row.cancelledBy()).isEqualTo("admin");
        assertThat(row.cancelledAt()).isNotNull();
        assertThat(row.amount()).isEqualByComparingTo("1000");
        assertThat(row.receiptDate()).isEqualTo(LocalDate.of(2026,10,20));
        assertThat(row.canCancel()).isFalse();
        balance("0","1000");
        assertThat(original()).isEqualTo(original);
        assertThat(rules.status(false,new BigDecimal("1000"),LocalDate.of(2025,9,25),LocalDate.of(2026,11,15))).isEqualTo("LATE");
    }
    @Test void missingReasonLeavesReceiptAndBalancesUntouched()throws Exception {
        String id=create(400);
        Object before=bills(),rows=receipts();
        cancel(id," ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("reason"));
        assertThat(bills()).isEqualTo(before);
        assertThat(receipts()).isEqualTo(rows);
    }
    @Test void closedReceiptMonthHidesCancellationAndPreservesEveryRow()throws Exception {
        String id=create(400);
        close("202610");
        Object before=bills(),rows=receipts();
        assertThat(mapper.receipt(id).canCancel()).isFalse();
        cancel(id,"정정").andExpect(status().isBadRequest());
        assertThat(bills()).isEqualTo(before);
        assertThat(receipts()).isEqualTo(rows);
    }
    @Test void sequentialRepeatKeepsFirstMetadataAndDoesNotReverseAgain()throws Exception {
        String id=create(400);
        cancel(id,"첫 사유").andExpect(status().isOk());
        Object before=bills(),rows=receipts();
        cancel(id,"두번째 사유").andExpect(status().isBadRequest());
        assertThat(bills()).isEqualTo(before);
        assertThat(receipts()).isEqualTo(rows);
    }
    @Test void databaseFailureDuringSecondInsertRollsBackEntireBatch()throws Exception {
        db.execute("ALTER TABLE TB_BL_RCPT ADD CONSTRAINT receipt_test_amount CHECK (RCPT_AMT<>777)");
        Object before=bills();
        postReceipt("{\"receiptDate\":\"2026-10-20\",\"items\":[{\"billId\":\"old-bill\",\"amount\":400},{\"billId\":\"second-bill\",\"amount\":777}]}")
            .andExpect(status().is5xxServerError());
        assertThat(receipts()).isEqualTo(List.of());
        assertThat(bills()).isEqualTo(before);
    }
    @Test void concurrentReceiptsCannotOverpay()throws Exception {
        var executor=Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> action=() -> postReceipt(input(700)).andReturn().getResponse().getStatus();
            var outcomes=executor.invokeAll(List.of(action,action));
            assertThat(List.of(outcomes.get(0).get(),outcomes.get(1).get())).containsExactlyInAnyOrder(200,400);
            balance("700","300");
            assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_BL_RCPT",Long.class)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
    @Test void concurrentCancellationHasOnlyOneBalanceReversal()throws Exception {
        String id=create(400);
        var executor=Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> action=() -> cancel(id,"정정").andReturn().getResponse().getStatus();
            var outcomes=executor.invokeAll(List.of(action,action));
            assertThat(List.of(outcomes.get(0).get(),outcomes.get(1).get())).containsExactlyInAnyOrder(200,400);
            balance("0","1000");
            assertThat(db.queryForObject("SELECT COUNT(*) FROM TB_BL_RCPT",Long.class)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
}
