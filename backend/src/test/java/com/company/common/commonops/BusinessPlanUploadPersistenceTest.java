package com.company.common.commonops;

import com.company.common.commonops.mapper.BusinessPlanComparisonMapper;
import com.company.common.commonops.service.BusinessPlanUploadForm;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.service.*;
import com.company.common.uploads.dto.UploadContracts.*;
import com.company.common.uploads.mapper.UploadMapper;
import com.company.common.uploads.service.*;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** SFR-118-04 목표 업로드(B.10)를 실제 PostgreSQL에서 공통 업로드 검사·반영으로 확인한다. 실적(TB_CO_MON_PL)은 그대로다. */
@SpringJUnitConfig(classes = {CommonUploadService.class, UploadWorkbookService.class, ExcelExportService.class,
    BusinessPlanUploadForm.class, CurrentBusinessActor.class, BusinessPlanUploadPersistenceTest.Database.class})
class BusinessPlanUploadPersistenceTest {
    static final String URL = System.getenv("NAVIGATION_TEST_DB_URL");
    static final String USER = System.getenv("NAVIGATION_TEST_DB_USER");
    static final String PASSWORD = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
    static final String SCHEMA = "plan_upload_" + Long.toUnsignedString(System.nanoTime());
    @TempDir static Path files;
    @Autowired CommonUploadService uploads;
    @Autowired UploadWorkbookService workbooks;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll static void prepare() throws Exception {
        assumeTrue(URL != null && !URL.isBlank() && USER != null && PASSWORD != null, "선택된 PostgreSQL 시험 연결이 없습니다.");
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            c.createStatement().execute("CREATE SCHEMA " + SCHEMA);
            c.setSchema(SCHEMA);
            var source = new SingleConnectionDataSource(c, true);
            var config = new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment(
                "postgresql", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();
        }
    }
    @AfterAll static void remove() throws Exception {
        if (URL != null && USER != null && PASSWORD != null)
            try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
                c.createStatement().execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            }
    }
    @BeforeEach void clean() {
        jdbc.execute("TRUNCATE TB_FI_BIZ_PLAN, TB_CL_CLOSE, TB_CO_MON_PL CASCADE");
        jdbc.update("UPDATE TB_SY_OPER_STD SET STD_VAL=202601 WHERE STD_CD='CASH_BASE_YM'");
        for (String ym : List.of("202601", "202602"))
            jdbc.update("INSERT INTO TB_CL_CLOSE(CLOSE_YM,CLOSE_STAT_CD,REG_USER_ID) VALUES (?,'CLOSE','admin')", ym);
        jdbc.update("INSERT INTO TB_CO_MON_PL(PL_YM,SALES_AMT,SALES_COST_AMT,SGA_TOT_AMT,OPRT_PRFT_AMT,FIX_YN,REG_USER_ID) VALUES ('202601',300,200,150,-50,'Y','admin')");
    }
    Result check(long revenue, String blankMonth) {
        var rows = new ArrayList<List<String>>();
        for (int m = 1; m <= 12; m++) {
            String ym = String.format("2026-%02d", m);
            rows.add(List.of(ym, ym.equals(blankMonth) ? "" : Long.toString(revenue), "1000"));
        }
        return uploads.postCommonUpload("B10", null, "plan.xlsx", workbooks.tabularFile(BusinessPlanUploadForm.HEADERS, rows, null));
    }
    Result apply(Result checked, String policy) {
        return uploads.postCommonUploadApply(checked.uploadId(),
            new Apply(checked.modifiedAt() == null ? null : Instant.parse(checked.modifiedAt()), policy));
    }
    Map<String, Long> revenueTargets() {
        var result = new TreeMap<String, Long>();
        jdbc.queryForList("SELECT PLAN_YM, SALES_TGT_AMT FROM TB_FI_BIZ_PLAN WHERE PART_ID='ALL'")
            .forEach(r -> result.put((String) r.get("plan_ym"), ((Number) r.get("sales_tgt_amt")).longValue()));
        return result;
    }
    List<Map<String, Object>> actuals() { return jdbc.queryForList("SELECT * FROM TB_CO_MON_PL ORDER BY PL_YM"); }

    @Test void firstUploadStoresTwelveAllPartMonthsBlankAsZeroWithTheUploadId() {
        var before = actuals();
        var checked = check(500, "2026-03");
        assertThat(checked.errorCount()).isZero();
        assertThat(checked.warningCount()).isEqualTo(1);
        var applied = apply(checked, null);
        assertThat(applied.errorCount()).isZero();
        var targets = revenueTargets();
        assertThat(targets).hasSize(12).containsEntry("202603", 0L).containsEntry("202601", 500L);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT UPLD_ID) FROM TB_FI_BIZ_PLAN", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT FORM_TYPE_CD FROM TB_SY_UPLD WHERE UPLD_ID=?", String.class,
            Long.parseLong(checked.uploadId()))).isEqualTo("B10");
        assertThat(actuals()).isEqualTo(before);
    }
    @Test void changedClosedMonthsNeedAChoiceAndKeepLeavesThemWhileReplaceChangesAll() {
        apply(check(500, null), null);
        var changed = check(700, null);
        assertThat(changed.issues()).extracting(Issue::reason).containsOnly(BusinessPlanUploadForm.CLOSED_CHANGED);
        assertThat(changed.issues()).extracting(Issue::inputValue).containsExactly("2026-01", "2026-02");
        assertThatThrownBy(() -> apply(changed, null)).isInstanceOf(CodeFailure.class);
        assertThat(revenueTargets()).containsEntry("202601", 500L).containsEntry("202603", 500L);
        apply(changed, "keep");
        assertThat(revenueTargets()).containsEntry("202601", 500L).containsEntry("202602", 500L)
            .containsEntry("202603", 700L).containsEntry("202612", 700L);
        apply(check(900, null), "replace");
        assertThat(new HashSet<>(revenueTargets().values())).containsExactly(900L);
    }
    @Test void sameClosedMonthTargetsApplyWithoutAChoice() {
        apply(check(500, null), null);
        var rows = new ArrayList<List<String>>();
        for (int m = 1; m <= 12; m++) rows.add(List.of(String.format("2026-%02d", m), m <= 2 ? "500" : "800", "1000"));
        var checked = uploads.postCommonUpload("B10", null, "plan.xlsx", workbooks.tabularFile(BusinessPlanUploadForm.HEADERS, rows, null));
        assertThat(checked.issues()).isEmpty();
        apply(checked, null);
        assertThat(revenueTargets()).containsEntry("202602", 500L).containsEntry("202603", 800L);
    }
    @Test void templateHasTheSelectedYearTwelveMonthRows() {
        var rows = workbooks.readTabular("t.xlsx", uploads.getCommonUploadTemplate("B10", 2026), BusinessPlanUploadForm.HEADERS);
        assertThat(rows).hasSize(12);
        assertThat(rows.get(0).values().get(0)).isEqualTo("2026-01");
    }

    @Configuration @EnableTransactionManagement
    @MapperScan(basePackageClasses = {BusinessPlanComparisonMapper.class, UserPreferencePeriodSourceMapper.class, UploadMapper.class},
        annotationClass = org.apache.ibatis.annotations.Mapper.class)
    static class Database {
        @Bean DataSource dataSource() {
            var ds = new DriverManagerDataSource(URL, USER, PASSWORD);
            var properties = new Properties();
            properties.setProperty("currentSchema", SCHEMA);
            ds.setConnectionProperties(properties);
            return ds;
        }
        @Bean org.apache.ibatis.session.SqlSessionFactory sessions(DataSource ds) throws Exception {
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(ds);
            var config = new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(true);
            config.setArgNameBasedConstructorAutoMapping(true);
            factory.setConfiguration(config);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml"));
            return factory.getObject();
        }
        @Bean org.springframework.transaction.PlatformTransactionManager transactions(DataSource ds) {
            return new DataSourceTransactionManager(ds);
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean UploadFileStore fileStore() { return new UploadFileStore(files); }
    }
}
