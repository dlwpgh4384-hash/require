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
            // V21(영업 파이프라인 UI-29)이 견적 승인 이력 TB_SL_APRV_HIST를 더한다.
            .isEqualTo("tb_sl_aprv_hist,tb_sl_opp,tb_sl_opp_need,tb_sl_rcmd_log,tb_sl_tent_asgn");
        assertThat(text("SELECT count(*)::text FROM tb_sl_opp")).isEqualTo("0");
        assertThat(text("SELECT count(*)::text FROM tb_sl_opp_need")).isEqualTo("0");
        assertThat(text("SELECT count(*)::text FROM tb_sl_tent_asgn")).isEqualTo("0");
        assertThat(text("SELECT string_agg(column_name,',' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_sl_tent_asgn'"))
            .isEqualTo("tent_id,opp_id,need_sn,dev_id,bgn_ym,tent_stat_cd,src_scrn_id,proc_dttm,reg_user_id,reg_dttm,mod_user_id,mod_dttm");
        assertThat(text("SELECT count(*)::text FROM pg_constraint WHERE connamespace=current_schema()::regnamespace AND contype='f' AND (conrelid::regclass::text LIKE 'tb_sl_%' OR conname='fk_tb_pj_proj_02')"))
            .isEqualTo("12"); // V21의 FK_TB_SL_APRV_HIST_01, 근무지 지역 FK_TB_SL_OPP_04(2026-11-25)를 더함
        assertThat(text("SELECT count(*)::text FROM pg_indexes WHERE schemaname=current_schema() AND indexname LIKE 'ix_tb_sl_%'"))
            .isEqualTo("11");
        // 근무지 지역(2026-11-25): 프로젝트·영업 기회 모두 맨 뒤 칸 WORK_AREA_CD → TB_SY_ADM_AREA, 색인 있음.
        for (String table : new String[]{"tb_pj_proj", "tb_sl_opp"}) {
            assertThat(text("SELECT column_name||':'||data_type||':'||character_maximum_length||':'||is_nullable FROM information_schema.columns "
                + "WHERE table_schema=current_schema() AND table_name='" + table + "' ORDER BY ordinal_position DESC LIMIT 1"))
                .as(table).isEqualTo("work_area_cd:character varying:10:YES");
            assertThat(text("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='" + table + "'::regclass AND contype='f' "
                + "AND conname=" + ("tb_pj_proj".equals(table) ? "'fk_tb_pj_proj_03'" : "'fk_tb_sl_opp_04'")))
                .as(table).isEqualTo("FOREIGN KEY (work_area_cd) REFERENCES tb_sy_adm_area(area_cd)");
            assertThat(text("SELECT indexdef FROM pg_indexes WHERE schemaname=current_schema() AND indexname="
                + ("tb_pj_proj".equals(table) ? "'ix_tb_pj_proj_05'" : "'ix_tb_sl_opp_05'"))).as(table).contains("(work_area_cd)");
        }
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
        // V10·V12와 같이 「표.제약 이름」을 원본 대문자로 알린다(ClientPartSchemaPreparationTest의 FK_TB_HR_DEV_01 검사와 같은 규칙).
        assertThatThrownBy(() -> execute(resource())).hasMessageContaining("TB_SL_OPP.FK_TB_SL_OPP_03");
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
