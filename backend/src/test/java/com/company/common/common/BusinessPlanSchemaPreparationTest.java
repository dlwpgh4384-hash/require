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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class BusinessPlanSchemaPreparationTest {
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
        schema = "businessplan_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET search_path TO " + schema);
        var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        source.setDriverProperties(properties);
        var config = new Configuration(new Environment("postgresql", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        for (String resource : List.of("mapper/CalculationRuleMapper.xml", "mapper/CodeGroupMapper.xml")) {
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
    void originalTargetColumnsKeyForeignKeyAndIndexesArePreparedWithoutSeedRows() throws Exception {
        assertThat(text("SELECT count(*)::text FROM tb_fi_biz_plan")).isEqualTo("0");
        assertThat(text("SELECT string_agg(column_name,',' ORDER BY ordinal_position) "
            + "FROM information_schema.columns WHERE table_schema=current_schema() "
            + "AND table_name='tb_fi_biz_plan'"))
            .isEqualTo("plan_ym,part_id,sales_tgt_amt,oprt_prft_tgt_amt,upld_id,reg_user_id,reg_dttm,mod_user_id,mod_dttm");
        assertThat(text("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
            + "WHERE conrelid='tb_fi_biz_plan'::regclass AND contype='p'"))
            .isEqualTo("PRIMARY KEY (plan_ym, part_id)");
        assertThat(text("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
            + "WHERE conrelid='tb_fi_biz_plan'::regclass AND contype='f'"))
            .isEqualTo("FOREIGN KEY (upld_id) REFERENCES tb_sy_upld(upld_id)");
        assertThat(text("SELECT pg_get_indexdef('ix_tb_fi_biz_plan_01'::regclass,1,true)"))
            .isEqualTo("part_id");
        assertThat(text("SELECT pg_get_indexdef('ix_tb_fi_biz_plan_02'::regclass,1,true)"))
            .isEqualTo("upld_id");
        assertThat(text("SELECT obj_description('tb_fi_biz_plan'::regclass)")).isNotBlank();
    }

    @Test
    void signedAndZeroTargetsUseOriginalCompositeIdentityWithoutPartForeignKey() throws Exception {
        execute("INSERT INTO tb_fi_biz_plan(plan_ym,part_id,sales_tgt_amt,oprt_prft_tgt_amt,reg_user_id) "
            + "VALUES ('202601','ALL',0,-999999999999999,'admin'), "
            + "('202601','owned-part',999999999999999,0,'admin')");
        assertThat(text("SELECT count(*)::text FROM tb_fi_biz_plan")).isEqualTo("2");
        assertThat(text("SELECT count(*)::text FROM tb_fi_biz_plan WHERE reg_dttm IS NOT NULL "
            + "AND mod_dttm IS NULL AND upld_id IS NULL")).isEqualTo("2");
        assertThatThrownBy(() -> execute("INSERT INTO tb_fi_biz_plan "
            + "(plan_ym,part_id,sales_tgt_amt,oprt_prft_tgt_amt,reg_user_id) "
            + "VALUES ('202601','ALL',1,2,'admin')")).isInstanceOf(java.sql.SQLException.class);
    }

    @Test
    void preparationAndDefinitionReadsPreserveTargetsActualsCloseRowsCodesAndFormulas() throws Exception {
        execute("INSERT INTO tb_fi_biz_plan(plan_ym,part_id,sales_tgt_amt,oprt_prft_tgt_amt,reg_user_id) "
            + "VALUES ('202601','ALL',0,-17,'admin'),('202601','owned-part',19,23,'admin')");
        execute("INSERT INTO tb_co_mon_pl(pl_ym,sales_amt,sales_cost_amt,sga_tot_amt,oprt_prft_amt,reg_user_id) "
            + "VALUES ('202601',31,13,7,11,'admin')");
        execute("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('202601','CLOSE','admin')");
        String before = snapshot();
        try (var session = factory.openSession()) {
            var definitions = new BusinessPlanComparisonDefinitions(new CalculationRuleService(
                session.getMapper(CalculationRuleMapper.class), mock(UserPreferencePeriodSourceMapper.class)));
            var values = definitions.definitions();
            assertThat(values).extracting(value -> value.calcId()).containsExactly("식-00", "식-30", "식-36");
            for (var value : values) {
                try (var query = database.prepareStatement(
                        "SELECT frml_cn,dtl_cn FROM tb_sy_calc_rule WHERE calc_id=?")) {
                    query.setString(1, value.calcId());
                    try (var rows = query.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(value.formula()).isEqualTo(rows.getString(1));
                        assertThat(value.details()).isEqualTo(rows.getString(2));
                    }
                }
            }
        }
        preparation.prepare();
        preparation.prepare();
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DSTRB_STEP", "FORM_TYPE", "UPLD_STAT"})
    void existingCodeServiceUsesDatabaseNamesAndExistingB10Code(String groupId) throws Exception {
        String before = snapshot();
        try (var session = factory.openSession()) {
            var service = new CommonCodeService(session.getMapper(CodeGroupMapper.class), mock(UserMapper.class));
            var options = service.getCommonCodes(groupId).items();
            assertThat(options).isNotEmpty();
            if (groupId.equals("FORM_TYPE")) {
                assertThat(options).extracting(option -> option.code).contains("B10");
            }
            try (var query = database.prepareStatement("SELECT code,code_nm FROM tb_sy_code "
                    + "WHERE code_grp_id=? AND del_yn='N' ORDER BY sort_ord,code")) {
                query.setString(1, groupId);
                try (var rows = query.executeQuery()) {
                    for (var option : options) {
                        assertThat(rows.next()).isTrue();
                        assertThat(option.code).isEqualTo(rows.getString(1));
                        assertThat(option.codeName).isEqualTo(rows.getString(2));
                    }
                    assertThat(rows.next()).isFalse();
                }
            }
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ALTER COLUMN sales_tgt_amt TYPE NUMERIC(16)",
        "ALTER COLUMN sales_tgt_amt DROP NOT NULL",
        "ALTER COLUMN sales_tgt_amt SET DEFAULT 0",
        "ALTER COLUMN mod_dttm TYPE TIMESTAMP",
        "ADD COLUMN lock_yn CHAR(1)",
        "DROP CONSTRAINT pk_tb_fi_biz_plan",
        "DROP CONSTRAINT fk_tb_fi_biz_plan_01"
    })
    void incompatibleExistingDefinitionIsReportedWithoutRepair(String change) throws Exception {
        execute("ALTER TABLE tb_fi_biz_plan " + change);
        String before = shape();
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_fi_biz_plan");
        assertThat(shape()).isEqualTo(before);
    }

    @Test
    void reversedPrimaryKeyIsNotSilentlyAcceptedOrReplaced() throws Exception {
        execute("ALTER TABLE tb_fi_biz_plan DROP CONSTRAINT pk_tb_fi_biz_plan");
        execute("ALTER TABLE tb_fi_biz_plan ADD CONSTRAINT pk_tb_fi_biz_plan PRIMARY KEY(part_id,plan_ym)");
        String before = shape();
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_fi_biz_plan");
        assertThat(shape()).isEqualTo(before);
    }

    @Test
    void wrongIndexIsReportedWithoutReplacement() throws Exception {
        execute("DROP INDEX ix_tb_fi_biz_plan_01");
        execute("CREATE INDEX ix_tb_fi_biz_plan_01 ON tb_fi_biz_plan(sales_tgt_amt)");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("ix_tb_fi_biz_plan_01");
        assertThat(text("SELECT pg_get_indexdef('ix_tb_fi_biz_plan_01'::regclass,1,true)"))
            .isEqualTo("sales_tgt_amt");
    }

    @Test
    void lateTargetMismatchRollsBackEarlierNewFinanceTable() throws Exception {
        execute("DROP TABLE tb_co_prev_adj");
        execute("ALTER TABLE tb_fi_biz_plan ALTER COLUMN sales_tgt_amt TYPE NUMERIC(16)");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_fi_biz_plan");
        assertThat(text("SELECT to_regclass('tb_co_prev_adj')::text")).isNull();
    }

    private String shape() throws Exception {
        return text("SELECT jsonb_agg(to_jsonb(c) ORDER BY ordinal_position)::text "
            + "FROM information_schema.columns c WHERE table_schema=current_schema() AND table_name='tb_fi_biz_plan'")
            + text("SELECT jsonb_agg(pg_get_constraintdef(oid) ORDER BY conname)::text "
                + "FROM pg_constraint WHERE conrelid='tb_fi_biz_plan'::regclass");
    }

    private String snapshot() throws Exception {
        var result = new StringBuilder();
        for (String table : List.of("tb_fi_biz_plan", "tb_co_mon_pl", "tb_cl_close", "tb_sy_upld",
                "tb_sy_upld_err", "tb_sy_calc_rule", "tb_sy_code", "tb_sy_oper_std")) {
            result.append(text("SELECT coalesce(jsonb_agg(value ORDER BY value::text)::text,'[]') "
                + "FROM (SELECT to_jsonb(r) AS value FROM " + table + " r) records"));
        }
        return result.toString();
    }

    private void execute(String sql) throws Exception {
        try (var statement = database.createStatement()) { statement.execute(sql); }
    }

    private String text(String sql) throws Exception {
        try (var statement = database.createStatement(); var rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }
}
