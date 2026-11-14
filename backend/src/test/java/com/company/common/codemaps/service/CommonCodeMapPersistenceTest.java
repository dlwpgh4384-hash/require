package com.company.common.codemaps.service;

import com.company.common.codegroups.service.CodeTextResolver;
import com.company.common.support.service.CodeFailure;
import com.company.common.codemaps.dto.CodeMapContracts.DeleteInput;
import com.company.common.codemaps.dto.CodeMapContracts.Item;
import com.company.common.codemaps.controller.CommonCodeMapController;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.support.controller.ApiExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.codemaps.mapper.CommonCodeMapMapper;
import com.company.common.users.mapper.UserMapper;
import com.company.common.schema.SchemaPreparation;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class CommonCodeMapPersistenceTest {
    Connection database;
    String schema;
    CommonCodeMapService service;
    CodeTextResolver resolver;
    Item input;
    MockMvc mvc;
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach void independentSchema() throws Exception {
        assertThat(new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8))
            .contains("operationId: postCommonCodeMap", "operationId: postCommonCodeMapDelete");
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        database = DriverManager.getConnection(url, user, password);
        assertThat(database.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
        schema = "code_maps_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET search_path TO " + schema);
        var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        source.setDriverProperties(properties);
        var config = new Configuration(new Environment("postgresql", new SpringManagedTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
        for (String resource : List.of("mapper/CommonCodeMapMapper.xml", "mapper/UserMapper.xml", "mapper/CalculationRuleMapper.xml")) {
            try (var stream = new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
            }
        }
        var preparationConfig = new Configuration(new Environment("preparation",
            new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), source));
        new SchemaPreparation(new SqlSessionFactoryBuilder().build(preparationConfig)).prepare();
        var sessions = new SqlSessionFactoryBuilder().build(config);
        var template = new SqlSessionTemplate(sessions);
        var target = new CommonCodeMapService(template.getMapper(CommonCodeMapMapper.class), template.getMapper(UserMapper.class));
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
            new AnnotationTransactionAttributeSource()));
        service = (CommonCodeMapService) proxy.getProxy();
        mvc = MockMvcBuilders.standaloneSetup(new CommonCodeMapController(service))
            .setControllerAdvice(new CodeExceptionHandler(), new ApiExceptionHandler()).build();
        resolver = new CodeTextResolver(template.getMapper(CalculationRuleMapper.class));
        input = new Item("독립 업로드 표기", "EMP_TYPE", text("SELECT code FROM tb_sy_code WHERE code_grp_id='EMP_TYPE' AND use_yn='Y' AND del_yn='N' ORDER BY code LIMIT 1"));
    }

    @AfterEach void removeOwnedSchema() throws Exception {
        if (database != null) {
            try { if (schema != null) execute("DROP SCHEMA " + schema + " CASCADE"); }
            finally { database.close(); }
        }
    }

    @Test void createCommitReadbackResolverAndDeleteCommitUseSameDomainRows() throws Exception {
        assertThat(service.getCommonCodeMaps().items()).isEmpty();
        mvc.perform(post("/api/common/code-maps").contentType("application/json").content(json.writeValueAsString(input)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.excelText").value(input.excelText()))
            .andExpect(jsonPath("$.data.groupId").value(input.groupId()))
            .andExpect(jsonPath("$.data.code").value(input.code()));
        assertThat(service.getCommonCodeMaps().items()).containsExactly(input);
        assertThat(text("SELECT reg_user_id FROM tb_sy_code_map")).isEqualTo("admin");
        assertThat(text("SELECT reg_user_id FROM tb_sy_chg_hist WHERE trgt_tbl_nm='TB_SY_CODE_MAP' AND chg_type_cd='NEW'")).isEqualTo("admin");
        var resolved = resolver.resolve(input.groupId(), input.excelText());
        assertThat(resolved.code()).isEqualTo(input.code());
        assertThat(resolved.warning()).contains(input.excelText(), "바꿈");
        // The mapping has already been consumed; use does not block physical deletion.
        mvc.perform(post("/api/common/code-maps/delete").contentType("application/json")
            .content(json.writeValueAsString(new DeleteInput(List.of(input)))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.deleted[0].code").value(input.code()));
        assertThat(service.getCommonCodeMaps().items()).isEmpty();
        assertThat(text("SELECT count(*)::text FROM tb_sy_code_map")).isEqualTo("0");
        assertThat(text("SELECT bfr_val FROM tb_sy_chg_hist WHERE chg_type_cd='DEL'"))
            .contains(input.excelText(), input.groupId(), input.code());
        assertThat(text("SELECT reg_user_id FROM tb_sy_chg_hist WHERE chg_type_cd='DEL'")).isEqualTo("admin");
        assertThatThrownBy(() -> resolver.resolve(input.groupId(), input.excelText())).isInstanceOf(CodeFailure.class);
    }

    @Test void insertionHistoryFailureRollsBackMapping() throws Exception {
        rejectHistory();
        assertThatThrownBy(() -> service.postCommonCodeMap(input)).isInstanceOf(RuntimeException.class);
        assertThat(text("SELECT count(*)::text FROM tb_sy_code_map")).isEqualTo("0");
        assertThat(text("SELECT count(*)::text FROM tb_sy_chg_hist")).isEqualTo("0");
    }

    @Test void deletionHistoryFailureRollsBackPhysicalDeletion() throws Exception {
        service.postCommonCodeMap(input);
        rejectHistory();
        assertThatThrownBy(() -> service.postCommonCodeMapDelete(new DeleteInput(List.of(input))))
            .isInstanceOf(RuntimeException.class);
        assertThat(service.getCommonCodeMaps().items()).containsExactly(input);
        assertThat(text("SELECT count(*)::text FROM tb_sy_chg_hist WHERE chg_type_cd='DEL'")).isEqualTo("0");
    }

    @Test void duplicateKeepsExistingRowButDifferentSeededGroupAllowsSameText() throws Exception {
        service.postCommonCodeMap(input);
        assertThatThrownBy(() -> service.postCommonCodeMap(input)).isInstanceOf(CodeFailure.class);
        String group = text("SELECT code_grp_id FROM tb_sy_code WHERE code_grp_id <> 'EMP_TYPE' AND use_yn='Y' AND del_yn='N' ORDER BY code_grp_id,code LIMIT 1");
        String code;
        try (var statement = database.prepareStatement("SELECT code FROM tb_sy_code WHERE code_grp_id=? AND use_yn='Y' AND del_yn='N' ORDER BY code LIMIT 1")) {
            statement.setString(1, group);
            try (var result = statement.executeQuery()) { result.next(); code = result.getString(1); }
        }
        var another = new Item(input.excelText(), group, code);
        service.postCommonCodeMap(another);
        assertThat(service.getCommonCodeMaps().items()).containsExactly(input, another);
        assertThat(text("SELECT count(*)::text FROM tb_sy_chg_hist WHERE chg_type_cd='NEW'")).isEqualTo("2");
    }

    private void rejectHistory() throws Exception {
        execute("CREATE FUNCTION reject_map_history() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'owned history rejection'; END $$");
        execute("CREATE TRIGGER reject_map_history BEFORE INSERT ON tb_sy_chg_hist FOR EACH ROW EXECUTE FUNCTION reject_map_history()");
    }
    private void execute(String sql) throws Exception {
        try (var statement = database.createStatement()) { statement.execute(sql); }
    }
    private String text(String sql) throws Exception {
        try (var statement = database.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }
}
