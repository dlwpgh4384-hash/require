package com.company.common.schema;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import java.util.UUID;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AssignmentSourcePreparationTest {
    private Connection database;
    private String schema;
    private SchemaPreparation preparation;

    @BeforeEach
    void independentSchema() throws Exception {
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        database = DriverManager.getConnection(url, user, password);
        schema = "assignments_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET search_path TO " + schema);
        var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        source.setDriverProperties(properties);
        var configuration = new Configuration(new Environment("postgresql", new JdbcTransactionFactory(), source));
        preparation = new SchemaPreparation(new SqlSessionFactoryBuilder().build(configuration));
        preparation.prepare();
    }

    @AfterEach
    void removeOwnedSchema() throws Exception {
        if (database != null) {
            try {
                if (schema != null) execute("DROP SCHEMA " + schema + " CASCADE");
            } finally {
                database.close();
            }
        }
    }

    @Test
    void originalSourcesHaveOnlyOriginalColumnsKeysAndIndexesWithoutBusinessSeeds() throws Exception {
        assertThat(text("SELECT string_agg(table_name,',' ORDER BY table_name) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name LIKE 'tb_sl_%'"))
            .isEqualTo("tb_sl_opp,tb_sl_opp_need,tb_sl_tent_asgn");
        assertThat(text("SELECT count(*)::text FROM tb_sl_opp")).isEqualTo("0");
        assertThat(text("SELECT count(*)::text FROM tb_sl_opp_need")).isEqualTo("0");
        assertThat(text("SELECT count(*)::text FROM tb_sl_tent_asgn")).isEqualTo("0");
        assertThat(text("SELECT string_agg(column_name,',' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_sl_tent_asgn'"))
            .isEqualTo("tent_id,opp_id,need_sn,dev_id,bgn_ym,tent_stat_cd,src_scrn_id,proc_dttm,reg_user_id,reg_dttm,mod_user_id,mod_dttm");
        assertThat(text("SELECT count(*)::text FROM pg_constraint WHERE connamespace=current_schema()::regnamespace AND contype='f' AND (conrelid::regclass::text LIKE 'tb_sl_%' OR conname='fk_tb_pj_proj_02')"))
            .isEqualTo("7");
        assertThat(text("SELECT count(*)::text FROM pg_indexes WHERE schemaname=current_schema() AND indexname LIKE 'ix_tb_sl_%'"))
            .isEqualTo("7");
    }

    @Test
    void repeatedPreparationPreservesSourceRowsDefinitionsCodesAndSequencePosition() throws Exception {
        execute("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES ('C-TEST','CUST','독립 고객','1234567890','admin')");
        execute("INSERT INTO tb_sl_opp(opp_id,opp_nm,cust_clnt_id,part_id,sales_step_cd,win_prbl_rt,expct_amt,bgn_plan_ym,opp_stat_cd,aprv_stat_cd,reg_user_id) SELECT 'O-TEST','독립 기회','C-TEST',part_id,'FIND',10,1000000,'202611','ING','NONE','admin' FROM tb_bs_part ORDER BY part_id LIMIT 1");
        assertThat(text("SELECT count(*)::text FROM tb_sl_opp")).isEqualTo("1");
        execute("UPDATE tb_sy_calc_rule SET frml_cn='독립 변경 정의' WHERE calc_id='식-58'");
        String definitions = text("SELECT jsonb_agg(to_jsonb(r) ORDER BY calc_id)::text FROM tb_sy_calc_rule r");
        String codes = text("SELECT jsonb_agg(to_jsonb(c) ORDER BY code_grp_id,code)::text FROM tb_sy_code c");
        String opportunity = text("SELECT to_jsonb(o)::text FROM tb_sl_opp o WHERE opp_id='O-TEST'");
        assertThat(text("SELECT nextval('sq_tb_sl_tent_asgn')::text")).isEqualTo("1");
        preparation.prepare();
        preparation.prepare();
        assertThat(text("SELECT to_jsonb(o)::text FROM tb_sl_opp o WHERE opp_id='O-TEST'")).isEqualTo(opportunity);
        assertThat(text("SELECT jsonb_agg(to_jsonb(r) ORDER BY calc_id)::text FROM tb_sy_calc_rule r")).isEqualTo(definitions);
        assertThat(text("SELECT jsonb_agg(to_jsonb(c) ORDER BY code_grp_id,code)::text FROM tb_sy_code c")).isEqualTo(codes);
        assertThat(text("SELECT nextval('sq_tb_sl_tent_asgn')::text")).isEqualTo("2");
    }

    @Test
    void incompatibleSourceColumnIsRejectedWithoutRepair() throws Exception {
        execute("ALTER TABLE tb_sl_opp_need ALTER COLUMN mon_mm TYPE NUMERIC(5,2)");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_sl_opp_need");
        assertThat(text("SELECT numeric_precision::text FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_sl_opp_need' AND column_name='mon_mm'"))
            .isEqualTo("5");
    }

    @Test
    void incompatibleForeignKeyIsRejectedWithoutReplacingIt() throws Exception {
        execute("ALTER TABLE tb_sl_opp DROP CONSTRAINT fk_tb_sl_opp_03");
        execute("ALTER TABLE tb_sl_opp ADD CONSTRAINT fk_tb_sl_opp_03 FOREIGN KEY (proj_id) REFERENCES tb_pj_proj(proj_id) ON DELETE CASCADE");
        assertThatThrownBy(() -> execute(resource())).hasMessageContaining("fk_tb_sl_opp_03");
        assertThat(text("SELECT confdeltype::text FROM pg_constraint WHERE conrelid='tb_sl_opp'::regclass AND conname='fk_tb_sl_opp_03'"))
            .isEqualTo("c");
    }

    private String resource() throws Exception {
        return new ClassPathResource("db/migration/V13__assignment_candidate_sources.sql")
            .getContentAsString(StandardCharsets.UTF_8);
    }

    private void execute(String sql) throws Exception {
        try (var statement = database.createStatement()) {
            statement.execute(sql);
        }
    }

    private String text(String sql) throws Exception {
        try (var statement = database.createStatement(); var rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }
}
