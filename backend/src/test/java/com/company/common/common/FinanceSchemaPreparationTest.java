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
class FinanceSchemaPreparationTest {
    private static final List<String> TABLES = List.of(
        "tb_co_mon_pl", "tb_co_prev_adj", "tb_fi_item", "tb_fi_corp_tax", "tb_fi_tax_pay",
        "tb_fi_loan", "tb_fi_card", "tb_fi_card_mon", "tb_fi_card_pay", "tb_fi_xfer");
    private Connection database;
    private String schema;
    private SchemaPreparation preparation;
    private SqlSessionFactory factory;

    @BeforeEach
    void independentSchema() throws Exception {
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        database = DriverManager.getConnection(url, user, password);
        schema = "finance_" + UUID.randomUUID().toString().replace("-", "");
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
    void originalFinanceSourcesArePreparedWithoutBusinessRows() throws Exception {
        for (String table : TABLES) {
            assertThat(text("SELECT count(*)::text FROM " + table)).as(table).isEqualTo("0");
            assertThat(text("SELECT obj_description('" + table + "'::regclass)"))
                .as(table).isNotBlank();
        }
        assertThat(columns("tb_fi_card_mon"))
            .isEqualTo("pay_ym,card_sum_amt,cash_amt,edit_yn,reg_user_id,reg_dttm,mod_user_id,mod_dttm");
        assertThat(columns("tb_fi_card_pay"))
            .isEqualTo("pay_ym,card_id,pay_amt,reg_user_id,reg_dttm,mod_user_id,mod_dttm");
        assertThat(columns("tb_fi_item"))
            .isEqualTo("fin_ym,item_sn,fin_cls_cd,sga_item_cd,item_nm,amt,vat_amt,io_dt,rpt_yn,auto_fill_yn,rmrk,upld_id,reg_user_id,reg_dttm,mod_user_id,mod_dttm");
        assertThat(text("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
            + "WHERE conrelid='tb_fi_item'::regclass AND contype='p'"))
            .isEqualTo("PRIMARY KEY (fin_ym, item_sn)");
        assertThat(text("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
            + "WHERE conrelid='tb_fi_tax_pay'::regclass AND contype='p'"))
            .isEqualTo("PRIMARY KEY (tax_yr, pay_type_cd)");
        assertThat(text("SELECT is_nullable FROM information_schema.columns "
            + "WHERE table_schema=current_schema() AND table_name='tb_fi_tax_pay' AND column_name='pay_amt'"))
            .isEqualTo("YES");
    }

    @Test
    void definitionsAndOperatingStandardsUseActualOriginalFields() throws Exception {
        try (var session = factory.openSession()) {
            var metadata = new CalculationRuleService(session.getMapper(CalculationRuleMapper.class),
                mock(UserPreferencePeriodSourceMapper.class));
            var definitions = new FinanceItemDefinitions(metadata).definitions();
            assertThat(definitions).extracting(value -> value.calcId()).containsExactly(
                "식-00", "식-29", "식-31", "식-47", "식-48", "식-50", "식-57", "식-59");
            for (var definition : definitions) {
                try (var query = database.prepareStatement(
                        "SELECT frml_cn,dtl_cn FROM tb_sy_calc_rule WHERE calc_id=?")) {
                    query.setString(1, definition.calcId());
                    try (var rows = query.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(definition.formula()).isEqualTo(rows.getString(1));
                        assertThat(definition.details()).isEqualTo(rows.getString(2));
                    }
                }
            }
            var row = session.getMapper(CalculationRuleMapper.class)
                .selectEffectiveStandard("PART_DSTRB_RT", "202611");
            assertThat(row).isNotNull();
            assertThat(row.value().value().toPlainString()).isEqualTo(text(
                "SELECT std_val::text FROM tb_sy_oper_std WHERE std_cd='PART_DSTRB_RT' "
                + "AND aply_bgn_ym<='202611' ORDER BY aply_bgn_ym DESC LIMIT 1"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"DSTRB_STEP", "SGA_ITEM", "FIN_CLS", "FORM_TYPE", "UPLD_STAT",
        "ADJ_ITEM", "PAY_TYPE", "IO", "XFER_CLS"})
    void commonCodeOptionsMatchOriginalDatabaseRows(String groupId) throws Exception {
        try (var session = factory.openSession()) {
            var codes = new CommonCodeService(session.getMapper(CodeGroupMapper.class), mock(UserMapper.class));
            var options = codes.getCommonCodes(groupId).items();
            assertThat(options).isNotEmpty();
            try (var query = database.prepareStatement(
                    "SELECT code,code_nm FROM tb_sy_code WHERE code_grp_id=? AND del_yn='N' ORDER BY sort_ord,code")) {
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
    }

    @Test
    void repeatPreparationPreservesAllSourcesCodesFormulasAndSequencePositions() throws Exception {
        execute("INSERT INTO tb_fi_item(fin_ym,item_sn,fin_cls_cd,item_nm,amt,vat_amt,reg_user_id) "
            + "SELECT '202611',1,code,'독립 항목',123,12,'admin' FROM tb_sy_code "
            + "WHERE code_grp_id='FIN_CLS' ORDER BY sort_ord,code LIMIT 1");
        execute("INSERT INTO tb_fi_card(card_id,card_nm,card_no_l4,reg_user_id) "
            + "VALUES ('owned-card','독립 카드','4321','admin')");
        execute("INSERT INTO tb_fi_card_mon(pay_ym,card_sum_amt,cash_amt,edit_yn,reg_user_id) "
            + "VALUES ('202611',57,61,'Y','admin')");
        execute("INSERT INTO tb_fi_card_pay(pay_ym,card_id,pay_amt,reg_user_id) "
            + "VALUES ('202611','owned-card',57,'admin')");
        execute("INSERT INTO tb_fi_corp_tax(tax_yr,est_tax_amt,reg_user_id) VALUES ('2026',120000000,'admin')");
        execute("INSERT INTO tb_fi_tax_pay(tax_yr,pay_type_cd,pay_due_dt,pay_amt,pay_dt,pay_yn,reg_user_id) "
            + "VALUES ('2026','MID','2026-08-31',123,'2026-09-01','Y','admin')");
        execute("INSERT INTO tb_fi_loan(loan_dt,io_cd,amt,reg_user_id) VALUES ('2026-11-01','IN',37,'admin')");
        execute("INSERT INTO tb_fi_xfer(xfer_dt,xfer_cls_cd,item_nm,amt,reg_user_id) "
            + "VALUES ('2026-11-02','EXP','독립 이체',41,'admin')");
        execute("INSERT INTO tb_co_prev_adj(aply_ym,trgt_ym,adj_item_cd,adj_amt,adj_rsn,reg_user_id) "
            + "VALUES ('202611','202610','SGA',-43,'독립 사유','admin')");
        execute("INSERT INTO tb_co_mon_pl(pl_ym,sales_amt,sales_cost_amt,sga_tot_amt,oprt_prft_amt,fix_yn,reg_user_id) "
            + "VALUES ('202610',101,51,23,27,'Y','admin')");
        execute("UPDATE tb_sy_calc_rule SET dtl_cn=dtl_cn || ' 독립 변경' WHERE calc_id='식-31'");
        execute("UPDATE tb_sy_code SET code_nm=code_nm || ' 독립 변경' WHERE code_grp_id='FIN_CLS'");
        String before = snapshot();
        String positions = sequencePositions();
        preparation.prepare();
        preparation.prepare();
        assertThat(snapshot()).isEqualTo(before);
        assertThat(sequencePositions()).isEqualTo(positions);
        try (var session = factory.openSession()) {
            var definitions = new FinanceItemDefinitions(new CalculationRuleService(
                session.getMapper(CalculationRuleMapper.class), mock(UserPreferencePeriodSourceMapper.class)));
            assertThat(definitions.definitions().stream().filter(row -> row.calcId().equals("식-31"))
                .findFirst().orElseThrow().details()).endsWith("독립 변경");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"tb_fi_item", "tb_fi_card_mon", "tb_fi_card_pay", "tb_fi_tax_pay", "tb_co_mon_pl"})
    void incompatibleColumnIsReportedWithoutDestructiveRepair(String table) throws Exception {
        execute("ALTER TABLE " + table + " ALTER COLUMN mod_dttm TYPE TIMESTAMP");
        assertThatThrownBy(preparation::prepare).hasMessageContaining(table);
        assertThat(text("SELECT format_type(atttypid,atttypmod) FROM pg_attribute WHERE attrelid='"
            + table + "'::regclass AND attname='mod_dttm'"))
            .isEqualTo("timestamp without time zone");
    }

    @Test
    void incompatibleNaturalKeyIsReportedWithoutReplacement() throws Exception {
        execute("ALTER TABLE tb_fi_item DROP CONSTRAINT pk_tb_fi_item");
        execute("ALTER TABLE tb_fi_item ADD CONSTRAINT pk_tb_fi_item PRIMARY KEY(item_sn,fin_ym)");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_fi_item");
        assertThat(text("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
            + "WHERE conrelid='tb_fi_item'::regclass AND contype='p'"))
            .isEqualTo("PRIMARY KEY (item_sn, fin_ym)");
    }

    @Test
    void missingFlagCheckIsReportedWithoutReplacement() throws Exception {
        execute("ALTER TABLE tb_fi_tax_pay DROP CONSTRAINT ck_tb_fi_tax_pay_01");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_fi_tax_pay");
        assertThat(text("SELECT count(*)::text FROM pg_constraint WHERE conrelid='tb_fi_tax_pay'::regclass "
            + "AND conname='ck_tb_fi_tax_pay_01'")).isEqualTo("0");
    }

    @Test
    void missingOriginalForeignKeyIsReportedWithoutPatchingTheExistingTable() throws Exception {
        execute("ALTER TABLE tb_fi_card_pay DROP CONSTRAINT fk_tb_fi_card_pay_02");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("FK_TB_FI_CARD_PAY_02");
        assertThat(text("SELECT count(*)::text FROM pg_constraint WHERE conrelid='tb_fi_card_pay'::regclass "
            + "AND conname='fk_tb_fi_card_pay_02'")).isEqualTo("0");
    }

    @Test
    void wrongIndexIsReportedWithoutReplacement() throws Exception {
        execute("DROP INDEX ix_tb_fi_loan_01");
        execute("CREATE INDEX ix_tb_fi_loan_01 ON tb_fi_loan(amt)");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("ix_tb_fi_loan_01");
        assertThat(text("SELECT pg_get_indexdef('ix_tb_fi_loan_01'::regclass,1,true)"))
            .isEqualTo("amt");
    }

    @Test
    void wrongSequenceIsReportedWithoutRestartingIt() throws Exception {
        execute("ALTER SEQUENCE sq_tb_fi_loan INCREMENT BY 2");
        String before = text("SELECT last_value::text FROM sq_tb_fi_loan");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("sq_tb_fi_loan");
        assertThat(text("SELECT last_value::text FROM sq_tb_fi_loan")).isEqualTo(before);
        assertThat(text("SELECT seqincrement::text FROM pg_sequence WHERE seqrelid='sq_tb_fi_loan'::regclass"))
            .isEqualTo("2");
    }

    @Test
    void laterMismatchRollsBackNewRelations() throws Exception {
        execute("DROP TABLE tb_co_prev_adj");
        execute("ALTER TABLE tb_fi_xfer ALTER COLUMN amt TYPE NUMERIC(16)");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_fi_xfer");
        assertThat(text("SELECT to_regclass('tb_co_prev_adj')::text")).isNull();
    }

    private String columns(String table) throws Exception {
        return text("SELECT string_agg(column_name,',' ORDER BY ordinal_position) FROM information_schema.columns "
            + "WHERE table_schema=current_schema() AND table_name='" + table + "'");
    }

    private String sequencePositions() throws Exception {
        var result = new StringBuilder();
        for (String sequence : List.of("sq_tb_co_prev_adj", "sq_tb_fi_loan", "sq_tb_fi_xfer")) {
            result.append(text("SELECT last_value::text || ':' || is_called::text FROM " + sequence));
        }
        return result.toString();
    }

    private String snapshot() throws Exception {
        var result = new StringBuilder();
        var tables = new java.util.ArrayList<>(TABLES);
        tables.addAll(List.of("tb_sy_upld", "tb_sy_calc_rule", "tb_sy_code", "tb_sy_oper_std",
            "tb_sy_chg_hist", "tb_sy_accs_log", "tb_co_part_setl"));
        for (String table : tables) {
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
