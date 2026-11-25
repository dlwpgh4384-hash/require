package com.company.common.salesopportunities.controller;

import com.company.common.common.SalesPipelineCodes;
import com.company.common.common.SalesPipelineApprovalStandards;
import com.company.common.codegroups.dto.CodeContracts.Item;
import com.company.common.salesopportunities.dto.SalesOpportunityContracts.Need;
import com.company.common.salesopportunities.mapper.SalesOpportunityMapper;
import com.company.common.salesopportunities.service.*;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.mybatis.spring.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(CommonSalesOpportunityController.class)
@ContextConfiguration(classes = {CommonSalesOpportunityController.class, CommonSalesOpportunityService.class,
    CurrentBusinessActor.class, CodeExceptionHandler.class, ApiExceptionHandler.class, SalesOpportunityPersistenceTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SalesOpportunityPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired OpportunityDatabase owned;
    @Autowired SalesOpportunityMapper mapper;
    @MockBean SalesOpportunityCalculations calculations;
    @MockBean SalesOpportunityProjects projects;
    @MockBean SalesPipelineCodes codes;
    JdbcTemplate db;
    record OpportunityDatabase(String name) {}
    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean OpportunityDatabase opportunityDatabase() { return new OpportunityDatabase("opportunities_" + UUID.randomUUID().toString().replace("-", "")); }
        @Bean DataSource dataSource(OpportunityDatabase owned) throws Exception {
            String url = System.getenv("NAVIGATION_TEST_DB_URL"), user = System.getenv("NAVIGATION_TEST_DB_USER"), password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
                statement.execute("CREATE SCHEMA " + owned.name());
            }
            var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new Properties(); properties.setProperty("currentSchema", owned.name()); source.setDriverProperties(properties);
            var configuration = new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration)).prepare(); return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/salesopportunities/SalesOpportunityMapper.xml"));
            var configuration = new org.apache.ibatis.session.Configuration(); configuration.setMapUnderscoreToCamelCase(false);
            configuration.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(configuration); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean SalesOpportunityMapper salesOpportunityMapper(SqlSessionTemplate session) { return session.getMapper(SalesOpportunityMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean BusinessCalendar businessCalendar() { return new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"), ZoneOffset.UTC)); }
    }
    @BeforeEach void setup() {
        db = new JdbcTemplate(source);
        db.update("INSERT INTO TB_BS_PART(part_id,part_nm,reg_user_id) VALUES('test-sales','시험 영업파트','admin')");
        db.update("INSERT INTO TB_BS_CLNT(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES('test-customer','CUST','시험 고객','1234567890','admin')");
        when(codes.name(anyString(), anyString())).thenAnswer(c -> c.getArgument(1));
        when(codes.selectable(anyString())).thenAnswer(c -> {
            var values = switch ((String)c.getArgument(0)) { case "SALES_STEP" -> List.of("FIND", "PROP", "NEGO", "PRE"); case "JOB" -> List.of("BE"); case "CTRT_TYPE" -> List.of("TK"); case "LOSE_RSN" -> List.of("PRICE"); default -> List.<String>of(); };
            return values.stream().map(code -> { var item = new Item(); item.code = code; item.codeName = code; item.useYn = "Y"; return item; }).toList();
        });
        when(calculations.price(any())).thenReturn(new BigDecimal("10000000"));
        when(calculations.stageProbability(anyString())).thenReturn(BigDecimal.TEN);
        when(calculations.evaluate(anyList())).thenReturn(new SalesOpportunityCalculations.Evaluation(new BigDecimal("14"), new SalesPipelineApprovalStandards.Values(new BigDecimal("15"), new BigDecimal("0.5"))));
    }
    @AfterEach void cleanup() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) { statement.execute("DROP SCHEMA " + owned.name() + " CASCADE"); }
    }
    ObjectNode input() throws Exception { return (ObjectNode)json.readTree("""
        {"opportunityName":"영업 기회","customerId":"test-customer","salesPartId":"test-sales","stageCode":"FIND","winningProbability":10,"expectedAmount":60000000,"startMonth":"2026-11","needs":[{"jobCode":"BE","requiredCount":1,"startMonth":"2026-11","months":6,"monthlyMm":1}]}
        """); }
    String create() throws Exception {
        return json.readTree(mvc.perform(post("/api/common/sales-opportunities").contentType("application/json").content(input().toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("opportunityId").asText();
    }
    ObjectNode version(String id) throws Exception {
        String value = json.readTree(mvc.perform(get("/api/common/sales-opportunities/{id}", id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("modifiedAt").asText();
        return json.createObjectNode().put("modifiedAt", value);
    }
    @Test void createAndUpdatePersistStableCompositeNeedKeyAndVersion() throws Exception {
        var id = create();
        var before = db.queryForMap("SELECT * FROM TB_SL_OPP WHERE opp_id=?", id);
        var body = input(); body.put("modifiedAt", version(id).path("modifiedAt").asText());
        ((ObjectNode)body.path("needs").get(0)).put("needSn", 1).put("months", 8);
        mvc.perform(put("/api/common/sales-opportunities/{id}", id).contentType("application/json").content(body.toString())).andExpect(status().isOk()).andExpect(jsonPath("$.data.needs[0].months").value(8));
        assertThat(db.queryForObject("SELECT prd_mcnt::int FROM TB_SL_OPP_NEED WHERE opp_id=? AND need_sn=1", Integer.class, id)).isEqualTo(8);
        assertThat(db.queryForMap("SELECT * FROM TB_SL_OPP WHERE opp_id=?", id).get("reg_user_id")).isEqualTo(before.get("reg_user_id"));
        mvc.perform(put("/api/common/sales-opportunities/{id}", id).contentType("application/json").content(body.toString())).andExpect(status().isConflict());
    }
    // 근무지 지역(2026-11-25): 실제 행정구역 표의 코드로 저장·이름 읽기, 쓰지 않는 구역은 400으로 막는다.
    @Test void workAreaCodePersistsWithItsNameAndUnusedAreaIsRejected() throws Exception {
        var area = db.queryForMap("SELECT area_cd, concat_ws(' ',sido_nm,sgg_nm,emd_nm) AS name FROM TB_SY_ADM_AREA "
            + "WHERE area_lvl_cd='EMD' AND use_yn='Y' ORDER BY area_cd LIMIT 1");
        String code = (String) area.get("area_cd");
        var body = input().put("workAreaCode", code);
        var id = json.readTree(mvc.perform(post("/api/common/sales-opportunities").contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("opportunityId").asText();
        assertThat(db.queryForObject("SELECT work_area_cd FROM TB_SL_OPP WHERE opp_id=?", String.class, id)).isEqualTo(code);
        mvc.perform(get("/api/common/sales-opportunities/{id}", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.workAreaCode").value(code)).andExpect(jsonPath("$.data.workAreaName").value(area.get("name")));
        db.update("UPDATE TB_SY_ADM_AREA SET use_yn='N' WHERE area_cd=?", code);
        mvc.perform(post("/api/common/sales-opportunities").contentType("application/json").content(input().put("workAreaCode", code).toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("workAreaCode"));
        var edit = input().put("workAreaCode", code); edit.put("modifiedAt", version(id).path("modifiedAt").asText());
        ((ObjectNode)edit.path("needs").get(0)).put("needSn", 1);
        mvc.perform(put("/api/common/sales-opportunities/{id}", id).contentType("application/json").content(edit.toString())).andExpect(status().isOk());
    }
    @Test void fullRejectionReasonPersistsAndSystemNotificationIsBounded() throws Exception {
        var id = create(); var reason = "가".repeat(300);
        mvc.perform(put("/api/common/sales-opportunities/{id}/reject", id).contentType("application/json").content(version(id).put("reason", reason).toString())).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT act_rsn FROM TB_SL_APRV_HIST WHERE opp_id=? AND aprv_act_cd='REJ'", String.class, id)).isEqualTo(reason);
        assertThat(db.queryForObject("SELECT char_length(noti_cn) FROM TB_SY_NOTI WHERE link_key=?", Integer.class, id)).isEqualTo(300);
        assertThat(db.queryForObject("SELECT send_mthd_cd FROM TB_SY_NOTI WHERE link_key=?", String.class, id)).isEqualTo("SYS");
    }
    @Test void notificationFailureRollsBackApprovalAndHistory() throws Exception {
        var id = create();
        db.execute("ALTER TABLE TB_SY_NOTI ADD CONSTRAINT notification_guard CHECK(noti_type_cd <> 'QUOTE_REJ')");
        mvc.perform(put("/api/common/sales-opportunities/{id}/reject", id).contentType("application/json").content(version(id).put("reason", "사유").toString())).andExpect(status().is5xxServerError());
        assertThat(db.queryForObject("SELECT aprv_stat_cd FROM TB_SL_OPP WHERE opp_id=?", String.class, id)).isEqualTo("WAIT");
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SL_APRV_HIST WHERE opp_id=? AND aprv_act_cd='REJ'", Integer.class, id)).isZero();
    }
    @Test void handedOffDeveloperStaysOnOneOpportunityAfterWinSoWithdrawalListHasOneOpportunity() throws Exception {
        db.update("INSERT INTO TB_HR_DEV(dev_id,mng_no,dev_nm,emp_type_cd,grade_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES('test-dev','test-dev','시험 개발자','EMP','MD','BE','DIRECT','test-sales','WORK','admin')");
        var first = input(); ((ObjectNode)first.path("needs").get(0)).putArray("tentativeDeveloperIds").add("test-dev");
        var won = json.readTree(mvc.perform(post("/api/common/sales-opportunities").contentType("application/json").content(first.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("opportunityId").asText();
        // 수주 처리 뒤 모습: 기회는 수주, 가배정은 투입 배정 후보(CNVT)
        db.update("UPDATE TB_SL_TENT_ASGN SET tent_stat_cd='CNVT',proc_dttm=clock_timestamp() WHERE opp_id=?", won);
        db.update("UPDATE TB_SL_OPP SET opp_stat_cd='WIN',rslt_dt=DATE '2026-11-15' WHERE opp_id=?", won);
        var other = create();
        var edit = input(); edit.put("modifiedAt", version(other).path("modifiedAt").asText());
        ((ObjectNode)edit.path("needs").get(0)).put("needSn", 1).putArray("tentativeDeveloperIds").add("test-dev");
        mvc.perform(put("/api/common/sales-opportunities/{id}", other).contentType("application/json").content(edit.toString()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("투입 배정으로 넘긴 후보입니다. 투입 상세에서 확인해 주세요."));
        mvc.perform(put("/api/common/sales-opportunities/{id}/tentative-assignment", other).contentType("application/json")
            .content(version(other).put("developerId", "test-dev").put("needSn", 1).put("startMonth", "2026-11").toString())).andExpect(status().isConflict());
        mvc.perform(post("/api/common/sales-opportunities/tentative-assignment-check").contentType("application/json")
            .content("{\"developerId\":\"test-dev\",\"startMonth\":\"2026-11\"}")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.warnings[0].message").value("투입 배정으로 넘긴 후보입니다. 투입 상세에서 확인해 주세요."));
        // 철수 리스트(UI-05)가 읽는 ACT·CNVT 가배정은 계속 한 기회에만 있다.
        assertThat(db.queryForObject("SELECT COUNT(DISTINCT opp_id) FROM TB_SL_TENT_ASGN WHERE dev_id='test-dev' AND tent_stat_cd IN ('ACT','CNVT')", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT tent_stat_cd FROM TB_SL_TENT_ASGN WHERE dev_id='test-dev'", String.class)).isEqualTo("CNVT");
    }
    @Test void stopAndResumeKeepPhysicalRowAndClearResultDate() throws Exception {
        var id = create();
        mvc.perform(post("/api/common/sales-opportunities/{id}/stop", id).contentType("application/json").content(version(id).toString())).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT rslt_dt FROM TB_SL_OPP WHERE opp_id=?", LocalDate.class, id)).isEqualTo(LocalDate.of(2026, 11, 15));
        mvc.perform(post("/api/common/sales-opportunities/{id}/resume", id).contentType("application/json").content(version(id).toString())).andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT opp_stat_cd FROM TB_SL_OPP WHERE opp_id=?", String.class, id)).isEqualTo("ING");
        assertThat(db.queryForObject("SELECT rslt_dt FROM TB_SL_OPP WHERE opp_id=?", LocalDate.class, id)).isNull();
    }
    // --- 점검 고침 2(UI-29) ---
    void developer(String id) {
        db.update("INSERT INTO TB_HR_DEV(dev_id,mng_no,dev_nm,emp_type_cd,grade_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES(?,?,'시험 개발자','EMP','MD','BE','DIRECT','test-sales','WORK','admin')", id, id);
    }
    void pay(String month, String developer) {
        db.update("INSERT INTO TB_HR_MON_PAY(pay_ym,dev_id,pay_amt,alwnc_amt,insr_cmpny_amt,retire_rsv_amt,emp_type_cd,drct_cls_cd,reg_user_id) VALUES(?,?,5000000,0,0,0,'EMP','DIRECT','admin')", month, developer);
    }
    void cost(String month, String developer, long labor) {
        db.update("INSERT INTO TB_CO_MON_COST(cost_ym,dev_id,proj_id,asgn_id,asgn_mm,free_mm,sales_amt,labor_alloc_amt,team_exps_amt,proj_exps_amt,drct_exps_amt,cost_cls_cd,reg_user_id) VALUES(?,?,'test-project','test-asgn',1,0,0,?,0,0,0,'ASGN','admin')", month, developer, labor);
    }
    @Test void monthlyCostUsesTheLatestPayMonthThatAlreadyHasMonthlyCost() {
        developer("test-dev-a"); developer("test-dev-b");
        for (var dev : List.of("test-dev-a", "test-dev-b")) { pay("202610", dev); pay("202611", dev); }
        cost("202610", "test-dev-a", 6800000); cost("202610", "test-dev-b", 7000000);
        // 11월 급여는 올라왔지만 11월 월 원가는 아직 없다 → 10월 원가 평균(690만 원)으로 계산한다.
        assertThat(mapper.monthlyCost("BE", "MD")).isEqualByComparingTo("6900000");
        cost("202611", "test-dev-a", 7200000); cost("202611", "test-dev-b", 7400000);
        assertThat(mapper.monthlyCost("BE", "MD")).isEqualByComparingTo("7300000");
    }
    @Test void opportunityIdsAreNextONumberSkippingOtherFormats() throws Exception {
        assertThat(create()).isEqualTo("O-0001");
        for (var other : List.of("O-0041", "Olegacy0000000000001", "O-99999"))
            db.update("INSERT INTO TB_SL_OPP SELECT (jsonb_populate_record(NULL::TB_SL_OPP, to_jsonb(o) || jsonb_build_object('opp_id', ?::text))).* FROM TB_SL_OPP o WHERE o.opp_id='O-0001'", other);
        assertThat(create()).isEqualTo("O-0042");
    }
    @Test void blankUnitPriceStaysBlankInTheNeedRow() throws Exception {
        var id = create();
        assertThat(db.queryForObject("SELECT expct_uprc FROM TB_SL_OPP_NEED WHERE opp_id=? AND need_sn=1", BigDecimal.class, id)).isNull();
    }
    @Test void savingDoesNotOverwriteTheReservationStartMonthSetByWithdrawalList() throws Exception {
        developer("test-dev");
        var first = input(); ((ObjectNode)first.path("needs").get(0)).putArray("tentativeDeveloperIds").add("test-dev");
        var id = json.readTree(mvc.perform(post("/api/common/sales-opportunities").contentType("application/json").content(first.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("opportunityId").asText();
        db.update("UPDATE TB_SL_TENT_ASGN SET bgn_ym='202612' WHERE opp_id=?", id);
        var tentative = db.queryForObject("SELECT tent_id FROM TB_SL_TENT_ASGN WHERE opp_id=?", Long.class, id);
        var edit = input().put("opportunityName", "고친 기회"); edit.put("modifiedAt", version(id).path("modifiedAt").asText());
        ((ObjectNode)edit.path("needs").get(0)).put("needSn", 1).putArray("tentativeDeveloperIds").add("test-dev");
        mvc.perform(put("/api/common/sales-opportunities/{id}", id).contentType("application/json").content(edit.toString())).andExpect(status().isOk());
        assertThat(db.queryForList("SELECT tent_id FROM TB_SL_TENT_ASGN WHERE opp_id=? AND tent_stat_cd='ACT'", Long.class, id)).containsExactly(tentative);
        assertThat(db.queryForObject("SELECT bgn_ym FROM TB_SL_TENT_ASGN WHERE tent_id=?", String.class, tentative)).isEqualTo("202612");
    }
    @Test void approvalWithoutStoredProfitRateIsBadRequestAndWritesNoHistory() throws Exception {
        var id = create();
        db.update("UPDATE TB_SL_OPP SET expct_prft_rt=NULL WHERE opp_id=?", id);
        var count = db.queryForObject("SELECT count(*) FROM TB_SL_APRV_HIST WHERE opp_id=?", Integer.class, id);
        mvc.perform(put("/api/common/sales-opportunities/{id}/approve", id).contentType("application/json").content(version(id).toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("예상 이익률을 다시 계산해 저장한 뒤 승인해 주세요."));
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SL_APRV_HIST WHERE opp_id=?", Integer.class, id)).isEqualTo(count);
        assertThat(db.queryForObject("SELECT aprv_stat_cd FROM TB_SL_OPP WHERE opp_id=?", String.class, id)).isEqualTo("WAIT");
    }
}
