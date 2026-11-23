package com.company.common.commonops;

import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.common.MonthCloseCodes;
import com.company.common.commonops.controller.MonthCloseReadController;
import com.company.common.commonops.mapper.MonthCloseReadMapper;
import com.company.common.commonops.service.MonthCloseReadService;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.users.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(MonthCloseReadController.class)
@ContextConfiguration(classes = {MonthCloseReadController.class, MonthCloseReadService.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class, MonthCloseReadPersistenceTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MonthCloseReadPersistenceTest {
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
            return new OwnedSchema("month_close_read_" + UUID.randomUUID().toString().replace("-", ""));
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
            factory.setMapperLocations(new ClassPathResource("mapper/monthcloses/MonthCloseReadMapper.xml"),
                new ClassPathResource("mapper/CodeGroupMapper.xml"));
            var config = new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config);
            return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }
        @Bean MonthCloseReadMapper monthCloseReadMapper(SqlSessionTemplate session) {
            return session.getMapper(MonthCloseReadMapper.class);
        }
        @Bean CommonCodeService commonCodeService(SqlSessionTemplate session) {
            return new CommonCodeService(session.getMapper(CodeGroupMapper.class), mock(UserMapper.class));
        }
        @Bean MonthCloseCodes monthCloseCodes(CommonCodeService codes) { return new MonthCloseCodes(codes); }
        @Bean BusinessCalendar businessCalendar() {
            return new BusinessCalendar(Clock.fixed(Instant.parse("2026-10-31T15:00:00Z"), ZoneOffset.UTC));
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }

    @BeforeEach void setup() throws Exception {
        new SchemaPreparation(sessions).prepare();
        db = new JdbcTemplate(source);
        // Disabled fixture actor; no usable login credential is created.
        db.update("INSERT INTO tb_sy_user(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,reg_user_id) "
            + "VALUES ('history-reader','이력 처리자','history@example.invalid','',0,'STOP',0,'history-reader')");
    }

    @AfterEach void cleanup() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + owned.name() + " CASCADE");
        }
    }

    private void history(String month, int sequence, String action, String reason, String names, String at) {
        db.update("INSERT INTO tb_cl_close_hist(close_ym,hist_sn,act_cd,act_rsn,remain_chk_cn,reg_user_id,reg_dttm) "
            + "VALUES (?,?,?,?,?,'history-reader',CAST(? AS timestamptz))", month, sequence, action, reason, names, at);
    }

    private Map<String, String> persisted() {
        var result = new LinkedHashMap<String, String>();
        for (String table : List.of("tb_cl_close", "tb_cl_close_hist", "tb_cl_close_chk", "tb_co_part_setl",
            "tb_co_part_setl_dtl", "tb_pj_asgn_mon", "tb_co_mon_cost", "tb_co_mon_pl", "tb_sy_accs_log")) {
            result.put(table, db.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text),'[]'::jsonb)::text FROM "
                + table + " r", String.class));
        }
        return result;
    }

    @Test void allAndFilteredHistoryUseOriginalTimestampMonthAndSequenceOrderingWithoutWrites() throws Exception {
        history("202610", 1, "CLOSE", null, "MM 초과,영업 파트 정산", "2026-11-01T01:00:00Z");
        history("202610", 2, "OPEN", "정정", "legacy warning", "2026-11-01T01:00:00Z");
        history("202609", 1, "CLOSE", null, null, "2026-11-01T01:00:00Z");
        history("202608", 1, "CLOSE", null, null, "2026-11-02T01:00:00Z");
        var before = persisted();
        var response = mvc.perform(get("/api/common/month-close-history"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(4))
            .andExpect(jsonPath("$.data.items[0].yearMonth").value("2026-08"))
            .andExpect(jsonPath("$.data.items[1].action").value("해제"))
            .andExpect(jsonPath("$.data.items[1].reason").value("정정"))
            .andExpect(jsonPath("$.data.items[1].remainingChecks").isEmpty())
            .andExpect(jsonPath("$.data.items[2].action").value("마감"))
            .andExpect(jsonPath("$.data.items[2].processor").value("이력 처리자"))
            .andExpect(jsonPath("$.data.items[2].remainingChecks[0]").value("MM 초과"))
            .andExpect(jsonPath("$.data.items[2].remainingChecks[1]").value("영업 파트 정산"))
            .andExpect(jsonPath("$.data.items[3].yearMonth").value("2026-09"))
            .andReturn().getResponse();
        assertThat(json.readTree(response.getContentAsString()).path("data").path("items").get(2).path("reason").isNull()).isTrue();
        mvc.perform(get("/api/common/month-close-history").param("yearMonth", "2026-10"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].action").value("해제"))
            .andExpect(jsonPath("$.data.items[1].action").value("마감"));
        assertThat(persisted()).isEqualTo(before);
    }

    @Test void currentMonthBadgeDistinguishesMissingCloseAndWorkRowsWithoutCreatingMetadata() throws Exception {
        var before = persisted();
        mvc.perform(get("/api/common/month-close-badge")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.needsAttention").value(true));
        assertThat(persisted()).isEqualTo(before);
        db.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('202610','CLOSE','history-reader')");
        mvc.perform(get("/api/common/month-close-badge")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.needsAttention").value(true));
        db.update("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('202611','CLOSE','history-reader')");
        before = persisted();
        mvc.perform(get("/api/common/month-close-badge")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.needsAttention").value(false));
        assertThat(persisted()).isEqualTo(before);
        db.update("UPDATE tb_cl_close SET close_stat_cd='WORK' WHERE close_ym='202611'");
        before = persisted();
        mvc.perform(get("/api/common/month-close-badge")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.needsAttention").value(true));
        assertThat(persisted()).isEqualTo(before);
    }

    @Test void emptyHistoryAndInvalidMonthNeverInitializeRows() throws Exception {
        var before = persisted();
        mvc.perform(get("/api/common/month-close-history")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(get("/api/common/month-close-history").param("yearMonth", "2026-13"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("yearMonth"));
        assertThat(persisted()).isEqualTo(before);
    }
}
