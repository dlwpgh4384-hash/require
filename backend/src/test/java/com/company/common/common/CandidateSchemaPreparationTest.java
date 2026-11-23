package com.company.common.common;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.users.mapper.UserMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class CandidateSchemaPreparationTest {
    private static final List<String> TABLES = List.of("tb_hr_cand", "tb_hr_cand_skill", "tb_hr_cand_cntc");
    private Connection database;
    private String schema;
    private SqlSessionFactory factory;
    private SchemaPreparation preparation;

    @BeforeEach
    void independentSchema() throws Exception {
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        database = DriverManager.getConnection(url, user, password);
        schema = "candidate_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET search_path TO " + schema);
        var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        source.setDriverProperties(properties);
        var config = new Configuration(new Environment("postgresql", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        for (String resource : List.of("mapper/CalculationRuleMapper.xml", "mapper/CodeGroupMapper.xml",
                "mapper/UserPreferencePeriodSourceMapper.xml")) {
            try (var input = new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
        }
        factory = new SqlSessionFactoryBuilder().build(config);
        preparation = new SchemaPreparation(factory);
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
    void originalCandidateColumnsAndCompositeKeysArePreparedWithoutResumeStorage() throws Exception {
        assertThat(columns("tb_hr_cand")).isEqualTo(
            "cand_id,cand_nm,emp_type_cd,blng_clnt_id,telno,email_addr,prvc_agre_yn,prvc_agre_dt,"
                + "prvc_agre_user_id,job_cd,sub_job_cd,grade_cd,home_area_cd,home_addr,hope_uprc,"
                + "input_psbl_dt,cntc_stat_cd,last_cntc_dt,cand_src_cd,memo_cn,reg_user_id,reg_dttm,mod_user_id,mod_dttm");
        assertThat(columns("tb_hr_cand_skill")).isEqualTo(
            "cand_id,skill_nm,sort_ord,reg_user_id,reg_dttm,mod_user_id,mod_dttm");
        assertThat(columns("tb_hr_cand_cntc")).isEqualTo(
            "cand_id,cntc_sn,cntc_stat_cd,cntc_memo,cntc_dt,reg_user_id,reg_dttm,mod_user_id,mod_dttm");
        assertThat(key("tb_hr_cand")).isEqualTo("PRIMARY KEY (cand_id)");
        assertThat(key("tb_hr_cand_skill")).isEqualTo("PRIMARY KEY (cand_id, skill_nm)");
        assertThat(key("tb_hr_cand_cntc")).isEqualTo("PRIMARY KEY (cand_id, cntc_sn)");
        for (String table : TABLES) {
            assertThat(text("SELECT count(*)::text FROM " + table)).isEqualTo("0");
            assertThat(text("SELECT obj_description('" + table + "'::regclass)")).isNotBlank();
        }
        assertThat(text("SELECT column_default FROM information_schema.columns WHERE table_schema=current_schema() "
            + "AND table_name='tb_hr_cand' AND column_name='cand_id'")).isNull();
        assertThat(text("SELECT column_default FROM information_schema.columns WHERE table_schema=current_schema() "
            + "AND table_name='tb_hr_cand_cntc' AND column_name='cntc_sn'")).isNull();
    }

    @Test
    void onlyNewTablesReceiveTheFourOriginalForeignKeysAndThreeIndexes() throws Exception {
        assertThat(text("SELECT string_agg(conname || ':' || pg_get_constraintdef(oid),'|' ORDER BY conname) "
            + "FROM pg_constraint WHERE conrelid IN ('tb_hr_cand'::regclass,'tb_hr_cand_skill'::regclass,"
            + "'tb_hr_cand_cntc'::regclass) AND contype='f'"))
            .isEqualTo("fk_tb_hr_cand_01:FOREIGN KEY (blng_clnt_id) REFERENCES tb_bs_clnt(clnt_id)|"
                + "fk_tb_hr_cand_02:FOREIGN KEY (home_area_cd) REFERENCES tb_sy_adm_area(area_cd)|"
                + "fk_tb_hr_cand_cntc_01:FOREIGN KEY (cand_id) REFERENCES tb_hr_cand(cand_id)|"
                + "fk_tb_hr_cand_skill_01:FOREIGN KEY (cand_id) REFERENCES tb_hr_cand(cand_id)");
        assertThat(text("SELECT string_agg(c.relname || ':' || pg_get_indexdef(i.indexrelid,1,true),',' ORDER BY c.relname) "
            + "FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid "
            + "WHERE i.indrelid='tb_hr_cand'::regclass AND NOT i.indisprimary"))
            .isEqualTo("ix_tb_hr_cand_01:job_cd,ix_tb_hr_cand_02:blng_clnt_id,ix_tb_hr_cand_03:home_area_cd");
    }

    @Test
    void repeatedPreparationPreservesCandidateSkillsContactsAndAllSources() throws Exception {
        insertCandidate();
        execute("INSERT INTO tb_hr_cand_skill(cand_id,skill_nm,sort_ord,reg_user_id) VALUES ('owned','Java',1,'admin')");
        execute("INSERT INTO tb_hr_cand_cntc(cand_id,cntc_sn,cntc_stat_cd,cntc_memo,cntc_dt,reg_user_id) "
            + "VALUES ('owned',1,'BEFORE','등록',DATE '2026-11-01','SYSTEM')");
        String before = snapshot() + shape();
        preparation.prepare();
        preparation.prepare();
        assertThat(snapshot() + shape()).isEqualTo(before);
    }

    @Test
    void missingExistingForeignKeysAndIndexesAreNotAddedBack() throws Exception {
        execute("ALTER TABLE tb_hr_cand DROP CONSTRAINT fk_tb_hr_cand_01, DROP CONSTRAINT fk_tb_hr_cand_02");
        execute("ALTER TABLE tb_hr_cand_skill DROP CONSTRAINT fk_tb_hr_cand_skill_01");
        execute("ALTER TABLE tb_hr_cand_cntc DROP CONSTRAINT fk_tb_hr_cand_cntc_01");
        execute("DROP INDEX ix_tb_hr_cand_01, ix_tb_hr_cand_02, ix_tb_hr_cand_03");
        insertCandidate();
        execute("COMMENT ON TABLE tb_hr_cand IS '기존 설명'");
        String before = snapshot() + shape();
        preparation.prepare();
        assertThat(snapshot() + shape()).isEqualTo(before);
        assertThat(text("SELECT obj_description('tb_hr_cand'::regclass)")).isEqualTo("기존 설명");
    }

    @Test
    void existingForeignKeyDefinitionDifferencesAreOutsideStructureComparison() throws Exception {
        execute("ALTER TABLE tb_hr_cand_skill DROP CONSTRAINT fk_tb_hr_cand_skill_01");
        execute("ALTER TABLE tb_hr_cand_skill ADD CONSTRAINT owned_reference FOREIGN KEY (cand_id) "
            + "REFERENCES tb_hr_cand(cand_id) DEFERRABLE INITIALLY DEFERRED");
        String before = shape();
        preparation.prepare();
        assertThat(shape()).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "tb_hr_cand|ALTER COLUMN hope_uprc TYPE NUMERIC(14)",
        "tb_hr_cand|ALTER COLUMN cand_nm TYPE VARCHAR(51)",
        "tb_hr_cand|ALTER COLUMN input_psbl_dt DROP NOT NULL",
        "tb_hr_cand|ALTER COLUMN prvc_agre_yn SET DEFAULT 'Y'",
        "tb_hr_cand|ALTER COLUMN mod_dttm TYPE TIMESTAMP",
        "tb_hr_cand|DROP CONSTRAINT ck_tb_hr_cand_01",
        "tb_hr_cand|ADD CONSTRAINT owned_unique UNIQUE(cand_nm)",
        "tb_hr_cand|ADD COLUMN resume_text TEXT",
        "tb_hr_cand_skill|ALTER COLUMN skill_nm TYPE VARCHAR(51)",
        "tb_hr_cand_skill|ALTER COLUMN sort_ord SET DEFAULT 1",
        "tb_hr_cand_skill|DROP CONSTRAINT pk_tb_hr_cand_skill",
        "tb_hr_cand_cntc|ALTER COLUMN cntc_sn TYPE NUMERIC(6)",
        "tb_hr_cand_cntc|ALTER COLUMN cntc_memo TYPE VARCHAR(201)",
        "tb_hr_cand_cntc|ALTER COLUMN cntc_dt DROP NOT NULL",
        "tb_hr_cand_cntc|DROP CONSTRAINT pk_tb_hr_cand_cntc"
    })
    void changedColumnsDefaultsNullabilityAndPrimaryCheckUniqueConstraintsAreRefused(String table, String change)
            throws Exception {
        execute("ALTER TABLE " + table + " " + change);
        String before = shape();
        assertThatThrownBy(preparation::prepare).hasMessageContaining(table);
        assertThat(shape()).isEqualTo(before);
    }

    @Test
    void reversedContactKeyIsRefusedWithoutRepair() throws Exception {
        execute("ALTER TABLE tb_hr_cand_cntc DROP CONSTRAINT pk_tb_hr_cand_cntc");
        execute("ALTER TABLE tb_hr_cand_cntc ADD CONSTRAINT pk_tb_hr_cand_cntc PRIMARY KEY(cntc_sn,cand_id)");
        String before = shape();
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_hr_cand_cntc");
        assertThat(shape()).isEqualTo(before);
    }

    @Test
    void viewCannotStandInForAnOriginalTable() throws Exception {
        execute("DROP TABLE tb_hr_cand_cntc");
        execute("CREATE VIEW tb_hr_cand_cntc AS SELECT cand_id FROM tb_hr_cand");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_hr_cand_cntc: expected ordinary table");
        assertThat(text("SELECT relkind::text FROM pg_class WHERE oid='tb_hr_cand_cntc'::regclass")).isEqualTo("v");
    }

    @Test
    void laterStructureFailureRollsBackEarlierMissingTableCreation() throws Exception {
        execute("DROP TABLE tb_hr_cand_skill");
        execute("ALTER TABLE tb_hr_cand_cntc ALTER COLUMN cntc_sn TYPE NUMERIC(6)");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_hr_cand_cntc");
        assertThat(text("SELECT to_regclass('tb_hr_cand_skill')::text")).isNull();
    }

    @Test
    void definitionsUseStoredCalculationTextAndNeverReseedExistingRows() throws Exception {
        execute("UPDATE tb_sy_calc_rule SET frml_cn='독립 시험 정의',dtl_cn='원문 보존' WHERE calc_id='식-60'");
        String before = snapshot();
        preparation.prepare();
        try (var session = factory.openSession()) {
            var definitions = new CandidateDefinitions(new CalculationRuleService(
                session.getMapper(CalculationRuleMapper.class), session.getMapper(UserPreferencePeriodSourceMapper.class)));
            var values = definitions.definitions();
            assertThat(values).extracting(value -> value.calcId()).containsExactly("식-00", "식-60", "식-61", "식-87");
            for (var value : values) {
                try (var query = database.prepareStatement("SELECT frml_cn,dtl_cn FROM tb_sy_calc_rule WHERE calc_id=?")) {
                    query.setString(1, value.calcId());
                    try (var rows = query.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(value.formula()).isEqualTo(rows.getString(1));
                        assertThat(value.details()).isEqualTo(rows.getString(2));
                    }
                }
            }
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMP_TYPE", "JOB", "GRADE", "CNTC_STAT", "CAND_SRC"})
    void commonCodeNamesAndOptionsFollowPreparedBaseData(String groupId) throws Exception {
        String before = snapshot();
        try (var session = factory.openSession()) {
            var codes = new CandidateCodes(new CommonCodeService(session.getMapper(CodeGroupMapper.class), mock(UserMapper.class)));
            var expected = new java.util.ArrayList<String>();
            try (var query = database.prepareStatement("SELECT code,code_nm FROM tb_sy_code "
                    + "WHERE code_grp_id=? AND del_yn='N' AND use_yn='Y' ORDER BY sort_ord,code")) {
                query.setString(1, groupId);
                try (var rows = query.executeQuery()) {
                    while (rows.next()) {
                        expected.add(rows.getString(1));
                        assertThat(codes.name(groupId, rows.getString(1))).isEqualTo(rows.getString(2));
                    }
                }
            }
            assertThat(expected).isNotEmpty();
            assertThat(codes.selectable(groupId)).extracting(item -> item.code).containsExactlyElementsOf(expected);
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    private void insertCandidate() throws Exception {
        execute("INSERT INTO tb_hr_cand(cand_id,cand_nm,emp_type_cd,job_cd,grade_cd,hope_uprc,"
            + "input_psbl_dt,cntc_stat_cd,last_cntc_dt,cand_src_cd,reg_user_id) "
            + "VALUES ('owned','시험 후보','FL','DEV','MD',6500000,DATE '2026-12-01','BEFORE',DATE '2026-11-01','SITE','admin')");
    }

    private String columns(String table) throws Exception {
        return text("SELECT string_agg(column_name,',' ORDER BY ordinal_position) FROM information_schema.columns "
            + "WHERE table_schema=current_schema() AND table_name='" + table + "'");
    }

    private String key(String table) throws Exception {
        return text("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='" + table + "'::regclass AND contype='p'");
    }

    private String shape() throws Exception {
        var result = new StringBuilder();
        for (String table : TABLES) {
            result.append(text("SELECT jsonb_agg(to_jsonb(c) ORDER BY ordinal_position)::text "
                + "FROM information_schema.columns c WHERE table_schema=current_schema() AND table_name='" + table + "'"));
            result.append(text("SELECT jsonb_agg(conname || ':' || pg_get_constraintdef(oid) ORDER BY conname)::text "
                + "FROM pg_constraint WHERE conrelid='" + table + "'::regclass"));
            result.append(text("SELECT jsonb_agg(indexdef ORDER BY indexname)::text FROM pg_indexes "
                + "WHERE schemaname=current_schema() AND tablename='" + table + "'"));
        }
        return result.toString();
    }

    private String snapshot() throws Exception {
        var tables = new java.util.ArrayList<String>();
        try (var statement = database.createStatement(); var rows = statement.executeQuery(
                "SELECT tablename FROM pg_tables WHERE schemaname=current_schema() ORDER BY tablename")) {
            while (rows.next()) tables.add(rows.getString(1));
        }
        var result = new StringBuilder();
        for (String table : tables) {
            result.append(table).append(text("SELECT coalesce(jsonb_agg(value ORDER BY value::text)::text,'[]') "
                + "FROM (SELECT to_jsonb(r) AS value FROM \"" + table.replace("\"", "\"\"") + "\" r) records"));
        }
        return result.toString();
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
