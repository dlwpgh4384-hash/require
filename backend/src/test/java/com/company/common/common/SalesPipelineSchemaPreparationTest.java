package com.company.common.common;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.candidates.mapper.CandidateMapper;
import com.company.common.candidates.service.CandidateCalculationRules;
import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.codegroups.service.CommonCodeService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.users.mapper.UserMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.YearMonth;
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
class SalesPipelineSchemaPreparationTest {
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
        assertThat(database.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
        schema = "sales_pipeline_" + UUID.randomUUID().toString().replace("-", "");
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
                "mapper/UserPreferencePeriodSourceMapper.xml", "mapper/candidates/CandidateMapper.xml")) {
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
    void originalApprovalColumnsCompositeKeyAndForeignKeyArePreparedEmpty() throws Exception {
        assertThat(text("SELECT string_agg(column_name,',' ORDER BY ordinal_position) "
            + "FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_sl_aprv_hist'"))
            .isEqualTo("opp_id,hist_sn,aprv_act_cd,prft_rt,act_rsn,reg_user_id,reg_dttm");
        assertThat(text("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
            + "WHERE conrelid='tb_sl_aprv_hist'::regclass AND contype='p'"))
            .isEqualTo("PRIMARY KEY (opp_id, hist_sn)");
        assertThat(text("SELECT conname || ':' || pg_get_constraintdef(oid) FROM pg_constraint "
            + "WHERE conrelid='tb_sl_aprv_hist'::regclass AND contype='f'"))
            .isEqualTo("fk_tb_sl_aprv_hist_01:FOREIGN KEY (opp_id) REFERENCES tb_sl_opp(opp_id)");
        assertThat(text("SELECT count(*)::text FROM tb_sl_aprv_hist")).isEqualTo("0");
        assertThat(text("SELECT obj_description('tb_sl_aprv_hist'::regclass)")).isNotBlank();
    }

    @Test
    void preparingOnlyMissingHistoryLeavesAllExistingBusinessRowsAndShapesUntouched() throws Exception {
        insertOpportunity();
        execute("DROP TABLE tb_sl_aprv_hist");
        String before = sources();
        preparation.prepare();
        assertThat(sources()).isEqualTo(before);
        assertThat(text("SELECT count(*)::text FROM tb_sl_aprv_hist")).isEqualTo("0");
        assertThat(text("SELECT count(*)::text FROM pg_constraint WHERE conrelid='tb_sl_aprv_hist'::regclass "
            + "AND conname='fk_tb_sl_aprv_hist_01'")).isEqualTo("1");
    }

    @Test
    void repeatedPreparationPreservesExistingHistoryReasonAndRegistrationTime() throws Exception {
        insertOpportunity();
        execute("INSERT INTO tb_sl_aprv_hist(opp_id,hist_sn,aprv_act_cd,prft_rt,act_rsn,reg_user_id,reg_dttm) "
            + "VALUES('owned',1,'REJ',12.34,repeat('가',300),'admin',clock_timestamp())");
        String before = sources();
        String history = text("SELECT to_jsonb(h)::text FROM tb_sl_aprv_hist h");
        preparation.prepare();
        preparation.prepare();
        assertThat(sources()).isEqualTo(before);
        assertThat(text("SELECT to_jsonb(h)::text FROM tb_sl_aprv_hist h")).isEqualTo(history);
    }

    @Test
    void missingForeignKeyIsNotRepairedAndExistingCommentIsPreserved() throws Exception {
        execute("ALTER TABLE tb_sl_aprv_hist DROP CONSTRAINT fk_tb_sl_aprv_hist_01");
        execute("COMMENT ON TABLE tb_sl_aprv_hist IS '기존 설명'");
        preparation.prepare();
        assertThat(text("SELECT count(*)::text FROM pg_constraint "
            + "WHERE conrelid='tb_sl_aprv_hist'::regclass AND contype='f'")).isEqualTo("0");
        assertThat(text("SELECT obj_description('tb_sl_aprv_hist'::regclass)")).isEqualTo("기존 설명");
    }

    @Test
    void differentForeignKeyDefinitionIsOutsideComparison() throws Exception {
        execute("ALTER TABLE tb_sl_aprv_hist DROP CONSTRAINT fk_tb_sl_aprv_hist_01");
        execute("ALTER TABLE tb_sl_aprv_hist ADD CONSTRAINT owned_reference FOREIGN KEY(opp_id) "
            + "REFERENCES tb_sl_opp(opp_id) DEFERRABLE INITIALLY DEFERRED");
        preparation.prepare();
        assertThat(text("SELECT condeferrable::text FROM pg_constraint "
            + "WHERE conrelid='tb_sl_aprv_hist'::regclass AND conname='owned_reference'")).isEqualTo("true");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ALTER COLUMN act_rsn TYPE VARCHAR(301)", "ALTER COLUMN prft_rt TYPE NUMERIC(8,2)",
        "ALTER COLUMN reg_dttm TYPE TIMESTAMP", "ALTER COLUMN reg_user_id DROP NOT NULL",
        "ALTER COLUMN reg_dttm DROP DEFAULT", "DROP CONSTRAINT pk_tb_sl_aprv_hist",
        "ADD CONSTRAINT owned_unique UNIQUE(opp_id)", "ADD CONSTRAINT owned_check CHECK(hist_sn>0)",
        "ADD COLUMN extra_value TEXT"
    })
    void originalColumnAndPrimaryCheckUniqueDriftIsRefusedWithoutRepair(String change) throws Exception {
        execute("ALTER TABLE tb_sl_aprv_hist " + change);
        String before = text("SELECT jsonb_agg(to_jsonb(c) ORDER BY ordinal_position)::text "
            + "FROM information_schema.columns c WHERE table_schema=current_schema() AND table_name='tb_sl_aprv_hist'");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_sl_aprv_hist");
        assertThat(text("SELECT jsonb_agg(to_jsonb(c) ORDER BY ordinal_position)::text "
            + "FROM information_schema.columns c WHERE table_schema=current_schema() AND table_name='tb_sl_aprv_hist'"))
            .isEqualTo(before);
    }

    @Test
    void viewCannotReplacePhysicalHistoryTable() throws Exception {
        execute("DROP TABLE tb_sl_aprv_hist");
        execute("CREATE VIEW tb_sl_aprv_hist AS SELECT opp_id FROM tb_sl_opp");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_sl_aprv_hist: expected ordinary table");
        assertThat(text("SELECT relkind::text FROM pg_class WHERE oid='tb_sl_aprv_hist'::regclass")).isEqualTo("v");
    }

    @Test
    void realCalculationMapperReturnsEveryStoredPipelineDefinitionUnchanged() throws Exception {
        try (var session = factory.openSession()) {
            var service = new CalculationRuleService(session.getMapper(CalculationRuleMapper.class),
                session.getMapper(UserPreferencePeriodSourceMapper.class));
            for (var value : new SalesPipelineDefinitions(service).definitions()) {
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
    }

    @Test
    void quoteAndCandidateRedThresholdShareEffectiveMonthSourceAndIgnoreFutureValues() throws Exception {
        execute("INSERT INTO tb_sy_oper_std(std_cd,aply_bgn_ym,std_nm,std_val,unit_nm,reg_user_id,reg_dttm) "
            + "VALUES('QUOTE_APRV_RT','203101','시험 기준',18,'%','admin',clock_timestamp()),"
            + "('QUOTE_REAPRV_GAP','203101','시험 차이',0.75,'%p','admin',clock_timestamp()),"
            + "('QUOTE_APRV_RT','203102','다음 기준',21,'%','admin',clock_timestamp()),"
            + "('QUOTE_REAPRV_GAP','203102','다음 차이',1.25,'%p','admin',clock_timestamp())");
        try (var session = factory.openSession()) {
            var metadata = new CalculationRuleService(session.getMapper(CalculationRuleMapper.class),
                session.getMapper(UserPreferencePeriodSourceMapper.class));
            var approval = new SalesPipelineApprovalStandards(metadata);
            var candidate = new CandidateCalculationRules(new CandidateDefinitions(metadata),
                session.getMapper(CandidateMapper.class), metadata);
            var january = approval.effective(YearMonth.of(2031, 1));
            var february = approval.effective(YearMonth.of(2031, 2));
            assertThat(january.approvalRate()).isEqualByComparingTo("18");
            assertThat(january.reapprovalGap()).isEqualByComparingTo("0.75");
            assertThat(february.approvalRate()).isEqualByComparingTo("21");
            assertThat(february.reapprovalGap()).isEqualByComparingTo("1.25");
            assertThat(candidate.terms(true, YearMonth.of(2031, 1)).lowMarginRate())
                .isEqualByComparingTo(january.approvalRate());
            assertThat(candidate.terms(true, YearMonth.of(2031, 2)).lowMarginRate())
                .isEqualByComparingTo(february.approvalRate());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOTI_TYPE", "SEND_MTHD", "CTRT_TYPE", "EMP_TYPE", "JOB", "GRADE", "JBPS",
        "INPUT_TYPE", "EXT_PSBL", "PROJ_STAT", "WORK_STAT", "SALES_STEP", "APRV_STAT", "CNTC_STAT",
        "CAND_SRC", "LOSE_RSN", "DRCT_CLS", "SRC", "ASGN_STAT", "OPP_STAT", "TENT_STAT", "APRV_ACT", "ACT"})
    void optionsAndDisplayNamesMatchPreparedBaseDataThroughRealMapper(String groupId) throws Exception {
        try (var session = factory.openSession()) {
            var codes = new SalesPipelineCodes(new CommonCodeService(
                session.getMapper(CodeGroupMapper.class), mock(UserMapper.class)));
            var expected = new java.util.ArrayList<String>();
            try (var query = database.prepareStatement("SELECT code,code_nm FROM tb_sy_code "
                    + "WHERE code_grp_id=? AND use_yn='Y' AND del_yn='N' ORDER BY sort_ord,code")) {
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
    }

    private void insertOpportunity() throws Exception {
        execute("INSERT INTO tb_bs_clnt(clnt_id,clnt_type_cd,clnt_nm,biz_reg_no,reg_user_id,reg_dttm) "
            + "VALUES('owned-customer','CUST','시험 고객','123-45-67890','admin',clock_timestamp())");
        execute("INSERT INTO tb_sl_opp(opp_id,opp_nm,cust_clnt_id,part_id,sales_step_cd,win_prbl_rt,"
            + "expct_amt,bgn_plan_ym,opp_stat_cd,aprv_stat_cd,reg_user_id,reg_dttm) "
            + "SELECT 'owned','시험 기회','owned-customer',part_id,'PROP',30,10000000,'203101','ING','WAIT',"
            + "'admin',clock_timestamp() FROM tb_bs_part ORDER BY part_id LIMIT 1");
        assertThat(text("SELECT count(*)::text FROM tb_sl_opp WHERE opp_id='owned'")).isEqualTo("1");
    }

    private String sources() throws Exception {
        var result = new StringBuilder();
        for (String table : List.of("tb_sl_opp", "tb_sl_opp_need", "tb_sl_tent_asgn", "tb_sy_code", "tb_sy_calc_rule")) {
            result.append(text("SELECT coalesce(jsonb_agg(value ORDER BY value::text)::text,'[]') "
                + "FROM (SELECT to_jsonb(r) AS value FROM " + table + " r) records"));
            result.append(text("SELECT jsonb_agg(to_jsonb(c) ORDER BY ordinal_position)::text "
                + "FROM information_schema.columns c WHERE table_schema=current_schema() AND table_name='" + table + "'"));
            result.append(text("SELECT jsonb_agg(conname || ':' || pg_get_constraintdef(oid) ORDER BY conname)::text "
                + "FROM pg_constraint WHERE conrelid='" + table + "'::regclass"));
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
