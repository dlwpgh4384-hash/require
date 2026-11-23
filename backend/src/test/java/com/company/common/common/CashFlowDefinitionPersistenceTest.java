package com.company.common.common;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.service.CodeFailure;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class CashFlowDefinitionPersistenceTest {
    private Connection database;
    private String schema;
    private SqlSessionFactory factory;

    @BeforeEach void independentSchema() throws Exception {
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        database = DriverManager.getConnection(url, user, password);
        schema = "cashdefinitions_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET search_path TO " + schema);
        var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        source.setDriverProperties(properties);
        var config = new Configuration(new Environment("postgresql", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        for (var resource : new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml")) {
            try (var input = resource.getInputStream()) {
                new XMLMapperBuilder(input, config, resource.toString(), config.getSqlFragments()).parse();
            }
        }
        factory = new SqlSessionFactoryBuilder().build(config);
        new SchemaPreparation(factory).prepare();
    }

    @AfterEach void removeOwnedSchema() throws Exception {
        if (database != null) {
            try {
                if (schema != null) execute("DROP SCHEMA " + schema + " CASCADE");
            } finally {
                database.close();
            }
        }
    }

    private CashFlowStatementDefinitions definitions(org.apache.ibatis.session.SqlSession session) {
        return new CashFlowStatementDefinitions(new CalculationRuleService(
            session.getMapper(CalculationRuleMapper.class), session.getMapper(UserPreferencePeriodSourceMapper.class)),
            session.getMapper(ProfitStructureMapper.class));
    }

    @Test void existingSeedDefinitionsAndSevenCodeGroupsAreReadWithoutSourceMutation() throws Exception {
        String before = snapshot();
        try (var session = factory.openSession()) {
            var definitions = definitions(session);
            assertThat(definitions.definitions()).hasSize(12);
            for (var definition : definitions.definitions()) {
                try (var query = database.prepareStatement(
                    "SELECT frml_cn,dtl_cn FROM tb_sy_calc_rule WHERE calc_id=?")) {
                    query.setString(1, definition.calcId());
                    try (var result = query.executeQuery()) {
                        assertThat(result.next()).isTrue();
                        assertThat(definition.formula()).isEqualTo(result.getString(1));
                        assertThat(definition.details()).isEqualTo(result.getString(2));
                    }
                }
            }
            var codes = definitions.codes();
            assertThat(codes).extracting(code -> code.groupCode()).contains(
                "EMP_TYPE", "SGA_ITEM", "FIN_CLS", "DRCT_CLS", "PAY_TYPE", "IO", "XFER_CLS");
            for (var code : codes) {
                try (var query = database.prepareStatement(
                    "SELECT code_nm,code_val FROM tb_sy_code WHERE code_grp_id=? AND code=?")) {
                    query.setString(1, code.groupCode());
                    query.setString(2, code.code());
                    try (var result = query.executeQuery()) {
                        assertThat(result.next()).isTrue();
                        assertThat(code.name()).isEqualTo(result.getString(1));
                        assertThat(code.value()).isEqualTo(result.getBigDecimal(2));
                    }
                }
            }
            var rules = definitions.paymentRules();
            assertThat(rules.freelancerPayment(new BigDecimal("5000000"), BigDecimal.ZERO))
                .isEqualByComparingTo("4835000");
            assertThat(rules.vendorPayment(new BigDecimal("8000000"))).isEqualByComparingTo("8800000");
        }
        assertThat(snapshot()).isEqualTo(before);
        assertThat(text("SELECT to_regclass('tb_fi_cash')::text")).isNull();
    }

    @Test void independentlyCommittedDefinitionChangeIsConsumedByANewRequest() throws Exception {
        try (var session = factory.openSession()) {
            assertThat(definitions(session).paymentRules().freelancerWithholdingRate())
                .isEqualByComparingTo("0.033");
        }
        execute("UPDATE tb_sy_calc_rule SET frml_cn=replace(frml_cn,'3.3%','4%') WHERE calc_id='식-49'");
        String changed = snapshot();
        try (var session = factory.openSession()) {
            assertThat(definitions(session).paymentRules()
                .freelancerPayment(new BigDecimal("5000000"), BigDecimal.ZERO)).isEqualByComparingTo("4800000");
        }
        assertThat(snapshot()).isEqualTo(changed);
    }

    @Test void absentDefinitionDoesNotAcquireAFallbackOrNewSeedRow() throws Exception {
        execute("DELETE FROM tb_sy_calc_rule WHERE calc_id='식-49'");
        String before = snapshot();
        try (var session = factory.openSession()) {
            assertThatThrownBy(() -> definitions(session).paymentRules())
                .isInstanceOfSatisfying(CodeFailure.class, failure ->
                    assertThat(failure.getField()).isEqualTo("calcId"));
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test void disabledDatabaseCodeGroupIsRejectedByTheReportRulesWithoutRestoringRows() throws Exception {
        try (var session = factory.openSession()) {
            assertThat(definitions(session).paymentRules()).isNotNull();
        }
        execute("UPDATE tb_sy_code SET use_yn='N' WHERE code_grp_id='IO'");
        String disabled = snapshot();
        try (var session = factory.openSession()) {
            assertThatThrownBy(() -> definitions(session).paymentRules())
                .isInstanceOfSatisfying(CodeFailure.class, failure -> {
                    assertThat(failure.getStatus()).isEqualTo(400);
                    assertThat(failure.getField()).isEqualTo("code");
                });
        }
        assertThat(snapshot()).isEqualTo(disabled);
    }

    @Test void blankDatabaseFormulaDoesNotAcquireAReplacementDefinition() throws Exception {
        execute("UPDATE tb_sy_calc_rule SET frml_cn=' ' WHERE calc_id='식-44'");
        String blank = snapshot();
        try (var session = factory.openSession()) {
            assertThatThrownBy(() -> definitions(session).paymentRules())
                .isInstanceOfSatisfying(CodeFailure.class, failure -> {
                    assertThat(failure.getField()).isEqualTo("calcId");
                    assertThat(failure.getMessage()).contains("식-44");
                });
        }
        assertThat(snapshot()).isEqualTo(blank);
    }

    private String snapshot() throws Exception {
        var result = new StringBuilder();
        for (String table : List.of("tb_sy_calc_rule", "tb_sy_code", "tb_sy_oper_std", "tb_bl_bill",
            "tb_bl_rcpt", "tb_hr_mon_pay", "tb_fi_item", "tb_fi_card_mon", "tb_fi_xfer", "tb_co_mon_pl",
            "tb_sy_accs_log")) {
            result.append(text("SELECT coalesce(jsonb_agg(value ORDER BY value::text)::text,'[]') "
                + "FROM (SELECT to_jsonb(r) AS value FROM " + table + " r) records"));
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
