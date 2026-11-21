package com.company.common.financeitems;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;

import com.company.common.common.FinanceItemDefinitions;
import com.company.common.financeitems.config.FinanceItemConfiguration;
import com.company.common.financeitems.controller.CommonFinanceItemController;
import com.company.common.financeitems.service.*;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.service.*;
import com.company.common.uploads.mapper.UploadMapper;
import com.company.common.uploads.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.StreamSupport;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonFinanceItemController.class)
@Import({CommonFinanceItemService.class,FinanceTaxService.class,FinanceItemUploadForm.class,
    FinanceItemDefinitions.class,CalculationRuleService.class,CurrentBusinessActor.class,
    BusinessCalendar.class,ExcelExportService.class,UploadWorkbookService.class,CommonUploadService.class,FinanceItemPersistenceTest.Database.class})
@TestPropertySource(properties="spring.flyway.enabled=false")
class FinanceItemPersistenceTest {
    static final String URL=System.getenv("NAVIGATION_TEST_DB_URL");
    static final String USER=System.getenv("NAVIGATION_TEST_DB_USER");
    static final String PASSWORD=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
    static final String SCHEMA="finance_test_"+Long.toUnsignedString(System.nanoTime());
    @TempDir static Path files;
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired CommonUploadService uploads;
    @BeforeAll static void prepare() throws Exception {
        assumeTrue(URL!=null&&!URL.isBlank()&&USER!=null&&PASSWORD!=null,"선택된 PostgreSQL 시험 연결이 없습니다.");
        try(Connection c=DriverManager.getConnection(URL,USER,PASSWORD)) {
            c.createStatement().execute("CREATE SCHEMA "+SCHEMA); c.setSchema(SCHEMA);
            var source=new SingleConnectionDataSource(c,true);
            var config=new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment(
                "postgresql",new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();
        }
    }
    @AfterAll static void remove() throws Exception {
        if(URL!=null&&USER!=null&&PASSWORD!=null)try(Connection c=DriverManager.getConnection(URL,USER,PASSWORD)){c.createStatement().execute("DROP SCHEMA IF EXISTS "+SCHEMA+" CASCADE");}
    }
    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE TB_FI_ITEM,TB_FI_CORP_TAX,TB_FI_TAX_PAY,TB_FI_CARD,TB_FI_CARD_PAY,TB_FI_CARD_MON,TB_FI_XFER,TB_FI_LOAN,TB_CO_PREV_ADJ,TB_CO_MON_PL,TB_CL_CLOSE CASCADE");
        jdbc.update("DELETE FROM TB_SY_CHG_HIST WHERE trgt_tbl_nm LIKE 'TB_FI_%' OR trgt_tbl_nm='TB_CO_PREV_ADJ'");
    }
    JsonNode getMonth(String month) throws Exception { return json.readTree(mvc.perform(get("/api/common/finance-items/"+month)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data"); }
    JsonNode save(String month,JsonNode body) throws Exception { return json.readTree(mvc.perform(put("/api/common/finance-items/"+month).contentType("application/json").content(body.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data"); }
    com.fasterxml.jackson.databind.node.ObjectNode input(JsonNode month) {
        var body=json.createObjectNode(); for(String field:List.of("items","taxEstimate","loans","adjustments","cards","cardPayment","transfers","modifiedAt"))body.set(field,month.path(field));
        var payments=body.putArray("taxPayments"); month.path("taxPayments").forEach(row->{if(!"Y".equals(row.path("paidYn").asText()))payments.add(row);}); return body;
    }
    void prepareYear() throws Exception { mvc.perform(post("/api/common/finance-items/tax-years/2026")).andExpect(status().isOk()); }
    void item(String month,int id,String name,String repeat,Long upload) {
        jdbc.update("INSERT INTO TB_FI_ITEM(FIN_YM,ITEM_SN,FIN_CLS_CD,ITEM_NM,AMT,VAT_AMT,RPT_YN,UPLD_ID,AUTO_FILL_YN,REG_USER_ID,REG_DTTM) VALUES(?,?,'SGA',?,100,10,?,?, 'N','admin',clock_timestamp())",month,id,name,repeat,upload);
    }
    JsonNode payment() throws Exception { for(var row:getMonth("202601").path("taxPayments"))if(row.path("paymentId").asText().equals("2026:CNFM"))return row; throw new AssertionError("CNFM absent"); }
    MockMultipartFile file(String amount) { return new MockMultipartFile("file","finance.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",new UploadWorkbookService(new ExcelExportService()).tabularFile(List.of("구분","판관비 항목","세부 항목","금액","부가세","입출금일","매달 반복","비고"),List.of(List.of("SGA","","교체 항목",amount,"","","N","")),null)); }
    @Test void publicPreparationCreatesOnlyMissingAnnualAndStatutoryRows() throws Exception {
        prepareYear(); prepareYear(); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TB_FI_CORP_TAX",Integer.class)).isEqualTo(1); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TB_FI_TAX_PAY",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT EST_TAX_AMT FROM TB_FI_CORP_TAX",java.math.BigDecimal.class)).isZero();
        assertThat(jdbc.queryForList("SELECT PAY_YN FROM TB_FI_TAX_PAY",String.class)).containsOnly("N"); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TB_FI_TAX_PAY WHERE PAY_AMT IS NULL",Integer.class)).isEqualTo(2);
        assertThat(payment().path("scheduledDate").asText()).isEqualTo("2027-03-31");
    }
    @Test void getDoesNotInitializeYearOrPersistRepeatedDrafts() throws Exception {
        item("202601",1,"반복","Y",null); var data=getMonth("202602"); assertThat(data.path("autoFillCount").asInt()).isEqualTo(1); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TB_FI_ITEM WHERE FIN_YM='202602'",Integer.class)).isZero(); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TB_FI_CORP_TAX",Integer.class)).isZero();
    }
    @Test void twoTabsCommitTogetherAndPerCardStorageUsesPhysicalPayTable() throws Exception {
        prepareYear(); var body=input(getMonth("202601")); body.withArray("items").addObject().putNull("itemId").putNull("modifiedAt").put("classification","SGA").put("itemName","직접 입력").put("amount",0).put("vatAmount",0).put("repeatYn","N");
        body.withArray("loans").addObject().putNull("loanId").putNull("modifiedAt").put("date","2026-01-02").put("direction","IN").put("amount",5);
        body.withArray("cards").addObject().putNull("cardId").putNull("modifiedAt").put("cardName","시험 카드").put("lastFourDigits","1234").put("useYn","Y").put("amount",7);
        var saved=save("202601",body); assertThat(saved.path("items").get(0).path("itemCode").isNull()).isTrue(); assertThat(jdbc.queryForObject("SELECT PAY_AMT FROM TB_FI_CARD_PAY",java.math.BigDecimal.class)).isEqualByComparingTo("7"); assertThat(jdbc.queryForObject("SELECT CARD_SUM_AMT FROM TB_FI_CARD_MON",java.math.BigDecimal.class)).isEqualByComparingTo("7"); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TB_CO_MON_PL",Integer.class)).isZero();
    }
    @Test void staleAggregateProtectsOmittedRowsFromDeletion() throws Exception {
        prepareYear(); item("202601",1,"원본","N",null); var first=input(getMonth("202601")); save("202601",first); first.withArray("items").removeAll(); mvc.perform(put("/api/common/finance-items/202601").contentType("application/json").content(first.toString())).andExpect(status().isConflict()); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TB_FI_ITEM",Integer.class)).isEqualTo(1);
    }
    @Test void savingAnotherMonthDoesNotConflictWithSharedCardsTaxOrPayments() throws Exception {
        prepareYear();
        var first=input(getMonth("202601")); first.withArray("cards").addObject().putNull("cardId").putNull("modifiedAt").put("cardName","공용 카드").put("lastFourDigits","1111").put("useYn","Y").put("amount",7);
        save("202601",first);
        var openedFeb=input(getMonth("202602"));
        var jan=input(getMonth("202601")); ((com.fasterxml.jackson.databind.node.ObjectNode)jan.withArray("cards").get(0)).put("amount",9); save("202601",jan);
        openedFeb.withArray("items").addObject().putNull("itemId").putNull("modifiedAt").put("classification","SGA").put("itemName","2월 항목").put("amount",5).put("vatAmount",0).put("repeatYn","N");
        save("202602",openedFeb);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TB_FI_ITEM WHERE FIN_YM='202602'",Integer.class)).isEqualTo(1);
        var stale=input(getMonth("202602"));
        var renamed=input(getMonth("202601")); ((com.fasterxml.jackson.databind.node.ObjectNode)renamed.withArray("cards").get(0)).put("cardName","새 이름"); save("202601",renamed);
        ((com.fasterxml.jackson.databind.node.ObjectNode)stale.withArray("cards").get(0)).put("cardName","다른 이름");
        mvc.perform(put("/api/common/finance-items/202602").contentType("application/json").content(stale.toString())).andExpect(status().isConflict());
    }
    @Test void sameCardNameAndLastFourDigitsIsRejectedLikeMockupEvenWhenDeleted() throws Exception {
        prepareYear(); var body=input(getMonth("202601"));
        body.withArray("cards").addObject().putNull("cardId").putNull("modifiedAt").put("cardName","법인카드").put("lastFourDigits","1234").put("useYn","N").put("amount",0);
        save("202601",body);
        var again=input(getMonth("202601")); again.withArray("cards").addObject().putNull("cardId").putNull("modifiedAt").put("cardName","법인카드").put("lastFourDigits","1234").put("useYn","Y").put("amount",0);
        mvc.perform(put("/api/common/finance-items/202601").contentType("application/json").content(again.toString())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value(org.hamcrest.Matchers.endsWith("].lastFourDigits"))).andExpect(jsonPath("$.errors[0].message").value("같은 카드가 이미 있습니다."));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TB_FI_CARD",Integer.class)).isEqualTo(1);
    }
    @Test void invalidAmountRollsBackBothTabs() throws Exception {
        item("202601",1,"원본","N",null); var body=input(getMonth("202601")); ((com.fasterxml.jackson.databind.node.ObjectNode)body.withArray("items").get(0)).putNull("amount"); body.withArray("loans").addObject().putNull("loanId").putNull("modifiedAt").put("date","2026-01-02").put("direction","IN").put("amount",5);
        mvc.perform(put("/api/common/finance-items/202601").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("items[0].amount")); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TB_FI_LOAN",Integer.class)).isZero(); assertThat(jdbc.queryForObject("SELECT AMT FROM TB_FI_ITEM",java.math.BigDecimal.class)).isEqualByComparingTo("100");
    }
    @Test void confirmationIsIndependentNumericImmutableAndVersionChecked() throws Exception {
        prepareYear(); var row=payment(); var body=json.createObjectNode().put("actualPaymentDate","2026-03-20").put("actualPaymentAmount",90).set("modifiedAt",row.path("modifiedAt"));
        mvc.perform(post("/api/common/finance-items/tax-payments/2026:CNFM/confirm").contentType("application/json").content(body.toString())).andExpect(status().isOk());
        var month=input(getMonth("202601")); ((com.fasterxml.jackson.databind.node.ObjectNode)month.path("taxEstimate")).put("estimatedTaxAmount",120000000); save("202601",month); prepareYear();
        assertThat(jdbc.queryForObject("SELECT PAY_AMT FROM TB_FI_TAX_PAY WHERE PAY_TYPE_CD='CNFM'",java.math.BigDecimal.class)).isEqualByComparingTo("90"); assertThat(jdbc.queryForObject("SELECT PAY_YN FROM TB_FI_TAX_PAY WHERE PAY_TYPE_CD='CNFM'",String.class)).isEqualTo("Y");
        mvc.perform(post("/api/common/finance-items/tax-payments/2026:CNFM/confirm").contentType("application/json").content(body.toString())).andExpect(status().isBadRequest());
    }
    @Test void unpaidStaleConfirmationLeavesAmountNull() throws Exception {
        prepareYear(); mvc.perform(post("/api/common/finance-items/tax-payments/2026:CNFM/confirm").contentType("application/json").content("{\"actualPaymentDate\":\"2026-03-20\",\"actualPaymentAmount\":1,\"modifiedAt\":\"2000-01-01T00:00:00Z\"}")).andExpect(status().isConflict()); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TB_FI_TAX_PAY WHERE PAY_AMT IS NULL AND PAY_YN='N'",Integer.class)).isEqualTo(2);
    }
    com.company.common.uploads.dto.UploadContracts.Result upload(String amount) {
        return uploads.postCommonUpload("B2","2026-01","finance.xlsx",new UploadWorkbookService(new ExcelExportService()).tabularFile(
            List.of("구분","판관비 항목","세부 항목","금액","부가세","입출금일","매달 반복","비고"),List.of(List.of("SGA","","교체 항목",amount,"","","N","")),null));
    }
    com.company.common.uploads.dto.UploadContracts.Result apply(com.company.common.uploads.dto.UploadContracts.Result checked) {
        return uploads.postCommonUploadApply(checked.uploadId(),new com.company.common.uploads.dto.UploadContracts.Apply(checked.modifiedAt()==null ? null : Instant.parse(checked.modifiedAt())));
    }
    @Test void sharedUploadReplacesOnlyUploadedRowsAndKeepsManualOnesAfterEdits() throws Exception {
        prepareYear(); item("202601",1,"직접","N",null); apply(upload("250"));
        var body=input(getMonth("202601")); var uploaded=StreamSupport.stream(body.withArray("items").spliterator(),false).filter(i->!i.path("uploadId").isNull()).findFirst().orElseThrow();
        ((com.fasterxml.jackson.databind.node.ObjectNode)uploaded).put("amount",300); save("202601",body);
        apply(upload("500"));
        assertThat(jdbc.queryForObject("SELECT AMT FROM TB_FI_ITEM WHERE UPLD_ID IS NOT NULL",java.math.BigDecimal.class)).isEqualByComparingTo("500"); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TB_FI_ITEM WHERE UPLD_ID IS NULL",Integer.class)).isEqualTo(1);
    }
    @Test void sharedUploadWithErrorsDoesNotTouchFinanceRows() throws Exception {
        item("202601",1,"직접","N",null); var checked=upload(""); assertThat(checked.errorCount()).isEqualTo(1);
        assertThat(apply(checked).errorCount()).isEqualTo(1); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TB_FI_ITEM",Integer.class)).isEqualTo(1);
    }
    @Test void previousItemsAreReadOnly() throws Exception {
        item("202601",1,"지난달","N",null); mvc.perform(get("/api/common/finance-items/202602/previous-items")).andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TB_FI_ITEM",Integer.class)).isEqualTo(1);
    }
    @TestConfiguration
    @org.springframework.transaction.annotation.EnableTransactionManagement
    @MapperScan(basePackageClasses={com.company.common.financeitems.mapper.FinanceItemMapper.class,CalculationRuleMapper.class,UserPreferencePeriodSourceMapper.class,UploadMapper.class},annotationClass=org.apache.ibatis.annotations.Mapper.class)
    static class Database {
        @Bean DataSource dataSource(){var ds=new DriverManagerDataSource(URL,USER,PASSWORD);var properties=new Properties();properties.setProperty("currentSchema",SCHEMA);ds.setConnectionProperties(properties);return ds;}
        @Bean org.apache.ibatis.session.SqlSessionFactory sessions(DataSource ds)throws Exception{var factory=new SqlSessionFactoryBean();factory.setDataSource(ds);var config=new org.apache.ibatis.session.Configuration();config.setMapUnderscoreToCamelCase(true);config.setArgNameBasedConstructorAutoMapping(true);factory.setConfiguration(config);factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml"));return factory.getObject();}
        @Bean org.springframework.transaction.PlatformTransactionManager transactions(DataSource ds){return new DataSourceTransactionManager(ds);}
        @Bean JdbcTemplate jdbc(DataSource ds){return new JdbcTemplate(ds);}
        @Bean UploadFileStore fileStore(){return new UploadFileStore(files);}
    }
}
