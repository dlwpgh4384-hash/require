package com.company.common.mapper;

import com.company.common.controller.CommonMenuController;
import com.company.common.controller.MenuExceptionHandler;
import com.company.common.schema.SchemaPreparation;
import com.company.common.service.CommonMenuService;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(CommonMenuController.class)
@ContextConfiguration(classes = {CommonMenuController.class, CommonMenuService.class, MenuExceptionHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class MenuReadIntegrationTest {
    @Autowired MockMvc mvc;
    @MockBean MenuMapper mapper;
    private Connection database;
    private String schema;
    private SqlSession session;
    private SqlSessionFactory factory;

    @BeforeEach
    void independentSchemaAndRealMapperDelegate() throws Exception {
        String url = System.getenv("NAVIGATION_TEST_DB_URL");
        String user = System.getenv("NAVIGATION_TEST_DB_USER");
        String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
        database = DriverManager.getConnection(url, user, password);
        assertThat(database.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
        schema = "navigation_" + UUID.randomUUID().toString().replace("-", "");
        execute("CREATE SCHEMA " + schema);
        execute("SET search_path TO " + schema);
        var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
        var properties = new java.util.Properties();
        properties.setProperty("currentSchema", schema);
        source.setDriverProperties(properties);
        var config = new Configuration(new Environment("postgresql", new JdbcTransactionFactory(), source));
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
        config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT);
        try (var input = new ClassPathResource("mapper/MenuMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input, config, "mapper/MenuMapper.xml", config.getSqlFragments()).parse();
        }
        factory = new SqlSessionFactoryBuilder().build(config);
        new SchemaPreparation(factory).prepare();
        execute("DELETE FROM tb_sy_menu");
        session = factory.openSession(true);
        when(mapper.selectCandidates()).thenAnswer(call -> session.getMapper(MenuMapper.class).selectCandidates());
    }

    @AfterEach
    void removeOnlyOwnedSchema() throws Exception {
        if (session != null) session.close();
        if (database != null) {
            try { if (schema != null) execute("DROP SCHEMA " + schema + " CASCADE"); }
            finally { database.close(); }
        }
    }

    @Test
    void committedDatabaseNameOrderAndNullsSurviveNewSession() throws Exception {
        insert("b", null, "B", 5, "Y", "N");
        insert("a", null, "A", 5, "Y", "N");
        mvc.perform(get("/api/common/menus")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].menuId").value("a"));
        execute("UPDATE tb_sy_menu SET menu_nm='변경된 DB 이름',sort_ord=2 WHERE menu_id='b'");
        session.close();
        session = factory.openSession(true);
        String before = snapshot();
        mvc.perform(get("/api/common/menus")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].menuNm").value("변경된 DB 이름"))
            .andExpect(jsonPath("$.data[0].sortOrd").value(2))
            .andExpect(jsonPath("$.data[0].menuLvl").value(7))
            .andExpect(jsonPath("$.data[0].pcOnlyYn").value("N"));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void emptyReadDoesNotSeedOrWriteAnyOfTheNineTables() throws Exception {
        String before = snapshot();
        mvc.perform(get("/api/common/menus")).andExpect(status().isOk())
            .andExpect(content().json("{\"success\":true,\"meta\":{\"screenAccess\":[]},\"data\":[]}", true));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void disabledParentAndChildRemainStoredButAbsentFromData() throws Exception {
        insert("parent", null, null, 1, "N", "N");
        insert("child", "parent", "CHILD", 2, "Y", "N");
        String before = snapshot();
        mvc.perform(get("/api/common/menus")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty())
            .andExpect(jsonPath("$.meta.screenAccess[0].available").value(false));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void disabledFirstDuplicateCannotPromoteEnabledLaterRow(CapturedOutput output) throws Exception {
        insert("first", null, "SAME", 1, "N", "N");
        insert("later", null, "SAME", 2, "Y", "N");
        String before = snapshot();
        mvc.perform(get("/api/common/menus")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty())
            .andExpect(jsonPath("$.meta.screenAccess.length()").value(1))
            .andExpect(jsonPath("$.meta.screenAccess[0].available").value(false));
        assertThat(snapshot()).isEqualTo(before);
        assertThat(output.getOut()).contains("WARN", "duplicate", "representative=first");
    }

    @Test
    void pcOnlyFirstDuplicateRetainsItsPcCondition() throws Exception {
        insert("first", null, "SAME", 1, "Y", "Y");
        insert("later", null, "SAME", 2, "Y", "N");
        String before = snapshot();
        mvc.perform(get("/api/common/menus")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].menuId").value("first"))
            .andExpect(jsonPath("$.meta.screenAccess[0].pcOnlyYn").value("Y"));
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void missingParentAndDescendantWarnAndRemainUnchanged(CapturedOutput output) throws Exception {
        insert("orphan", "missing", "ORPHAN", 1, "Y", "N");
        insert("child", "orphan", "CHILD", 2, "Y", "N");
        assertInvalidBranch("missing-parent", output);
    }

    @Test
    void selfReferenceAndDescendantWarnAndRemainUnchanged(CapturedOutput output) throws Exception {
        insert("self", "self", "SELF", 1, "Y", "N");
        insert("child", "self", "CHILD", 2, "Y", "N");
        assertInvalidBranch("self-reference", output);
    }

    @Test
    void cycleAndDescendantWarnAndRemainUnchanged(CapturedOutput output) throws Exception {
        insert("a", "b", "A", 1, "Y", "N");
        insert("b", "a", "B", 2, "Y", "N");
        insert("child", "a", "CHILD", 3, "Y", "N");
        assertInvalidBranch("cycle", output);
    }

    @Test
    void mixedScreenIsUnavailableButItsGroupAndChildRemain(CapturedOutput output) throws Exception {
        insert("mixed", null, "MIXED", 1, "Y", "N");
        insert("child", "mixed", "CHILD", 2, "Y", "N");
        String before = snapshot();
        mvc.perform(get("/api/common/menus")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.meta.screenAccess[0].available").value(false))
            .andExpect(jsonPath("$.meta.screenAccess[1].available").value(true));
        assertThat(output.getOut()).contains("WARN", "mixed group");
        assertThat(snapshot()).isEqualTo(before);
    }

    @Test
    void normalGroupAndUniqueChildHaveNoMixedOrDuplicateWarning(CapturedOutput output) throws Exception {
        insert("group", null, null, 1, "Y", "N");
        insert("child", "group", "CHILD", 2, "Y", "N");
        mvc.perform(get("/api/common/menus")).andExpect(status().isOk());
        assertThat(output.getOut()).doesNotContain("Menu duplicate", "Menu mixed group");
    }

    @Test
    void actualSqlReadFailureProducesSanitized500WithoutCreatingTables() throws Exception {
        execute("DROP TABLE tb_sy_menu");
        mvc.perform(get("/api/common/menus")).andExpect(status().isInternalServerError())
            .andExpect(content().json("{\"success\":false,\"meta\":{}}", true));
        try (var result = database.createStatement().executeQuery("SELECT to_regclass('tb_sy_menu')")) {
            result.next(); assertThat(result.getString(1)).isNull();
        }
    }

    private void assertInvalidBranch(String reason, CapturedOutput output) throws Exception {
        insert("root", "", "ROOT", 9, "Y", "N");
        String before = snapshot();
        mvc.perform(get("/api/common/menus")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].menuId").value("root"));
        assertThat(output.getOut()).contains("WARN", reason);
        assertThat(snapshot()).isEqualTo(before);
    }

    private void insert(String id, String parent, String screen, int order, String use, String pc) throws Exception {
        try (var statement = database.prepareStatement("INSERT INTO tb_sy_menu(menu_id,upper_menu_id,menu_nm,scrn_id,menu_lvl,sort_ord,use_yn,pc_only_yn,reg_user_id) VALUES (?,?,?,?,7,?,?,?,'OWNED')")) {
            statement.setString(1, id); statement.setString(2, parent); statement.setString(3, "메뉴 " + id);
            statement.setString(4, screen); statement.setInt(5, order); statement.setString(6, use); statement.setString(7, pc);
            statement.executeUpdate();
        }
    }

    private String snapshot() throws Exception {
        StringBuilder value = new StringBuilder();
        try (var tables = database.createStatement().executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema=current_schema() ORDER BY table_name")) {
            while (tables.next()) {
                String table = tables.getString(1);
                try (var statement = database.createStatement(); var rows = statement.executeQuery("SELECT coalesce(jsonb_agg(v ORDER BY v::text),'[]'::jsonb)::text FROM (SELECT to_jsonb(t) v FROM " + table + " t) entries")) {
                    rows.next(); value.append(table).append(rows.getString(1));
                }
            }
        }
        return value.toString();
    }

    private void execute(String sql) throws Exception {
        try (var statement = database.createStatement()) { statement.execute(sql); }
    }
}
