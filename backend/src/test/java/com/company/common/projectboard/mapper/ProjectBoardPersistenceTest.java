package com.company.common.projectboard.mapper;

import com.company.common.projectboard.dto.CommonProjectBoardContracts.Filter;
import com.company.common.schema.SchemaPreparation;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class ProjectBoardPersistenceTest {
    DataSource source;
    SqlSessionFactory sessions;
    JdbcTemplate db;
    String schema;
    final LocalDate today = LocalDate.of(2026, 11, 15);

    @BeforeEach void setup() throws Exception {
        schema = "projectboard_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        try (var connection = DriverManager.getConnection(url, user, password);
                var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        var dataSource = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        dataSource.setDriverProperties(properties);
        source = dataSource;
        var configuration = new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment(
            "postgresql", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), source));
        new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration)).prepare();
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setMapperLocations(new ClassPathResource("mapper/projectboard/ProjectBoardMapper.xml"));
        var mapping = new org.apache.ibatis.session.Configuration();
        mapping.setMapUnderscoreToCamelCase(false);
        mapping.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
        factory.setConfiguration(mapping);
        sessions = factory.getObject();
        db = new JdbcTemplate(source);
    }

    @AfterEach void cleanup() throws Exception {
        if (source != null) try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    void project(String id, String status, String end) {
        db.update("""
            INSERT INTO TB_PJ_PROJ (PROJ_ID, PROJ_NM, CTRT_TYPE_CD, PROJ_STAT_CD, CTRT_BGN_DT,
              CTRT_END_DT, CTRT_AMT, BDGT_AMT, TEAM_EXPS_AMT, PROJ_EXPS_AMT, REG_USER_ID)
            VALUES (?, ?, 'TK', ?, '2026-10-01', ?::date, 1000, 800, 0, 0, 'admin')
            """, id, "조회 프로젝트 " + id, status, end);
    }

    @Test void realPostgresMapsWrittenDateLatestNonNullProgressAndStrictLifecycle() throws Exception {
        project("active", "ING", "2026-12-31");
        project("pre", "PRE", "2026-12-31");
        project("lost", "LOSE", "2026-12-31");
        project("ended", "ING", "2026-11-14");
        db.update("""
            INSERT INTO TB_PJ_WEEK_MEMO (PROJ_ID, MEMO_SN, WRT_DT, ACTL_PRGRS_RT, REG_USER_ID, MOD_DTTM)
            VALUES ('active', 1, '2026-10-31', 37, 'admin', CURRENT_TIMESTAMP),
              ('active', 2, '2026-10-31', 42, 'admin', NULL),
              ('active', 3, '2026-11-01', NULL, 'admin', NULL)
            """);
        try (var session = sessions.openSession()) {
            var mapper = session.getMapper(ProjectBoardMapper.class);
            var rows = mapper.sources(new Filter(null, null, null), today, "202611");
            assertThat(rows).extracting(row -> row.projectId).containsExactly("active", "pre");
            var active = rows.get(0);
            assertThat(active.latestMemoDate).isEqualTo(LocalDate.of(2026, 11, 1));
            assertThat(active.actualProgress).isEqualByComparingTo("42");
            assertThat(active.budgetAmount).isEqualByComparingTo("800");
            assertThat(active.pmUserId).isNull();
            assertThat(active.pmName).isNull();
        }
    }

    @Test void costSourceIncludesOnlyCurrentMonthlyLaborDirectAndAdditionalExpense() throws Exception {
        project("cost", "ING", "2026-12-31");
        db.update("""
            INSERT INTO TB_CO_MON_COST (COST_YM, DEV_ID, PROJ_ID, ASGN_ID, ASGN_MM, FREE_MM,
              SALES_AMT, LABOR_ALLOC_AMT, TEAM_EXPS_AMT, PROJ_EXPS_AMT, DRCT_EXPS_AMT, COST_CLS_CD, REG_USER_ID)
            VALUES ('202611', 'cost-dev', 'cost', 'cost-asgn', 1, 0, 0, 100, 900, 900, 20, 'PRE', 'admin'),
              ('202612', 'cost-dev', 'cost', 'cost-asgn', 1, 0, 0, 1000, 900, 900, 20, 'PRE', 'admin')
            """);
        db.update("""
            INSERT INTO TB_PJ_DRCT_EXPS (PROJ_ID, EXPS_YM, EXPS_SN, DRCT_EXPS_ITEM_CD, EXPS_AMT, REG_USER_ID)
            VALUES ('cost', '202611', 1, 'ETC', 30, 'admin'), ('cost', '202612', 1, 'ETC', 300, 'admin')
            """);
        try (var session = sessions.openSession()) {
            assertThat(session.getMapper(ProjectBoardMapper.class)
                .sources(new Filter(null, "TK", null), today, "202611").get(0).costAmount)
                .isEqualByComparingTo("150");
        }
    }

    @Test void pmIdentityAndNameComeFromTheSameLowestAssignedPmAccount() throws Exception {
        project("pm-project", "ING", "2026-12-31");
        String encoded = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4)
            .encode(UUID.randomUUID().toString());
        for (String user : java.util.List.of("board-z", "board-a", "board-other")) {
            db.update("""
                INSERT INTO TB_SY_USER (USER_ID, USER_NM, EMAIL_ADDR, PWD_HASH, PWD_ERR_CNT,
                  USER_STAT_CD, MFA_ERR_CNT, REG_USER_ID)
                VALUES (?, ?, ?, ?, 0, 'ACTIVE', 0, 'admin')
                """, user, "이름 " + user, user + "@example.invalid", encoded);
            db.update("""
                INSERT INTO TB_SY_USER_SCOPE (USER_ID, SCOPE_SN, SCOPE_TYPE_CD, PROJ_ID, REG_USER_ID)
                VALUES (?, 1, 'PROJ', 'pm-project', 'admin')
                """, user);
        }
        for (String user : java.util.List.of("board-z", "board-a")) {
            db.update("INSERT INTO TB_SY_USER_ROLE (USER_ID, ROLE_ID, REG_USER_ID) VALUES (?, 'PM', 'admin')", user);
        }
        try (var session = sessions.openSession()) {
            var row = session.getMapper(ProjectBoardMapper.class)
                .sources(new Filter(null, null, null), today, "202611").get(0);
            assertThat(row.pmUserId).isEqualTo("board-a");
            assertThat(row.pmName).isEqualTo("이름 board-a");
        }
    }

    @Test void issuesKeepNumericStringIdentityAndCloseStatusAndDueDate() throws Exception {
        project("issues", "ING", "2026-12-31");
        db.update("""
            INSERT INTO TB_PJ_ISSUE (PROJ_ID, ISSUE_TTL, IMPR_CD, DDLN_DT, ISSUE_STAT_CD, REG_USER_ID)
            VALUES ('issues', '열린 이슈', 'HIGH', '2026-11-15', 'OPEN', 'admin'),
              ('issues', '종료 이슈', 'HIGH', '2026-11-14', 'CLOSE', 'admin')
            """);
        try (var session = sessions.openSession()) {
            var rows = session.getMapper(ProjectBoardMapper.class).issues("issues");
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).issueId()).matches("[0-9]+");
            assertThat(rows.get(0).dueDate()).isEqualTo(today);
            assertThat(rows.get(1).statusCode()).isEqualTo("CLOSE");
        }
    }
}
