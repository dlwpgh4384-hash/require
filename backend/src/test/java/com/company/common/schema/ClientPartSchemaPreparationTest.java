package com.company.common.schema;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
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
class ClientPartSchemaPreparationTest {
    private Connection database;
    private String schema;
    private SchemaPreparation preparation;

    @BeforeEach void independentSchema() throws Exception {
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        database = DriverManager.getConnection(url, user, password);
        assertThat(database.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
        schema = "client_part_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET search_path TO " + schema);
        var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        source.setDriverProperties(properties);
        var configuration = new Configuration(new Environment("postgresql", new JdbcTransactionFactory(), source));
        preparation = new SchemaPreparation(new SqlSessionFactoryBuilder().build(configuration));
    }
    @AfterEach void removeOwnedSchema() throws Exception {
        if (database != null) {
            try { if (schema != null) execute("DROP SCHEMA " + schema + " CASCADE"); }
            finally { database.close(); }
        }
    }
    @Test void createsOnlyOriginalDependenciesWithKeysIndexesAndNoProductFixtureRows() throws Exception {
        preparation.prepare();
        for (String table : new String[]{"tb_bs_clnt", "tb_sy_upld", "tb_sy_upld_err",
            "tb_hr_dev", "tb_pj_asgn", "tb_pj_proj_part", "tb_co_part_setl"}) {
            assertThat(text("SELECT count(*)::text FROM " + table)).isEqualTo("0");
            assertThat(text("SELECT count(*)::text FROM pg_constraint WHERE conrelid='" + table + "'::regclass AND contype='p'"))
                .isEqualTo("1");
        }
        assertThat(text("SELECT count(*)::text FROM pg_constraint WHERE connamespace=current_schema()::regnamespace AND contype='f'"))
            .isEqualTo("18");
        assertThat(text("SELECT count(*)::text FROM tb_sy_adm_area")).isNotEqualTo("0");
        assertThat(text("SELECT indexdef FROM pg_indexes WHERE schemaname=current_schema() AND indexname='ix_tb_pj_asgn_02'"))
            .contains("(dev_id, input_bgn_dt, input_end_dt)");
        assertThat(text("SELECT count(*)::text FROM tb_sy_user WHERE user_id='admin'")).isEqualTo("1");
        execute("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES ('owned','CUST','독립 거래처','123-45-67890','admin')");
        execute("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,blng_clnt_id,reg_user_id) VALUES ('owned','owned','독립 인력','FT','BE','DRCT','HQ','WORK','owned','admin')");
        assertThat(text("SELECT blng_clnt_id FROM tb_hr_dev WHERE dev_id='owned'")).isEqualTo("owned");
    }
    @Test void repeatedPreparationPreservesRowsKeysFormulaContentAndSequencePosition() throws Exception {
        preparation.prepare();
        execute("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES ('owned','CUST','보존 거래처','123-45-67890','admin')");
        String client = text("SELECT row_to_json(c)::text FROM tb_bs_clnt c WHERE clnt_id='owned'");
        String formulas = text("SELECT jsonb_agg(to_jsonb(c) ORDER BY calc_id)::text FROM tb_sy_calc_rule c");
        String keys = text("SELECT jsonb_agg(jsonb_build_array(c.conname,pg_get_constraintdef(c.oid)) ORDER BY c.conname)::text FROM pg_constraint c WHERE connamespace=current_schema()::regnamespace");
        assertThat(text("SELECT nextval('sq_tb_sy_upld')::text")).isEqualTo("1");
        preparation.prepare();
        assertThat(text("SELECT row_to_json(c)::text FROM tb_bs_clnt c WHERE clnt_id='owned'")).isEqualTo(client);
        assertThat(text("SELECT jsonb_agg(to_jsonb(c) ORDER BY calc_id)::text FROM tb_sy_calc_rule c")).isEqualTo(formulas);
        assertThat(text("SELECT count(*)::text FROM tb_sy_calc_rule")).isEqualTo("94");
        assertThat(text("SELECT count(*)::text FROM tb_sy_calc_rule WHERE calc_id IN ('식-00','식-45')")).isEqualTo("2");
        assertThat(text("SELECT jsonb_agg(jsonb_build_array(c.conname,pg_get_constraintdef(c.oid)) ORDER BY c.conname)::text FROM pg_constraint c WHERE connamespace=current_schema()::regnamespace"))
            .isEqualTo(keys);
        assertThat(text("SELECT nextval('sq_tb_sy_upld')::text")).isEqualTo("2");
    }
    @Test void existingWrongColumnFailsWithoutRepairOrCreatingOtherTables() throws Exception {
        execute("CREATE TABLE tb_bs_clnt(clnt_id text)");
        execute("INSERT INTO tb_bs_clnt VALUES ('owned')");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_bs_clnt.clnt_id");
        assertThat(text("SELECT clnt_id FROM tb_bs_clnt")).isEqualTo("owned");
        assertThat(text("SELECT count(*)::text FROM information_schema.tables WHERE table_schema=current_schema()"))
            .isEqualTo("1");
    }
    @Test void existingMissingForeignKeyIsReportedNotRecreated() throws Exception {
        preparation.prepare();
        execute("ALTER TABLE tb_hr_dev DROP CONSTRAINT fk_tb_hr_dev_01");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("FK_TB_HR_DEV_01");
        assertThat(text("SELECT count(*)::text FROM pg_constraint WHERE conrelid='tb_hr_dev'::regclass AND conname='fk_tb_hr_dev_01'"))
            .isEqualTo("0");
    }
    @Test void existingWrongIndexIsReportedNotReplaced() throws Exception {
        preparation.prepare();
        execute("DROP INDEX ix_tb_pj_asgn_02");
        execute("CREATE INDEX ix_tb_pj_asgn_02 ON tb_pj_asgn(dev_id)");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("IX_TB_PJ_ASGN_02");
        assertThat(text("SELECT indexdef FROM pg_indexes WHERE schemaname=current_schema() AND indexname='ix_tb_pj_asgn_02'"))
            .endsWith("(dev_id)");
    }
    @Test void originalForeignKeysRejectInvalidOwnershipReferences() throws Exception {
        preparation.prepare();
        assertThatThrownBy(() -> execute("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,blng_clnt_id,reg_user_id) VALUES ('owned','owned','잘못된 참조','FT','BE','DRCT','HQ','WORK','missing','admin')"))
            .hasMessageContaining("fk_tb_hr_dev_01");
        assertThat(text("SELECT count(*)::text FROM tb_hr_dev")).isEqualTo("0");
    }
    @Test void originalMigrationDoesNotChangeExistingMasterDefinitions() throws Exception {
        for (String file : new String[]{"V1__navigation_schema.sql", "V2__navigation_menu_seed.sql", "V3__common_function_seed.sql", "V4__user_management_seed.sql", "V5__role_permissions.sql", "V6__role_schema_contract.sql", "V7__code_definitions_schema.sql", "V8__calculation_rule_seed.sql", "V9__common_entity_contract.sql"}) {
            execute(new ClassPathResource("db/migration/" + file).getContentAsString(StandardCharsets.UTF_8));
        }
        String before = text("SELECT jsonb_agg(jsonb_build_array(table_name,column_name,data_type,character_maximum_length,numeric_precision,numeric_scale,is_nullable,column_default) ORDER BY table_name,ordinal_position)::text FROM information_schema.columns WHERE table_schema=current_schema() AND table_name IN ('tb_bs_part','tb_bs_part_std','tb_pj_proj')");
        execute(new ClassPathResource("db/migration/V10__client_part_upload_schema.sql").getContentAsString(StandardCharsets.UTF_8));
        assertThat(text("SELECT jsonb_agg(jsonb_build_array(table_name,column_name,data_type,character_maximum_length,numeric_precision,numeric_scale,is_nullable,column_default) ORDER BY table_name,ordinal_position)::text FROM information_schema.columns WHERE table_schema=current_schema() AND table_name IN ('tb_bs_part','tb_bs_part_std','tb_pj_proj')"))
            .isEqualTo(before);
    }
    private void execute(String sql) throws Exception {
        try (var statement = database.createStatement()) { statement.execute(sql); }
    }
    private String text(String sql) throws Exception {
        try (var statement = database.createStatement(); var row = statement.executeQuery(sql)) {
            assertThat(row.next()).isTrue();
            return row.getString(1);
        }
    }
}
