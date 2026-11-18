package com.company.common.withdrawals.service;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.users.mapper.UserMapper;
import java.math.BigDecimal;
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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class WithdrawalDefinitionPersistenceTest {
    private Connection database;
    private String schema;
    private SqlSessionFactory sessions;
    private SchemaPreparation preparation;

    @BeforeEach void independentSchema() throws Exception {
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        database = DriverManager.getConnection(url, user, password);
        schema = "withdrawal_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET search_path TO " + schema);
        var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        source.setDriverProperties(properties);
        var configuration = new Configuration(new Environment("postgresql", new JdbcTransactionFactory(), source));
        configuration.setMapUnderscoreToCamelCase(false);
        configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        for (String resource : List.of("mapper/CalculationRuleMapper.xml",
                "mapper/UserPreferencePeriodSourceMapper.xml", "mapper/CodeGroupMapper.xml")) {
            try (var input = new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        sessions = new SqlSessionFactoryBuilder().build(configuration);
        preparation = new SchemaPreparation(sessions);
        preparation.prepare();
    }

    @AfterEach void removeOwnedSchema() throws Exception {
        if (database != null) {
            try {
                if (schema != null) execute("DROP SCHEMA " + schema + " CASCADE");
            } finally { database.close(); }
        }
    }

    @Test void changedUsedDefinitionChangesCalculationAndPreparationNeverOverwritesIt() throws Exception {
        try (var session = sessions.openSession(true)) {
            var definitions = new CalculationRuleService(session.getMapper(CalculationRuleMapper.class),
                session.getMapper(UserPreferencePeriodSourceMapper.class));
            var rules = new WithdrawalCalculationRules(definitions);
            var input = new WithdrawalCalculationRules.ScoreInput(true, false, 1, 0, false, new BigDecimal("60"));
            assertThat(rules.aiRecommendScore(input)).isEqualByComparingTo("80");
            execute("UPDATE TB_SY_CALC_RULE SET FRML_CN=replace(FRML_CN,'일치 40','일치 32') WHERE CALC_ID='식-80'");
            execute("UPDATE TB_SY_CALC_RULE SET FRML_CN='미사용 정의의 독립 변경' WHERE CALC_ID='식-58'");
            session.clearCache();
            String before = text("SELECT jsonb_agg(to_jsonb(r) ORDER BY calc_id)::text FROM tb_sy_calc_rule r");
            assertThat(rules.aiRecommendScore(input)).isEqualByComparingTo("72");
            preparation.prepare();
            assertThat(rules.aiRecommendScore(input)).isEqualByComparingTo("72");
            assertThat(text("SELECT jsonb_agg(to_jsonb(r) ORDER BY calc_id)::text FROM tb_sy_calc_rule r"))
                .isEqualTo(before);
        }
    }

    @Test void optionsMatchExistingCodeSourceWithoutAddingUnknownOrPreviewCodes() throws Exception {
        String before = text("SELECT jsonb_agg(to_jsonb(c) ORDER BY code_grp_id,code)::text FROM tb_sy_code c");
        try (var session = sessions.openSession(true)) {
            var source = new CommonCodeService(session.getMapper(CodeGroupMapper.class), mock(UserMapper.class));
            var codes = new WithdrawalCodes(source);
            for (String group : List.of("EXT_PSBL", "JOB")) {
                var expected = source.getCommonCodes(group).items().stream()
                    .filter(item -> "Y".equals(item.useYn)).toList();
                assertThat(codes.options(group)).usingRecursiveComparison().isEqualTo(expected);
                assertThat(expected).isNotEmpty();
            }
            assertThat(codes.extensionStorageCode("")).isNull();
            assertThat(text("SELECT count(*)::text FROM tb_sy_code WHERE code_grp_id='EXT_PSBL' AND code NOT IN ('H','M','L')"))
                .isEqualTo("0");
        }
        assertThat(text("SELECT jsonb_agg(to_jsonb(c) ORDER BY code_grp_id,code)::text FROM tb_sy_code c"))
            .isEqualTo(before);
    }

    @Test void existingPreparationIncludesWithdrawalMenuAndOnlyDeployedSourceTables() throws Exception {
        assertThat(text("SELECT menu_id||'/'||upper_menu_id FROM tb_sy_menu WHERE scrn_id='UI-05'"))
            .isEqualTo("M007/G003");
        assertThat(text("SELECT to_regclass('tb_sl_rcmd_log')::text")).isNull();
        for (String table : List.of("tb_pj_asgn", "tb_pj_asgn_hist", "tb_sl_tent_asgn",
                "tb_pj_asgn_mon", "tb_pj_mnpw_plan")) {
            assertThat(text("SELECT to_regclass('" + table + "')::text")).isEqualTo(table);
        }
    }

    private void execute(String sql) throws Exception {
        try (var statement = database.createStatement()) { statement.execute(sql); }
    }

    private String text(String sql) throws Exception {
        try (var statement = database.createStatement(); var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }
}
