package com.company.common.schema;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class RoleSchemaContractIntegrationTest {
    private Connection database;
    private String schema;
    private Flyway flyway;

    @BeforeEach
    void migrateOwnedSchema() throws Exception {
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        database = DriverManager.getConnection(url, user, password);
        assertThat(database.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
        schema = "role_contract_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET search_path TO " + schema);
        flyway = Flyway.configure().dataSource(url, user, password)
            .schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(6);
    }

    @AfterEach
    void cleanup() throws Exception {
        if (database != null) {
            try {
                if (schema != null) execute("DROP SCHEMA " + schema + " CASCADE");
            } finally { database.close(); }
        }
    }

    @Test
    void flywayPackagesRealEntitiesAndOriginalPermissionIndexWithoutInventingScreenTables() throws Exception {
        assertThat(text("SELECT count(*)::text FROM information_schema.tables WHERE table_schema=current_schema() AND table_name!='flyway_schema_history'"))
            .isEqualTo("19");
        assertThat(text("SELECT to_regclass('u_i_24_s_l_i_c_e_006')::text")).isNull();
        assertThat(text("SELECT indexdef FROM pg_indexes WHERE schemaname=current_schema() AND indexname='ix_tb_sy_role_menu_auth_01'"))
            .contains("tb_sy_role_menu_auth", "(menu_id)");
        assertThat(text("SELECT count(*)::text FROM pg_constraint WHERE connamespace=current_schema()::regnamespace AND contype='f'"))
            .isEqualTo("0");
        assertThat(text("SELECT count(*)::text FROM tb_sy_role_menu_auth")).isEqualTo("150");
        assertThat(text("SELECT count(*)::text FROM pg_constraint WHERE conrelid='tb_sy_role_menu_auth'::regclass AND contype='c'"))
            .isEqualTo("7");
        assertThatThrownBy(() -> execute("INSERT INTO tb_sy_role(role_id,role_nm,data_scope_cd,sort_ord,reg_user_id) SELECT 'owned',role_nm,'ALL',99,'admin' FROM tb_sy_role WHERE role_id='ADMIN'"))
            .hasMessageContaining("uk_tb_sy_role_01");
        assertThatThrownBy(() -> execute("INSERT INTO tb_sy_role_menu_auth SELECT * FROM tb_sy_role_menu_auth LIMIT 1"))
            .hasMessageContaining("pk_tb_sy_role_menu_auth");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void repeatedContractCheckPreservesEditedPermissionsAndIndex() throws Exception {
        execute("UPDATE tb_sy_role_menu_auth SET cnfm_yn='Y' WHERE role_id='EXEC' AND menu_id='M011'");
        String before = text("SELECT jsonb_agg(to_jsonb(a) ORDER BY role_id,menu_id)::text FROM tb_sy_role_menu_auth a");
        execute(contract());
        execute(contract());
        assertThat(text("SELECT jsonb_agg(to_jsonb(a) ORDER BY role_id,menu_id)::text FROM tb_sy_role_menu_auth a")).isEqualTo(before);
        assertThat(text("SELECT count(*)::text FROM pg_indexes WHERE schemaname=current_schema() AND indexname='ix_tb_sy_role_menu_auth_01'"))
            .isEqualTo("1");
    }

    @Test
    void incompatibleColumnIsReportedAndNotAutomaticallyAltered() throws Exception {
        execute("ALTER TABLE tb_sy_role_menu_auth ALTER COLUMN mod_user_id TYPE text");
        assertThatThrownBy(() -> execute(contract())).hasMessageContaining("tb_sy_role_menu_auth.mod_user_id").hasMessageContaining("expected");
        assertThat(text("SELECT data_type FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_sy_role_menu_auth' AND column_name='mod_user_id'"))
            .isEqualTo("text");
        assertThat(text("SELECT count(*)::text FROM tb_sy_role_menu_auth")).isEqualTo("150");
    }

    @Test
    void weakenedCheckMissingKeyAndChangedDefaultAreRejected() throws Exception {
        execute("ALTER TABLE tb_sy_role_menu_auth DROP CONSTRAINT ck_tb_sy_role_menu_auth_07");
        execute("ALTER TABLE tb_sy_role_menu_auth ADD CONSTRAINT ck_tb_sy_role_menu_auth_07 CHECK (cnfm_yn IN ('Y','N','X'))");
        execute("ALTER TABLE tb_sy_role_menu_auth DROP CONSTRAINT pk_tb_sy_role_menu_auth");
        execute("ALTER TABLE tb_sy_role_menu_auth ALTER COLUMN inq_yn SET DEFAULT 'Y'");
        assertThatThrownBy(() -> execute(contract())).hasMessageContaining("cnfm_yn").hasMessageContaining("PK_TB_SY_ROLE_MENU_AUTH")
            .hasMessageContaining("inq_yn");
        assertThat(text("SELECT count(*)::text FROM tb_sy_role_menu_auth")).isEqualTo("150");
    }

    private String contract() throws Exception {
        return new ClassPathResource("db/migration/V6__role_schema_contract.sql").getContentAsString(StandardCharsets.UTF_8);
    }
    private void execute(String sql) throws Exception {
        try (var statement = database.createStatement()) { statement.execute(sql); }
    }
    private String text(String sql) throws Exception {
        try (var statement = database.createStatement(); var result = statement.executeQuery(sql)) {
            result.next(); return result.getString(1);
        }
    }
}
