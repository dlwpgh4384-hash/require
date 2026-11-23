package com.company.common.common;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.preferences.service.UserPreferencePeriodBasis;
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
class MonthCloseSchemaPreparationTest {
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
        schema = "monthclose_" + UUID.randomUUID().toString().replace("-", "");
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
    void historyUsesOriginalColumnsCompositeKeyDdlForeignKeyAndNoSequence() throws Exception {
        assertThat(text("SELECT count(*)::text FROM tb_cl_close_hist")).isEqualTo("0");
        assertThat(text("SELECT string_agg(column_name,',' ORDER BY ordinal_position) "
            + "FROM information_schema.columns WHERE table_schema=current_schema() "
            + "AND table_name='tb_cl_close_hist'"))
            .isEqualTo("close_ym,hist_sn,act_cd,act_rsn,remain_chk_cn,reg_user_id,reg_dttm");
        assertThat(text("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
            + "WHERE conrelid='tb_cl_close_hist'::regclass AND contype='p'"))
            .isEqualTo("PRIMARY KEY (close_ym, hist_sn)");
        assertThat(text("SELECT conname || ':' || pg_get_constraintdef(oid) FROM pg_constraint "
            + "WHERE conrelid='tb_cl_close_hist'::regclass AND contype='f'"))
            .isEqualTo("fk_tb_cl_close_hist_01:FOREIGN KEY (close_ym) REFERENCES tb_cl_close(close_ym)");
        assertThat(text("SELECT count(*)::text FROM pg_constraint "
            + "WHERE conrelid='tb_cl_close_hist'::regclass AND contype='f'")).isEqualTo("1");
        assertThat(text("SELECT col_description('tb_cl_close_hist'::regclass, 1)")).doesNotContain("미선언");
        assertThat(text("SELECT column_default FROM information_schema.columns "
            + "WHERE table_schema=current_schema() AND table_name='tb_cl_close_hist' AND column_name='hist_sn'"))
            .isNull();
        assertThat(text("SELECT obj_description('tb_cl_close_hist'::regclass)")).isNotBlank();
    }

    @Test
    void repeatPreparationPreservesHistoryCloseRowsAndEveryExistingSource() throws Exception {
        execute("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('202601','CLOSE','admin')");
        try (var insert = database.prepareStatement("INSERT INTO tb_cl_close_hist "
                + "(close_ym,hist_sn,act_cd,act_rsn,remain_chk_cn,reg_user_id) VALUES ('202601',1,'OPEN',?,?,'admin')")) {
            insert.setString(1, "해".repeat(500));
            insert.setString(2, "급여 업로드,영업 파트 정산");
            insert.executeUpdate();
        }
        String before = snapshot();
        preparation.prepare();
        preparation.prepare();
        assertThat(snapshot()).isEqualTo(before);
        assertThat(text("SELECT char_length(act_rsn)::text FROM tb_cl_close_hist")).isEqualTo("500");
    }

    @Test
    void historyReasonDatabaseBoundIsFiveHundredCharactersWithoutTruncation() throws Exception {
        execute("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('202601','CLOSE','admin')");
        try (var insert = database.prepareStatement("INSERT INTO tb_cl_close_hist "
                + "(close_ym,hist_sn,act_cd,act_rsn,reg_user_id) VALUES ('202601',1,'OPEN',?,'admin')")) {
            insert.setString(1, "해".repeat(501));
            assertThatThrownBy(insert::executeUpdate).isInstanceOf(java.sql.SQLException.class);
        }
        assertThat(text("SELECT count(*)::text FROM tb_cl_close_hist")).isEqualTo("0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ALTER COLUMN hist_sn TYPE NUMERIC(6)", "ALTER COLUMN act_rsn TYPE VARCHAR(501)",
        "ALTER COLUMN remain_chk_cn TYPE VARCHAR(501)", "ALTER COLUMN reg_user_id DROP NOT NULL",
        "ALTER COLUMN hist_sn SET DEFAULT 1", "ALTER COLUMN reg_dttm TYPE TIMESTAMP",
        "ADD COLUMN generated_id BIGINT", "DROP CONSTRAINT pk_tb_cl_close_hist",
        "DROP CONSTRAINT fk_tb_cl_close_hist_01"})
    void incompatibleHistoryIsReportedWithoutChangingExistingShape(String change) throws Exception {
        execute("ALTER TABLE tb_cl_close_hist " + change);
        String before = shape();
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_cl_close_hist");
        assertThat(shape()).isEqualTo(before);
    }

    @Test
    void reversedHistoryKeyIsNotSilentlyAccepted() throws Exception {
        execute("ALTER TABLE tb_cl_close_hist DROP CONSTRAINT pk_tb_cl_close_hist");
        execute("ALTER TABLE tb_cl_close_hist ADD CONSTRAINT pk_tb_cl_close_hist PRIMARY KEY(hist_sn,close_ym)");
        String before = shape();
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_cl_close_hist");
        assertThat(shape()).isEqualTo(before);
    }

    @Test
    void historyBuiltFromDdlWithSeparateForeignKeyIsAcceptedAndKeepsRows() throws Exception {
        execute("DROP TABLE tb_cl_close_hist");
        execute("CREATE TABLE TB_CL_CLOSE_HIST (CLOSE_YM CHAR(6) NOT NULL, HIST_SN NUMERIC(5) NOT NULL, "
            + "ACT_CD VARCHAR(10) NOT NULL, ACT_RSN VARCHAR(500), REMAIN_CHK_CN VARCHAR(500), "
            + "REG_USER_ID VARCHAR(30) NOT NULL, REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL, "
            + "CONSTRAINT PK_TB_CL_CLOSE_HIST PRIMARY KEY (CLOSE_YM, HIST_SN))");
        execute("ALTER TABLE TB_CL_CLOSE_HIST ADD CONSTRAINT FK_TB_CL_CLOSE_HIST_01 "
            + "FOREIGN KEY (CLOSE_YM) REFERENCES TB_CL_CLOSE (CLOSE_YM)");
        execute("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('202601','CLOSE','admin')");
        execute("INSERT INTO tb_cl_close_hist(close_ym,hist_sn,act_cd,reg_user_id) VALUES ('202601',1,'CLOSE','admin')");
        String before = snapshot() + shape();
        preparation.prepare();
        assertThat(snapshot() + shape()).isEqualTo(before);
    }

