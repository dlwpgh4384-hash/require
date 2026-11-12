package com.company.common.service;

import com.company.common.controller.CommonUserController;
import com.company.common.controller.UserExceptionHandler;
import com.company.common.mapper.UserMapper;

import com.company.common.mapper.UserOptionsMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.company.common.dto.UserContracts.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(CommonUserController.class)
@ContextConfiguration(classes = {CommonUserController.class, CommonUserService.class, TemporaryUserPassword.class,
    UserExceptionHandler.class, ExcelExportService.class, UserManagementIntegrationTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired OwnedSchema owned;
    @Autowired CommonUserService service;
    JdbcTemplate db;
    record OwnedSchema(String name) {}
    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean OwnedSchema ownedSchema() { return new OwnedSchema("users_" + UUID.randomUUID().toString().replace("-", "")); }
        @Bean DataSource dataSource(OwnedSchema owned) throws Exception {
            String url = System.getenv("NAVIGATION_TEST_DB_URL");
            String user = System.getenv("NAVIGATION_TEST_DB_USER");
            String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
                statement.execute("CREATE SCHEMA " + owned.name());
                statement.execute("SET search_path TO " + owned.name());
                for (String resource : List.of("db/migration/V1__navigation_schema.sql", "db/migration/V4__user_management_seed.sql"))
                    statement.execute(new ClassPathResource(resource).getContentAsString(StandardCharsets.UTF_8));
            }
            var data = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new java.util.Properties(); properties.setProperty("currentSchema", owned.name());
            data.setDriverProperties(properties); return data;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/UserMapper.xml"), new ClassPathResource("mapper/UserOptionsMapper.xml"));
            var config = new org.apache.ibatis.session.Configuration(); config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean UserMapper userMapper(SqlSessionTemplate sessions) { return sessions.getMapper(UserMapper.class); }
        @Bean UserOptionsMapper userOptionsMapper(SqlSessionTemplate sessions) { return sessions.getMapper(UserOptionsMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void setup() {
        db = new JdbcTemplate(source);
        db.update("INSERT INTO TB_SY_USER(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,mfa_err_cnt,user_stat_cd,reg_user_id) VALUES (?,?,?,?,0,0,'USE','admin')",
            "admin", "합성 관리자", "admin@example.invalid", new TemporaryUserPassword().hashAndDeliver("admin@example.invalid"));
        db.update("INSERT INTO TB_SY_ROLE(role_id,role_nm,data_scope_cd,sort_ord,reg_user_id) VALUES ('PROJECT_READER','시험 프로젝트 조회','PROJ',90,'admin')");
        db.update("INSERT INTO TB_PJ_PROJ(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES ('owned-project','합성 프로젝트','TEST','TEST',CURRENT_DATE,CURRENT_DATE,0,0,0,'admin')");
    }
    @AfterEach void cleanup() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) { statement.execute("DROP SCHEMA " + owned.name() + " CASCADE"); }
    }
    private String create(String id, String role, List<String> projects) throws Exception {
        return json.writeValueAsString(Map.of("userId",id,"name","시험 " + id,"email",id + "@example.invalid",
            "roleCds",List.of(role),"headquarters",false,"projectIds",projects));
    }
    private String token(String id) throws Exception {
        var response = mvc.perform(get("/api/common/users/" + id)).andExpect(status().isOk()).andReturn();
        var value = json.readTree(response.getResponse().getContentAsString()).path("data").get("modDttm");
        return value.isNull() ? null : value.textValue();
    }
    private String version(String id) throws Exception {
        var body = json.createObjectNode(); String token = token(id);
        if (token == null) body.putNull("modDttm"); else body.put("modDttm", token);
        return body.toString();
    }
    private long count(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    @Test void registrationResetUnlockAndAccessLogsUseRealSqlAndPreserveMicroseconds() throws Exception {
        mvc.perform(post("/api/common/users").contentType("application/json").content(create("test", "PM", List.of("owned-project"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.account.projectIds[0]").value("owned-project"));
        assertThat(count("SELECT count(*) FROM TB_SY_USER_ROLE WHERE user_id='test' AND role_id='PM'")).isEqualTo(1);
        var values = db.queryForMap("SELECT pwd_hash,temp_pwd_yn,reg_user_id FROM TB_SY_USER WHERE user_id='test'");
        assertThat(values.get("pwd_hash").toString()).hasSize(60).startsWith("$2");
        assertThat(values.get("temp_pwd_yn")).isEqualTo("Y"); assertThat(values.get("reg_user_id")).isEqualTo("admin");
        assertThat(count("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE user_id='test' AND act_type_cd='USER_REG' AND reg_user_id='admin' AND scrn_id='UI-23'")).isEqualTo(1);
        mvc.perform(get("/api/common/users/test/access-logs")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        String before = token("test");
        mvc.perform(post("/api/common/users/test/password-reset").contentType("application/json").content(version("test")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.tempPwdYn").value("Y"))
            .andExpect(jsonPath("$.data.password").doesNotExist());
        assertThat(db.queryForObject("SELECT pwd_hash FROM TB_SY_USER WHERE user_id='test'", String.class)).isNotEqualTo(values.get("pwd_hash"));
        assertThat(token("test")).isNotEqualTo(before);
        assertThat(db.queryForObject("SELECT mod_user_id FROM TB_SY_USER WHERE user_id='test'", String.class)).isEqualTo("admin");
        assertThat(count("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE user_id='test'")).isEqualTo(1);
        db.update("UPDATE TB_SY_USER SET user_stat_cd='LOCK',pwd_err_cnt=5 WHERE user_id='test'");
        String lockedVersion = token("test");
        mvc.perform(post("/api/common/users/test/unlock").contentType("application/json").content(version("test")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.userStatCd").value("USE"))
            .andExpect(jsonPath("$.data.pwdErrCnt").value(0));
        assertThat(token("test")).isNotEqualTo(lockedVersion);
        assertThat(db.queryForObject("SELECT user_stat_cd FROM TB_SY_USER WHERE user_id='test'", String.class)).isEqualTo("USE");
        assertThat(count("SELECT pwd_err_cnt FROM TB_SY_USER WHERE user_id='test'")).isZero();
        assertThat(count("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE user_id='test'")).isEqualTo(1);
        assertThat(db.queryForObject("SELECT mod_user_id FROM TB_SY_USER WHERE user_id='test'", String.class)).isEqualTo("admin");
    }
    @Test void accessLogWithMissingTargetStillReturnsContractString() throws Exception {
        db.update("INSERT INTO TB_SY_ACCS_LOG(user_id,act_type_cd,act_dttm,reg_user_id) VALUES ('admin','LOGIN',CURRENT_TIMESTAMP,'admin')");
        mvc.perform(get("/api/common/users/admin/access-logs"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.items[0].target").value(""));
        assertThat(count("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE user_id='admin' AND trgt_desc IS NULL")).isEqualTo(1);
    }
    @Test void stopAndResumePreserveHistoryAndDoNotRestoreProjectScope() throws Exception {
        mvc.perform(post("/api/common/users").contentType("application/json").content(create("test", "PM", List.of("owned-project")))).andExpect(status().isOk());
        var body = json.createObjectNode();
        body.putArray("users").addObject().put("userId","test").put("modDttm",token("test"));
        body.putArray("confirmedProjectIds").add("owned-project");
        String activeVersion = token("test");
        mvc.perform(post("/api/common/users/stop").contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.processedUserIds[0]").value("test"))
            .andExpect(jsonPath("$.data.unprocessedCount").value(0));
        assertThat(db.queryForObject("SELECT user_stat_cd FROM TB_SY_USER WHERE user_id='test'", String.class)).isEqualTo("STOP");
        assertThat(db.queryForObject("SELECT mod_user_id FROM TB_SY_USER WHERE user_id='test'", String.class)).isEqualTo("admin");
        String stoppedVersion = token("test");
        assertThat(stoppedVersion).isNotEqualTo(activeVersion);
        assertThat(count("SELECT count(*) FROM TB_SY_USER_SCOPE WHERE user_id='test' AND scope_type_cd='PROJ'")).isZero();
        mvc.perform(post("/api/common/users/test/resume").contentType("application/json").content(version("test")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.userStatCd").value("USE"))
            .andExpect(jsonPath("$.data.projectIds").isEmpty());
        assertThat(db.queryForObject("SELECT user_stat_cd FROM TB_SY_USER WHERE user_id='test'", String.class)).isEqualTo("USE");
        assertThat(db.queryForObject("SELECT mod_user_id FROM TB_SY_USER WHERE user_id='test'", String.class)).isEqualTo("admin");
        assertThat(token("test")).isNotEqualTo(stoppedVersion);
        assertThat(count("SELECT count(*) FROM TB_SY_USER_SCOPE WHERE user_id='test' AND scope_type_cd='PROJ'")).isZero();
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key='test' AND chg_type_cd IN ('STOP','RESUME') AND reg_user_id='admin' AND reg_dttm IS NOT NULL")).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key='test' AND chg_item_nm='담당 프로젝트' AND aft_val='–'")).isEqualTo(1);
    }
    @Test void sideEffectRoleUpdatePersistsModUserIdModDttmAndTbSyAccsLog() throws Exception {
        String contract = new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8);
        assertThat(contract).contains("operationId: putCommonUser");
        mvc.perform(post("/api/common/users").contentType("application/json").content(create("test", "PM", List.of("owned-project"))))
            .andExpect(status().isOk());
        String before = token("test");
        var body = json.createObjectNode();
        body.put("name", "합성 계정");
        body.put("email", "test@example.invalid");
        body.putArray("roleCds").add("EXEC");
        body.put("headquarters", false);
        body.putArray("projectIds");
        body.putArray("confirmedProjectIds").add("owned-project");
        body.put("modDttm", before);
        mvc.perform(put("/api/common/users/test").contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.account.roleCds[0]").value("EXEC"))
            .andExpect(jsonPath("$.data.account.projectIds").isEmpty());
        assertThat(token("test")).isNotEqualTo(before);
        assertThat(db.queryForObject("SELECT mod_user_id FROM TB_SY_USER WHERE user_id='test'", String.class)).isEqualTo("admin");
        assertThat(db.queryForList("SELECT role_id FROM TB_SY_USER_ROLE WHERE user_id='test'", String.class)).containsExactly("EXEC");
        assertThat(count("SELECT count(*) FROM TB_SY_USER_SCOPE WHERE user_id='test' AND scope_type_cd='PROJ'")).isZero();
        assertThat(count("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE user_id='test' AND act_type_cd='AUTH_CHG' AND reg_user_id='admin' AND act_dttm IS NOT NULL")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key='test' AND chg_item_nm IN ('역할','담당 프로젝트') AND reg_user_id='admin' AND reg_dttm IS NOT NULL")).isEqualTo(2);
    }
    @Test void sqlFailureRollsBackAccountRolesScopesAndLogsTogether() throws Exception {
        db.execute("CREATE FUNCTION reject_registration() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test transaction rollback'; END $$");
        db.execute("CREATE TRIGGER reject_registration BEFORE INSERT ON TB_SY_ACCS_LOG FOR EACH ROW EXECUTE FUNCTION reject_registration()");
        mvc.perform(post("/api/common/users").contentType("application/json").content(create("test", "PM", List.of("owned-project"))))
            .andExpect(status().isInternalServerError());
        assertThat(count("SELECT count(*) FROM TB_SY_USER WHERE user_id='test'")).isZero();
        assertThat(count("SELECT count(*) FROM TB_SY_USER_ROLE WHERE user_id='test'")).isZero();
        assertThat(count("SELECT count(*) FROM TB_SY_USER_SCOPE WHERE user_id='test'")).isZero();
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST")).isZero();
    }
    @Test void concurrentAdminRemovalSerializesAndLeavesExactlyOneUseAdmin() throws Exception {
        mvc.perform(post("/api/common/users").contentType("application/json").content(create("secondadmin", "ADMIN", List.of()))).andExpect(status().isOk());
        var first = service.getCommonUser("admin"); var second = service.getCommonUser("secondadmin");
        var gate = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(first,second).stream().map(account -> executor.submit(() -> {
                gate.await();
                try {
                    service.putCommonUser(account.userId(), new Update(account.name(), account.email(), List.of("EXEC"), false, List.of(),
                        account.modDttm() == null ? null : java.time.OffsetDateTime.parse(account.modDttm()).toInstant(), List.of()));
                    return true;
                } catch (UserFailure failure) { assertThat(failure.getStatus()).isEqualTo(400); return false; }
            })).toList();
            gate.countDown();
            int succeeded = 0;
            for (var future : futures) if (future.get(20, java.util.concurrent.TimeUnit.SECONDS)) succeeded++;
            assertThat(succeeded).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM TB_SY_USER u JOIN TB_SY_USER_ROLE r ON r.user_id=u.user_id WHERE u.user_stat_cd='USE' AND r.role_id='ADMIN'")).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
    @Test void concurrentPmAssignmentsTransferRatherThanCreateTwoOwners() throws Exception {
        var gate = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of("pmone","pmtwo").stream().map(id -> executor.submit(() -> {
                gate.await(); return service.putCommonUsers(new Create(id,"시험 " + id,id + "@example.invalid",List.of("PM"),false,List.of("owned-project")));
            })).toList();
            gate.countDown(); for (var future : futures) future.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(count("SELECT count(*) FROM TB_SY_USER_SCOPE s JOIN TB_SY_USER_ROLE r ON r.user_id=s.user_id WHERE s.scope_type_cd='PROJ' AND s.proj_id='owned-project' AND r.role_id='PM'")).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
}
