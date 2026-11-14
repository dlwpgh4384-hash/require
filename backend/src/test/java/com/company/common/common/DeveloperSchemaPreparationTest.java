package com.company.common.common;

import com.company.common.schema.SchemaPreparation;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
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
class DeveloperSchemaPreparationTest {
    private Connection database;
    private String schema;
    private SchemaPreparation preparation;

    @BeforeEach void independentSchema() throws Exception {
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        database = DriverManager.getConnection(url, user, password);
        assertThat(database.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
        schema = "developer_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET search_path TO " + schema);
        var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        source.setDriverProperties(properties);
        var configuration = new Configuration(new Environment("postgresql", new JdbcTransactionFactory(), source));
        preparation = new SchemaPreparation(new SqlSessionFactoryBuilder().build(configuration));
        for (String file : new String[]{"V1__navigation_schema.sql", "V2__navigation_menu_seed.sql",
            "V3__common_function_seed.sql", "V4__user_management_seed.sql", "V5__role_permissions.sql",
            "V6__role_schema_contract.sql", "V7__code_definitions_schema.sql", "V8__calculation_rule_seed.sql",
            "V9__common_entity_contract.sql", "V10__client_part_upload_schema.sql"}) {
            execute(resource(file));
        }
    }

    @AfterEach void removeOwnedSchema() throws Exception {
        if (database != null) {
            try { if (schema != null) execute("DROP SCHEMA " + schema + " CASCADE"); }
            finally { database.close(); }
        }
    }

    @Test void preparesExactlyFiveMissingTablesAndPreservesExistingDefinitionsAndRows() throws Exception {
        execute("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id) VALUES ('owned','CUST','보존 거래처','123-45-67890','admin')");
        execute("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) VALUES ('owned','owned','보존 인력','FT','BE','DRCT','HQ','WORK','admin')");
        var before = new LinkedHashMap<String, String>();
        for (String table : new String[]{"tb_hr_dev", "tb_pj_asgn", "tb_sy_adm_area", "tb_bs_clnt", "tb_bs_part"}) {
            before.put(table, definition(table));
        }
        String developer = rows("tb_hr_dev");
        String client = rows("tb_bs_clnt");
        String part = rows("tb_bs_part");
        preparation.prepare();
        assertThat(text("SELECT count(*)::text FROM information_schema.tables WHERE table_schema=current_schema() AND table_type='BASE TABLE'"))
            .isEqualTo("34");
        for (var entry : before.entrySet()) assertThat(definition(entry.getKey())).isEqualTo(entry.getValue());
        assertThat(rows("tb_hr_dev")).isEqualTo(developer);
        assertThat(rows("tb_bs_clnt")).isEqualTo(client);
        assertThat(rows("tb_bs_part")).isEqualTo(part);
        for (String table : new String[]{"tb_hr_dev_prc", "tb_hr_dev_skill", "tb_hr_dev_stat", "tb_hr_mon_pay"}) {
            assertThat(text("SELECT count(*)::text FROM " + table)).isEqualTo("0");
        }
        execute("INSERT INTO tb_hr_dev_prc(dev_id,aply_bgn_ym,mon_uprc,reg_user_id) VALUES ('owned','202611',123,'admin')");
        execute("INSERT INTO tb_hr_dev_skill(dev_id,skill_nm,sort_ord,reg_user_id) VALUES ('owned','보존 스킬',1,'admin')");
        execute("INSERT INTO tb_hr_dev_stat(dev_id,stat_sn,work_stat_cd,bgn_dt,reg_user_id) VALUES ('owned',1,'LEAVE','2026-11-01','admin')");
        execute("INSERT INTO tb_hr_mon_pay(pay_ym,dev_id,pay_amt,alwnc_amt,insr_cmpny_amt,retire_rsv_amt,emp_type_cd,drct_cls_cd,reg_user_id) VALUES ('202611','owned',100,0,0,0,'FT','DRCT','admin')");
        var saved = new LinkedHashMap<String, String>();
        for (String table : new String[]{"tb_hr_dev_prc", "tb_hr_dev_skill", "tb_hr_dev_stat", "tb_hr_mon_pay", "tb_sy_adm_hdong", "tb_sy_adm_area"}) {
            saved.put(table, rows(table));
        }
        preparation.prepare();
        for (var entry : saved.entrySet()) assertThat(rows(entry.getKey())).isEqualTo(entry.getValue());
    }

