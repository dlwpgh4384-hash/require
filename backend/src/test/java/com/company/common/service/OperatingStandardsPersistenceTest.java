package com.company.common.service;

import com.company.common.dto.OperatingStandardsContracts.*;
import com.company.common.dto.CalculationRuleContracts.Source;
import com.company.common.dto.CalculationRuleContracts.ValueRequest;
import com.company.common.mapper.*;
import com.company.common.controller.CommonOperatingStandardsController;
import com.company.common.controller.CodeExceptionHandler;
import com.company.common.controller.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import com.company.common.schema.SchemaPreparation;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class OperatingStandardsPersistenceTest {
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Transactions {}
    private Connection database;
    private String schema;
    private AnnotationConfigApplicationContext context;
    private CommonOperatingStandardsService service;
    private CalculationRuleService calculations;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void independentSchemaAndRealMappers() throws Exception {
        assertThat(new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8))
            .contains("operationId: putCommonOperatingStandards");
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        database = DriverManager.getConnection(url, user, password);
        assertThat(database.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
        schema = "operating_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET search_path TO " + schema);
        var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        source.setDriverProperties(properties);
        var preparationConfig = new org.apache.ibatis.session.Configuration(
            new Environment("postgresql", new JdbcTransactionFactory(), source));
        new SchemaPreparation(new SqlSessionFactoryBuilder().build(preparationConfig)).prepare();
        var config = new org.apache.ibatis.session.Configuration(
            new Environment("postgresql", new SpringManagedTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
        for (var resource : List.of("mapper/OperatingStandardsMapper.xml", "mapper/UserMapper.xml",
            "mapper/UserPreferencePeriodSourceMapper.xml", "mapper/CalculationRuleMapper.xml")) {
            try (var stream = new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
            }
        }
        var template = new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(config));
        context = new AnnotationConfigApplicationContext();
        context.register(Transactions.class);
        context.registerBean("transactionManager", DataSourceTransactionManager.class,
            () -> new DataSourceTransactionManager(source));
        context.registerBean(OperatingStandardsMapper.class, () -> template.getMapper(OperatingStandardsMapper.class));
        context.registerBean(UserMapper.class, () -> template.getMapper(UserMapper.class));
        context.registerBean(UserPreferencePeriodSourceMapper.class,
            () -> template.getMapper(UserPreferencePeriodSourceMapper.class));
        context.registerBean(CalculationRuleMapper.class, () -> template.getMapper(CalculationRuleMapper.class));
        context.register(CommonOperatingStandardsService.class, CalculationRuleService.class);
        context.refresh();
        service = context.getBean(CommonOperatingStandardsService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CommonOperatingStandardsController(service))
            .setControllerAdvice(new CodeExceptionHandler(), new ApiExceptionHandler()).build();
        calculations = context.getBean(CalculationRuleService.class);
    }
    @AfterEach void removeOwnedSchema() throws Exception {
        if (context != null) context.close();
        if (database != null) {
            try { if (schema != null) execute("DROP SCHEMA " + schema + " CASCADE"); }
            finally { database.close(); }
        }
    }
    private Input input(Change... changes) { return new Input(List.of(changes)); }
    private Change change(String code, String month, String action, String value) {
        return new Change(code, value, month, action, null);
    }
    private void execute(String sql) throws Exception {
        try (var statement = database.createStatement()) { statement.execute(sql); }
    }
    private String text(String sql) throws Exception {
        try (var statement = database.createStatement(); var result = statement.executeQuery(sql)) {
            result.next(); return result.getString(1);
        }
    }
    private void closeMonth(String month) throws Exception {
        execute("INSERT INTO tb_cl_close(close_ym,close_stat_cd,reg_user_id) VALUES ('" + month + "','CLOSE','admin')");
    }
    private ValueRequest resolve(String month, String stored) {
        return new ValueRequest("VAT_RT", YearMonth.parse(month), null,
            stored == null ? null : new BigDecimal(stored));
    }
    @Test void anonymousServerActorHistoryAndOpenEffectiveValuesSurviveFreshRead() throws Exception {
        closeMonth("202702");
        mvc.perform(put("/api/common/operating-standards").contentType("application/json")
            .content(json.writeValueAsString(input(change("VAT_RT", "2027-01", "add", "12.1250")))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.items[?(@.standardCode == 'VAT_RT' && @.effectiveMonth == '2027-01')].value")
                .value(org.hamcrest.Matchers.contains("12.1250")));
        assertThat(service.getCommonOperatingStandards().items()).anySatisfy(row -> {
            assertThat(row.standardCode()).isEqualTo("VAT_RT");
            assertThat(row.effectiveMonth()).isEqualTo("2027-01");
            assertThat(new BigDecimal(row.value())).isEqualByComparingTo("12.1250");
        });
        assertThat(calculations.resolve(resolve("2026-12", null)).value()).isEqualByComparingTo("10");
        assertThat(calculations.resolve(resolve("2027-01", null)).value()).isEqualByComparingTo("12.1250");
        assertThat(calculations.resolve(resolve("2027-03", null)).value()).isEqualByComparingTo("12.1250");
        assertThat(calculations.resolve(resolve("2027-02", "9.75")).source()).isEqualTo(Source.CLOSED_RESULT);
        assertThat(calculations.resolve(resolve("2027-02", "9.75")).value()).isEqualByComparingTo("9.75");
        assertThat(text("SELECT reg_user_id FROM tb_sy_oper_std WHERE std_cd='VAT_RT' AND aply_bgn_ym='202701'"))
            .isEqualTo("admin");
        assertThat(text("SELECT reg_user_id || ':' || (reg_dttm IS NOT NULL)::text || ':' || aft_val FROM tb_sy_chg_hist WHERE trgt_key='VAT_RT:202701'"))
            .isEqualTo("admin:true:12.125");
        service.putCommonOperatingStandards(input(change("VAT_RT", "2027-01", "update", "13")));
        assertThat(text("SELECT mod_user_id || ':' || (mod_dttm IS NOT NULL)::text FROM tb_sy_oper_std WHERE std_cd='VAT_RT' AND aply_bgn_ym='202701'"))
            .isEqualTo("admin:true");
        assertThat(text("SELECT bfr_val || ':' || aft_val FROM tb_sy_chg_hist WHERE trgt_key='VAT_RT:202701' AND chg_type_cd='UPD'"))
            .isEqualTo("12.1250:13");
        service.putCommonOperatingStandards(input(change("VAT_RT", "2027-01", "delete", null)));
        assertThat(calculations.resolve(resolve("2027-01", null)).value()).isEqualByComparingTo("10");
        assertThat(text("SELECT count(*) FROM tb_sy_chg_hist WHERE trgt_key='VAT_RT:202701' AND chg_type_cd='DEL'"))
            .isEqualTo("1");
    }
    @Test void staleModifiedAtIsRejectedWith409AndKeepsTheFirstSave() throws Exception {
        String read = service.getCommonOperatingStandards().items().stream()
            .filter(row -> row.standardCode().equals("VAT_RT")).findFirst().orElseThrow().modifiedAt();
        assertThat(read).isNotBlank();
        service.putCommonOperatingStandards(input(new Change("VAT_RT", "11", "2027-01", "add", read)));
        assertThatThrownBy(() -> service.putCommonOperatingStandards(
            input(new Change("VAT_RT", "12", "2027-01", "update", read))))
            .isInstanceOfSatisfying(com.company.common.service.CodeFailure.class,
                failure -> assertThat(failure.getStatus()).isEqualTo(409));
        assertThat(text("SELECT std_val::text FROM tb_sy_oper_std WHERE std_cd='VAT_RT' AND aply_bgn_ym='202701'"))
            .startsWith("11");
    }
    @Test void auditFailureRollsBackEveryMutationAndEarlierHistory() throws Exception {
        execute("ALTER TABLE tb_sy_chg_hist ADD CONSTRAINT owned_history_check CHECK (trgt_key <> 'VAT_RT:202701')");
        assertThatThrownBy(() -> service.putCommonOperatingStandards(input(
            change("PART_DSTRB_RT", "2027-01", "add", "41"), change("VAT_RT", "2027-01", "add", "11"))))
            .isInstanceOf(RuntimeException.class);
        assertThat(text("SELECT count(*) FROM tb_sy_oper_std WHERE aply_bgn_ym='202701'")).isEqualTo("0");
        assertThat(text("SELECT count(*) FROM tb_sy_chg_hist")).isEqualTo("0");
    }
    @Test void closedMonthAndCashMutationsLeaveOriginalRowsAndHistoryUntouched() throws Exception {
        closeMonth("202601");
        String before = text("SELECT jsonb_agg(to_jsonb(s) ORDER BY std_cd,aply_bgn_ym)::text FROM tb_sy_oper_std s");
        assertThatThrownBy(() -> service.putCommonOperatingStandards(input(change("VAT_RT", "2026-01", "update", "11"))))
            .isInstanceOf(CodeFailure.class).hasMessage("마감한 달에는 적용할 수 없습니다.");
        assertThatThrownBy(() -> service.putCommonOperatingStandards(input(change("VAT_RT", "2026-01", "delete", null))))
            .isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> service.putCommonOperatingStandards(input(change("CASH_BASE_YM", null, "update", "2026-02"),
            change("CASH_BASE_AMT", null, "update", "100")))).isInstanceOf(CodeFailure.class);
        assertThat(text("SELECT jsonb_agg(to_jsonb(s) ORDER BY std_cd,aply_bgn_ym)::text FROM tb_sy_oper_std s")).isEqualTo(before);
        assertThat(text("SELECT count(*) FROM tb_sy_chg_hist")).isEqualTo("0");
    }
    @Test void cashImmediateKeepsSeedIdentityAndChangesExistingPeriodSource() throws Exception {
        service.putCommonOperatingStandards(input(change("CASH_BASE_YM", null, "update", "2026-02"),
            change("CASH_BASE_AMT", null, "update", "100.1234")));
        assertThat(text("SELECT count(*) FROM tb_sy_oper_std WHERE std_cd IN ('CASH_BASE_YM','CASH_BASE_AMT')"))
            .isEqualTo("2");
        assertThat(text("SELECT aply_bgn_ym FROM tb_sy_oper_std WHERE std_cd='CASH_BASE_YM'")).isEqualTo("202601");
        assertThat(context.getBean(UserPreferencePeriodSourceMapper.class).selectCashBaseYm())
            .isEqualByComparingTo("202602");
        assertThat(text("SELECT std_val FROM tb_sy_oper_std WHERE std_cd='CASH_BASE_AMT'"))
            .isEqualTo("100.1234");
    }
}
