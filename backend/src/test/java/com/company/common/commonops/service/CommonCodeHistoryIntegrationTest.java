package com.company.common.commonops.service;

import com.company.common.controller.ApiExceptionHandler;
import com.company.common.controller.CodeExceptionHandler;
import com.company.common.controller.CommonCodeHistoryController;
import com.company.common.mapper.CommonCodeHistoryMapper;
import com.company.common.service.CommonCodeHistoryService;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
@WebMvcTest(CommonCodeHistoryController.class)
@ContextConfiguration(classes = {CommonCodeHistoryController.class, CommonCodeHistoryService.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class, CommonCodeHistoryIntegrationTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CommonCodeHistoryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired DataSource source;
    @Autowired OwnedSchema owned;
    JdbcTemplate db;
    record OwnedSchema(String name) {}

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean OwnedSchema ownedSchema() {
            return new OwnedSchema("codehistory_" + UUID.randomUUID().toString().replace("-", ""));
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
                statement.execute(new ClassPathResource("db/migration/V1__navigation_schema.sql")
                    .getContentAsString(StandardCharsets.UTF_8));
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
            factory.setMapperLocations(new ClassPathResource("mapper/CommonCodeHistoryMapper.xml"));
            var config = new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config);
            return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory);
        }
        @Bean CommonCodeHistoryMapper commonCodeHistoryMapper(SqlSessionTemplate session) {
            return session.getMapper(CommonCodeHistoryMapper.class);
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }

    @BeforeEach void setup() { db = new JdbcTemplate(source); }
    @AfterEach void cleanup() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + owned.name() + " CASCADE");
        }
    }
    private void history(String table, String key, String item, String before, String after, String time) {
        db.update("""
            INSERT INTO TB_SY_CHG_HIST
              (trgt_tbl_nm, trgt_key, chg_type_cd, chg_item_nm, bfr_val, aft_val, reg_user_id, reg_dttm)
            VALUES (?, ?, 'UPD', ?, ?, ?, 'admin', CAST(? AS timestamptz))
            """, table, key, item, before, after, time);
    }

    @Test void fourTargetsOnlyIncludingMapHistoryWhoseSourceRowNoLongerExists() throws Exception {
        history("TB_SY_CODE_GRP", "HISTORY_GROUP", "그룹명", "그룹 전", "그룹 후", "2026-11-13T00:00:00Z");
        history("TB_SY_CODE", "HISTORY_GROUP:A", "코드명", "코드 전", "코드 후", "2026-11-13T00:01:00Z");
        history("TB_SY_OPER_STD", "PART_RT:202611", "기준값", "40", "45", "2026-11-13T00:02:00Z");
        history("TB_SY_CODE_MAP", "999999", "표기 대응", "표기 전", "표기 후", "2026-11-13T00:03:00Z");
        history("TB_SY_USER", "admin", "HISTORY_GROUP:A", "TB_SY_CODE", "TB_SY_CODE_GRP", "2026-11-14T00:00:00Z");
        history("TB_SY_MENU", "UI-27", "메뉴", "전", "후", "2026-11-15T00:00:00Z");
        mvc.perform(get("/api/common/code-change-history"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(4))
            .andExpect(jsonPath("$.data.items[0].kind").value("표기 대응"))
            .andExpect(jsonPath("$.data.items[1].kind").value("운영 기준값"))
            .andExpect(jsonPath("$.data.items[2].kind").value("코드"))
            .andExpect(jsonPath("$.data.items[3].kind").value("그룹"))
            .andExpect(jsonPath("$.data.items[0].before").value("표기 전"))
            .andExpect(jsonPath("$.data.items[0].after").value("표기 후"))
            .andExpect(jsonPath("$.data.items[0].processor").value("admin"));
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_CHG_HIST", Long.class)).isEqualTo(6);
    }

    @Test void newestTimestampThenNewestIdentityDeterminesOrder() throws Exception {
        history("TB_SY_CODE", "A", "먼저 입력", "a", "b", "2026-11-13T00:01:00Z");
        history("TB_SY_CODE", "B", "동일 시각 나중 입력", "a", "b", "2026-11-13T00:01:00Z");
        history("TB_SY_CODE", "C", "오래된 기록", "a", "b", "2026-11-12T00:01:00Z");
        mvc.perform(get("/api/common/code-change-history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].item").value("동일 시각 나중 입력"))
            .andExpect(jsonPath("$.data.items[1].item").value("먼저 입력"))
            .andExpect(jsonPath("$.data.items[2].item").value("오래된 기록"))
            .andExpect(jsonPath("$.data.items[0].changedAt").value("2026-11-13T00:01:00Z"));
    }

    @Test void nullableStorageFieldsBecomeRequiredStringsWithoutChangingStoredHistory() throws Exception {
        history("TB_SY_CODE_GRP", "NEW_GROUP", null, null, "새 그룹", "2026-11-13T00:00:00Z");
        history("TB_SY_CODE_MAP", "DELETED_MAP", "삭제", "이전 대응", null, "2026-11-13T00:01:00Z");
        mvc.perform(get("/api/common/code-change-history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].after").value(""))
            .andExpect(jsonPath("$.data.items[1].before").value(""))
            .andExpect(jsonPath("$.data.items[1].item").value("NEW_GROUP"));
        assertThat(db.queryForObject("SELECT count(*) FROM TB_SY_CHG_HIST WHERE bfr_val IS NULL", Long.class))
            .isEqualTo(1);
    }

    @Test void noServerPaginationOrActionTypeFilter() throws Exception {
        for (int i = 0; i < 125; i++)
            history("TB_SY_CODE", "OWNED:" + i, "CODE_NM", "전", "후", "2026-11-13T00:00:00Z");
        db.update("UPDATE TB_SY_CHG_HIST SET chg_type_cd='DEL' WHERE trgt_key='OWNED:0'");
        mvc.perform(get("/api/common/code-change-history"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(125));
    }

    @Test void actualStoredActorIsNotReplacedByFixedAdminOrUserDisplayName() throws Exception {
        history("TB_SY_CODE", "OWNED:A", "CODE_NM", "전", "후", "2026-11-13T00:00:00Z");
        db.update("UPDATE TB_SY_CHG_HIST SET reg_user_id='historical-actor'");
        mvc.perform(get("/api/common/code-change-history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].processor").value("historical-actor"));
    }
}
