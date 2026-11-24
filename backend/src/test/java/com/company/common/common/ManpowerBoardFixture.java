package com.company.common.common;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.schema.SchemaPreparation;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

/** Test-owned PostgreSQL schema; never connects to or changes a shared business schema. */
public final class ManpowerBoardFixture implements AutoCloseable {
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC);
    private final Connection database;
    private final String schema = "manpowerboard_" + UUID.randomUUID().toString().replace("-", "");
    private final QueryCounter counter = new QueryCounter();
    private final SqlSessionFactory factory;
    private final JdbcTemplate db;
    private final String partId;
    private final String customerId;

    public ManpowerBoardFixture() throws Exception {
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        database = DriverManager.getConnection(url, user, password);
        try {
            if (database.getMetaData().getDatabaseMajorVersion() != 16) {
                throw new IllegalStateException("PostgreSQL 16 is required for this integration test.");
            }
            execute("CREATE SCHEMA " + schema);
            var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new Properties();
            properties.setProperty("currentSchema", schema);
            source.setDriverProperties(properties);
            var configuration = new Configuration(new Environment(
                "postgresql", new JdbcTransactionFactory(), source));
            configuration.setMapUnderscoreToCamelCase(false);
            configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
            // Includes constructor resultMaps and dependencies of the reused forecast/withdrawal mappers.
            for (var resource : new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml")) {
                try (var input = resource.getInputStream()) {
                    new XMLMapperBuilder(input, configuration, resource.toString(),
                        configuration.getSqlFragments()).parse();
                }
            }
            factory = new SqlSessionFactoryBuilder().build(configuration);
            new SchemaPreparation(factory).prepare();
            configuration.addInterceptor(counter);
            db = new JdbcTemplate(source);
            partId = id();
            customerId = id();
            db.update("INSERT INTO TB_BS_PART(PART_ID,PART_NM,REG_USER_ID) VALUES(?,?,'admin')",
                partId, "보드 시험 파트 " + partId);
            db.update("""
                INSERT INTO TB_BS_CLNT(CLNT_ID,CLNT_TYPE_CD,CLNT_NM,BIZ_REG_NO,REG_USER_ID)
                VALUES(?,'CUST',?,?,'admin')
                """, customerId, "보드 시험 고객 " + customerId,
                UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        } catch (Exception failure) {
            try {
                close();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    public SqlSessionFactory factory() { return factory; }
    public JdbcTemplate db() { return db; }
    public QueryCounter counter() { return counter; }

    public CalculationRuleService metadata(SqlSession session) {
        return new CalculationRuleService(session.getMapper(CalculationRuleMapper.class),
            session.getMapper(UserPreferencePeriodSourceMapper.class));
    }

    public String person(String name) {
        String id = id();
        db.update("""
            INSERT INTO TB_HR_DEV(DEV_ID,MNG_NO,DEV_NM,EMP_TYPE_CD,GRADE_CD,JOB_CD,
                DRCT_CLS_CD,PART_ID,JNCMP_DT,WORK_STAT_CD,REG_USER_ID)
            VALUES(?,?,?,'FT','MD','BE','DRCT',?,DATE '2026-01-01','IDLE','admin')
            """, id, id, name, partId);
        return id;
    }

    /** FIX-BOARD-028: IDs are unique even when called twice in one test schema. */
    public Map<String, String> boardSources() {
        var people = new LinkedHashMap<String, String>();
        for (String name : new String[]{"idle", "partial", "full", "concurrent", "excess",
            "retired", "leave", "futureHire", "tentative", "early"}) {
            people.put(name, person(name));
        }
        db.update("UPDATE TB_HR_DEV SET RETIRE_DT=DATE '2026-11-20',USE_YN='N' WHERE DEV_ID=?",
            people.get("retired"));
        db.update("UPDATE TB_HR_DEV SET JNCMP_DT=DATE '2027-02-01' WHERE DEV_ID=?",
            people.get("futureHire"));
        status(people.get("leave"), "LEAVE");

        String project = project("ING");
        assignment(people.get("partial"), project, "0.40");
        assignment(people.get("full"), project, "1.00");
        assignment(people.get("concurrent"), project, "0.40");
        assignment(people.get("concurrent"), project("ING"), "0.60");
        assignment(people.get("excess"), project, "0.70");
        assignment(people.get("excess"), project("ING"), "0.60");
        assignment(people.get("early"), project("PRE"), "0.50");
        String opportunity = forecastSources();
        tentative(people.get("tentative"), opportunity, "ACT", 1);
        tentative(people.get("idle"), opportunity, "CNCL", 1);
        tentative(people.get("full"), opportunity, "CNVT", 1);
        String stopped = forecastSources();
        db.update("UPDATE TB_SL_OPP SET OPP_STAT_CD='STOP' WHERE OPP_ID=?", stopped);
        tentative(people.get("idle"), stopped, "ACT", 1);
        return Map.copyOf(people);
    }

    /** FIX-FORECAST-028: forecast and board tests share the same database and clock. */
    public String forecastSources() {
        String opportunity = id();
        db.update("""
            INSERT INTO TB_SL_OPP(OPP_ID,OPP_NM,CUST_CLNT_ID,PART_ID,SALES_STEP_CD,
                WIN_PRBL_RT,EXPCT_AMT,BGN_PLAN_YM,EXPCT_PRD_MCNT,OPP_STAT_CD,
                APRV_STAT_CD,REG_USER_ID,REG_DTTM)
            VALUES(?,?,?,?,'PROP',30,300000000,'202612',6,'ING','WAIT','admin',clock_timestamp())
            """, opportunity, "예측 시험 " + opportunity, customerId, partId);
        db.update("""
            INSERT INTO TB_SL_OPP_NEED(OPP_ID,NEED_SN,JOB_CD,GRADE_CD,NEED_CNT,
                BGN_YM,PRD_MCNT,MON_MM,EXPCT_UPRC,REG_USER_ID)
            VALUES(?,1,'BE','MD',2,'202612',6,1,8000000,'admin')
            """, opportunity);
        return opportunity;
    }

    private void status(String developer, String code) {
        db.update("""
            INSERT INTO TB_HR_DEV_STAT(DEV_ID,STAT_SN,WORK_STAT_CD,BGN_DT,END_DT,REG_USER_ID)
            VALUES(?,1,?,DATE '2026-11-01',DATE '2026-12-31','admin')
            """, developer, code);
    }

    private String project(String state) {
        String project = id();
        db.update("""
            INSERT INTO TB_PJ_PROJ(PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,
                CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID)
            VALUES(?,?,'MO',?,DATE '2026-01-01',DATE '2027-05-31',0,0,0,'admin')
            """, project, "보드 프로젝트 " + project, state);
        db.update("""
            INSERT INTO TB_PJ_MNPW_PLAN(PROJ_ID,JOB_CD,PLAN_MM,SRC_CD,REG_USER_ID)
            VALUES(?,'BE',12,'MANUAL','admin')
            """, project);
        return project;
    }

    private void assignment(String developer, String project, String mm) {
        String assignment = id();
        var value = new java.math.BigDecimal(mm);
        db.update("""
            INSERT INTO TB_PJ_ASGN(ASGN_ID,PROJ_ID,DEV_ID,JOB_CD,INPUT_BGN_DT,INPUT_END_DT,
                CTRT_MM,INPUT_TYPE_CD,EXT_PSBL_CD,EXT_CNT,ASGN_STAT_CD,REG_USER_ID)
            VALUES(?,?,?,'BE',DATE '2026-01-01',DATE '2026-12-31',?,'NEW','H',0,'ACT','admin')
            """, assignment, project, developer, value);
        for (int month = 1; month <= 12; month++) {
            var period = java.time.YearMonth.of(2026, month);
            db.update("""
                INSERT INTO TB_PJ_ASGN_MON(ASGN_ID,INPUT_YM,DEV_ID,PROJ_ID,INPUT_DAY_CNT,
                    MON_MM,SALES_AMT,REG_USER_ID)
                VALUES(?,?,?,?,?,?,0,'admin')
                """, assignment, period.format(java.time.format.DateTimeFormatter.ofPattern("uuuuMM")),
                developer, project, period.lengthOfMonth(), value);
        }
    }

    private void tentative(String developer, String opportunity, String state, int need) {
        db.update("""
            INSERT INTO TB_SL_TENT_ASGN(OPP_ID,NEED_SN,DEV_ID,BGN_YM,TENT_STAT_CD,SRC_SCRN_ID,REG_USER_ID)
            VALUES(?,?,?,'202612',?,'UI-05','admin')
            """, opportunity, need, developer, state);
    }

    public String businessSnapshot() {
        var result = new StringBuilder();
        for (String table : new String[]{"TB_HR_DEV", "TB_HR_DEV_STAT", "TB_PJ_PROJ", "TB_PJ_ASGN",
            "TB_PJ_ASGN_MON", "TB_PJ_ASGN_HIST", "TB_PJ_MNPW_PLAN", "TB_SL_OPP", "TB_SL_OPP_NEED",
            "TB_SL_TENT_ASGN", "TB_SY_CALC_RULE", "TB_SY_CODE", "TB_SY_OPER_STD", "TB_SY_ACCS_LOG"}) {
            result.append(db.queryForObject("SELECT COALESCE(jsonb_agg(value ORDER BY value::text)::text,'[]') "
                + "FROM (SELECT to_jsonb(r) AS value FROM " + table + " r) records", String.class));
        }
        return result.toString();
    }

    private static String id() { return UUID.randomUUID().toString().replace("-", "").substring(0, 20); }

    private void execute(String sql) throws Exception {
        try (var statement = database.createStatement()) { statement.execute(sql); }
    }

    @Override
    public void close() throws Exception {
        try {
            execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        } finally {
            database.close();
        }
    }

    @Intercepts({
        @Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
    })
    public static final class QueryCounter implements Interceptor {
        private final Map<String, Integer> calls = new LinkedHashMap<>();
        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            var statement = (MappedStatement) invocation.getArgs()[0];
            calls.merge(statement.getId(), 1, Integer::sum);
            return invocation.proceed();
        }
        public void reset() { calls.clear(); }
        public Map<String, Integer> calls() { return Map.copyOf(calls); }
    }
}
