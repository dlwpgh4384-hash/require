package com.company.common.session;

import com.company.common.session.mapper.SessionMapper;
import com.company.common.session.controller.CommonSessionController;
import com.company.common.session.controller.SessionResponseAdvice;
import com.company.common.session.service.SessionPolicy;
import com.company.common.support.service.BusinessCalendar;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.company.common.support.controller.OpenApiResponseContract.response;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class SessionPersistenceTest {
    String schema;
    UnpooledDataSource source;
    SessionMapper mapper;
    JdbcTemplate db;
    MockMvc mvc;
    MockHttpSession session;
    BusinessCalendar calendar;
    final Instant now = Instant.parse("2026-01-02T00:00:00Z");

    @BeforeEach void setup() throws Exception {
        schema = "session_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        try (var connection = DriverManager.getConnection(url, user, password);
            var statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
            statement.execute("CREATE SCHEMA " + schema);
            statement.execute("SET search_path TO " + schema);
            statement.execute(new ClassPathResource("db/migration/V1__navigation_schema.sql")
                .getContentAsString(StandardCharsets.UTF_8));
        }
        source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        source.setDriverProperties(properties);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setMapperLocations(new ClassPathResource("mapper/SessionMapper.xml"));
        var config = new org.apache.ibatis.session.Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        factory.setConfiguration(config);
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(SessionMapper.class);
        db = new JdbcTemplate(source);
        db.update("INSERT INTO TB_SY_USER(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,mfa_err_cnt,"
            + "user_stat_cd,reg_user_id) VALUES ('reader','세션 시험','reader@example.invalid',?,0,0,'USE','reader')",
            new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(10)
                .encode(UUID.randomUUID().toString()));
        for (String month : new String[] {"202501", "202601", "202701"}) {
            db.update("INSERT INTO TB_SY_OPER_STD(std_cd,aply_bgn_ym,std_nm,std_val,unit_nm,reg_user_id) "
                + "VALUES ('SESSION_MIN',?,'세션 시간',?,'분','reader')", month,
                "202501".equals(month) ? 30 : "202601".equals(month) ? 12 : 60);
        }
        calendar = mock(BusinessCalendar.class);
        when(calendar.now()).thenReturn(now);
        when(calendar.currentMonth()).thenReturn(YearMonth.of(2026, 1));
        when(calendar.toDatabaseMonth(YearMonth.of(2026, 1))).thenReturn("202601");
        var policy = new SessionPolicy(mapper, calendar);
        session = new MockHttpSession();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("reader", null, List.of()));
        policy.initialize(session, "reader");
        mvc = MockMvcBuilders.standaloneSetup(new CommonSessionController(policy))
            .setControllerAdvice(new SessionResponseAdvice(new ObjectMapper().findAndRegisterModules())).build();
    }

    @AfterEach void cleanup() throws Exception {
        SecurityContextHolder.clearContext();
        if (source != null) {
            try (var connection = source.getConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    @Test void latestEffectiveStandardExcludesFutureMonthAndMaterializesNumeric() {
        assertThat(mapper.selectSessionMinutes("202512")).isEqualByComparingTo(BigDecimal.valueOf(30));
        assertThat(mapper.selectSessionMinutes("202601")).isEqualByComparingTo(BigDecimal.valueOf(12));
        assertThat(mapper.selectSessionMinutes("202401")).isNull();
    }

    @Test void accountStopIsVisibleImmediatelyWithoutAnyRoleAssignment() {
        assertThat(mapper.selectAccountStatus("reader")).isEqualTo("USE");
        assertThat(mapper.selectTemporaryPassword("reader")).isEqualTo("N");
        db.update("UPDATE TB_SY_USER SET user_stat_cd='STOP' WHERE user_id='reader'");
        assertThat(mapper.selectAccountStatus("reader")).isEqualTo("STOP");
        assertThat(mapper.selectAccountStatus("absent")).isNull();
    }

    @Test void remainingTraversesControllerServiceMapperAndDatabaseWithoutExtendingDeadline() throws Exception {
        when(calendar.now()).thenReturn(now.plusSeconds(60));
        mvc.perform(get("/api/common/session/remaining").session(session))
            .andExpect(status().isOk()).andExpect(response("GET", "/api/common/session/remaining"))
            .andExpect(jsonPath("$.data.remainingSeconds").value(660))
            .andExpect(jsonPath("$.data.sessionMinutes").value(12));
        assertThat(db.queryForObject("SELECT std_val FROM TB_SY_OPER_STD WHERE std_cd='SESSION_MIN' AND aply_bgn_ym='202601'", BigDecimal.class))
            .isEqualByComparingTo("12");
    }

    @Test void extensionReadsChangedDatabaseStandardAndStoppedAccountCannotExtend() throws Exception {
        db.update("UPDATE TB_SY_OPER_STD SET std_val=7 WHERE std_cd='SESSION_MIN' AND aply_bgn_ym='202601'");
        when(calendar.now()).thenReturn(now.plusSeconds(60));
        mvc.perform(post("/api/common/session/extend").session(session))
            .andExpect(status().isOk()).andExpect(response("POST", "/api/common/session/extend"))
            .andExpect(jsonPath("$.data.remainingSeconds").value(420))
            .andExpect(jsonPath("$.data.sessionMinutes").value(7));
        assertThat(session.getMaxInactiveInterval()).isEqualTo(420);
        db.update("UPDATE TB_SY_USER SET user_stat_cd='STOP' WHERE user_id='reader'");
        mvc.perform(post("/api/common/session/extend").session(session))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        assertThat(session.isInvalid()).isTrue();
        assertThat(db.queryForObject("SELECT user_stat_cd FROM TB_SY_USER WHERE user_id='reader'", String.class)).isEqualTo("STOP");
    }
}