    @Test void nonemptyAddressTableIsNotSupplementedWhenOnlyAdministrativeDongIsEmpty() throws Exception {
        preparation.prepare();
        execute("UPDATE tb_sy_adm_area SET sido_nm='보존' WHERE area_cd='1100000000'");
        String area = rows("tb_sy_adm_area");
        String dong = text("SELECT count(*)::text FROM tb_sy_adm_hdong");
        execute("DELETE FROM tb_sy_adm_hdong");
        preparation.prepare();
        assertThat(rows("tb_sy_adm_area")).isEqualTo(area);
        assertThat(text("SELECT count(*)::text FROM tb_sy_adm_hdong")).isEqualTo(dong);
    }

    @Test void addressGuardsAreIndependentWhenOnlyLegalAreaIsEmpty() throws Exception {
        // Exercise the address unit with the pre-existing parent/child tables before FK installation.
        String sql = resource("V11__developer_pool_schema.sql");
        var table = java.util.regex.Pattern.compile("\\$ddl\\$(CREATE TABLE IF NOT EXISTS TB_SY_ADM_HDONG .*?)\\$ddl\\$",
            java.util.regex.Pattern.DOTALL).matcher(sql);
        assertThat(table.find()).isTrue();
        execute(table.group(1));
        execute("INSERT INTO tb_sy_adm_hdong(hdong_cd,area_cd,hdong_nm,reg_user_id) VALUES ('owned','1100000000','보존 행정동','admin')");
        String dong = rows("tb_sy_adm_hdong");
        execute(sql.substring(sql.indexOf("DO $developer_addresses$")));
        assertThat(text("SELECT count(*)::text FROM tb_sy_adm_area")).isNotEqualTo("0");
        assertThat(rows("tb_sy_adm_hdong")).isEqualTo(dong);
    }

    @Test void incompatibleExistingTableIsDiagnosedWithoutRepairOrPartialCreation() throws Exception {
        execute("CREATE TABLE tb_hr_dev_skill(dev_id text)");
        execute("INSERT INTO tb_hr_dev_skill VALUES ('owned')");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_hr_dev_skill");
        assertThat(text("SELECT dev_id FROM tb_hr_dev_skill")).isEqualTo("owned");
        assertThat(text("SELECT to_regclass('tb_hr_mon_pay')::text")).isNull();
        assertThat(text("SELECT count(*)::text FROM tb_sy_adm_area")).isEqualTo("0");
    }

    @Test void existingFormulaAndCodeChangesAreNeverReplacedByPreparation() throws Exception {
        execute("UPDATE tb_sy_calc_rule SET frml_cn='검수 정의' WHERE calc_id='식-10'");
        execute("UPDATE tb_sy_code SET code_nm='검수 명칭' WHERE code_grp_id='JOB' AND code='BE'");
        String formulas = rows("tb_sy_calc_rule");
        String codes = rows("tb_sy_code");
        preparation.prepare();
        assertThat(rows("tb_sy_calc_rule")).isEqualTo(formulas);
        assertThat(rows("tb_sy_code")).isEqualTo(codes);
    }

    private String definition(String table) throws Exception {
        return text("SELECT jsonb_agg(jsonb_build_array(column_name,data_type,character_maximum_length,numeric_precision,numeric_scale,is_nullable,column_default) ORDER BY ordinal_position)::text FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='" + table + "'");
    }
    private String rows(String table) throws Exception {
        return text("SELECT jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text FROM " + table + " t");
    }
    private String resource(String file) throws Exception {
        return new ClassPathResource("db/migration/" + file).getContentAsString(StandardCharsets.UTF_8);
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