    @Test
    void equivalentRenamedForeignKeyIsAcceptedButExtraForeignKeyIsRejected() throws Exception {
        execute("ALTER TABLE tb_cl_close_hist RENAME CONSTRAINT fk_tb_cl_close_hist_01 TO owned_close_month");
        String before = shape();
        preparation.prepare();
        assertThat(shape()).isEqualTo(before);
        execute("ALTER TABLE tb_cl_close_hist ADD CONSTRAINT owned_extra FOREIGN KEY (close_ym) REFERENCES tb_cl_close(close_ym)");
        before = shape();
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_cl_close_hist");
        assertThat(shape()).isEqualTo(before);
    }

    @Test
    void definitionsReadOriginalDatabaseTextWithoutWritingAnySource() throws Exception {
        String before = snapshot();
        try (var session = factory.openSession()) {
            var definitions = new MonthCloseDefinitions(new CalculationRuleService(
                session.getMapper(CalculationRuleMapper.class), session.getMapper(UserPreferencePeriodSourceMapper.class)));
            var values = definitions.definitions();
            assertThat(values).extracting(value -> value.calcId()).containsExactly(
                "식-00", "식-31", "식-44", "식-57", "식-58", "식-59", "식-88", "식-95");
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
    @ValueSource(strings = {"CTRT_TYPE", "EMP_TYPE", "JOB", "INPUT_TYPE", "EXT_PSBL", "SGA_ITEM",
        "FIN_CLS", "CLOSE_STAT", "DRCT_CLS", "ASGN_STAT", "COST_CLS", "SETL_STAT", "ACT", "CHK_ITEM", "CHK_STAT"})
    void commonCodesMatchPreparedDmlWithoutNewEnums(String groupId) throws Exception {
        String before = snapshot();
        try (var session = factory.openSession()) {
            var codes = new MonthCloseCodes(new CommonCodeService(
                session.getMapper(CodeGroupMapper.class), mock(UserMapper.class)));
            var stored = new java.util.ArrayList<String>();
            try (var query = database.prepareStatement("SELECT code,code_nm FROM tb_sy_code "
                    + "WHERE code_grp_id=? AND del_yn='N' AND use_yn='Y' ORDER BY sort_ord,code")) {
                query.setString(1, groupId);
                try (var rows = query.executeQuery()) {
                    while (rows.next()) {
                        stored.add(rows.getString(1));
                        assertThat(codes.name(groupId, rows.getString(1))).isEqualTo(rows.getString(2));
                    }
                }
            }
            assertThat(stored).isNotEmpty();
            if (groupId.equals("CHK_ITEM")) {
                assertThat(stored).containsExactly("PAY", "MM_OVER", "BILL", "FI", "SETL", "BILL_MISS");
            }
        }
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void periodSourceImmediatelyReflectsCloseAndWorkWithoutTreatingStoredWorkAsClosed() throws Exception {
        execute("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('202601','WORK','admin')");
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(UserPreferencePeriodSourceMapper.class);
            var basis = new UserPreferencePeriodBasis(mapper);
            assertThat(mapper.selectClosedMonths()).isEmpty();
            assertThat(basis.metadata().get("periodBasis"))
                .isEqualTo(new UserPreferencePeriodBasis.Basis("202601", "202601", List.of()));
            execute("UPDATE tb_cl_close SET close_stat_cd='CLOSE' WHERE close_ym='202601'");
            session.clearCache();
            assertThat(basis.metadata().get("periodBasis"))
                .isEqualTo(new UserPreferencePeriodBasis.Basis("202602", "202601", List.of("202601")));
            execute("UPDATE tb_cl_close SET close_stat_cd='WORK' WHERE close_ym='202601'");
            session.clearCache();
            assertThat(basis.metadata().get("periodBasis"))
                .isEqualTo(new UserPreferencePeriodBasis.Basis("202601", "202601", List.of()));
        }
        assertThat(text("SELECT count(*)::text FROM tb_cl_close")).isEqualTo("1");
    }

    private String shape() throws Exception {
        return text("SELECT jsonb_agg(to_jsonb(c) ORDER BY ordinal_position)::text "
            + "FROM information_schema.columns c WHERE table_schema=current_schema() AND table_name='tb_cl_close_hist'")
            + text("SELECT jsonb_agg(pg_get_constraintdef(oid) ORDER BY conname)::text "
                + "FROM pg_constraint WHERE conrelid='tb_cl_close_hist'::regclass");
    }

    private String snapshot() throws Exception {
        var result = new StringBuilder();
        for (String table : List.of("tb_cl_close_hist", "tb_cl_close", "tb_co_part_setl", "tb_pj_asgn_mon",
                "tb_co_mon_cost", "tb_co_mon_pl", "tb_bl_bill", "tb_cl_close_chk", "tb_fi_corp_tax",
                "tb_fi_item", "tb_hr_mon_pay", "tb_pj_asgn", "tb_sy_calc_rule", "tb_sy_code", "tb_sy_oper_std")) {
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
