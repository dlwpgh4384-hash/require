package com.company.common.monthcloses;

import com.company.common.assignments.mapper.AssignmentMapper;
import com.company.common.assignments.service.AssignmentMonthRules;
import com.company.common.billchecks.mapper.BillCheckMapper;
import com.company.common.billchecks.service.BillCheckService;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.common.*;
import com.company.common.commonops.mapper.PartSettlementCloseMapper;
import com.company.common.commonops.service.PartSettlementCloseService;
import com.company.common.developers.service.DeveloperManMonthRules;
import com.company.common.monthcloses.controller.CommonMonthCloseController;
import com.company.common.monthcloses.mapper.MonthCloseMapper;
import com.company.common.monthcloses.service.*;
import com.company.common.partsettlements.mapper.PartSettlementMapper;
import com.company.common.partsettlements.service.CommonPartSettlementService;
import com.company.common.partsettlements.service.PartSettlementAutoConfirmService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.profitstructures.service.ProfitStructureMonthlyProfitService;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.*;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.users.mapper.UserMapper;
import com.fasterxml.jackson.databind.*;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(CommonMonthCloseController.class)
@ContextConfiguration(classes = {CommonMonthCloseController.class, CommonMonthCloseService.class,
    MonthCloseCheckService.class, MonthCloseSnapshotService.class, MonthCloseAssignmentService.class,
    MonthClosePeriodService.class, MonthCloseCheckRecorder.class, MonthCloseCodes.class, MonthCloseDefinitions.class,
    CommonPartSettlementService.class, PartSettlementAutoConfirmService.class, PartSettlementDefinitions.class,
    PartSettlementCloseService.class, CalculationRuleService.class, CommonCodeService.class,
    AssignmentMonthRules.class, DeveloperManMonthRules.class, ProfitStructureMonthlyProfitService.class,
    BillCheckService.class, BillingCalculationRules.class, BillingNumberAllocator.class, CurrentBusinessActor.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class, MonthClosePersistenceTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MonthClosePersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired Owned owned;
    @Autowired ProfitStructureMonthlyProfitService profits;
    JdbcTemplate db;
    record Owned(String name) {}
    @TestConfiguration(proxyBeanMethods = false) @EnableTransactionManagement
    static class Persistence {
        @Bean Owned owned() { return new Owned("monthclose_" + UUID.randomUUID().toString().replace("-", "")); }
        @Bean DataSource dataSource(Owned owned) throws Exception {
            String url = System.getenv("NAVIGATION_TEST_DB_URL"), user = System.getenv("NAVIGATION_TEST_DB_USER"), password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + owned.name());
            }
            var ds = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new Properties(); properties.setProperty("currentSchema", owned.name()); ds.setDriverProperties(properties);
            var config = new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment("postgresql", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), ds));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();
            return ds;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
            String[] paths = {"mapper/monthcloses/MonthCloseMapper.xml", "mapper/assignments/AssignmentMapper.xml",
                "mapper/profitstructures/ProfitStructureMapper.xml", "mapper/personalcostprofits/PersonalCostProfitMapper.xml",
                "mapper/partsettlements/PartSettlementMapper.xml",
                "mapper/PartSettlementCloseMapper.xml", "mapper/billchecks/BillCheckMapper.xml", "mapper/projects/ProjectMapper.xml",
                "mapper/CodeGroupMapper.xml", "mapper/CalculationRuleMapper.xml", "mapper/UserPreferencePeriodSourceMapper.xml",
                "mapper/UserMapper.xml", "mapper/common/BillingNumberMapper.xml"};
            factory.setMapperLocations(Arrays.stream(paths).map(ClassPathResource::new).toArray(ClassPathResource[]::new));
            var config = new org.apache.ibatis.session.Configuration(); config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config); return factory.getObject();
        }
        @Bean SqlSessionTemplate template(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean MonthCloseMapper monthCloseMapper(SqlSessionTemplate s) { return s.getMapper(MonthCloseMapper.class); }
        @Bean AssignmentMapper assignmentMapper(SqlSessionTemplate s) { return s.getMapper(AssignmentMapper.class); }
        @Bean ProfitStructureMapper profitMapper(SqlSessionTemplate s) { return s.getMapper(ProfitStructureMapper.class); }
        @Bean PartSettlementMapper settlementMapper(SqlSessionTemplate s) { return s.getMapper(PartSettlementMapper.class); }
        @Bean PartSettlementCloseMapper closeMapper(SqlSessionTemplate s) { return s.getMapper(PartSettlementCloseMapper.class); }
        @Bean BillCheckMapper billMapper(SqlSessionTemplate s) { return s.getMapper(BillCheckMapper.class); }
        @Bean ProjectMapper projectMapper(SqlSessionTemplate s) { return s.getMapper(ProjectMapper.class); }
        @Bean CodeGroupMapper codeMapper(SqlSessionTemplate s) { return s.getMapper(CodeGroupMapper.class); }
        @Bean CalculationRuleMapper calculationMapper(SqlSessionTemplate s) { return s.getMapper(CalculationRuleMapper.class); }
        @Bean UserPreferencePeriodSourceMapper periodMapper(SqlSessionTemplate s) { return s.getMapper(UserPreferencePeriodSourceMapper.class); }
        @Bean UserMapper userMapper(SqlSessionTemplate s) { return s.getMapper(UserMapper.class); }
        @Bean BillingNumberMapper billingNumberMapper(SqlSessionTemplate s) { return s.getMapper(BillingNumberMapper.class); }
        @Bean BusinessCalendar calendar() { return new BusinessCalendar(Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC)); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void fixture() {
        db = new JdbcTemplate(source);
        String job = db.queryForObject("SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB' AND USE_YN='Y' AND DEL_YN='N'", String.class);
        db.update("INSERT INTO TB_BS_PART(PART_ID,PART_NM,HQ_YN,USE_YN,REG_USER_ID) VALUES('monthclose-part','마감 시험 파트','N','Y','admin')");
        db.update("INSERT INTO TB_BS_PART_STD(PART_ID,APLY_BGN_YM,PART_LDR_USER_ID,PRFT_DSTRB_RT,SALES_EXPS_AMT,REG_USER_ID) VALUES('monthclose-part','202601','admin',40,0,'admin')");
        db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,REG_USER_ID) VALUES('monthclose-dev','monthclose-dev','마감 시험 인력','FT',?,'DRCT','monthclose-part','IDLE','admin')", job);
        db.update("INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES('monthclose-project','마감 시험 프로젝트','MO','ING','2026-01-01','2026-12-31',100000000,0,0,'admin')");
        db.update("INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,CTRT_MM,INPUT_TYPE_CD,EXT_CNT,ASGN_STAT_CD,BILL_UPRC,REG_USER_ID) VALUES('monthclose-asgn','monthclose-project','monthclose-dev',?,'2026-01-01','2026-12-31',1,'NEW',0,'ACT',7000000,'admin')", job);
        db.update("INSERT INTO TB_HR_MON_PAY(PAY_YM,DEV_ID,PAY_AMT,ALWNC_AMT,INSR_CMPNY_AMT,RETIRE_RSV_AMT,EMP_TYPE_CD,DRCT_CLS_CD,REG_USER_ID) VALUES('202601','monthclose-dev',4000000,0,0,0,'FT','DRCT','admin')");
    }
    @AfterEach void removeOwnedSchema() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + owned.name() + " CASCADE");
        }
    }
    String root(String month) { return "/api/common/month-closes/" + month; }
    JsonNode read(String month) throws Exception {
        return json.readTree(mvc.perform(get(root(month))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }
    String body(JsonNode row, String reason) throws Exception {
        var value = json.createObjectNode(); value.set("modifiedAt", row.path("modifiedAt"));
        if (reason != null) value.put("reason", reason);
        return json.writeValueAsString(value);
    }
    JsonNode write(String month, String action, String body) throws Exception {
        return json.readTree(mvc.perform(put(root(month) + "/" + action).contentType("application/json").content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }
    Map<String, String> state() {
        var result = new LinkedHashMap<String, String>();
        for (String table : List.of("TB_CL_CLOSE", "TB_PJ_ASGN_MON", "TB_CO_MON_COST", "TB_CO_MON_PL",
            "TB_CO_PART_SETL", "TB_CO_PART_SETL_DTL", "TB_CL_CLOSE_HIST", "TB_SY_ACCS_LOG", "TB_SY_CHG_HIST",
            "TB_CL_CLOSE_CHK", "TB_CO_PREV_ADJ", "TB_HR_MON_PAY", "TB_FI_CORP_TAX"))
            result.put(table, db.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM " + table + " t", String.class));
        return result;
    }
    @Test void recheckStoresOnlyCheckResultsAndReferenceChecksAreAlwaysNormal() throws Exception {
        var before = state(); var row = read("2026-01");
        assertThat(row.path("checks").size()).isEqualTo(6);
        assertThat(row.path("checks").get(0).path("status").asText()).isEqualTo("정상");
        assertThat(row.path("checks").get(3).path("status").asText()).isEqualTo("정상");
        assertThat(row.path("reopenable").asBoolean()).isFalse();
        assertThat(db.queryForList("SELECT CHK_ITEM_CD FROM TB_CL_CLOSE_CHK WHERE CLOSE_YM='202601' ORDER BY CHK_ITEM_CD", String.class))
            .containsExactly("BILL", "BILL_MISS", "FI", "MM_OVER", "PAY", "SETL");
        assertThat(db.queryForObject("SELECT CHK_STAT_CD FROM TB_CL_CLOSE_CHK WHERE CLOSE_YM='202601' AND CHK_ITEM_CD='SETL'", String.class)).isEqualTo("WARN");
        assertThat(db.queryForObject("SELECT CHK_STAT_CD||':'||CHK_CNT||':'||COALESCE(LINK_SCRN_ID,'-')||':'||REG_USER_ID FROM TB_CL_CLOSE_CHK WHERE CLOSE_YM='202601' AND CHK_ITEM_CD='PAY'", String.class))
            .isEqualTo("OK:1:-:admin");
        assertThat(db.queryForObject("SELECT CLOSE_STAT_CD||':'||(MOD_DTTM IS NULL) FROM TB_CL_CLOSE WHERE CLOSE_YM='202601'", String.class)).isEqualTo("WORK:true");
        var firstCheck = db.queryForObject("SELECT CHK_DTTM FROM TB_CL_CLOSE_CHK WHERE CLOSE_YM='202601' AND CHK_ITEM_CD='PAY'", java.sql.Timestamp.class);
        var again = read("2026-01");
        assertThat(again.path("modifiedAt").isNull()).isTrue();
        assertThat(db.queryForObject("SELECT count(*) FROM TB_CL_CLOSE_CHK WHERE CLOSE_YM='202601'", Integer.class)).isEqualTo(6);
        assertThat(db.queryForObject("SELECT CHK_DTTM FROM TB_CL_CLOSE_CHK WHERE CLOSE_YM='202601' AND CHK_ITEM_CD='PAY'", java.sql.Timestamp.class)).isAfter(firstCheck);
        var after = state();
        for (String table : List.of("TB_CL_CLOSE", "TB_CL_CLOSE_CHK")) { after.remove(table); before.remove(table); }
        assertThat(after).isEqualTo(before);
    }
    @Test void closedFutureAndPreBaseMonthsNeverStoreCheckResults() throws Exception {
        write("2026-01", "confirm", body(read("2026-01"), null));
        String checks = state().get("TB_CL_CLOSE_CHK");
        var january = read("2026-01");
        assertThat(january.path("isClosed").asBoolean()).isTrue(); assertThat(january.path("reopenable").asBoolean()).isTrue();
        var before = read("2025-12");
        assertThat(before.path("isClosed").asBoolean()).isTrue(); assertThat(before.path("reopenable").asBoolean()).isFalse();
        assertThat(before.path("totalCount").asInt()).isZero(); assertThat(before.path("checks").size()).isZero();
        read("2026-05");
        assertThat(state().get("TB_CL_CLOSE_CHK")).isEqualTo(checks);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_CL_CLOSE WHERE CLOSE_YM IN ('202512','202605')", Integer.class)).isZero();
    }
    @Test void fixedIdleAndIndirectRowsFollowTheDdlAndMatchTheLiveCost() throws Exception {
        String job = db.queryForObject("SELECT min(CODE) FROM TB_SY_CODE WHERE CODE_GRP_ID='JOB' AND USE_YN='Y' AND DEL_YN='N'", String.class);
        db.update("UPDATE TB_PJ_ASGN SET CTRT_MM=0.5 WHERE ASGN_ID='monthclose-asgn'");
        db.update("INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,JOB_CD,DRCT_CLS_CD,PART_ID,WORK_STAT_CD,REG_USER_ID) VALUES('monthclose-indirect','monthclose-indirect','마감 간접 인력','FT',?,'INDRCT','monthclose-part','IDLE','admin')", job);
        db.update("INSERT INTO TB_HR_MON_PAY(PAY_YM,DEV_ID,PAY_AMT,ALWNC_AMT,INSR_CMPNY_AMT,RETIRE_RSV_AMT,EMP_TYPE_CD,DRCT_CLS_CD,REG_USER_ID) VALUES('202601','monthclose-indirect',3000000,0,0,0,'FT','INDRCT','admin')");
        write("2026-01", "confirm", body(read("2026-01"), null));
        assertThat(db.queryForObject("SELECT ASGN_ID||':'||trim_scale(ASGN_MM)||':'||COST_CLS_CD||':'||trim_scale(TEAM_EXPS_AMT)||':'||trim_scale(FREE_MM) FROM TB_CO_MON_COST WHERE COST_YM='202601' AND DEV_ID='monthclose-dev' AND PROJ_ID='IDLE'", String.class))
            .isEqualTo("IDLE:0.5:IDLE:0:0");
        assertThat(db.queryForObject("SELECT PROJ_ID||':'||ASGN_ID||':'||trim_scale(ASGN_MM)||':'||COST_CLS_CD||':'||trim_scale(TEAM_EXPS_AMT) FROM TB_CO_MON_COST WHERE COST_YM='202601' AND DEV_ID='monthclose-indirect'", String.class))
            .isEqualTo("INDRCT:INDRCT:1:INDRCT:0");
        assertThat(db.queryForObject("SELECT ASGN_MM FROM TB_CO_MON_COST WHERE COST_YM='202601' AND DEV_ID='monthclose-dev' AND PROJ_ID='monthclose-project'", java.math.BigDecimal.class))
            .isEqualByComparingTo("0.5");

        assertThat(db.queryForObject("SELECT sum(LABOR_ALLOC_AMT) FROM TB_CO_MON_COST WHERE COST_YM='202601' AND DEV_ID='monthclose-dev'", java.math.BigDecimal.class))
            .isPositive();
    }
    @Test void leftoverFixedRowsAreAClearSourceErrorNotADuplicateKey() throws Exception {
        var body = body(read("2026-01"), null);
        db.update("INSERT INTO TB_PJ_ASGN_MON(ASGN_ID,INPUT_YM,DEV_ID,PROJ_ID,INPUT_DAY_CNT,MON_MM,APLY_UPRC,SALES_AMT,FIX_YN,REG_USER_ID) "
            + "VALUES('monthclose-asgn','202601','monthclose-dev','monthclose-project',31,1,7000000,7000000,'Y','admin')");
        var before = state();
        mvc.perform(put(root("2026-01") + "/confirm").contentType("application/json").content(body))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("source"));
        assertThat(state()).isEqualTo(before);
    }
    @Test void confirmStoresSnapshotsServerActorWarningNamesAndSuccessLogAtomically() throws Exception {
        var before = read("2026-01"); assertThat(before.path("attentionCount").asInt()).isPositive();
        var result = write("2026-01", "confirm", body(before, null));
        assertThat(result.path("isClosed").asBoolean()).isTrue();
        assertThat(result.path("closedAt").isTextual()).isTrue(); assertThat(result.path("closedBy").asText()).isNotBlank();
        assertThat(db.queryForObject("SELECT CLOSE_USER_ID FROM TB_CL_CLOSE WHERE CLOSE_YM='202601'", String.class)).isEqualTo("admin");
        assertThat(db.queryForObject("SELECT CLOSE_STAT_CD FROM TB_CL_CLOSE WHERE CLOSE_YM='202601'", String.class)).isEqualTo("CLOSE");
        for (String query : List.of("SELECT FIX_YN FROM TB_PJ_ASGN_MON WHERE INPUT_YM='202601'", "SELECT FIX_YN FROM TB_CO_MON_COST WHERE COST_YM='202601'", "SELECT FIX_YN FROM TB_CO_MON_PL WHERE PL_YM='202601'"))
            assertThat(db.queryForList(query, String.class)).isNotEmpty().containsOnly("Y");
        assertThat(db.queryForObject("SELECT REMAIN_CHK_CN FROM TB_CL_CLOSE_HIST WHERE CLOSE_YM='202601' AND ACT_CD='CLOSE'", String.class)).contains("영업 파트 정산");
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-14' AND ACT_TYPE_CD='CLOSE' AND USER_ID='admin' AND TRGT_DESC='202601'", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_CO_PART_SETL WHERE SETL_YM='202601' AND SETL_STAT_CD='WORK'", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT CNFM_USER_ID FROM TB_CO_PART_SETL WHERE PART_ID='monthclose-part' AND SETL_YM='202601'", String.class)).isEqualTo("admin");
        assertThat(read("2026-01").path("modifiedAt")).isEqualTo(result.path("modifiedAt"));
    }
    @ParameterizedTest @ValueSource(strings = {"2025-12", "2026-02", "2026-04", "2026-05", "2026-13"})
    void invalidPeriodAndSkippedMonthLeaveAllTablesUntouched(String month) throws Exception {
        var before = state();
        mvc.perform(put(root(month) + "/confirm").contentType("application/json").content("{\"modifiedAt\":null}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("yearMonth"));
        assertThat(state()).isEqualTo(before);
    }
    @Test void sequentialConfirmReopenAndRecloseKeepOtherMonthAndSettlementsImmutable() throws Exception {
        write("2026-01", "confirm", body(read("2026-01"), null));
        var january = db.queryForMap("SELECT * FROM TB_CO_MON_PL WHERE PL_YM='202601'");
        var february = write("2026-02", "confirm", body(read("2026-02"), null));
        String settlements = state().get("TB_CO_PART_SETL");
        var fixedAt = db.queryForObject("SELECT MOD_DTTM FROM TB_CO_MON_PL WHERE PL_YM='202602'", java.time.OffsetDateTime.class);
        var reopened = write("2026-02", "reopen", body(february, "수정 필요"));
        // Unfixing is a change: MOD_USER_ID / MOD_DTTM are refreshed on the unfixed rows.
        assertThat(db.queryForObject("SELECT MOD_DTTM FROM TB_CO_MON_PL WHERE PL_YM='202602'", java.time.OffsetDateTime.class)).isAfter(fixedAt);
        assertThat(db.queryForObject("SELECT MOD_USER_ID FROM TB_CO_MON_PL WHERE PL_YM='202602'", String.class)).isEqualTo("admin");
        assertThat(reopened.path("isClosed").asBoolean()).isFalse();
        assertThat(state().get("TB_CO_PART_SETL")).isEqualTo(settlements);
        assertThat(db.queryForObject("SELECT FIX_YN FROM TB_CO_MON_PL WHERE PL_YM='202602'", String.class)).isEqualTo("N");
        assertThat(db.queryForObject("SELECT CLOSE_STAT_CD FROM TB_CL_CLOSE WHERE CLOSE_YM='202602'", String.class)).isEqualTo("WORK");
        assertThat(db.queryForObject("SELECT ACT_RSN FROM TB_CL_CLOSE_HIST WHERE CLOSE_YM='202602' AND ACT_CD='OPEN'", String.class)).isEqualTo("수정 필요");
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-14' AND ACT_TYPE_CD='UNCLOSE' AND USER_ID='admin' AND TRGT_DESC='202602'", Integer.class)).isEqualTo(1);
        assertThat(february.path("reopenable").asBoolean()).isTrue();
        assertThat(read("2026-01").path("reopenable").asBoolean()).isTrue(); // January is the latest close again
        assertThat(db.queryForObject("SELECT REMAIN_CHK_CN FROM TB_CL_CLOSE_HIST WHERE CLOSE_YM='202602' AND ACT_CD='OPEN'", String.class)).isNull();
        db.update("UPDATE TB_PJ_ASGN SET BILL_UPRC=BILL_UPRC+1 WHERE ASGN_ID='monthclose-asgn'");
        var reclosed = write("2026-02", "confirm", body(reopened, null));
        assertThat(reclosed.path("modifiedAt").asText()).isNotEqualTo(february.path("modifiedAt").asText());
        assertThat(db.queryForMap("SELECT * FROM TB_CO_MON_PL WHERE PL_YM='202601'")).isEqualTo(january);
        assertThat(db.queryForObject("SELECT FIX_YN FROM TB_CO_MON_PL WHERE PL_YM='202602'", String.class)).isEqualTo("Y");
        assertThat(state().get("TB_CO_PART_SETL")).isEqualTo(settlements);
    }
    @ParameterizedTest @ValueSource(strings = {"TB_CO_PART_SETL_DTL", "TB_CL_CLOSE_HIST"})
    void failureAfterSnapshotRollsBackEveryBusinessAndAuditRow(String table) throws Exception {
        var body = body(read("2026-01"), null); var before = state(); failInsert(table);
        mvc.perform(put(root("2026-01") + "/confirm").contentType("application/json").content(body)).andExpect(status().is5xxServerError());
        assertThat(state()).isEqualTo(before);
    }
    @Test void reopenHistoryFailureRollsBackUnfixAndCloseState() throws Exception {
        var closed = write("2026-01", "confirm", body(read("2026-01"), null));
        var before = state(); failInsert("TB_CL_CLOSE_HIST");
        mvc.perform(put(root("2026-01") + "/reopen").contentType("application/json").content(body(closed, "실패 시험"))).andExpect(status().is5xxServerError());
        assertThat(state()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings = {"confirm", "reopen"})
    void staleVersionProduces409AndNoSuccessfulHistory(String action) throws Exception {
        var row = action.equals("reopen") ? write("2026-01", "confirm", body(read("2026-01"), null)) : read("2026-01");
        if (action.equals("confirm")) db.update("INSERT INTO TB_CL_CLOSE(CLOSE_YM,CLOSE_STAT_CD,REG_USER_ID,MOD_DTTM) VALUES('202601','WORK','admin',clock_timestamp()) "
            + "ON CONFLICT(CLOSE_YM) DO UPDATE SET MOD_DTTM=clock_timestamp()");
        else db.update("UPDATE TB_CL_CLOSE SET MOD_DTTM=MOD_DTTM+interval '1 second' WHERE CLOSE_YM='202601'");
        var before = state();
        mvc.perform(put(root("2026-01") + "/" + action).contentType("application/json").content(body(row, "정정")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."));
        assertThat(state()).isEqualTo(before);
    }
    void failInsert(String table) {
        db.execute("CREATE FUNCTION monthclose_reject() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'monthclose transaction test'; END $$");
        db.execute("CREATE TRIGGER monthclose_reject BEFORE INSERT ON " + table + " FOR EACH ROW EXECUTE FUNCTION monthclose_reject()");
    }
    @Test void reopeningRedistributesOnlyOpenTaxAndRecloseFreezesFreshAllocation() throws Exception {
        db.update("INSERT INTO TB_FI_CORP_TAX(TAX_YR,EST_TAX_AMT,REG_USER_ID) VALUES('2026',1200000,'admin')");
        write("2026-01", "confirm", body(read("2026-01"), null));
        var february = write("2026-02", "confirm", body(read("2026-02"), null));
        var january = db.queryForMap("SELECT * FROM TB_CO_MON_PL WHERE PL_YM='202601'");
        db.update("UPDATE TB_FI_CORP_TAX SET EST_TAX_AMT=EST_TAX_AMT*2 WHERE TAX_YR='2026'");
        var reopened = write("2026-02", "reopen", body(february, "법인세 추정 검토"));
        var live = profits.year(2026, 12, "202601");
        var expected = com.company.common.financeitems.service.FinanceItemRules.allocation(2026,
            db.queryForObject("SELECT EST_TAX_AMT FROM TB_FI_CORP_TAX WHERE TAX_YR='2026'", java.math.BigDecimal.class),
            Set.of("202601"), month -> (java.math.BigDecimal) january.get("corp_tax_amt"));
        for (var month : expected.entrySet()) assertThat(live.get(month.getKey()).values().tax()).isEqualByComparingTo(month.getValue());
        assertThat(db.queryForMap("SELECT * FROM TB_CO_MON_PL WHERE PL_YM='202601'")).isEqualTo(january);
        write("2026-02", "confirm", body(reopened, null));
        assertThat(db.queryForObject("SELECT CORP_TAX_AMT FROM TB_CO_MON_PL WHERE PL_YM='202602'", java.math.BigDecimal.class))
            .isEqualByComparingTo(expected.get("202602"));
        assertThat(db.queryForMap("SELECT * FROM TB_CO_MON_PL WHERE PL_YM='202601'")).isEqualTo(january);
    }
    @Test void olderMonthReopenIsRejectedWithoutChangingEitherMonth() throws Exception {
        var january = write("2026-01", "confirm", body(read("2026-01"), null));
        write("2026-02", "confirm", body(read("2026-02"), null));
        var before = state();
        mvc.perform(put(root("2026-01") + "/reopen").contentType("application/json").content(body(january, "과거월 정정")))
            .andExpect(status().isBadRequest());
        assertThat(state()).isEqualTo(before);
    }
    @Test void impactFiltersBothApplyAndTargetMonthsAndNeverNetsAdjustments() throws Exception {
        for (String target : List.of("202601", "202512"))
            db.update("INSERT INTO TB_CO_PREV_ADJ(APLY_YM,TRGT_YM,ADJ_ITEM_CD,ADJ_AMT,ADJ_RSN,REG_USER_ID) VALUES('202602',?,'COST',1,'영향 조회 시험','admin')", target);
        var before = state();
        mvc.perform(get(root("2026-01") + "/reopen-impact")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.previousAdjustmentCount").value(1));
        assertThat(state()).isEqualTo(before);
    }
}
