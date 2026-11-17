package com.company.common.projects.service;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.users.mapper.UserMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;
import java.util.UUID;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class ProjectSchemaPreparationTest {
    private Connection database;
    private String schema;
    private UnpooledDataSource source;

    @BeforeEach
    void independentSchema() throws Exception {
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        database = DriverManager.getConnection(url, user, password);
        schema = "projects_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET search_path TO " + schema);
        source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        source.setDriverProperties(properties);
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
    void freshFlywayStartupCreatesAllProjectSourcesWithoutBusinessSeeds() throws Exception {
        flyway().migrate();
        assertThat(text("SELECT count(*)::text FROM information_schema.tables WHERE table_schema=current_schema() AND table_type='BASE TABLE'"))
            .isEqualTo("48");
        assertThat(text("SELECT count(*)::text FROM tb_pj_proj")).isEqualTo("0");
        assertThat(text("SELECT count(*)::text FROM tb_pj_drct_exps")).isEqualTo("0");
        assertThat(text("SELECT count(*)::text FROM flyway_schema_history WHERE success AND version='12'"))
            .isEqualTo("1");
        assertThat(flyway().migrate().migrationsExecuted).isZero();
    }

    @Test
    void existingUnversionedSchemaBaselinesAtZeroReplaysAllVersionsAndPreservesRows() throws Exception {
        prepareOriginalResources();
        execute("INSERT INTO tb_pj_proj(proj_id,proj_nm,ctrt_type_cd,proj_stat_cd,ctrt_bgn_dt,ctrt_end_dt,ctrt_amt,bdgt_amt,bdgt_manual_yn,team_exps_amt,proj_exps_amt,reg_user_id) VALUES ('P-0001','보존 프로젝트','MO','ING','2026-01-01','2026-12-31',1000000,123456,'Y',50000,50000,'admin')");
        execute("UPDATE tb_sy_calc_rule SET frml_cn='예산 기본값 = 현재 계약금액 × (0.9 − 목표 마진율)' WHERE calc_id='식-19'");
        String codes = text("SELECT jsonb_agg(to_jsonb(c) ORDER BY code_grp_id,code)::text FROM tb_sy_code c");
        flyway().migrate();
        assertThat(text("SELECT version FROM flyway_schema_history WHERE type='BASELINE'")).isEqualTo("0");
        assertThat(text("SELECT count(*)::text FROM flyway_schema_history WHERE type='SQL' AND success"))
            .isEqualTo(Integer.toString(new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*__*.sql").length));
        assertThat(text("SELECT bdgt_amt::text FROM tb_pj_proj WHERE proj_id='P-0001'")).isEqualTo("123456");
        assertThat(text("SELECT bdgt_manual_yn FROM tb_pj_proj WHERE proj_id='P-0001'")).isEqualTo("Y");
        assertThat(text("SELECT frml_cn FROM tb_sy_calc_rule WHERE calc_id='식-19'"))
            .contains("0.9");
        assertThat(text("SELECT jsonb_agg(to_jsonb(c) ORDER BY code_grp_id,code)::text FROM tb_sy_code c")).isEqualTo(codes);
        execute(projectResource());
        execute(projectResource());
        assertThat(text("SELECT nextval('sq_tb_pj_issue')::text")).isEqualTo("1");
        execute(projectResource());
        assertThat(text("SELECT nextval('sq_tb_pj_issue')::text")).isEqualTo("2");
    }

    @Test
    void realMapperRequeriesChangedFormulaAndMarginWithoutReseeding() throws Exception {
        flyway().migrate();
        assertThat(automaticBudget()).isEqualByComparingTo("800000");
        execute("UPDATE tb_sy_calc_rule SET frml_cn='예산 기본값 = 현재 계약금액 × (0.9 − 목표 마진율)' WHERE calc_id='식-19'");
        assertThat(automaticBudget()).isEqualByComparingTo("700000");
        execute("INSERT INTO tb_sy_oper_std(std_cd,aply_bgn_ym,std_nm,std_val,unit_nm,reg_user_id) VALUES ('TGT_MRGN_RT','202611','목표 마진율',25,'%', 'admin')");
        assertThat(automaticBudget()).isEqualByComparingTo("650000");
        execute(projectResource());
        assertThat(automaticBudget()).isEqualByComparingTo("650000");
        try (var session = sessions().openSession()) {
            var service = new CalculationRuleService(session.getMapper(CalculationRuleMapper.class),
                session.getMapper(UserPreferencePeriodSourceMapper.class));
            assertThat(new ProjectAmountRules(service).definitions()).hasSize(17).doesNotContainNull();
        }
    }

    @Test
    void sameSessionReadsChangedFormulaAndEffectiveStandardWithoutClearingItsCache() throws Exception {
        flyway().migrate();
        String unchanged = text("SELECT jsonb_agg(to_jsonb(r) ORDER BY calc_id)::text FROM tb_sy_calc_rule r WHERE calc_id!='식-19'");
        try (var session = sessions().openSession()) {
            var service = new CalculationRuleService(session.getMapper(CalculationRuleMapper.class),
                session.getMapper(UserPreferencePeriodSourceMapper.class));
            var amounts = new ProjectAmountRules(service);
            var contract = new BigDecimal("1000000");
            var month = YearMonth.of(2026, 11);
            assertThat(amounts.automaticBudget(contract, month)).isEqualByComparingTo("800000");
            execute("UPDATE tb_sy_calc_rule SET frml_cn='예산 기본값 = 현재 계약금액 × (0.9 − 목표 마진율)' WHERE calc_id='식-19'");
            assertThat(amounts.automaticBudget(contract, month)).isEqualByComparingTo("700000");
            execute("INSERT INTO tb_sy_oper_std(std_cd,aply_bgn_ym,std_nm,std_val,unit_nm,reg_user_id) VALUES ('TGT_MRGN_RT','202611','목표 마진율',25,'%', 'admin')");
            assertThat(amounts.automaticBudget(contract, month)).isEqualByComparingTo("650000");
            assertThat(service.definition("식-19").formula()).contains("0.9");
        }
        assertThat(text("SELECT jsonb_agg(to_jsonb(r) ORDER BY calc_id)::text FROM tb_sy_calc_rule r WHERE calc_id!='식-19'"))
            .isEqualTo(unchanged);
    }

    @Test
    void codeOptionsUseOnlyOriginalDmlValuesAndEveryRequestedGroupExists() throws Exception {
        flyway().migrate();
        try (var session = sessions().openSession()) {
            var management = mock(UserMapper.class);
            var codes = new CommonCodeService(session.getMapper(CodeGroupMapper.class), management);
            assertThat(codes.getCommonCodes("CTRT_TYPE").items()).extracting(item -> item.code)
                .containsExactly("TK", "MO", "DP", "IN");
            assertThat(codes.getCommonCodes("LOSE_RSN").items()).extracting(item -> item.code)
                .containsExactly("PRICE", "TECH", "SCHD", "CNCL", "ETC");
            verifyNoInteractions(management);
        }
        assertThat(text("SELECT string_agg(code,',' ORDER BY sort_ord) FROM tb_sy_code WHERE code_grp_id='CTRT_TYPE'"))
            .isEqualTo("TK,MO,DP,IN");
        assertThat(text("SELECT string_agg(code,',' ORDER BY sort_ord) FROM tb_sy_code WHERE code_grp_id='LOSE_RSN'"))
            .isEqualTo("PRICE,TECH,SCHD,CNCL,ETC");
        for (String group : new String[]{"CTRT_TYPE", "EMP_TYPE", "JOB", "GRADE", "JBPS", "INPUT_TYPE",
                "DSTRB_STEP", "EXT_PSBL", "DRCT_EXPS_ITEM", "CHG_ITEM", "PROJ_STAT", "WORK_STAT",
                "ASGN_CHG_RSN", "LOSE_RSN", "CHG_TYPE", "DRCT_CLS", "SRC", "ROW_TYPE", "ASGN_STAT", "ASGN_CHG_TYPE"}) {
            try (var statement = database.prepareStatement("SELECT count(*) FROM tb_sy_code WHERE code_grp_id=?")) {
                statement.setString(1, group);
                try (var rows = statement.executeQuery()) { rows.next(); assertThat(rows.getInt(1)).isPositive(); }
            }
        }
        String before = text("SELECT jsonb_agg(to_jsonb(c) ORDER BY code_grp_id,code)::text FROM tb_sy_code c");
        execute(projectResource());
        assertThat(text("SELECT jsonb_agg(to_jsonb(c) ORDER BY code_grp_id,code)::text FROM tb_sy_code c"))
            .isEqualTo(before);
        assertThat(text("SELECT count(*)::text FROM tb_sy_code WHERE code_grp_id='CTRT_TYPE' AND code NOT IN ('TK','MO','DP','IN')"))
            .isEqualTo("0");
    }

    @Test
    void incompatibleOriginalColumnIsRejectedRatherThanAltered() throws Exception {
        flyway().migrate();
        execute("ALTER TABLE tb_pj_drct_exps ALTER COLUMN exps_amt TYPE NUMERIC(14)");
        assertThatThrownBy(() -> execute(projectResource()))
            .hasMessageContaining("tb_pj_drct_exps").hasMessageContaining("original columns");
        assertThat(text("SELECT numeric_precision::text FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_pj_drct_exps' AND column_name='exps_amt'"))
            .isEqualTo("14");
    }

    @Test
    void unsupportedDbDefinitionIsDiagnosableOnTheNextRead() throws Exception {
        flyway().migrate();
        execute("UPDATE tb_sy_calc_rule SET frml_cn='지원하지 않는 계산식' WHERE calc_id='식-19'");
        assertThatThrownBy(this::automaticBudget).isInstanceOf(IllegalStateException.class).hasMessageContaining("식-19");
    }

    private Flyway flyway() {
        return Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").baselineOnMigrate(true).baselineVersion("0").load();
    }

    private void prepareOriginalResources() throws Exception {
        var resources = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql");
        for (var resource : Arrays.stream(resources).sorted(Comparator.comparingInt(item -> version(item.getFilename()))).toList()) {
            if (version(resource.getFilename()) < 12) execute(resource.getContentAsString(StandardCharsets.UTF_8));
        }
    }

    private static int version(String name) { return Integer.parseInt(name.substring(1, name.indexOf("__"))); }

    private SqlSessionFactory sessions() throws Exception {
        var config = new Configuration(new Environment("postgresql", new JdbcTransactionFactory(), source));
        for (String name : new String[]{"mapper/CalculationRuleMapper.xml", "mapper/UserPreferencePeriodSourceMapper.xml",
                "mapper/CodeGroupMapper.xml"}) {
            try (var stream = new ClassPathResource(name).getInputStream()) {
                new org.apache.ibatis.builder.xml.XMLMapperBuilder(stream, config, name, config.getSqlFragments()).parse();
            }
        }
        return new SqlSessionFactoryBuilder().build(config);
    }

    private BigDecimal automaticBudget() throws Exception {
        try (var session = sessions().openSession()) {
            var service = new CalculationRuleService(session.getMapper(CalculationRuleMapper.class),
                session.getMapper(UserPreferencePeriodSourceMapper.class));
            return new ProjectAmountRules(service).automaticBudget(new BigDecimal("1000000"), YearMonth.of(2026, 11));
        }
    }

    private String projectResource() throws Exception {
        return new ClassPathResource("db/migration/V12__project_operations_schema.sql").getContentAsString(StandardCharsets.UTF_8);
    }

    private void execute(String sql) throws Exception { try (var statement = database.createStatement()) { statement.execute(sql); } }
    private String text(String sql) throws Exception {
        try (var statement = database.createStatement(); var rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }
}
