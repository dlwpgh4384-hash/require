package com.company.common.commonops;

import com.company.common.commonops.controller.*;
import com.company.common.commonops.mapper.AccountAccessMapper;
import com.company.common.commonops.service.*;
import com.company.common.mfa.service.CommonMfaService;
import com.company.common.support.auth.AccountSessions;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.users.mapper.UserMapper;
import com.company.common.users.service.TemporaryUserPassword;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import jakarta.servlet.http.Cookie;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(AccountAccessController.class)
@ContextConfiguration(classes = {AccountAccessController.class, AccountAccessExceptionHandler.class,
    AccountAccessService.class, AccountLoginCompletion.class, AccountSessions.class, AccountApiPolicy.class,
    LoginFailureDelay.class, BusinessCalendar.class, AccountAccessConfiguration.class, AccountAccessDatabaseTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountAccessDatabaseTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired OwnedSchema owned;
    @Autowired AccountAccessMapper mapper;
    @MockBean TemporaryUserPassword passwords;
    @MockBean CommonMfaService mfa;
    JdbcTemplate db;
    String secret;
    record OwnedSchema(String name) {}
    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean OwnedSchema ownedSchema() { return new OwnedSchema("accounts_" + UUID.randomUUID().toString().replace("-", "")); }
        @Bean DataSource dataSource(OwnedSchema owned) throws Exception {
            String url = System.getenv("NAVIGATION_TEST_DB_URL");
            String user = System.getenv("NAVIGATION_TEST_DB_USER");
            String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
                statement.execute("CREATE SCHEMA " + owned.name());
                statement.execute("SET search_path TO " + owned.name());
                statement.execute(new ClassPathResource("db/migration/V1__navigation_schema.sql").getContentAsString(StandardCharsets.UTF_8));
                statement.execute(new ClassPathResource("db/migration/V5__role_permissions.sql").getContentAsString(StandardCharsets.UTF_8));
            }
            var data = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new java.util.Properties(); properties.setProperty("currentSchema", owned.name()); data.setDriverProperties(properties); return data;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/AccountAccessMapper.xml"), new ClassPathResource("mapper/UserMapper.xml"));
            var configuration = new org.apache.ibatis.session.Configuration(); configuration.setMapUnderscoreToCamelCase(false);
            configuration.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(configuration); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean AccountAccessMapper accountAccessMapper(SqlSessionTemplate sessions) { return sessions.getMapper(AccountAccessMapper.class); }
        @Bean UserMapper userMapper(SqlSessionTemplate sessions) { return sessions.getMapper(UserMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void setup() {
        db = new JdbcTemplate(source); secret = UUID.randomUUID() + "A1!";
        db.update("INSERT INTO TB_SY_USER(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,mfa_err_cnt,temp_pwd_yn,user_stat_cd,reg_user_id,mod_dttm) VALUES ('reader','조회 사용자','reader@example.invalid',?,2,2,'N','USE','SYSTEM',clock_timestamp())", new BCryptPasswordEncoder(10).encode(secret));
        db.update("INSERT INTO TB_SY_ROLE(role_id,role_nm,data_scope_cd,sort_ord,reg_user_id) VALUES ('FIN','재무','ALL',3,'SYSTEM')");
        db.update("INSERT INTO TB_SY_USER_ROLE(user_id,role_id,reg_user_id) VALUES ('reader','FIN','SYSTEM')");
        for (var standard : Map.of("LOCK_FAIL_CNT", 5, "SESSION_MIN", 30, "PWD_CHG_DAY", 90).entrySet())
            db.update("INSERT INTO TB_SY_OPER_STD(std_cd,aply_bgn_ym,std_nm,std_val,unit_nm,reg_user_id) VALUES (?,'200001',?,?,'시험','SYSTEM')", standard.getKey(), standard.getKey(), standard.getValue());
    }
    @AfterEach void cleanup() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) { statement.execute("DROP SCHEMA " + owned.name() + " CASCADE"); }
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postJson(String path, MockHttpSession session, Object body) throws Exception {
        return post(path).servletPath(path).session(session).cookie(new Cookie("XSRF-TOKEN", "test-token")).header("X-XSRF-TOKEN", "test-token")
            .contentType("application/json").content(json.writeValueAsString(body));
    }
    MockHttpSession login() throws Exception {
        var session = new MockHttpSession(); String before = session.getId();
        mvc.perform(postJson("/api/common/login", session, Map.of("userId", "reader", "password", secret))).andExpect(status().isOk());
        assertThat(session.getId()).isNotEqualTo(before); return session;
    }
    @Test void successfulLoginCommitsCountersTimestampAndOneAuditRow() throws Exception {
        mvc.perform(postJson("/api/common/login", new MockHttpSession(), Map.of("userId", "reader", "password", secret)))
            .andExpect(status().isOk())
            .andExpect(com.company.common.support.controller.OpenApiResponseContract.response("POST", "/api/common/login"))
            .andExpect(jsonPath("$.data.nextScreenId").value("UI-34"));
        var row = mapper.account("reader");
        assertThat(row.failures).isZero(); assertThat(row.lastLoginAt).isNotNull();
        assertThat(db.queryForObject("SELECT mfa_err_cnt FROM TB_SY_USER WHERE user_id='reader'", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE user_id='reader' AND act_type_cd='LOGIN'", Long.class)).isEqualTo(1L);
    }
    @Test void thresholdFailureCommitsLockEvenThoughHttpIsUnauthorized() throws Exception {
        db.update("UPDATE TB_SY_USER SET pwd_err_cnt=4 WHERE user_id='reader'");
        mvc.perform(postJson("/api/common/login", new MockHttpSession(), Map.of("userId", "reader", "password", UUID.randomUUID().toString())))
            .andExpect(status().isUnauthorized());
        assertThat(mapper.account("reader").status).isEqualTo("LOCK"); assertThat(mapper.account("reader").failures).isEqualTo(5);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE user_id='reader' AND act_type_cd='LOGIN_FAIL'", Long.class)).isEqualTo(1L);
    }
    @Test void changeCommitsNewHashAndInvalidatesOnlyOtherDevice() throws Exception {
        var current = login(); var other = login(); String next = UUID.randomUUID() + "A2!"; var before = mapper.account("reader");
        mvc.perform(put("/api/common/my-password").servletPath("/api/common/my-password").session(current)
            .cookie(new Cookie("XSRF-TOKEN", "test-token")).header("X-XSRF-TOKEN", "test-token").contentType("application/json")
            .content(json.writeValueAsString(Map.of("currentPassword", secret, "newPassword", next, "modifiedAt", before.modifiedAt))))
            .andExpect(status().isOk());
        var row = mapper.account("reader"); assertThat(row.passwordChangedAt).isAfter(before.modifiedAt);
        assertThat(row.hash).startsWith("$2a$10$"); assertThat(new BCryptPasswordEncoder(10).matches(next, row.hash)).isTrue();
        assertThat(other.isInvalid()).isTrue();
        mvc.perform(get("/api/common/my-info").servletPath("/api/common/my-info").session(current)).andExpect(status().isOk());
        secret = next; login();
    }
    @Test void directLogoutCommitsOneAuditAndKeepsOtherDevice() throws Exception {
        var current = login(); var other = login();
        mvc.perform(postJson("/api/common/logout", current, Map.of())).andExpect(status().isNoContent());
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE user_id='reader' AND act_type_cd='LOGOUT' AND trgt_desc='직접 로그아웃'", Long.class)).isEqualTo(1L);
        mvc.perform(get("/api/common/my-info").servletPath("/api/common/my-info").session(other)).andExpect(status().isOk());
    }
    @Test void passwordFindCommitsOnlyAfterRealLocalDeliveryAndEndsEveryDevice() throws Exception {
        var current = login(); var other = login(); var before = mapper.account("reader");
        var mailbox = org.mockito.Mockito.spy(new com.company.common.bills.service.BillLocalMailbox(null, ""));
        var delivery = new java.util.concurrent.atomic.AtomicReference<java.nio.file.Path>();
        org.mockito.Mockito.doAnswer(call -> {
            var path = (java.nio.file.Path) call.callRealMethod();
            delivery.set(path); return path;
        }).when(mailbox).deliver(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyString());
        var actual = new TemporaryUserPassword(mailbox);
        org.mockito.Mockito.when(passwords.hashAndDeliver("reader@example.invalid"))
            .thenAnswer(call -> actual.hashAndDeliver(call.getArgument(0)));
        try {
            mvc.perform(postJson("/api/common/password-find", new MockHttpSession(),
                Map.of("userId", "reader", "email", "reader@example.invalid")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value(AccountAccessService.FIND_MESSAGE));
            assertThat(delivery.get()).isRegularFile();
            assertThat(delivery.get().getFileName().toString()).endsWith(".eml");
            var row = mapper.account("reader");
            assertThat(row.hash).isNotEqualTo(before.hash).startsWith("$2a$10$");
            assertThat(row.temporary).isEqualTo("Y"); assertThat(row.failures).isZero();
            assertThat(current.isInvalid()).isTrue(); assertThat(other.isInvalid()).isTrue();
            mvc.perform(postJson("/api/common/password-find", new MockHttpSession(),
                Map.of("userId", "reader", "email", "reader@example.invalid"))).andExpect(status().isOk());
            org.mockito.Mockito.verify(passwords).hashAndDeliver("reader@example.invalid");
            assertThat(mapper.account("reader").hash).isEqualTo(row.hash);
        } finally {
            mailbox.discard(delivery.get());
        }
    }
    @Test void deliveryFailureLeavesPersistedHashAndAllSessionsUnchanged() throws Exception {
        var current = login(); var other = login(); var before = mapper.account("reader");
        org.mockito.Mockito.when(passwords.hashAndDeliver("reader@example.invalid"))
            .thenThrow(new IllegalStateException("delivery unavailable"));
        mvc.perform(postJson("/api/common/password-find", new MockHttpSession(),
            Map.of("userId", "reader", "email", "reader@example.invalid")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.message").value(AccountAccessService.FIND_MESSAGE));
        var row = mapper.account("reader");
        assertThat(row.hash).isEqualTo(before.hash); assertThat(row.modifiedAt).isEqualTo(before.modifiedAt);
        assertThat(row.temporary).isEqualTo(before.temporary);
        mvc.perform(get("/api/common/my-info").servletPath("/api/common/my-info").session(current)).andExpect(status().isOk());
        mvc.perform(get("/api/common/my-info").servletPath("/api/common/my-info").session(other)).andExpect(status().isOk());
    }
}
