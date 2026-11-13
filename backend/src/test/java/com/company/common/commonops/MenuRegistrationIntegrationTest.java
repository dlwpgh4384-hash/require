package com.company.common.commonops;

import com.company.common.controller.*;
import com.company.common.dto.MenuRegistrationContracts.Create;
import com.company.common.dto.RoleContracts.Copy;
import com.company.common.mapper.*;
import com.company.common.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest({MenuRegistrationController.class, CommonRoleController.class, CommonMenuController.class})
@ContextConfiguration(classes = {MenuRegistrationController.class, MenuRegistrationService.class,
    DevelopedScreenRegistry.class, MenuRegistrationExceptionHandler.class, ApiExceptionHandler.class,
    CommonRoleController.class, CommonRoleService.class, RoleExceptionHandler.class,
    CommonMenuController.class, CommonMenuService.class, MenuExceptionHandler.class,
    MenuRegistrationIntegrationTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MenuRegistrationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired OwnedSchema owned;
    @Autowired MenuRegistrationService service;
    @Autowired CommonRoleService roles;
    @Autowired MenuRegistrationMapper mapper;
    JdbcTemplate db;
    record OwnedSchema(String name) {}

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean OwnedSchema ownedSchema() {
            return new OwnedSchema("menus_" + UUID.randomUUID().toString().replace("-", ""));
        }
        @Bean DataSource dataSource(OwnedSchema owned) throws Exception {
            String url = System.getenv("NAVIGATION_TEST_DB_URL");
            String user = System.getenv("NAVIGATION_TEST_DB_USER");
            String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var connection = DriverManager.getConnection(url, user, password);
                 var statement = connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
                statement.execute("CREATE SCHEMA " + owned.name());
                statement.execute("SET search_path TO " + owned.name());
                for (String resource : List.of("V1__navigation_schema.sql", "V2__navigation_menu_seed.sql",
                    "V3__common_function_seed.sql", "V4__user_management_seed.sql",
                    "V5__role_permissions.sql", "V6__role_schema_contract.sql")) {
                    statement.execute(new ClassPathResource("db/migration/" + resource)
                        .getContentAsString(StandardCharsets.UTF_8));
                }
            }
            var data = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new java.util.Properties();
            properties.setProperty("currentSchema", owned.name());
            data.setDriverProperties(properties);
            return data;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/MenuRegistrationMapper.xml"),
                new ClassPathResource("mapper/UserMapper.xml"), new ClassPathResource("mapper/RoleMapper.xml"),
                new ClassPathResource("mapper/MenuMapper.xml"));
            var config = new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config);
            return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean MenuRegistrationMapper menuRegistrationMapper(SqlSessionTemplate session) { return session.getMapper(MenuRegistrationMapper.class); }
        @Bean UserMapper userMapper(SqlSessionTemplate session) { return session.getMapper(UserMapper.class); }
        @Bean RoleMapper roleMapper(SqlSessionTemplate session) { return session.getMapper(RoleMapper.class); }
        @Bean MenuMapper menuMapper(SqlSessionTemplate session) { return session.getMapper(MenuMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void setup() throws Exception {
        db = new JdbcTemplate(source);
        assertThat(new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8))
            .contains("operationId: postCommonMenu");
    }
    @AfterEach void cleanup() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + owned.name() + " CASCADE");
        }
    }
    private long count(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    private String text(String sql, Object... args) { return db.queryForObject(sql, String.class, args); }
    private String parent() {
        return text("SELECT menu_id FROM TB_SY_MENU WHERE menu_lvl=1 AND use_yn='Y' ORDER BY sort_ord,menu_id LIMIT 1");
    }
    private void freeDevelopedScreen() {
        // 이 시험이 소유한 독립 스키마에서 실제 개발 화면의 기존 연결만 비운다.
        db.update("UPDATE TB_SY_MENU SET scrn_id=NULL WHERE scrn_id='UI-25'");
    }
    private String childBody() throws Exception {
        return json.writeValueAsString(Map.of("menuLvl", 2, "menuNm", "합성 메뉴",
            "upperMenuId", parent(), "scrnId", "UI-25", "pcOnlyYn", "N"));
    }
    private String postChild() throws Exception {
        var result = mvc.perform(post("/api/common/menus").contentType("application/json").content(childBody()))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .path("data").path("menuId").asText();
    }
    private String snapshot() {
        return text("SELECT jsonb_build_array("
            + "(SELECT jsonb_agg(to_jsonb(m) ORDER BY m.menu_id) FROM TB_SY_MENU m),"
            + "(SELECT jsonb_agg(to_jsonb(a) ORDER BY a.role_id,a.menu_id) FROM TB_SY_ROLE_MENU_AUTH a),"
            + "(SELECT jsonb_agg(to_jsonb(h) ORDER BY h.hist_id) FROM TB_SY_CHG_HIST h))::text");
    }
    @Test
    @DisplayName("happy side-effect: registered TB_SY_MENU is read by Sidebar via the next menu GET")
    void childAppendsPersistsAuditAndSurvivesFreshConnections() throws Exception {
        freeDevelopedScreen();
        String parent = parent();
        long expected = count("SELECT COALESCE(MAX(sort_ord),0)+1 FROM TB_SY_MENU WHERE upper_menu_id=?", parent);
        var response = mvc.perform(post("/api/common/menus").contentType("application/json").content(childBody()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.scrnId").value("UI-25")).andReturn();
        String id = json.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8))
            .path("data").path("menuId").asText();
        assertThat(id).matches("M[0-9]{3,}");
        assertThat(count("SELECT sort_ord FROM TB_SY_MENU WHERE menu_id=?", id)).isEqualTo(expected);
        assertThat(text("SELECT reg_user_id FROM TB_SY_MENU WHERE menu_id=?", id)).isEqualTo("admin");
        assertThat(text("SELECT icon_nm FROM TB_SY_MENU WHERE menu_id=?", id)).isNull();
        assertThat(mapper.selectMenu(id).value().scrnId()).isEqualTo("UI-25");
        assertThat(mapper.selectMenu(id).sortOrd).isEqualTo((int) expected);
        mvc.perform(get("/api/common/menus")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.menuId == '" + id + "')].scrnId")
                .value(org.hamcrest.Matchers.contains("UI-25")));
    }
    @Test void anonymousGroupHasFolderAndNullScreenAndInitialRowsForEveryRole() throws Exception {
        var result = mvc.perform(post("/api/common/menus").contentType("application/json")
            .content(json.writeValueAsString(Map.of("menuLvl", 1, "menuNm", "합성 묶음"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.iconNm").value("folder")).andReturn();
        String id = json.readTree(result.getResponse().getContentAsString()).path("data").path("menuId").asText();
        assertThat(id).matches("G[0-9]{3,}");
        assertThat(text("SELECT scrn_id FROM TB_SY_MENU WHERE menu_id=?", id)).isNull();
        assertThat(text("SELECT reg_user_id FROM TB_SY_MENU WHERE menu_id=?", id)).isEqualTo("admin");
        assertThat(count("SELECT count(*) FROM TB_SY_ROLE_MENU_AUTH WHERE menu_id=?", id))
            .isEqualTo(count("SELECT count(*) FROM TB_SY_ROLE"));
    }
    @Test void stoppedAndActiveRolesReceiveSevenNAndOnlyOneRegistrationHistory() throws Exception {
        freeDevelopedScreen();
        db.update("INSERT INTO TB_SY_ROLE(role_id,role_nm,data_scope_cd,sort_ord,use_yn,reg_user_id) "
            + "SELECT 'owned-stopped','합성 중지 역할',r.data_scope_cd,99,'N','admin' FROM TB_SY_ROLE r WHERE r.role_id='ADMIN'");
        String id = postChild();
        var roleIds = db.queryForList("SELECT role_id FROM TB_SY_ROLE ORDER BY role_id", String.class);
        assertThat(count("SELECT count(*) FROM TB_SY_ROLE_MENU_AUTH WHERE menu_id=?", id)).isEqualTo(roleIds.size());
        assertThat(count("SELECT count(*) FROM TB_SY_ROLE_MENU_AUTH WHERE menu_id=? "
            + "AND 'Y' IN (inq_yn,reg_yn,upd_yn,del_yn,upld_yn,dnld_yn,cnfm_yn)", id)).isZero();
        for (String roleId : roleIds) {
            var response = mvc.perform(get("/api/common/roles/" + roleId)).andExpect(status().isOk()).andReturn();
            var rows = json.readTree(response.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data").path("menuAuths");
            var found = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
            rows.forEach(row -> { if (id.equals(row.path("menuId").asText())) found.add(row); });
            assertThat(found).hasSize(1);
            for (String flag : List.of("readYn", "createYn", "updateYn", "deleteYn", "uploadYn", "downloadYn", "cnfmYn"))
                assertThat(found.get(0).path(flag).asText()).isEqualTo("N");
        }
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_tbl_nm='TB_SY_MENU' "
            + "AND trgt_key=? AND chg_type_cd='NEW' AND bfr_val IS NULL AND aft_val='합성 메뉴' AND reg_user_id='admin'", id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_tbl_nm='TB_SY_ROLE_MENU_AUTH'")).isZero();
    }
    @ParameterizedTest @ValueSource(strings = {"upperMenuId", "scrnId"})
    void missingRequiredChildFieldsLeaveAllRowsUnchanged(String field) throws Exception {
        var body = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(childBody());
        body.remove(field);
        String before = snapshot();
        mvc.perform(post("/api/common/menus").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void duplicateStoppedScreenDoesNotChangeAnyExistingRow() throws Exception {
        db.update("UPDATE TB_SY_MENU SET use_yn='N' WHERE scrn_id='UI-25'");
        String before = snapshot();
        mvc.perform(post("/api/common/menus").contentType("application/json").content(childBody()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("이미 다른 메뉴에 등록된 화면입니다."));
        assertThat(snapshot()).isEqualTo(before);
    }
    @ParameterizedTest @ValueSource(strings = {"TB_SY_ROLE_MENU_AUTH", "TB_SY_CHG_HIST"})
    void downstreamFailureRollsBackMenuPermissionsAndHistory(String table) throws Exception {
        freeDevelopedScreen();
        db.execute("ALTER TABLE " + table + " ADD CONSTRAINT owned_rejection CHECK (reg_user_id <> 'admin') NOT VALID");
        String before = snapshot();
        mvc.perform(post("/api/common/menus").contentType("application/json").content(childBody()))
            .andExpect(status().isInternalServerError());
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void historyIssuedNumberCannotBeReusedAfterRemoval() throws Exception {
        db.update("INSERT INTO TB_SY_CHG_HIST(trgt_tbl_nm,trgt_key,chg_type_cd,reg_user_id) VALUES ('TB_SY_MENU','G999','NEW','admin')");
        var item = service.postCommonMenu(new Create(1, "합성 묶음", null, null, null));
        assertThat(item.menuId()).isEqualTo("G1000");
    }
    @Test void concurrentMenusIssueDifferentIdsAndSiblingOrders() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.postCommonMenu(new Create(1, "합성 첫 묶음", null, null, null)));
            var second = executor.submit(() -> service.postCommonMenu(new Create(1, "합성 둘째 묶음", null, null, null)));
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);
            assertThat(a.menuId()).isNotEqualTo(b.menuId());
            assertThat(a.sortOrd()).isNotEqualTo(b.sortOrd());
            assertThat(count("SELECT count(*) FROM TB_SY_MENU WHERE menu_id IN (?,?)", a.menuId(), b.menuId())).isEqualTo(2);
        } finally { executor.shutdownNow(); }
    }
    @Test void concurrentRoleAndChildCreationRetainNewRolePermission() throws Exception {
        freeDevelopedScreen();
        String parent = parent();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var menu = executor.submit(() -> service.postCommonMenu(new Create(2, "합성 메뉴", parent, "UI-25", "N")));
            var role = executor.submit(() -> roles.postCommonRoleCopy(new Copy(null, "합성 동시 역할", "ALL")));
            var item = menu.get(20, TimeUnit.SECONDS);
            var settings = role.get(20, TimeUnit.SECONDS);
            assertThat(count("SELECT count(*) FROM TB_SY_ROLE_MENU_AUTH WHERE role_id=? AND menu_id=?",
                settings.role().roleId(), item.menuId())).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM TB_SY_ROLE_MENU_AUTH WHERE menu_id=?", item.menuId()))
                .isEqualTo(count("SELECT count(*) FROM TB_SY_ROLE"));
        } finally { executor.shutdownNow(); }
    }
}
