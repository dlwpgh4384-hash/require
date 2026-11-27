package com.company.common.mfa.service;

import com.company.common.bills.service.BillLocalMailbox;
import com.company.common.mfa.controller.CommonMfaController;
import com.company.common.mfa.controller.MfaExceptionHandler;
import com.company.common.mfa.mapper.MfaMapper;
import com.company.common.support.auth.LoginCompletion;
import com.company.common.support.service.BusinessCalendar;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
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
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(CommonMfaController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {CommonMfaController.class, CommonMfaService.class, MfaExceptionHandler.class,
    MfaPersistenceIntegrationTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MfaPersistenceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired OwnedSchema owned;
    @Autowired CommonMfaService service;
    @Autowired MfaMapper mfaMapper;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @SpyBean BillLocalMailbox mailbox;
    @MockBean BusinessCalendar calendar;
    @MockBean LoginCompletion completion;
    JdbcTemplate db;
    Instant now;
    List<Path> mail = new ArrayList<>();
    record OwnedSchema(String name) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class Persistence {
        @Bean OwnedSchema ownedSchema() { return new OwnedSchema("mfa_" + UUID.randomUUID().toString().replace("-", "")); }
        @Bean DataSource dataSource(OwnedSchema owned) throws Exception {
            String url = System.getenv("NAVIGATION_TEST_DB_URL");
            String user = System.getenv("NAVIGATION_TEST_DB_USER");
            String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
                statement.execute("CREATE SCHEMA " + owned.name());
                statement.execute("SET search_path TO " + owned.name());
                statement.execute(new ClassPathResource("db/migration/V1__navigation_schema.sql").getContentAsString(StandardCharsets.UTF_8));
            }
            var data = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new java.util.Properties(); properties.setProperty("currentSchema", owned.name());
            data.setDriverProperties(properties); return data;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/MfaMapper.xml"));
            var configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(false);
            configuration.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(configuration); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean MfaMapper mfaMapper(SqlSessionTemplate sessions) { return sessions.getMapper(MfaMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean BillLocalMailbox mailbox() {
            return new BillLocalMailbox(new StaticListableBeanFactory().getBeanProvider(JavaMailSender.class), "");
        }
    }
    @BeforeEach void setup() {
        db = new JdbcTemplate(source);
        now = Instant.parse("2026-01-02T00:00:00Z");
        when(calendar.now()).thenAnswer(call -> now);
        when(completion.prepare(anyString(), anyBoolean())).thenReturn(
            new LoginCompletion.Prepared("UI-34", Map.of("modifiedAt", now.toString(), "passwordChangeDays", 90)));
        db.update("INSERT INTO TB_SY_USER(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,mfa_err_cnt,user_stat_cd,temp_pwd_yn,reg_user_id) VALUES ('mfa-admin','시험 관리자','actor@example.invalid',?,2,0,'USE','Y','mfa-admin')",
            new BCryptPasswordEncoder(10).encode(UUID.randomUUID().toString()));
        db.update("INSERT INTO TB_SY_ROLE(role_id,role_nm,data_scope_cd,sort_ord,reg_user_id) VALUES ('ADMIN','시험 관리자','ALL',1,'mfa-admin')");
        db.update("INSERT INTO TB_SY_USER_ROLE(user_id,role_id,reg_user_id) VALUES ('mfa-admin','ADMIN','mfa-admin')");
        doAnswer(call -> { Path path = (Path) call.callRealMethod(); mail.add(path); return path; })
            .when(mailbox).deliver(anyList(), anyString());
    }
    @AfterEach void cleanup() throws Exception {
        mail.forEach(mailbox::discard);
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + owned.name() + " CASCADE");
        }
    }
    MockHttpSession start() {
        var request = new MockHttpServletRequest();
        service.startAfterPasswordVerified(request, "mfa-admin");
        return (MockHttpSession) request.getSession(false);
    }
    String code() throws Exception {
        String mime = Files.readString(mail.get(mail.size() - 1), StandardCharsets.UTF_8);
        String body = new String(Base64.getMimeDecoder().decode(mime.substring(mime.indexOf("\r\n\r\n") + 4)), StandardCharsets.UTF_8);
        var match = java.util.regex.Pattern.compile("인증번호: ([0-9]{6})").matcher(body);
        if (!match.find()) throw new AssertionError("인증 메일 본문이 없습니다.");
        return match.group(1);
    }
    String hash() { return db.queryForObject("SELECT mfa_code_hash FROM TB_SY_USER WHERE user_id='mfa-admin'", String.class); }
    void verifyCode(MockHttpSession session, String code, int expected) throws Exception {
        mvc.perform(post("/api/common/mfa/verify").session(session).contentType("application/json")
            .content(json.writeValueAsString(Map.of("code", code)))).andExpect(status().is(expected));
    }
    @Test void resendReplacesPersistedHashAndCompletionWritesExactlyOneTemporaryLogin() throws Exception {
        var session = start(); String previous = code(); String oldHash = hash();
        assertThat(new BCryptPasswordEncoder().matches(previous, oldHash)).isTrue();
        now = now.plusSeconds(60);
        mvc.perform(post("/api/common/mfa/resend").session(session)).andExpect(status().isOk());
        String latest = code();
        assertThat(hash()).isNotEqualTo(oldHash);
        assertThat(new BCryptPasswordEncoder().matches(latest, hash())).isTrue();
        verifyCode(session, previous, 400);
        mvc.perform(post("/api/common/mfa/verify").session(session).contentType("application/json")
            .content(json.writeValueAsString(Map.of("code", latest))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.nextScreenId").value("UI-40"));
        assertThat(hash()).isNull();
        assertThat(db.queryForObject("SELECT pwd_err_cnt FROM TB_SY_USER WHERE user_id='mfa-admin'", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT last_login_dttm FROM TB_SY_USER WHERE user_id='mfa-admin'", java.sql.Timestamp.class).toInstant()).isEqualTo(now);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE user_id='mfa-admin' AND act_type_cd='LOGIN' AND trgt_desc='임시 비밀번호 로그인'", Long.class)).isEqualTo(1L);
        verify(completion).establish(any(), eq("mfa-admin"), eq(true));
        verifyCode(session, latest, 401);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE act_type_cd='LOGIN'", Long.class)).isEqualTo(1L);
    }
    @Test void wrongCodesCommitButDoNotLockAccountOrWriteLogin() throws Exception {
        var session = start(); String incorrect = code().equals("000000") ? "000001" : "000000";
        for (int attempt = 0; attempt < 5; attempt++) verifyCode(session, incorrect, 400);
        assertThat(db.queryForObject("SELECT mfa_err_cnt FROM TB_SY_USER WHERE user_id='mfa-admin'", Integer.class)).isEqualTo(5);
        assertThat(db.queryForObject("SELECT user_stat_cd FROM TB_SY_USER WHERE user_id='mfa-admin'", String.class)).isEqualTo("USE");
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE act_type_cd='LOGIN'", Long.class)).isZero();
        verifyNoInteractions(completion);
    }
    @Test
    @DisplayName("로그인(DB 행 잠금 먼저)과 같은 계정 인증번호 확인이 동시에 와도 둘 다 끝난다")
    void concurrentLoginAndVerifyDoNotDeadlock() throws Exception {
        var waiting = start(); String code = code();
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        var locked = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            // 로그인과 같은 순서: 트랜잭션 안에서 계정 행을 먼저 잠그고, 잠근 채 인증번호 단계를 시작한다.
            var login = pool.submit(() -> tx.execute(status -> {
                mfaMapper.lockAccount("mfa-admin");
                locked.countDown();
                try { Thread.sleep(500); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
                return service.startAfterPasswordVerified(new MockHttpServletRequest(), "mfa-admin");
            }));
            assertThat(locked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var verify = pool.submit(() -> {
                var request = new MockHttpServletRequest(); request.setSession(waiting);
                try { return service.postCommonMfaVerify(request, code).success(); }
                catch (MfaFailure failure) { return false; }
            });
            assertThat(login.get(20, java.util.concurrent.TimeUnit.SECONDS)).isNotNull();
            verify.get(20, java.util.concurrent.TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }
    @Test void wrongCodeCounterIsCappedForTwoDigitColumn() throws Exception {
        var session = start(); String incorrect = code().equals("000000") ? "000001" : "000000";
        db.update("UPDATE TB_SY_USER SET mfa_err_cnt=99 WHERE user_id='mfa-admin'");
        verifyCode(session, incorrect, 400);
        assertThat(db.queryForObject("SELECT mfa_err_cnt FROM TB_SY_USER WHERE user_id='mfa-admin'", Integer.class)).isEqualTo(99);
    }
    @Test void newerDeviceCannotBeCompletedByPreviousPendingSession() throws Exception {
        var old = start(); var latest = start(); String code = code();
        verifyCode(old, code, 401);
        verifyNoInteractions(completion);
        verifyCode(latest, code, 200);
    }
    @Test void failedResendLeavesPersistedChallengeAndLoginHistoryUnchanged() throws Exception {
        var session = start();
        String previous = code();
        String previousHash = hash();
        var expiry = db.queryForObject(
            "SELECT mfa_expr_dttm FROM TB_SY_USER WHERE user_id='mfa-admin'", java.sql.Timestamp.class);
        Object pending = session.getAttribute(CommonMfaService.PENDING_ATTRIBUTE);
        now = now.plusSeconds(60);
        doThrow(new IllegalStateException("delivery unavailable")).when(mailbox).deliver(anyList(), anyString());
        mvc.perform(post("/api/common/mfa/resend").session(session))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(CommonMfaService.DELIVERY_FAILURE));
        assertThat(hash()).isEqualTo(previousHash);
        assertThat(db.queryForObject(
            "SELECT mfa_expr_dttm FROM TB_SY_USER WHERE user_id='mfa-admin'", java.sql.Timestamp.class))
            .isEqualTo(expiry);
        assertThat(session.getAttribute(CommonMfaService.PENDING_ATTRIBUTE)).isSameAs(pending);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE act_type_cd='LOGIN'", Long.class))
            .isZero();
        verifyNoInteractions(completion);
        verifyCode(session, previous, 200);
    }
    @Test void failedNewDeviceDeliveryDoesNotReplacePersistedChallenge() throws Exception {
        var previous = start();
        String previousCode = code();
        String previousHash = hash();
        doThrow(new IllegalStateException("delivery unavailable")).when(mailbox).deliver(anyList(), anyString());
        var request = new MockHttpServletRequest();
        assertThat(service.startAfterPasswordVerified(request, "mfa-admin").deliveryError())
            .isEqualTo(CommonMfaService.DELIVERY_FAILURE);
        assertThat(hash()).isEqualTo(previousHash);
        verifyCode((MockHttpSession) request.getSession(false), previousCode, 400);
        verifyNoInteractions(completion);
        verifyCode(previous, previousCode, 200);
    }
}
