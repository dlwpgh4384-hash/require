package com.company.common.schema;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class PostgreSqlPreparationTest {
    private Connection database;
    private String schema;
    private SchemaPreparation preparation;

    @BeforeEach
    void independentSchema() throws Exception {
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        database = DriverManager.getConnection(url, user, password);
        assertThat(database.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
        schema = "navigation_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET search_path TO " + schema);
        var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        source.setDriverProperties(properties);
        var config = new Configuration(new Environment("postgresql", new JdbcTransactionFactory(), source));
        preparation = new SchemaPreparation(new SqlSessionFactoryBuilder().build(config));
    }

    @AfterEach
    void removeOwnedSchema() throws Exception {
        if (database != null) {
            try {
                if (schema != null) execute("DROP SCHEMA " + schema + " CASCADE");
            } finally { database.close(); }
        }
    }

    @Test
    void createsPreparedTablesAndOnlyBaseSeedRowsWithoutPhysicalForeignKeys() throws Exception {
        preparation.prepare();
        assertThat(number("SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_type='BASE TABLE'")).isEqualTo(19);
        assertThat(number("SELECT count(*) FROM tb_sy_menu")).isEqualTo(40);
        assertThat(number("SELECT count(*) FROM tb_sy_menu WHERE scrn_id IS NULL")).isEqualTo(10);
        assertThat(number("SELECT count(*) FROM tb_sy_menu WHERE scrn_id IS NOT NULL")).isEqualTo(30);
        assertThat(number("SELECT count(*) FROM tb_sy_user WHERE user_id='admin'")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM tb_sy_code_grp")).isEqualTo(64);
        assertThat(number("SELECT count(*) FROM tb_sy_code WHERE code_grp_id='PREF_TYPE'")).isEqualTo(8);
        assertThat(number("SELECT count(*) FROM tb_sy_oper_std")).isEqualTo(43);
        assertThat(text("SELECT std_val::bigint::text FROM tb_sy_oper_std WHERE std_cd='CASH_BASE_YM'")).isEqualTo("202601");
        assertThat(number("SELECT count(*) FROM pg_constraint WHERE connamespace=current_schema()::regnamespace AND contype='f'")).isZero();
        assertThat(number("SELECT nextval('sq_tb_sy_noti')")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM tb_sy_role")).isEqualTo(5);
        assertThat(number("SELECT count(*) FROM tb_sy_user_role WHERE user_id='admin' AND role_id='ADMIN'")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM tb_bs_part WHERE part_id='HQ' AND hq_yn='Y'")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM tb_sy_role_menu_auth")).isEqualTo(150);
        try (var rows = database.createStatement().executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema=current_schema() AND table_name NOT IN ('tb_sy_menu','tb_sy_user','tb_sy_code_grp','tb_sy_code','tb_sy_oper_std','tb_sy_role','tb_sy_user_role','tb_bs_part','tb_sy_role_menu_auth')")) {
            while (rows.next()) assertThat(number("SELECT count(*) FROM " + rows.getString(1))).isZero();
        }
    }

    @Test
    void repeatedPreparationPreservesEveryMenuValueAndSequencePosition() throws Exception {
        preparation.prepare();
        String before = text("SELECT jsonb_agg(to_jsonb(m) ORDER BY menu_id)::text FROM tb_sy_menu m");
        execute("SELECT nextval('sq_tb_sy_noti')");
        preparation.prepare();
        assertThat(text("SELECT jsonb_agg(to_jsonb(m) ORDER BY menu_id)::text FROM tb_sy_menu m")).isEqualTo(before);
        assertThat(number("SELECT nextval('sq_tb_sy_noti')")).isEqualTo(2);
    }

    @Test
    void existingNonemptyMenuIsNeverSupplemented() throws Exception {
        preparation.prepare();
        execute("DELETE FROM tb_sy_menu");
        execute("INSERT INTO tb_sy_menu(menu_id,menu_nm,menu_lvl,sort_ord,reg_user_id) VALUES ('owned','독립 메뉴',1,1,'OWNED')");
        preparation.prepare();
        assertThat(number("SELECT count(*) FROM tb_sy_menu")).isEqualTo(1);
        assertThat(text("SELECT menu_nm FROM tb_sy_menu")).isEqualTo("독립 메뉴");
    }

    @Test
    void mismatchBeforeCreationPreservesRowsAndCreatesNothingElse() throws Exception {
        execute("CREATE TABLE tb_sy_menu(menu_id text)");
        execute("INSERT INTO tb_sy_menu VALUES ('owned')");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_sy_menu.menu_id").hasMessageContaining("expected");
        assertThat(text("SELECT menu_id FROM tb_sy_menu")).isEqualTo("owned");
        assertThat(number("SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema()")).isEqualTo(1);
        assertThat(text("SELECT to_regclass('sq_tb_sy_noti')::text")).isNull();
    }

    @Test
    void changedNamedCheckIsRejectedWithoutAutomaticRepair() throws Exception {
        preparation.prepare();
        execute("ALTER TABLE tb_sy_menu DROP CONSTRAINT ck_tb_sy_menu_01");
        execute("ALTER TABLE tb_sy_menu ADD CONSTRAINT ck_tb_sy_menu_01 CHECK (use_yn IN ('Y','N','X'))");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("CK_TB_SY_MENU_01");
        assertThat(number("SELECT count(*) FROM tb_sy_menu")).isEqualTo(40);
    }

    @Test
    void changedDefaultIsRejected() throws Exception {
        preparation.prepare();
        execute("ALTER TABLE tb_sy_menu ALTER COLUMN use_yn SET DEFAULT 'N'");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_sy_menu.use_yn");
        assertThat(number("SELECT count(*) FROM tb_sy_menu")).isEqualTo(40);
    }

    @Test
    void seedFailureRollsBackNewSchemaAndCanBeRetried() throws Exception {
        // An owned event trigger is not needed: execute both actual resources in a transaction and force an error before commit.
        database.setAutoCommit(false);
        try {
            execute(resource("V1__navigation_schema.sql"));
            execute(resource("V2__navigation_menu_seed.sql"));
            assertThatThrownBy(() -> execute("SELECT 1/0")).isInstanceOf(java.sql.SQLException.class);
            database.rollback();
        } finally { database.setAutoCommit(true); }
        assertThat(text("SELECT to_regclass('tb_sy_menu')::text")).isNull();
        preparation.prepare();
        assertThat(number("SELECT count(*) FROM tb_sy_menu")).isEqualTo(40);
    }

    @Test
    void notificationDefaultUsesPreparedSequence() throws Exception {
        preparation.prepare();
        assertThat(number("INSERT INTO tb_sy_noti(rcvr_user_id,noti_type_cd,noti_cn,send_mthd_cd,noti_dttm,reg_user_id) VALUES ('OWNED','OWNED','독립 시험','OWNED',CURRENT_TIMESTAMP,'OWNED') RETURNING noti_id")).isEqualTo(1);
    }

    @Test
    void existingPreferenceRowsRemainUnchangedAndForeignKeysAreNotRemoved() throws Exception {
        preparation.prepare();
        execute("INSERT INTO tb_sy_user_pref(user_id,pref_type_cd,pref_key,pref_val,reg_user_id) VALUES ('OWNED','OWNED','OWNED','보존값','OWNED')");
        execute("ALTER TABLE tb_sy_menu ADD CONSTRAINT owned_parent FOREIGN KEY(upper_menu_id) REFERENCES tb_sy_menu(menu_id)");
        preparation.prepare();
        assertThat(text("SELECT pref_val FROM tb_sy_user_pref WHERE user_id='OWNED'")).isEqualTo("보존값");
        assertThat(number("SELECT count(*) FROM pg_constraint WHERE conrelid='tb_sy_menu'::regclass AND conname='owned_parent' AND contype='f'")).isEqualTo(1);
    }

    @Test
    void concurrentPreparersSerializeAndSeedOnlyOnce() throws Exception {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { preparation.prepare(); return true; });
            var second = pool.submit(() -> { preparation.prepare(); return true; });
            assertThat(first.get(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(number("SELECT count(*) FROM tb_sy_menu")).isEqualTo(40);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void userTablesRowsAndBothSequencePositionsSurviveRepeatedPreparation() throws Exception {
        preparation.prepare();
        execute("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) VALUES ('owneduser','PM','admin')");
        assertThat(number("INSERT INTO tb_sy_accs_log(user_id,act_type_cd,act_dttm,reg_user_id) "
            + "VALUES ('admin','USER_REG',CURRENT_TIMESTAMP,'admin') RETURNING log_id")).isEqualTo(1);
        assertThat(number("INSERT INTO tb_sy_chg_hist(trgt_tbl_nm,trgt_key,chg_type_cd,reg_user_id) "
            + "VALUES ('TB_SY_USER','owneduser','STOP','admin') RETURNING hist_id")).isEqualTo(1);
        var snapshots = new java.util.LinkedHashMap<String, String>();
        for (String table : new String[]{"tb_sy_role", "tb_sy_user_role", "tb_bs_part", "tb_sy_accs_log", "tb_sy_chg_hist"}) {
            snapshots.put(table, text("SELECT jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text FROM " + table + " t"));
        }
        preparation.prepare();
        for (var entry : snapshots.entrySet()) {
            assertThat(text("SELECT jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text FROM " + entry.getKey() + " t"))
                .isEqualTo(entry.getValue());
        }
        assertThat(number("SELECT nextval('sq_tb_sy_accs_log')")).isEqualTo(2);
        assertThat(number("SELECT nextval('sq_tb_sy_chg_hist')")).isEqualTo(2);
        assertThat(number("SELECT count(*) FROM pg_constraint WHERE connamespace=current_schema()::regnamespace AND contype='f'"))
            .isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"tb_sy_role", "tb_sy_user_role", "tb_bs_part", "tb_sy_code"})
    void nonemptyUserReferenceTableIsNeverSupplemented(String table) throws Exception {
        preparation.prepare();
        execute("DELETE FROM " + table);
        switch (table) {
            case "tb_sy_role" -> execute("INSERT INTO tb_sy_role(role_id,role_nm,data_scope_cd,sort_ord,reg_user_id) "
                + "VALUES ('OWNED','독립 역할','PROJ',9,'OWNED')");
            case "tb_sy_user_role" -> execute("INSERT INTO tb_sy_user_role(user_id,role_id,reg_user_id) "
                + "VALUES ('owneduser','OWNED','OWNED')");
            case "tb_bs_part" -> execute("INSERT INTO tb_bs_part(part_id,part_nm,reg_user_id) "
                + "VALUES ('OWNED','독립 파트','OWNED')");
            case "tb_sy_code" -> execute("INSERT INTO tb_sy_code(code_grp_id,code,code_nm,sort_ord,reg_user_id) "
                + "VALUES ('USER_STAT','OWNED','독립 코드',9,'OWNED')");
            default -> throw new IllegalArgumentException(table);
        }
        String before = text("SELECT jsonb_agg(to_jsonb(t))::text FROM " + table + " t");
        preparation.prepare();
        assertThat(number("SELECT count(*) FROM " + table)).isEqualTo(1);
        assertThat(text("SELECT jsonb_agg(to_jsonb(t))::text FROM " + table + " t")).isEqualTo(before);
    }

    @Test
    void newTableStructureMismatchIsReportedBeforeAnyCreation() throws Exception {
        execute("CREATE TABLE tb_sy_role(role_id text)");
        execute("INSERT INTO tb_sy_role VALUES ('OWNED')");
        assertThatThrownBy(preparation::prepare)
            .hasMessageContaining("tb_sy_role.role_id").hasMessageContaining("expected");
        assertThat(number("SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema()"))
            .isEqualTo(1);
        assertThat(text("SELECT role_id FROM tb_sy_role")).isEqualTo("OWNED");
        assertThat(text("SELECT to_regclass('sq_tb_sy_accs_log')::text")).isNull();
    }

    @Test
    void missingOriginalUniqueConstraintIsReportedNotRecreated() throws Exception {
        preparation.prepare();
        execute("ALTER TABLE tb_sy_role DROP CONSTRAINT uk_tb_sy_role_01");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("UK_TB_SY_ROLE_01");
        assertThat(number("SELECT count(*) FROM tb_sy_role")).isEqualTo(5);
        assertThat(number("SELECT count(*) FROM pg_constraint WHERE conrelid='tb_sy_role'::regclass "
            + "AND conname='uk_tb_sy_role_01'")).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"sq_tb_sy_accs_log", "sq_tb_sy_chg_hist"})
    void incompatibleSequenceIsReportedWithoutResetOrTableCreation(String sequence) throws Exception {
        execute("CREATE SEQUENCE " + sequence + " START WITH 7 INCREMENT BY 2");
        assertThatThrownBy(preparation::prepare).hasMessageContaining(sequence);
        assertThat(number("SELECT nextval('" + sequence + "')")).isEqualTo(7);
        assertThat(text("SELECT to_regclass('tb_sy_user_role')::text")).isNull();
    }

    @Test
    void userSeedErrorRollsBackEarlierSeedAndMissingTableCreation() throws Exception {
        preparation.prepare();
        execute("DELETE FROM tb_sy_user_role");
        execute("DELETE FROM tb_sy_role");
        execute("DROP TABLE tb_sy_accs_log");
        execute("CREATE FUNCTION reject_owned_role() RETURNS trigger LANGUAGE plpgsql AS $$ "
            + "BEGIN RAISE EXCEPTION 'owned seed failure'; END; $$");
        execute("CREATE TRIGGER reject_owned_role BEFORE INSERT ON tb_sy_user_role "
            + "FOR EACH ROW EXECUTE FUNCTION reject_owned_role()");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("owned seed failure");
        assertThat(number("SELECT count(*) FROM tb_sy_role")).isZero();
        assertThat(number("SELECT count(*) FROM tb_sy_user_role")).isZero();
        assertThat(text("SELECT to_regclass('tb_sy_accs_log')::text")).isNull();
    }

    private void execute(String sql) throws Exception {
        try (var statement = database.createStatement()) { statement.execute(sql); }
    }
    private long number(String sql) throws Exception {
        try (var statement = database.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next(); return rows.getLong(1);
        }
    }
    private String text(String sql) throws Exception {
        try (var statement = database.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next(); return rows.getString(1);
        }
    }
    private String resource(String name) throws Exception {
        return new ClassPathResource("db/migration/" + name).getContentAsString(StandardCharsets.UTF_8);
    }
}
