package com.company.common.common;

import com.company.common.schema.SchemaPreparation;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.YearMonth;
import java.util.Properties;
import java.util.UUID;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class BillingReceiptPreparationTest {
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
        schema = "billing_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET search_path TO " + schema);
        var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        source.setDriverProperties(properties);
        var configuration = new Configuration(new Environment("postgresql", new JdbcTransactionFactory(), source));
        try (var input = new ClassPathResource("mapper/CalculationRuleMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, configuration, "mapper/CalculationRuleMapper.xml", configuration.getSqlFragments()).parse();
        }
        factory = new SqlSessionFactoryBuilder().build(configuration);
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
    void rulesReadPreparedDatabaseDefinitionsAndEffectiveStandards() throws Exception {
        try (var session = factory.openSession()) {
            var rules = new BillingCalculationRules(new CalculationRuleService(
                session.getMapper(CalculationRuleMapper.class), mock(UserPreferencePeriodSourceMapper.class)));
            for (var definition : rules.definitions()) {
                try (var query = database.prepareStatement("SELECT frml_cn FROM tb_sy_calc_rule WHERE calc_id=?")) {
                    query.setString(1, definition.calcId());
                    try (var rows = query.executeQuery()) {
                        assertThat(rows.next()).isTrue();
                        assertThat(definition.formula()).isEqualTo(rows.getString(1));
                    }
                }
            }
            execute("UPDATE tb_sy_oper_std SET std_val=20000 WHERE std_cd='BILL_MISS_MIN'");
            execute("UPDATE tb_sy_oper_std SET std_val=0.5 WHERE std_cd='BILL_MISS_RT'");
            assertThat(rules.check(new BigDecimal("1000000"), new BigDecimal("980000"), YearMonth.of(2026, 11)).judgement())
                .isEqualTo("맞음");
            execute("UPDATE tb_sy_oper_std SET std_val=15000 WHERE std_cd='BILL_MISS_MIN'");
            assertThat(rules.check(new BigDecimal("1000000"), new BigDecimal("980000"), YearMonth.of(2026, 11)).judgement())
                .isEqualTo("적게 청구");
            assertThat(rules.amounts(new BigDecimal("101"), BigDecimal.TEN).vatAmount()).isEqualByComparingTo("10");
        }
    }

    @Test
    void originalReceiptIdPrecisionColumnsAndLogicalReferencesArePreserved() throws Exception {
        assertThat(text("SELECT string_agg(column_name,',' ORDER BY ordinal_position) FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_bl_rcpt'"))
            .isEqualTo("rcpt_id,bill_id,rcpt_dt,rcpt_amt,cncl_yn,cncl_rsn,cncl_user_id,cncl_dttm,reg_user_id,reg_dttm,mod_user_id,mod_dttm");
        assertThat(text("SELECT numeric_precision::text || ':' || numeric_scale::text FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_bl_rcpt' AND column_name='rcpt_id'"))
            .isEqualTo("12:0");
        // 2026-11-19 DDL 외래 키 FK_TB_BL_RCPT_01(청구)·02(취소 처리자)를 준비 파일에 더함
        assertThat(text("SELECT string_agg(conname,',' ORDER BY conname) FROM pg_constraint WHERE conrelid='tb_bl_rcpt'::regclass AND contype='f'"))
            .isEqualTo("fk_tb_bl_rcpt_01,fk_tb_bl_rcpt_02");
        assertThat(text("SELECT count(*)::text FROM pg_indexes WHERE schemaname=current_schema() AND indexname IN ('ix_tb_bl_rcpt_01','ix_tb_bl_rcpt_02')"))
            .isEqualTo("2");
        assertThat(text("SELECT count(*)::text FROM tb_bl_rcpt")).isEqualTo("0");
    }

    @Test
    void repeatedPreparationKeepsCancelledReceiptAuditAndSequencePosition() throws Exception {
        insertReceipt();
        execute("UPDATE tb_bl_rcpt SET cncl_yn='Y',cncl_rsn='독립 취소 사유',cncl_user_id='admin',cncl_dttm=CURRENT_TIMESTAMP WHERE rcpt_id=1");
        String before = text("SELECT to_jsonb(r)::text FROM tb_bl_rcpt r WHERE rcpt_id=1");
        String codes = text("SELECT jsonb_agg(to_jsonb(c) ORDER BY code_grp_id,code)::text FROM tb_sy_code c WHERE code_grp_id IN ('BILL_STAT','NOTI_TYPE','SEND_MTHD')");
        preparation.prepare();
        preparation.prepare();
        assertThat(text("SELECT to_jsonb(r)::text FROM tb_bl_rcpt r WHERE rcpt_id=1")).isEqualTo(before);
        assertThat(text("SELECT nextval('sq_tb_bl_rcpt')::text")).isEqualTo("2");
        assertThat(text("SELECT jsonb_agg(to_jsonb(c) ORDER BY code_grp_id,code)::text FROM tb_sy_code c WHERE code_grp_id IN ('BILL_STAT','NOTI_TYPE','SEND_MTHD')"))
            .isEqualTo(codes);
    }

    @Test
    void incompatibleReceiptColumnIsReportedWithoutRepair() throws Exception {
        execute("ALTER TABLE tb_bl_rcpt ALTER COLUMN rcpt_id TYPE NUMERIC(13)");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_bl_rcpt");
        assertThat(text("SELECT numeric_precision::text FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='tb_bl_rcpt' AND column_name='rcpt_id'"))
            .isEqualTo("13");
    }

    @Test
    void incompatibleReceiptPrimaryKeyIsReportedWithoutRepair() throws Exception {
        execute("ALTER TABLE tb_bl_rcpt DROP CONSTRAINT pk_tb_bl_rcpt");
        execute("ALTER TABLE tb_bl_rcpt ADD CONSTRAINT pk_tb_bl_rcpt PRIMARY KEY (bill_id,rcpt_id)");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("tb_bl_rcpt");
        assertThat(text("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='tb_bl_rcpt'::regclass AND conname='pk_tb_bl_rcpt'"))
            .isEqualTo("PRIMARY KEY (bill_id, rcpt_id)");
    }

    @Test
    void incompatibleReceiptIndexIsReportedWithoutReplacement() throws Exception {
        execute("DROP INDEX ix_tb_bl_rcpt_01");
        execute("CREATE INDEX ix_tb_bl_rcpt_01 ON tb_bl_rcpt (rcpt_dt)");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("ix_tb_bl_rcpt_01");
        assertThat(text("SELECT pg_get_indexdef('ix_tb_bl_rcpt_01'::regclass,1,true)"))
            .isEqualTo("rcpt_dt");
    }

    @Test
    void cyclingSequenceIsReportedWithoutRepair() throws Exception {
        execute("ALTER SEQUENCE sq_tb_bl_rcpt CYCLE");
        assertThatThrownBy(preparation::prepare).hasMessageContaining("sq_tb_bl_rcpt");
        assertThat(text("SELECT seqcycle::text FROM pg_sequence WHERE seqrelid='sq_tb_bl_rcpt'::regclass"))
            .isEqualTo("true");
    }

    @Test
    void receiptIdentifierIsReturnedAsDecimalStringWithoutScientificNotation() throws Exception {
        execute("ALTER SEQUENCE sq_tb_bl_rcpt RESTART WITH 999999999999");
        insertReceipt();
        assertThat(text("SELECT rcpt_id::text FROM tb_bl_rcpt")).isEqualTo("999999999999");
    }

    private void insertReceipt() throws Exception {
        // 입금은 청구를 가리켜야 한다(FK_TB_BL_RCPT_01).
        execute("INSERT INTO tb_bl_bill(bill_id,proj_id,bill_ord,bill_ym,bill_dt,sply_amt,vat_rt,vat_amt,tot_amt,rcpt_sum_amt,unpaid_amt,pay_due_dt,reg_user_id) "
            + "VALUES ('B-202611-001','receipt-project',1,'202611',DATE '2026-11-01',1000,10,100,1100,0,1100,DATE '2026-12-25','admin') ON CONFLICT DO NOTHING");
        execute("INSERT INTO tb_bl_rcpt(bill_id,rcpt_dt,rcpt_amt,reg_user_id) VALUES ('B-202611-001',DATE '2026-11-01',100,'admin')");
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
