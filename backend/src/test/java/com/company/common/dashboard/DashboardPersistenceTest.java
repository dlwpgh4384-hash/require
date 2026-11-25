package com.company.common.dashboard;

import com.company.common.dashboard.mapper.DashboardMapper;
import com.company.common.schema.SchemaPreparation;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Properties;
import java.util.UUID;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class DashboardPersistenceTest {
    String schema;
    JdbcTemplate db;
    SqlSessionFactory factory;
    @BeforeEach void setup() throws Exception {
        schema = "dashboard_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("NAVIGATION_TEST_DB_URL"), user = System.getenv("NAVIGATION_TEST_DB_USER"), password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        try (var connection = DriverManager.getConnection(url, user, password); var statement = connection.createStatement()) {
            assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
            statement.execute("CREATE SCHEMA " + schema);
        }
        var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new Properties(); properties.setProperty("currentSchema", schema); source.setDriverProperties(properties);
        var configuration = new Configuration(new Environment("postgresql", new JdbcTransactionFactory(), source));
        new SchemaPreparation(new SqlSessionFactoryBuilder().build(configuration)).prepare();
        var builder = new SqlSessionFactoryBean(); builder.setDataSource(source);
        builder.setMapperLocations(new ClassPathResource("mapper/dashboard/DashboardMapper.xml"));
        factory = builder.getObject(); db = new JdbcTemplate(source);
        db.update("INSERT INTO TB_BS_PART(part_id,part_nm,reg_user_id) VALUES('dash-part','대시보드 독립 시험','admin')");
        db.update("INSERT INTO TB_PJ_PROJ(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,team_exps_amt,proj_exps_amt,reg_user_id) VALUES('dash-project','독립 시험','MO','ING',DATE '2026-01-01',DATE '2027-12-31',0,0,0,'admin')");
    }
    @AfterEach void cleanup() throws Exception {
        if (schema == null) return;
        try (var connection = DriverManager.getConnection(System.getenv("NAVIGATION_TEST_DB_URL"),
                System.getenv("NAVIGATION_TEST_DB_USER"), System.getenv("NAVIGATION_TEST_DB_PASSWORD")); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
    void person(String id, String direct, LocalDate hire, LocalDate retirement) {
        db.update("INSERT INTO TB_HR_DEV(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,jncmp_dt,retire_dt,reg_user_id) VALUES(?,?,?,'EMP','BE',?,'dash-part','WORK',?,?,'admin')",
            id, id, id, direct, hire, retirement);
    }
    @Test void selectedMonthUsesDistinctDirectPeopleAndRetirementMonthWithoutWrites() {
        person("dash-active", "DRCT", LocalDate.of(2026, 1, 1), null);
        person("dash-retiring", "DRCT", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 1));
        person("dash-retired", "DRCT", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 31));
        person("dash-future", "DRCT", LocalDate.of(2026, 12, 1), null);
        for (String assignment : new String[]{"dash-a", "dash-b"}) {
            db.update("INSERT INTO TB_PJ_ASGN(asgn_id,proj_id,dev_id,job_cd,input_bgn_dt,input_end_dt,ctrt_mm,input_type_cd,ext_cnt,asgn_stat_cd,reg_user_id) VALUES(?,'dash-project','dash-active','BE',DATE '2026-01-01',DATE '2026-12-31',0.5,'PAID',0,'CNFM','admin')", assignment);
            db.update("INSERT INTO TB_PJ_ASGN_MON(asgn_id,input_ym,dev_id,proj_id,input_day_cnt,mon_mm,aply_uprc,sales_amt,reg_user_id) VALUES(?,'202611','dash-active','dash-project',20,0.5,0,0,'admin')", assignment);
        }
        var before = db.queryForList("SELECT * FROM TB_PJ_ASGN_MON ORDER BY asgn_id");
        try (var session = factory.openSession()) {
            var row = session.getMapper(DashboardMapper.class).utilization("202611", LocalDate.of(2026,11,1), LocalDate.of(2026,11,30));
            assertThat(row.totalPeople()).isEqualTo(2);
            assertThat(row.assignedPeople()).isEqualTo(1);
        }
        assertThat(db.queryForList("SELECT * FROM TB_PJ_ASGN_MON ORDER BY asgn_id")).isEqualTo(before);
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE scrn_id='UI-16'", Integer.class)).isZero();
    }
}
