package com.company.common.menus.service;

import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.menus.controller.CommonMenuController;
import com.company.common.menus.controller.CommonMenuSettingsController;
import com.company.common.menus.controller.MenuExceptionHandler;
import com.company.common.menus.controller.MenuSettingsExceptionHandler;
import com.company.common.menus.mapper.MenuMapper;
import com.company.common.menus.mapper.MenuSettingsMapper;
import com.company.common.users.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
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
@WebMvcTest({CommonMenuSettingsController.class, CommonMenuController.class})
@ContextConfiguration(classes = {CommonMenuSettingsController.class, CommonMenuSettingsService.class,
    MenuSettingsExceptionHandler.class, ApiExceptionHandler.class, CommonMenuController.class,
    CommonMenuService.class, MenuExceptionHandler.class, MenuSettingsIntegrationTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MenuSettingsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired OwnedSchema owned;
    @Autowired MenuSettingsMapper mapper;
    JdbcTemplate db;
    record OwnedSchema(String name) {}

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean OwnedSchema ownedSchema() {
            return new OwnedSchema("menu_settings_" + UUID.randomUUID().toString().replace("-", ""));
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
                for (String file : List.of("V1__navigation_schema.sql", "V2__navigation_menu_seed.sql",
                    "V3__common_function_seed.sql", "V4__user_management_seed.sql",
                    "V5__role_permissions.sql", "V6__role_schema_contract.sql")) {
                    statement.execute(new ClassPathResource("db/migration/" + file)
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
            factory.setMapperLocations(new ClassPathResource("mapper/MenuSettingsMapper.xml"),
                new ClassPathResource("mapper/UserMapper.xml"), new ClassPathResource("mapper/MenuMapper.xml"));
            var config = new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config);
            return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean MenuSettingsMapper menuSettingsMapper(SqlSessionTemplate session) { return session.getMapper(MenuSettingsMapper.class); }
        @Bean MenuMapper menuMapper(SqlSessionTemplate session) { return session.getMapper(MenuMapper.class); }
        @Bean UserMapper userMapper(SqlSessionTemplate session) { return session.getMapper(UserMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void setup() throws Exception {
        db = new JdbcTemplate(source);
        assertThat(new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8))
            .contains("operationId: getCommonMenuSettings", "operationId: putCommonMenuSettings");
    }
    @AfterEach void cleanup() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + owned.name() + " CASCADE");
        }
    }
    private com.fasterxml.jackson.databind.node.ObjectNode item(String id) {
        var row = mapper.selectSettings().stream().filter(value -> value.menuId.equals(id)).findFirst().orElseThrow();
        return json.createObjectNode().put("menuId", id).put("menuNm", row.menuNm)
            .put("upperMenuId", row.upperMenuId).put("sortOrd", row.sortOrd);
    }
    private org.springframework.test.web.servlet.ResultActions save(List<com.fasterxml.jackson.databind.node.ObjectNode> items,
        String... moved) throws Exception {
        var body = json.createObjectNode();
        var list = body.putArray("items"); items.forEach(list::add);
        var ids = body.putArray("movedMenuIds"); for (String id : moved) ids.add(id);
        return mvc.perform(put("/api/common/menu-settings").contentType("application/json").content(body.toString()));
    }
    private String text(String sql, Object... args) { return db.queryForObject(sql, String.class, args); }
    private long count(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    private String snapshot() {
        return text("SELECT jsonb_build_array("
            + "(SELECT jsonb_agg(to_jsonb(m) ORDER BY m.menu_id) FROM TB_SY_MENU m),"
            + "(SELECT jsonb_agg(to_jsonb(h) ORDER BY h.hist_id) FROM TB_SY_CHG_HIST h))::text");
    }

    @Test
    @DisplayName("happy auth side-effect: MOD_USER_ID=admin persists; getCommonMenus refreshes Sidebar after save")
    void anonymousRenameAndDirectReorderPersistAuditAndFreshGet() throws Exception {
        var request = json.createObjectNode();
        request.putArray("items").add(item("M028").put("menuNm", " 합성 권한 이름 "))
            .add(item("M006").put("sortOrd", 1)).add(item("M004").put("sortOrd", 2))
            .add(item("M005").put("sortOrd", 3));
        request.putArray("movedMenuIds").add("M006");
        mvc.perform(put("/api/common/menu-settings").contentType("application/json").content(request.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertThat(text("SELECT menu_nm FROM TB_SY_MENU WHERE menu_id='M028'")).isEqualTo(" 합성 권한 이름 ");
        assertThat(count("SELECT sort_ord FROM TB_SY_MENU WHERE menu_id='M006'")).isEqualTo(1);
        assertThat(text("SELECT mod_user_id FROM TB_SY_MENU WHERE menu_id='M006'")).isEqualTo("admin");
        assertThat(count("SELECT count(*) FROM TB_SY_MENU WHERE menu_id='M006' AND mod_dttm IS NOT NULL")).isEqualTo(1);
        mvc.perform(get("/api/common/menu-settings")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[?(@.menuId == 'M028')].menuNm").value(org.hamcrest.Matchers.contains(" 합성 권한 이름 ")))
            .andExpect(jsonPath("$.data.items[?(@.menuId == 'M006')].sortOrd").value(org.hamcrest.Matchers.contains(1)));
        mvc.perform(get("/api/common/menus")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.menuId == 'M028')].menuNm").value(org.hamcrest.Matchers.contains(" 합성 권한 이름 ")));
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE chg_item_nm='SORT_ORD' AND trgt_key='M006' "
            + "AND bfr_val='3' AND aft_val='1' AND reg_user_id='admin' AND reg_dttm IS NOT NULL")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key IN ('M004','M005')")).isZero();
    }
    @ParameterizedTest @ValueSource(strings = {"", " ", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    void invalidNameKeepsDatabaseAndHistoryUnchanged(String name) throws Exception {
        String before = snapshot();
        save(List.of(item("M006").put("menuNm", name))).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("menuNm"));
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void fiftyCodePointsPersist() throws Exception {
        save(List.of(item("M006").put("menuNm", "😀".repeat(50)))).andExpect(status().isOk());
        assertThat(text("SELECT menu_nm FROM TB_SY_MENU WHERE menu_id='M006'")).isEqualTo("😀".repeat(50));
    }
    @Test void lockedDisableIsAtomic() throws Exception {
        String before = snapshot();
        save(List.of(item("M006").put("menuNm", "합성 변경"), item("M028").put("useYn", "N")))
            .andExpect(status().isBadRequest());
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void lockedMoveIsAtomic() throws Exception {
        String before = snapshot();
        save(List.of(item("M028").put("upperMenuId", "G003").put("sortOrd", 6)))
            .andExpect(status().isBadRequest());
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void movedMenuRecordsEachFieldAndPreservesDisplacedSiblingWithoutHistory() throws Exception {
        save(List.of(item("M006").put("upperMenuId", "G002").put("sortOrd", 1)
            .put("menuNm", "변경 메뉴").put("useYn", "N").put("pcOnlyYn", "Y"),
            item("M002").put("sortOrd", 2), item("M003").put("sortOrd", 3),
            item("M007").put("sortOrd", 3), item("M008").put("sortOrd", 4)), "M006")
            .andExpect(status().isOk());
        assertThat(db.queryForList("SELECT chg_item_nm FROM TB_SY_CHG_HIST WHERE trgt_key='M006'", String.class))
            .containsExactlyInAnyOrder("MENU_NM", "UPPER_MENU_ID", "USE_YN", "PC_ONLY_YN", "SORT_ORD");
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key='M006' AND reg_user_id='admin' "
            + "AND reg_dttm IS NOT NULL AND bfr_val IS NOT NULL AND aft_val IS NOT NULL")).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key='M006' AND ("
            + "(chg_item_nm='MENU_NM' AND bfr_val='투입 배정' AND aft_val='변경 메뉴') OR "
            + "(chg_item_nm='UPPER_MENU_ID' AND bfr_val='G003' AND aft_val='G002') OR "
            + "(chg_item_nm='USE_YN' AND bfr_val='Y' AND aft_val='N') OR "
            + "(chg_item_nm='PC_ONLY_YN' AND bfr_val='N' AND aft_val='Y') OR "
            + "(chg_item_nm='SORT_ORD' AND bfr_val='3' AND aft_val='1'))")).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key IN ('M002','M003','M007','M008')")).isZero();
        assertThat(text("SELECT upper_menu_id FROM TB_SY_MENU WHERE menu_id='M006'")).isEqualTo("G002");
    }
    @Test void historyFailureRollsBackEveryMenuAndHistory() throws Exception {
        db.execute("ALTER TABLE TB_SY_CHG_HIST ADD CONSTRAINT owned_rejection CHECK(reg_user_id <> 'admin') NOT VALID");
        String before = snapshot();
        save(List.of(item("M006").put("menuNm", "합성 변경"))).andExpect(status().isInternalServerError());
        assertThat(snapshot()).isEqualTo(before);
    }
    @Test void disabledRowIsPreservedInManagementButAbsentFromSidebar() throws Exception {
        save(List.of(item("M006").put("useYn", "N"))).andExpect(status().isOk());
        mvc.perform(get("/api/common/menus")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.menuId == 'M006')]").isEmpty());
        mvc.perform(get("/api/common/menu-settings")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[?(@.menuId == 'M006')].useYn").value(org.hamcrest.Matchers.contains("N")));
        assertThat(count("SELECT count(*) FROM TB_SY_MENU WHERE menu_id='M006'")).isEqualTo(1);
    }
    @Test
    @DisplayName("side-effect: PC_ONLY_YN N to Y persists and getCommonMenus exposes the new screen access")
    void pcOnlySaveIsReflectedInExistingScreenAccess() throws Exception {
        assertThat(text("SELECT pc_only_yn FROM TB_SY_MENU WHERE menu_id='M006'")).isEqualTo("N");
        var request = json.createObjectNode();
        request.putArray("items").add(item("M006").put("pcOnlyYn", "Y"));
        request.putArray("movedMenuIds");
        mvc.perform(put("/api/common/menu-settings").contentType("application/json").content(request.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertThat(text("SELECT pc_only_yn FROM TB_SY_MENU WHERE menu_id='M006'")).isEqualTo("Y");
        mvc.perform(get("/api/common/menus")).andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.screenAccess[?(@.scrnId == 'UI-12')].pcOnlyYn")
                .value(org.hamcrest.Matchers.contains("Y")));
    }
    @Test void noOpHasNoHistory() throws Exception {
        long before = count("SELECT count(*) FROM TB_SY_CHG_HIST");
        save(List.of(item("M006")), "M006").andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST")).isEqualTo(before);
    }
}
