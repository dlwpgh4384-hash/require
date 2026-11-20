package com.company.common.commonops;

import com.company.common.commonops.controller.PartSettlementCloseController;
import com.company.common.commonops.mapper.PartSettlementCloseMapper;
import com.company.common.commonops.service.PartSettlementCloseService;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(PartSettlementCloseController.class)
@ContextConfiguration(classes = {PartSettlementCloseController.class, PartSettlementCloseService.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class, PartSettlementClosePersistenceTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PartSettlementClosePersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired SqlSessionFactory sessions;
    @Autowired OwnedSchema owned;
    JdbcTemplate db;
    record OwnedSchema(String name) {}

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean OwnedSchema ownedSchema() {
            return new OwnedSchema("settlement_check_" + UUID.randomUUID().toString().replace("-", ""));
        }
        @Bean DataSource dataSource(OwnedSchema owned) throws Exception {
            String url = System.getenv("NAVIGATION_TEST_DB_URL");
            String user = System.getenv("NAVIGATION_TEST_DB_USER");
            String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var connection = DriverManager.getConnection(url, user, password);
                 var statement = connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
                statement.execute("CREATE SCHEMA " + owned.name());
            }
            var data = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new java.util.Properties();
            properties.setProperty("currentSchema", owned.name());
            data.setDriverProperties(properties);
            return data;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/PartSettlementCloseMapper.xml"));
            var config = new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config);
            return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean PartSettlementCloseMapper partSettlementCloseMapper(SqlSessionTemplate session) {
            return session.getMapper(PartSettlementCloseMapper.class);
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }

    @BeforeEach void setup() throws Exception {
        new SchemaPreparation(sessions).prepare();
        db = new JdbcTemplate(source);
    }
    @AfterEach void cleanup() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + owned.name() + " CASCADE");
        }
    }
    private void part(String id, String hq, String use) {
        db.update("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES (?,?,?,?,'admin')",
            id, id, hq, use);
    }
    private void criteria(String id, String start, String end) {
        db.update("INSERT INTO tb_bs_part_std(part_id,aply_bgn_ym,aply_end_ym,part_ldr_user_id,prft_dstrb_rt,sales_exps_amt,reg_user_id) "
            + "VALUES (?,?,?,'admin',37,100,'admin')", id, start, end);
    }
    private void target(String id) { part(id, "N", "Y"); criteria(id, "202601", null); }
    private void history(String id, String before, String after, String at) {
        db.update("INSERT INTO tb_sy_chg_hist(trgt_tbl_nm,trgt_key,chg_type_cd,chg_item_nm,bfr_val,aft_val,reg_user_id,reg_dttm) "
            + "VALUES ('TB_BS_PART',?,'UPD','사용 여부',?,?,'admin',CAST(? AS timestamptz))", id, before, after, at);
    }
    private void header(String id, String state) {
        db.update("INSERT INTO tb_co_part_setl(part_id,setl_ym,corp_card_amt,part_prft_amt,setl_stat_cd,reg_user_id,mod_dttm) "
            + "VALUES (?,'202610',123,456,?,'admin',TIMESTAMPTZ '2026-10-31 15:00:00.123456+00')", id, state);
    }
    private JsonNode check() throws Exception {
        return json.readTree(mvc.perform(get("/api/common/part-settlement-close-checks").param("yearMonth", "2026-10"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    private List<String> ids(JsonNode data) {
        var ids = new java.util.ArrayList<String>();
        data.path("items").forEach(item -> ids.add(item.path("partId").asText()));
        return ids;
    }
    private Map<String, String> persisted() {
        var values = new LinkedHashMap<String, String>();
        for (String table : List.of("tb_co_part_setl", "tb_co_part_setl_dtl", "tb_cl_close", "tb_cl_close_chk",
            "tb_sy_chg_hist", "tb_sy_accs_log")) {
            values.put(table, db.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text FROM "
                + table + " r", String.class));
        }
        return values;
    }

    @Test void anonymousCountUsesExactlyTheReturnedTargetsWithoutCreatingCloseMetadata() throws Exception {
        target("check-draft"); target("check-direct"); target("check-auto"); target("check-missing");
        header("check-draft", "WORK"); header("check-direct", "CNFM"); header("check-auto", "AUTO");
        var before = persisted();
        var data = check();
        assertThat(ids(data)).contains("check-draft", "check-direct", "check-auto", "check-missing");
        int unconfirmed = 0;
        for (var item : data.path("items")) {
            if (!item.path("confirmed").asBoolean()) unconfirmed++;
            if (item.path("partId").asText().equals("check-missing")) assertThat(item.path("modifiedAt").isNull()).isTrue();
            if (item.path("partId").asText().equals("check-auto")) {
                assertThat(item.path("confirmed").asBoolean()).isTrue();
                assertThat(item.path("modifiedAt").asText()).isEqualTo("2026-10-31T15:00:00.123456Z");
            }
        }
        assertThat(data.path("totalCount").asInt()).isEqualTo(data.path("items").size());
        assertThat(data.path("unconfirmedCount").asInt()).isEqualTo(unconfirmed);
        assertThat(persisted()).isEqualTo(before);
    }

    @Test void staleLegacyCheckMetadataNeverSuppliesTargetsOrCounts() throws Exception {
        target("check-legacy");
        db.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('202610','CLOSE','admin')");
        db.update("INSERT INTO tb_cl_close_chk(close_ym,chk_item_cd,chk_stat_cd,chk_cnt,chk_dttm,reg_user_id) "
            + "VALUES ('202610','SETL','OK',0,clock_timestamp(),'admin')");
        var before = persisted();
        var data = check();
        assertThat(ids(data)).containsExactly("check-legacy");
        assertThat(data.path("unconfirmedCount").asInt()).isEqualTo(1);
        assertThat(persisted()).isEqualTo(before);
    }
    @Test void headquartersIsExcludedEvenWithEffectiveCriteriaAndAConfirmedHeader() throws Exception {
        part("check-hq", "Y", "Y"); criteria("check-hq", "202601", null); header("check-hq", "CNFM");
        assertThat(ids(check())).doesNotContain("check-hq");
    }
    @Test void missingCriteriaIsExcluded() throws Exception {
        part("check-no-criteria", "N", "Y");
        assertThat(ids(check())).doesNotContain("check-no-criteria");
    }
    @Test void criteriaStartAndEndAreInclusiveAndOverlapsDoNotDuplicateTargets() throws Exception {
        part("check-inclusive", "N", "Y");
        criteria("check-inclusive", "202601", "202610"); criteria("check-inclusive", "202610", "202612");
        assertThat(ids(check())).containsOnlyOnce("check-inclusive");
    }
    @Test void futureCriteriaIsExcluded() throws Exception {
        part("check-future", "N", "Y"); criteria("check-future", "202611", null);
        assertThat(ids(check())).doesNotContain("check-future");
    }
    @Test void expiredCriteriaIsExcluded() throws Exception {
        part("check-expired", "N", "Y"); criteria("check-expired", "202601", "202609");
        assertThat(ids(check())).doesNotContain("check-expired");
    }
    @Test void stoppedDuringMonthIsExcludedEvenThoughItWasPreviouslyActive() throws Exception {
        part("check-stop", "N", "N"); criteria("check-stop", "202601", null);
        history("check-stop", "Y", "N", "2026-10-15T00:00:00Z");
        assertThat(ids(check())).doesNotContain("check-stop");
    }
    @Test void stoppedBeforeMonthIsExcludedWithoutSubsequentHistory() throws Exception {
        part("check-old-stop", "N", "N"); criteria("check-old-stop", "202601", null);
        history("check-old-stop", "Y", "N", "2026-09-15T00:00:00Z");
        assertThat(ids(check())).doesNotContain("check-old-stop");
    }
    @Test void stopAtNextSeoulMidnightDoesNotRewritePreviousMonthActivity() throws Exception {
        part("check-later-stop", "N", "N"); criteria("check-later-stop", "202601", null);
        history("check-later-stop", "Y", "N", "2026-10-31T15:00:00Z");
        assertThat(ids(check())).contains("check-later-stop");
    }
    @Test void laterResumeDoesNotRewriteStoppedMonthAsActive() throws Exception {
        target("check-resume");
        history("check-resume", "Y", "N", "2026-10-15T00:00:00Z");
        history("check-resume", "N", "Y", "2026-11-05T00:00:00Z");
        assertThat(ids(check())).doesNotContain("check-resume");
    }
    @Test void earliestSubsequentHistoryWinsWithHistoryIdResolvingTimestampTies() throws Exception {
        part("check-tie", "N", "N"); criteria("check-tie", "202601", null);
        history("check-tie", "N", "Y", "2026-11-05T00:00:00Z");
        history("check-tie", "Y", "N", "2026-11-05T00:00:00Z");
        assertThat(ids(check())).doesNotContain("check-tie");
    }
    @Test void invalidMonthLeavesHeadersDetailsAndCloseAndAuditRowsUnchanged() throws Exception {
        target("check-invalid"); header("check-invalid", "WORK");
        var before = persisted();
        mvc.perform(get("/api/common/part-settlement-close-checks").param("yearMonth", "2026-13"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("yearMonth"));
        assertThat(persisted()).isEqualTo(before);
    }
    @Test void rereadReflectsPersistedAutoStateWithoutChangingFrozenValues() throws Exception {
        target("check-transition"); header("check-transition", "WORK");
        var first = check();
        assertThat(first.path("totalCount").asInt()).isEqualTo(1);
        assertThat(first.path("unconfirmedCount").asInt()).isEqualTo(1);
        // The command belongs to the settlement module. This fixture transition tests the read boundary only.
        db.update("UPDATE tb_co_part_setl SET setl_stat_cd='AUTO' WHERE part_id='check-transition' AND setl_ym='202610'");
        var beforeRead = persisted();
        var second = check();
        assertThat(second.path("totalCount").asInt()).isEqualTo(first.path("totalCount").asInt());
        assertThat(second.path("unconfirmedCount").asInt()).isEqualTo(first.path("unconfirmedCount").asInt() - 1);
        assertThat(second.path("unconfirmedCount").asInt()).isZero();
        assertThat(persisted()).isEqualTo(beforeRead);
    }
}
