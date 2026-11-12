package com.company.common.service;

import com.company.common.controller.CommonUserPreferenceController;
import com.company.common.controller.UserPreferenceExceptionHandler;
import com.company.common.mapper.UserPreferenceMapper;
import com.company.common.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.dto.UserPreferenceInput;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(CommonUserPreferenceController.class)
@ContextConfiguration(classes = {CommonUserPreferenceController.class, CommonUserPreferenceService.class,
    UserPreferenceRules.class, UserPreferenceExceptionHandler.class, UserPreferencePeriodBasis.class,
    UserPreferenceIntegrationTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserPreferenceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired OwnedSchema owned;
    @Autowired CommonUserPreferenceService service;
    private static final String ROOT = "/api/common/user-preferences/";

    record OwnedSchema(String name) {}

    @BeforeEach
    void readDurableOpenApiContract() throws Exception {
        String contract = new ClassPathResource("contracts/openapi.yaml")
            .getContentAsString(StandardCharsets.UTF_8);
        assertThat(contract).contains("operationId: getCommonUserPreference",
            "operationId: putCommonUserPreference", "x-side-effects:", "x-state-transitions:");
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean OwnedSchema ownedSchema() { return new OwnedSchema("preferences_" + UUID.randomUUID().toString().replace("-", "")); }
        @Bean DataSource dataSource(OwnedSchema owned) throws Exception {
            String url = System.getenv("NAVIGATION_TEST_DB_URL");
            String user = System.getenv("NAVIGATION_TEST_DB_USER");
            String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var connection = DriverManager.getConnection(url, user, password);
                 var statement = connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
                statement.execute("CREATE SCHEMA " + owned.name());
                statement.execute("SET search_path TO " + owned.name());
                statement.execute(new ClassPathResource("db/migration/V1__navigation_schema.sql")
                    .getContentAsString(StandardCharsets.UTF_8));
            }
            var dataSource = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new java.util.Properties();
            properties.setProperty("currentSchema", owned.name());
            dataSource.setDriverProperties(properties);
            return dataSource;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/UserPreferenceMapper.xml"),
                new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var config = new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config);
            return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean UserPreferenceMapper userPreferenceMapper(SqlSessionTemplate sessions) { return sessions.getMapper(UserPreferenceMapper.class); }
        @Bean UserPreferencePeriodSourceMapper userPreferencePeriodSourceMapper(SqlSessionTemplate sessions) {
            return sessions.getMapper(UserPreferencePeriodSourceMapper.class);
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }

    @AfterEach
    void removeOnlyOwnedSchema() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + owned.name() + " CASCADE");
        }
    }

    private void seed(String type, String value, Instant token) throws Exception {
        try (var connection = source.getConnection(); var statement = connection.prepareStatement("""
            INSERT INTO TB_SY_USER_PREF(user_id,pref_type_cd,pref_key,pref_val,reg_user_id,mod_dttm)
            VALUES ('admin',?,'GLOBAL',?,'original',?)
            """)) {
            statement.setString(1, type);
            statement.setString(2, value);
            statement.setObject(3, token == null ? null : token.atOffset(java.time.ZoneOffset.UTC));
            statement.executeUpdate();
        }
    }

    private String getToken(String type) throws Exception {
        String body = mvc.perform(get(ROOT + type + "/GLOBAL")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var token = json.readTree(body).path("data").path("modDttm");
        return token.isNull() ? null : token.textValue();
    }

    private org.springframework.test.web.servlet.ResultActions save(String type, String value, String token) throws Exception {
        var body = json.createObjectNode().put("prefVal", value);
        if (token == null) body.putNull("modDttm"); else body.put("modDttm", token);
        return mvc.perform(put(ROOT + type + "/GLOBAL").contentType("application/json").content(body.toString()));
    }

    @Test
    void happyUpdateIsCommittedAndReadByNewConnection() throws Exception {
        seed("PAGE_SIZE", "20", java.time.Instant.parse("2026-01-01T00:00:00.123456Z"));
        mvc.perform(put("/api/common/user-preferences/PAGE_SIZE/GLOBAL")
            .contentType("application/json")
            .content(json.writeValueAsString(java.util.Map.of("prefVal", "50", "modDttm", getToken("PAGE_SIZE")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.found").value(true))
            .andExpect(jsonPath("$.data.prefVal").value("50"));
        mvc.perform(get("/api/common/user-preferences/PAGE_SIZE/GLOBAL"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.prefVal").value("50"));
        assertStored("PAGE_SIZE", "50");
    }

    @Test
    void authSideEffectAnonymousFoldUpdateWritesAdminAuditAndSameMicrosecondToken() throws Exception {
        Instant originalToken = java.time.Instant.parse("2026-01-01T00:00:00.000000Z");
        seed("MENU_FOLD", "false", originalToken);
        var saved = mvc.perform(put("/api/common/user-preferences/MENU_FOLD/GLOBAL")
            .contentType("application/json")
            .content("{\"prefVal\":\"true\",\"modDttm\":\"2026-01-01T00:00:00Z\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.prefVal").value("true")).andReturn();
        String token = json.readTree(saved.getResponse().getContentAsString()).path("data").path("modDttm").textValue();
        mvc.perform(get("/api/common/user-preferences/MENU_FOLD/GLOBAL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.prefVal").value("true"))
            .andExpect(jsonPath("$.data.modDttm").value(token));
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT pref_val,mod_user_id,mod_dttm,reg_user_id FROM tb_sy_user_pref WHERE user_id='admin' AND pref_type_cd='MENU_FOLD' AND pref_key='GLOBAL'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("true");
            assertThat(rows.getString(2)).isEqualTo("admin");
            assertThat(rows.getObject(3, OffsetDateTime.class).toInstant()).isEqualTo(OffsetDateTime.parse(token).toInstant());
            assertThat(rows.getObject(3, OffsetDateTime.class).toInstant()).isAfter(originalToken);
            assertThat(rows.getString(4)).isEqualTo("original");
        }
    }

    @Test
    void validationInvalidPageSizeLeavesExistingDatabaseValueUnchanged() throws Exception {
        seed("PAGE_SIZE", "20", null);
        mvc.perform(put("/api/common/user-preferences/PAGE_SIZE/GLOBAL")
            .contentType("application/json").content("{\"prefVal\":\"30\",\"modDttm\":null}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors[0].field").value("prefVal"));
        assertStored("PAGE_SIZE", "20");
    }

    @Test
    void businessStaleUpdateIs409AndLatestDatabaseValueIsPreserved() throws Exception {
        seed("PAGE_SIZE", "20", null);
        save("PAGE_SIZE", "100", null).andExpect(status().isOk());
        mvc.perform(put("/api/common/user-preferences/PAGE_SIZE/GLOBAL")
            .contentType("application/json").content("{\"prefVal\":\"50\",\"modDttm\":null}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.message").value(UserPreferenceFailure.CONFLICT));
        assertStored("PAGE_SIZE", "100");
    }

    @Test
    void sideEffectFirstInsertRecordsRegistrationAndDoesNotAllowAnotherNullTokenOverwrite() throws Exception {
        mvc.perform(put("/api/common/user-preferences/DENSE/GLOBAL")
            .contentType("application/json").content("{\"prefVal\":\"좁게\",\"modDttm\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.found").value(true))
            .andExpect(jsonPath("$.data.prefVal").value("좁게"));
        save("DENSE", "넓게", null).andExpect(status().isConflict());
        assertStored("DENSE", "좁게");
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT reg_user_id,reg_dttm,mod_dttm FROM tb_sy_user_pref")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("admin");
            assertThat(rows.getTimestamp(2)).isNotNull();
            assertThat(rows.getTimestamp(3)).isNotNull();
        }
    }

    @Test
    void simultaneousFirstSaveHasExactlyOneWinner() throws Exception {
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> first = () -> attempt(start, "좁게");
            Callable<Integer> second = () -> attempt(start, "넓게");
            var a = pool.submit(first);
            var b = pool.submit(second);
            start.countDown();
            assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(200, 409);
            try (var connection = source.getConnection(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT count(*) FROM tb_sy_user_pref")) {
                rows.next(); assertThat(rows.getInt(1)).isEqualTo(1);
            }
        } finally { pool.shutdownNow(); }
    }

    private int attempt(CountDownLatch start, String value) throws Exception {
        start.await();
        try {
            service.putCommonUserPreference("DENSE", "GLOBAL", new UserPreferenceInput(value, null));
            return 200;
        } catch (UserPreferenceFailure failure) { return failure.getStatus(); }
    }

    private void assertStored(String type, String value) throws Exception {
        try (var connection = source.getConnection(); var statement = connection.prepareStatement(
            "SELECT pref_val FROM tb_sy_user_pref WHERE user_id='admin' AND pref_type_cd=? AND pref_key='GLOBAL'")) {
            statement.setString(1, type);
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(value);
                assertThat(rows.next()).isFalse();
            }
        }
    }
}
