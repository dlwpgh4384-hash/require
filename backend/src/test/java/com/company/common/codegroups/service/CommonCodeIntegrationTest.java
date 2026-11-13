package com.company.common.codegroups.service;

import com.company.common.codegroups.controller.CommonCodeController;
import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.controller.ApiExceptionHandler;
import com.company.common.controller.CodeExceptionHandler;
import com.company.common.mapper.UserMapper;
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
@WebMvcTest(CommonCodeController.class)
@ContextConfiguration(classes = {CommonCodeController.class, CommonCodeService.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class, CommonCodeIntegrationTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class CommonCodeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired OwnedSchema owned;
    @Autowired CodeGroupMapper mapper;
    JdbcTemplate db;
    record OwnedSchema(String name) {}

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean OwnedSchema ownedSchema() { return new OwnedSchema("code_groups_" + UUID.randomUUID().toString().replace("-", "")); }
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
                    "V3__common_function_seed.sql", "V4__user_management_seed.sql", "V5__role_permissions.sql",
                    "V6__role_schema_contract.sql", "V7__code_definitions_schema.sql", "V8__calculation_rule_seed.sql")) {
                    statement.execute(new ClassPathResource("db/migration/" + file).getContentAsString(StandardCharsets.UTF_8));
                }
            }
            var data = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new java.util.Properties(); properties.setProperty("currentSchema", owned.name());
            data.setDriverProperties(properties); return data;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/CodeGroupMapper.xml"), new ClassPathResource("mapper/UserMapper.xml"));
            var config = new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean CodeGroupMapper codeGroupMapper(SqlSessionTemplate session) { return session.getMapper(CodeGroupMapper.class); }
        @Bean UserMapper userMapper(SqlSessionTemplate session) { return session.getMapper(UserMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void setup() throws Exception {
        db = new JdbcTemplate(source);
        assertThat(new ClassPathResource("contracts/openapi.yaml").getContentAsString(StandardCharsets.UTF_8))
            .contains("operationId: postCommonCodeGroup", "operationId: postCommonCodeDelete");
    }
    @AfterEach void cleanup() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + owned.name() + " CASCADE");
        }
    }
    private long count(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    private String text(String sql, Object... args) { return db.queryForObject(sql, String.class, args); }
    private void group() throws Exception {
        mvc.perform(post("/api/common/code-groups").contentType("application/json")
            .content("{\"groupId\":\"OWNED\",\"groupName\":\"시험 그룹\"}")).andExpect(status().isOk());
    }
    private void code(String group, String code) throws Exception {
        mvc.perform(post("/api/common/code-groups/{groupId}/codes", group).contentType("application/json")
            .content(json.createObjectNode().put("code", code).put("codeName", "시험 이름").toString())).andExpect(status().isOk());
    }
    private org.springframework.test.web.servlet.ResultActions remove(String group, String code) throws Exception {
        return mvc.perform(post("/api/common/code-groups/{groupId}/codes/{code}/delete", group, code));
    }
    @Test void originalGroupCountsAndAllowancesMaterializeWithoutChangingSeeds() throws Exception {
        var groups = mapper.selectGroups();
        assertThat(groups).hasSize(64);
        assertThat(groups.stream().filter(g -> g.sysCodeYn.equals("Y"))).hasSize(44);
        assertThat(groups.stream().filter(g -> g.sysCodeYn.equals("N"))).hasSize(20);
        for (var group : groups) assertThat((long) group.codeCount).isEqualTo(count("SELECT count(*) FROM TB_SY_CODE WHERE code_grp_id=? AND del_yn='N'", group.groupId));
        var allowances = mapper.selectCodes("ALWNC");
        assertThat(allowances).hasSize(6);
        assertThat(allowances).extracting(row -> row.codeVal.stripTrailingZeros().toPlainString())
            .containsExactlyInAnyOrder("200000", "400000", "9.4", "8.33", "50000", "50000");
        assertThat(allowances).extracting(row -> row.unitNm).containsOnly("원", "%");
        mvc.perform(get("/api/common/code-groups")).andExpect(status().isOk()).andExpect(jsonPath("$.data.items.length()").value(64));
    }
    @Test void anonymousCreateAndDeleteRetainsPkAndTimestampedAdminAudit() throws Exception {
        mvc.perform(post("/api/common/code-groups").contentType("application/json")
            .content("{\"groupId\":\"OWNED\",\"groupName\":\"시험 그룹\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.groupId").value("OWNED"));
        assertThat(text("SELECT reg_user_id FROM TB_SY_CHG_HIST WHERE trgt_tbl_nm='TB_SY_CODE_GRP' AND trgt_key='OWNED'"))
            .isEqualTo("admin");
        mvc.perform(post("/api/common/code-groups/OWNED/codes").contentType("application/json")
            .content("{\"code\":\"lower\",\"codeName\":\"시험 이름\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.code").value("LOWER"));
        assertThat(text("SELECT reg_user_id FROM TB_SY_CODE WHERE code_grp_id='OWNED' AND code='LOWER'")).isEqualTo("admin");
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key IN ('OWNED','OWNED:LOWER') AND reg_user_id='admin' AND reg_dttm IS NOT NULL")).isEqualTo(2);
        mvc.perform(post("/api/common/code-groups/OWNED/codes/LOWER/delete"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.delYn").value("Y"));
        assertThat(text("SELECT del_yn FROM TB_SY_CODE WHERE code_grp_id='OWNED' AND code='LOWER'")).isEqualTo("Y");
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key='OWNED:LOWER' AND chg_type_cd='DEL' AND reg_user_id='admin'")).isEqualTo(1);
        mvc.perform(get("/api/common/code-groups/OWNED/codes")).andExpect(jsonPath("$.data.items").isEmpty());
        mvc.perform(post("/api/common/code-groups/OWNED/codes").contentType("application/json")
            .content("{\"code\":\"lower\",\"codeName\":\"재사용\"}")).andExpect(status().isBadRequest());
        assertThat(count("SELECT count(*) FROM TB_SY_CODE WHERE code_grp_id='OWNED'")).isEqualTo(1);
    }
    @Test void contextualBusinessHistoryBlocksDeletionButOwnAuditDoesNot() throws Exception {
        group(); code("OWNED", "HISTORY");
        db.update("INSERT INTO TB_SY_CHG_HIST(trgt_tbl_nm,trgt_key,chg_type_cd,chg_item_nm,bfr_val,aft_val,reg_user_id) VALUES ('TB_PJ_PROJ','PAST','UPD','OWNED','HISTORY','OTHER','admin')");
        long before = count("SELECT count(*) FROM TB_SY_CHG_HIST");
        remove("OWNED", "HISTORY").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("사용된 코드라 삭제할 수 없습니다. 사용을 끄세요."));
        assertThat(mapper.selectCode("OWNED", "HISTORY").delYn).isEqualTo("N");
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST")).isEqualTo(before);
    }
    @Test void additionalReferencesIncludeSoftDeletedRowsAndUseExactTokens() throws Exception {
        mvc.perform(post("/api/common/code-groups/JOB/codes").contentType("application/json")
            .content("{\"code\":\"OWNED_JOB\",\"codeName\":\"시험 직무\",\"relatedJobs\":\"NONE\",\"estimateUnitPrice\":1}")).andExpect(status().isOk());
        db.update("INSERT INTO TB_SY_CODE(code_grp_id,code,code_nm,sort_ord,use_yn,del_yn,add_val1,reg_user_id) VALUES ('SKILL','OWNED_SKILL','시험 스킬',999,'N','Y','OTHER,OWNED_JOB','admin')");
        remove("JOB", "OWNED_JOB").andExpect(status().isBadRequest());
        db.update("UPDATE TB_SY_CODE SET add_val1='OTHER,OWNED_JOB_LONG' WHERE code_grp_id='SKILL' AND code='OWNED_SKILL'");
        remove("JOB", "OWNED_JOB").andExpect(status().isOk());
    }
    @Test void mappedCodeCannotBeDeleted() throws Exception {
        group(); code("OWNED", "MAPPED");
        db.update("INSERT INTO TB_SY_CODE_MAP(src_text,code_grp_id,code,reg_user_id) VALUES ('시험 대응','OWNED','MAPPED','admin')");
        remove("OWNED", "MAPPED").andExpect(status().isBadRequest());
        assertThat(mapper.selectCode("OWNED", "MAPPED").delYn).isEqualTo("N");
    }
    @Test void batchPersistsNamesUsageAndCompleteOldNewHistoryWithAdmin() throws Exception {
        group(); code("OWNED", "ONE"); code("OWNED", "TWO");
        mvc.perform(put("/api/common/code-groups/OWNED/codes").contentType("application/json")
            .content("{\"items\":[{\"code\":\"ONE\",\"codeName\":\"변경 이름\",\"sortOrd\":1,\"useYn\":\"N\"},{\"code\":\"TWO\",\"codeName\":\"둘째 이름\",\"sortOrd\":2,\"useYn\":\"Y\"}]}"))
            .andExpect(status().isOk());
        assertThat(mapper.selectCode("OWNED", "ONE").useYn).isEqualTo("N");
        assertThat(text("SELECT mod_user_id FROM TB_SY_CODE WHERE code_grp_id='OWNED' AND code='ONE'")).isEqualTo("admin");
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key='OWNED:ONE' AND chg_item_nm='CODE_NM' AND bfr_val='시험 이름' AND aft_val='변경 이름' AND reg_user_id='admin' AND reg_dttm IS NOT NULL")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key='OWNED:ONE' AND chg_item_nm='USE_YN' AND bfr_val='Y' AND aft_val='N'")).isEqualTo(1);
    }
    @Test void staleModifiedAtIsRejectedWith409AndKeepsTheFirstSave() throws Exception {
        group(); code("OWNED", "ONE");
        String read = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
            mvc.perform(get("/api/common/code-groups/OWNED/codes")).andReturn().getResponse().getContentAsString())
            .at("/data/items/0/modifiedAt").asText();
        assertThat(read).isNotBlank();
        String body = "{\"items\":[{\"code\":\"ONE\",\"codeName\":\"%s\",\"sortOrd\":1,\"useYn\":\"Y\",\"modifiedAt\":\"" + read + "\"}]}";
        mvc.perform(put("/api/common/code-groups/OWNED/codes").contentType("application/json")
            .content(body.formatted("먼저 저장"))).andExpect(status().isOk());
        mvc.perform(put("/api/common/code-groups/OWNED/codes").contentType("application/json")
            .content(body.formatted("나중 저장"))).andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요."));
        assertThat(mapper.selectCode("OWNED", "ONE").codeName).isEqualTo("먼저 저장");
    }
    @Test void historyInsertionFailureRollsBackDataAndEarlierAudit() throws Exception {
        group(); code("OWNED", "ONE"); code("OWNED", "TWO");
        long before = count("SELECT count(*) FROM TB_SY_CHG_HIST");
        db.execute("ALTER TABLE TB_SY_CHG_HIST ADD CONSTRAINT owned_history_rejection CHECK(aft_val <> '두번째') NOT VALID");
        var input = json.createObjectNode(); var items = input.putArray("items");
        items.add(json.createObjectNode().put("code","ONE").put("codeName","첫번째").put("sortOrd",1).put("useYn","N"));
        items.add(json.createObjectNode().put("code","TWO").put("codeName","두번째").put("sortOrd",2).put("useYn","Y"));
        mvc.perform(put("/api/common/code-groups/OWNED/codes").contentType("application/json").content(input.toString()))
            .andExpect(status().isInternalServerError());
        assertThat(mapper.selectCode("OWNED", "ONE").codeName).isEqualTo("시험 이름");
        assertThat(mapper.selectCode("OWNED", "ONE").useYn).isEqualTo("Y");
        assertThat(mapper.selectCode("OWNED", "TWO").codeName).isEqualTo("시험 이름");
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST")).isEqualTo(before);
    }
}
