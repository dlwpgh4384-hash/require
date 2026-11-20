package com.company.common.common;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.parts.mapper.PartMapper;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.schema.SchemaPreparation;
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
class PartSettlementPreparationTest {
    private Connection database;
    private String schema;
    private SchemaPreparation preparation;
    private SqlSessionFactory factory;
    private String partId;
    private String developerId;

    @BeforeEach
    void independentSchema() throws Exception {
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        database = DriverManager.getConnection(url, user, password);
        schema = "part_settlement_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET search_path TO " + schema);
        var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        source.setDriverProperties(properties);
        var config = new Configuration(new Environment("postgresql", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        for (String resource : List.of("mapper/CalculationRuleMapper.xml", "mapper/PartMapper.xml")) {
            try (var input = new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
            }
        }
        factory = new SqlSessionFactoryBuilder().build(config);
        preparation = new SchemaPreparation(factory);
        preparation.prepare();
        partId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        developerId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
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
    void everyRequestedDefinitionIsReadThroughTheRealServiceFromTheIndependentDatabase() throws Exception {
        try (var session = factory.openSession()) {
            var definitions = new PartSettlementDefinitions(new CalculationRuleService(
                session.getMapper(CalculationRuleMapper.class), mock(UserPreferencePeriodSourceMapper.class)));
            for (var definition : definitions.definitions()) {
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
        }
    }

    @Test
    void originalDetailAndCloseCheckColumnsKeysAndPrecisionArePreparedWithoutSeedRows() throws Exception {
        assertThat(text("SELECT string_agg(column_name,',' ORDER BY ordinal_position) FROM information_schema.columns "
            + "WHERE table_schema=current_schema() AND table_name='tb_co_part_setl_dtl'"))
            .isEqualTo("part_id,setl_ym,dev_id,emp_type_cd,proj_nm_list,mm,uprc_amt,pay_amt,sales_exps_amt,retire_amt,cost_amt,diff_amt,prft_amt,rmrk,reg_user_id,reg_dttm,mod_user_id,mod_dttm");
        assertThat(text("SELECT string_agg(column_name,',' ORDER BY ordinal_position) FROM information_schema.columns "
            + "WHERE table_schema=current_schema() AND table_name='tb_cl_close_chk'"))
            .isEqualTo("close_ym,chk_item_cd,chk_stat_cd,chk_cnt,chk_cn,link_scrn_id,chk_dttm,reg_user_id,reg_dttm,mod_user_id,mod_dttm");
        assertThat(text("SELECT format_type(atttypid,atttypmod) FROM pg_attribute "
            + "WHERE attrelid='tb_co_part_setl_dtl'::regclass AND attname='mm'"))
            .isEqualTo("numeric(6,2)");
        assertThat(text("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
            + "WHERE conrelid='tb_co_part_setl_dtl'::regclass AND contype='p'"))
            .isEqualTo("PRIMARY KEY (part_id, setl_ym, dev_id)");
        assertThat(text("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
            + "WHERE conrelid='tb_cl_close_chk'::regclass AND contype='p'"))
            .isEqualTo("PRIMARY KEY (close_ym, chk_item_cd)");
        assertThat(text("SELECT string_agg(conname,',' ORDER BY conname) FROM pg_constraint "
            + "WHERE conrelid IN ('tb_co_part_setl_dtl'::regclass,'tb_cl_close_chk'::regclass) AND contype='f'"))
            .isEqualTo("fk_tb_cl_close_chk_01,fk_tb_co_part_setl_dtl_01,fk_tb_co_part_setl_dtl_02");
        assertThat(text("SELECT count(*)::text FROM tb_co_part_setl_dtl")).isEqualTo("0");
        assertThat(text("SELECT count(*)::text FROM tb_cl_close_chk")).isEqualTo("0");
    }

    @Test
    void repeatedPreparationKeepsSnapshotsAuditCodesAndCustomDatabaseDefinitions() throws Exception {
        insertSnapshot("AUTO");
        execute("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('202611','WORK','admin')");
        execute("INSERT INTO tb_cl_close_chk(close_ym,chk_item_cd,chk_stat_cd,chk_cnt,chk_dttm,reg_user_id) "
            + "VALUES ('202611','SETL','OK',0,clock_timestamp(),'admin')");
        execute("INSERT INTO tb_sy_chg_hist(trgt_tbl_nm,trgt_key,chg_type_cd,chg_item_nm,bfr_val,aft_val,chg_rsn,reg_user_id,reg_dttm) "
            + "VALUES ('TB_CO_PART_SETL','" + partId + ":202611','UPD','정산 상태','CNFM','WORK','보존할 사유','admin',clock_timestamp())");
        execute("UPDATE tb_sy_calc_rule SET dtl_cn=dtl_cn || ' 독립 변경' WHERE calc_id='식-37'");
        execute("UPDATE tb_sy_code SET code_nm=code_nm || ' 독립 변경' WHERE code_grp_id='SETL_STAT' AND code='AUTO'");
        String before = snapshot();
        preparation.prepare();
        preparation.prepare();
        assertThat(snapshot()).isEqualTo(before);
        try (var session = factory.openSession()) {
            assertThat(session.getMapper(PartMapper.class).selectLastConfirmed(partId, "202611"))
                .isEqualTo("202611");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CNFM", "AUTO"})
    void existingPartCriteriaConsumerProtectsBothConfirmedStates(String status) throws Exception {
        insertSnapshot(status);
        try (var session = factory.openSession()) {
            var mapper = session.getMapper(PartMapper.class);
            assertThat(mapper.selectLastConfirmed(partId, "202610")).isEqualTo("202611");
            assertThat(mapper.selectLastConfirmed(partId, "202611")).isEqualTo("202611");
            assertThat(mapper.selectLastConfirmed(partId, "202612")).isNull();
            assertThat(mapper.selectLastConfirmed("absent-part", "202611")).isNull();
        }
    }

    @Test
    void draftIsNotProtectedAsConfirmed() throws Exception {
        insertSnapshot("WORK");
        try (var session = factory.openSession()) {
            assertThat(session.getMapper(PartMapper.class).selectLastConfirmed(partId, "202611")).isNull();
        }
    }

    @Test
    void closedMonthKeepsExistingConsumerExclusion() throws Exception {
        insertSnapshot("AUTO");
        execute("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('202611','CLOSE','admin')");
        try (var session = factory.openSession()) {
            assertThat(session.getMapper(PartMapper.class).selectLastConfirmed(partId, "202611")).isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"mm", "proj_nm_list", "rmrk"})
    void incompatiblePhysicalColumnIsReportedAndNotRepaired(String column) throws Exception {
        String type = "mm".equals(column) ? "NUMERIC(7,2)" : "VARCHAR(1100)";
        execute("ALTER TABLE tb_co_part_setl_dtl ALTER COLUMN " + column + " TYPE " + type);
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_co_part_setl_dtl");
        assertThat(text("SELECT format_type(atttypid,atttypmod) FROM pg_attribute "
            + "WHERE attrelid='tb_co_part_setl_dtl'::regclass AND attname='" + column + "'"))
            .isEqualTo("mm".equals(column) ? "numeric(7,2)" : "character varying(1100)");
    }

    @Test
    void missingOriginalForeignKeyIsReportedWithoutAlteringExistingTable() throws Exception {
        execute("ALTER TABLE tb_co_part_setl_dtl DROP CONSTRAINT fk_tb_co_part_setl_dtl_02");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("FK_TB_CO_PART_SETL_DTL_02");
        assertThat(text("SELECT count(*)::text FROM pg_constraint "
            + "WHERE conrelid='tb_co_part_setl_dtl'::regclass AND conname='fk_tb_co_part_setl_dtl_02'"))
            .isEqualTo("0");
    }

    @Test
    void incompatiblePrimaryKeyIsReportedWithoutRepair() throws Exception {
        execute("ALTER TABLE tb_cl_close_chk DROP CONSTRAINT pk_tb_cl_close_chk");
        execute("ALTER TABLE tb_cl_close_chk ADD CONSTRAINT pk_tb_cl_close_chk PRIMARY KEY(chk_item_cd,close_ym)");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_cl_close_chk");
        assertThat(text("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
            + "WHERE conrelid='tb_cl_close_chk'::regclass AND contype='p'"))
            .isEqualTo("PRIMARY KEY (chk_item_cd, close_ym)");
    }

    @Test
    void incompatibleIndexIsReportedWithoutReplacement() throws Exception {
        execute("DROP INDEX ix_tb_co_part_setl_dtl_01");
        execute("CREATE INDEX ix_tb_co_part_setl_dtl_01 ON tb_co_part_setl_dtl(dev_id)");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("ix_tb_co_part_setl_dtl_01");
        assertThat(text("SELECT pg_get_indexdef('ix_tb_co_part_setl_dtl_01'::regclass,1,true)"))
            .isEqualTo("dev_id");
    }

    @Test
    void laterShapeFailureRollsBackCreationOfMissingDetailTable() throws Exception {
        execute("DROP TABLE tb_co_part_setl_dtl");
        execute("ALTER TABLE tb_cl_close_chk ALTER COLUMN chk_cnt TYPE NUMERIC(8)");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_cl_close_chk");
        assertThat(text("SELECT to_regclass('tb_co_part_setl_dtl')::text")).isNull();
    }

    private void insertSnapshot(String status) throws Exception {
        execute("INSERT INTO tb_bs_part(part_id,part_nm,hq_yn,use_yn,reg_user_id) VALUES ('"
            + partId + "','독립 파트','N','Y','admin')");
        execute("INSERT INTO tb_bs_part_std(part_id,aply_bgn_ym,part_ldr_user_id,prft_dstrb_rt,sales_exps_amt,reg_user_id) "
            + "SELECT '" + partId + "','202601','admin',"
            + "(SELECT std_val FROM tb_sy_oper_std WHERE std_cd='PART_DSTRB_RT' ORDER BY aply_bgn_ym DESC LIMIT 1),"
            + "(SELECT std_val FROM tb_sy_oper_std WHERE std_cd='PART_SALES_EXPS' ORDER BY aply_bgn_ym DESC LIMIT 1),'admin'");
        execute("INSERT INTO tb_hr_dev(dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) "
            + "SELECT '" + developerId + "','" + developerId + "','독립 인력',"
            + "(SELECT min(code) FROM tb_sy_code WHERE code_grp_id='EMP_TYPE' AND use_yn='Y' AND del_yn='N'),"
            + "(SELECT min(code) FROM tb_sy_code WHERE code_grp_id='JOB' AND use_yn='Y' AND del_yn='N'),"
            + "(SELECT min(code) FROM tb_sy_code WHERE code_grp_id='DRCT_CLS' AND use_yn='Y' AND del_yn='N'),'" + partId + "',"
            + "(SELECT min(code) FROM tb_sy_code WHERE code_grp_id='WORK_STAT' AND use_yn='Y' AND del_yn='N'),'admin'");
        execute("INSERT INTO tb_co_part_setl(part_id,setl_ym,corp_card_amt,part_prft_amt,setl_stat_cd,reg_user_id,reg_dttm) "
            + "VALUES ('" + partId + "','202611',19,-29,'" + status + "','admin',clock_timestamp())");
        execute("INSERT INTO tb_co_part_setl_dtl(part_id,setl_ym,dev_id,emp_type_cd,proj_nm_list,mm,uprc_amt,pay_amt,"
            + "sales_exps_amt,retire_amt,cost_amt,diff_amt,prft_amt,rmrk,reg_user_id,reg_dttm) "
            + "SELECT '" + partId + "','202611',dev_id,emp_type_cd,'확정 프로젝트',0.75,101,201,0,0,201,-100,-10,"
            + "'보존 비고','admin',clock_timestamp() FROM tb_hr_dev WHERE dev_id='" + developerId + "'");
    }

    private String snapshot() throws Exception {
        var result = new StringBuilder();
        for (String table : List.of("tb_co_part_setl", "tb_co_part_setl_dtl", "tb_cl_close_chk",
                "tb_sy_calc_rule", "tb_sy_code", "tb_sy_chg_hist", "tb_sy_accs_log")) {
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
