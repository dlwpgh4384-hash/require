package com.company.common.service;

import com.company.common.controller.CommonRoleController;
import com.company.common.controller.RoleExceptionHandler;
import com.company.common.mapper.RoleMapper;
import com.company.common.dto.RoleContracts;

import com.company.common.controller.ApiExceptionHandler;
import com.company.common.controller.CommonUserOptionsController;
import com.company.common.mapper.UserMapper;
import com.company.common.mapper.UserOptionsMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.service.CommonUserOptionsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
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
@WebMvcTest({CommonRoleController.class, CommonUserOptionsController.class})
@ContextConfiguration(classes = {CommonRoleController.class, CommonRoleService.class,
    RoleExceptionHandler.class, ApiExceptionHandler.class, CommonUserOptionsController.class,
    CommonUserOptionsService.class, RoleManagementIntegrationTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RoleManagementIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired OwnedSchema owned;
    @Autowired CommonRoleService service;
    @Autowired SqlSessionFactory sessions;
    JdbcTemplate db;
    record OwnedSchema(String name) {}
    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean OwnedSchema ownedSchema() { return new OwnedSchema("roles_"+UUID.randomUUID().toString().replace("-","")); }
        @Bean DataSource dataSource(OwnedSchema owned) throws Exception {
            String url=System.getenv("NAVIGATION_TEST_DB_URL");
            String user=System.getenv("NAVIGATION_TEST_DB_USER");
            String password=System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try(var connection=DriverManager.getConnection(url,user,password); var statement=connection.createStatement()) {
                assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(16);
                statement.execute("CREATE SCHEMA "+owned.name());
                statement.execute("SET search_path TO "+owned.name());
                for(String resource:List.of("V1__navigation_schema.sql","V2__navigation_menu_seed.sql",
                    "V3__common_function_seed.sql","V4__user_management_seed.sql",
                    "V5__role_permissions.sql","V6__role_schema_contract.sql"))
                    statement.execute(new ClassPathResource("db/migration/"+resource).getContentAsString(StandardCharsets.UTF_8));
            }
            var data=new UnpooledDataSource("org.postgresql.Driver",url,user,password);
            var properties=new java.util.Properties(); properties.setProperty("currentSchema",owned.name());
            data.setDriverProperties(properties); return data;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory=new SqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/RoleMapper.xml"),
                new ClassPathResource("mapper/UserMapper.xml"), new ClassPathResource("mapper/UserOptionsMapper.xml"));
            var config=new org.apache.ibatis.session.Configuration(); config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean RoleMapper roleMapper(SqlSessionTemplate session) { return session.getMapper(RoleMapper.class); }
        @Bean UserMapper userMapper(SqlSessionTemplate session) { return session.getMapper(UserMapper.class); }
        @Bean UserOptionsMapper userOptionsMapper(SqlSessionTemplate session) { return session.getMapper(UserOptionsMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void setup() throws Exception {
        db = new JdbcTemplate(source);
        String contract = new ClassPathResource("contracts/openapi.yaml")
            .getContentAsString(StandardCharsets.UTF_8);
        assertThat(contract).contains("operationId: postCommonRoleCopy",
            "operationId: postCommonRoleStop", "operationId: putCommonRole");
    }
    @AfterEach void cleanup() throws Exception {
        try(var connection=source.getConnection(); var statement=connection.createStatement()) {
            statement.execute("DROP SCHEMA "+owned.name()+" CASCADE");
        }
    }
    private long count(String sql, Object... args) { return db.queryForObject(sql,Long.class,args); }
    private String text(String sql, Object... args) { return db.queryForObject(sql,String.class,args); }
    private String copy(String name, String sourceId, String scope) throws Exception {
        var result=mvc.perform(post("/api/common/roles").contentType("application/json")
            .content(json.writeValueAsString(Map.of("sourceRoleId",sourceId,"roleName",name,"dataScopeCd",scope))))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("role").path("roleId").asText();
    }
    private ObjectNode update(String id, String scope, String sensitive, List<String> confirmed) throws Exception {
        var result=mvc.perform(get("/api/common/roles/"+id)).andExpect(status().isOk()).andReturn();
        var settings=json.readTree(result.getResponse().getContentAsString()).path("data");
        var body=json.createObjectNode().put("dataScopeCd",scope).put("snstvViewYn",sensitive);
        var menus=body.putArray("menuAuths");
        for(var menu:settings.path("menuAuths")) {
            var input=menu.deepCopy(); ((ObjectNode)input).remove(List.of("menuName","screenId","groupName")); menus.add(input);
        }
        body.set("confirmedAffectedUserIds",json.valueToTree(confirmed)); return body;
    }
    private void assign(String user, List<String> roles, String scopeType) {
        db.update("INSERT INTO TB_SY_USER(user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,mfa_err_cnt,user_stat_cd,reg_user_id) VALUES (?,?,?,'unusable',0,0,'STOP','admin')",
            user,"합성 "+user,user+"@example.invalid");
        for(String role:roles) db.update("INSERT INTO TB_SY_USER_ROLE(user_id,role_id,reg_user_id) VALUES (?,?,'admin')",user,role);
        if(scopeType!=null) db.update("INSERT INTO TB_SY_USER_SCOPE(user_id,scope_sn,scope_type_cd,part_id,proj_id,reg_user_id) VALUES (?,1,?,?,?,'admin')",
            user,scopeType,"PART".equals(scopeType)?"HQ":null,"PROJ".equals(scopeType)?"owned-project":null);
    }
    @Test
    @DisplayName("side-effect: 역할 복사의 REG_DTTM은 DB clock_timestamp 시각이며 원본 권한을 보존한다")
    void copyReadsBackFullPersistedFlagsAndPreservesBasicRoles() throws Exception {
        String original=text("SELECT jsonb_agg(to_jsonb(r) ORDER BY r.role_id)::text FROM TB_SY_ROLE r");
        var started = db.queryForObject("SELECT clock_timestamp()", java.time.OffsetDateTime.class);
        var response = mvc.perform(post("/api/common/roles").contentType("application/json")
                .content(json.writeValueAsString(Map.of("sourceRoleId", "EXEC",
                    "roleName", "합성 복사", "dataScopeCd", "PART"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.role.roleId").value("R-001"))
            .andExpect(jsonPath("$.data.role.dataScopeCd").value("PART"))
            .andReturn();
        String id = json.readTree(response.getResponse().getContentAsString())
            .path("data").path("role").path("roleId").asText();
        var finished = db.queryForObject("SELECT clock_timestamp()", java.time.OffsetDateTime.class);
        assertThat(id).isEqualTo("R-001");
        var registered = db.queryForObject(
            "SELECT reg_dttm FROM TB_SY_ROLE WHERE role_id=?", java.time.OffsetDateTime.class, id);
        assertThat(registered.toInstant()).isBetween(started.toInstant(), finished.toInstant());
        assertThat(count("SELECT count(*) FROM TB_SY_ROLE_MENU_AUTH WHERE role_id=? AND (reg_dttm < ? OR reg_dttm > ? OR reg_user_id!='admin')",
            id, started, finished)).isZero();
        assertThat(text("SELECT reg_user_id FROM TB_SY_ROLE WHERE role_id=?",id)).isEqualTo("admin");
        assertThat(text("SELECT data_scope_cd FROM TB_SY_ROLE WHERE role_id=?",id)).isEqualTo("PART");
        assertThat(text("SELECT snstv_view_yn FROM TB_SY_ROLE WHERE role_id=?",id)).isEqualTo("Y");
        assertThat(count("SELECT count(*) FROM TB_SY_ROLE_MENU_AUTH a JOIN TB_SY_ROLE_MENU_AUTH b ON b.menu_id=a.menu_id WHERE a.role_id=? AND b.role_id='EXEC' AND (a.inq_yn,a.reg_yn,a.upd_yn,a.del_yn,a.upld_yn,a.dnld_yn,a.cnfm_yn) IS DISTINCT FROM (b.inq_yn,b.reg_yn,b.upd_yn,b.del_yn,b.upld_yn,b.dnld_yn,b.cnfm_yn)",id)).isZero();
        assertThat(count("SELECT count(*) FROM TB_SY_ROLE_MENU_AUTH WHERE role_id=?",id)).isEqualTo(30);
        assertThat(text("SELECT jsonb_agg(to_jsonb(r) ORDER BY r.role_id)::text FROM TB_SY_ROLE r WHERE role_id!=?",id)).isEqualTo(original);
        mvc.perform(get("/api/common/roles")).andExpect(jsonPath("$.data.items[5].roleId").value(id));
        mvc.perform(get("/api/common/roles/"+id)).andExpect(jsonPath("$.data.menuAuths.length()").value(30));
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key=? AND trgt_tbl_nm='TB_SY_ROLE' AND chg_type_cd='NEW' AND reg_user_id='admin'",id)).isEqualTo(1);
    }
    @Test
    @DisplayName("side-effect: USE_YN Y→N·MOD_USER_ID 저장 후 getCommonUserOptions.roles에서 제외한다")
    void stopFiltersBothTabsAndUserOptionsButRetainsAllPermissionAndIssuedRows() throws Exception {
        String id=copy("합성 추가","PM","PROJ");
        assertThat(id).isEqualTo("R-001");
        String permissions = text("SELECT jsonb_agg(to_jsonb(a) ORDER BY a.menu_id)::text FROM TB_SY_ROLE_MENU_AUTH a WHERE a.role_id=?", id);
        assertThat(text("SELECT use_yn FROM TB_SY_ROLE WHERE role_id=?", id)).isEqualTo("Y");
        mvc.perform(get("/api/common/user-options")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles[*].roleCd", org.hamcrest.Matchers.hasItem("R-001")));
        mvc.perform(post("/api/common/roles/R-001/stop")).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.roleId").value("R-001"))
            .andExpect(jsonPath("$.data.useYn").value("N"));
        String modified=text("SELECT mod_dttm::text FROM TB_SY_ROLE WHERE role_id=?",id);
        assertThat(modified).isNotBlank();
        mvc.perform(post("/api/common/roles/R-001/stop")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.useYn").value("N"));
        assertThat(text("SELECT mod_dttm::text FROM TB_SY_ROLE WHERE role_id=?",id)).isEqualTo(modified);
        assertThat(text("SELECT mod_user_id FROM TB_SY_ROLE WHERE role_id=?",id)).isEqualTo("admin");
        assertThat(text("SELECT use_yn FROM TB_SY_ROLE WHERE role_id=?",id)).isEqualTo("N");
        assertThat(count("SELECT count(*) FROM TB_SY_ROLE_MENU_AUTH WHERE role_id=?",id)).isEqualTo(30);
        mvc.perform(get("/api/common/roles")).andExpect(jsonPath("$.data.items.length()").value(5));
        assertThat(text("SELECT jsonb_agg(to_jsonb(a) ORDER BY a.menu_id)::text FROM TB_SY_ROLE_MENU_AUTH a WHERE a.role_id=?", id))
            .isEqualTo(permissions);
        mvc.perform(get("/api/common/user-options")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles.length()").value(5))
            .andExpect(jsonPath("$.data.roles[*].roleCd", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("R-001"))));
        assertThat(copy("합성 다음","PM","PROJ")).isEqualTo("R-002");
    }
    @Test
    @DisplayName("시안 역할 추가: 원본 없이 만들면 모든 메뉴 권한 없음·민감 항목 끔, 번호는 복사와 같은 R-nnn")
    void addWithoutSourceStartsWithEveryMenuOff() throws Exception {
        mvc.perform(post("/api/common/roles").contentType("application/json")
                .content(json.writeValueAsString(Map.of("roleName", "합성 빈 역할", "dataScopeCd", "PROJ"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role.roleId").value("R-001"))
            .andExpect(jsonPath("$.data.role.snstvViewYn").value("N"))
            .andExpect(jsonPath("$.data.menuAuths.length()").value(30))
            .andExpect(jsonPath("$.data.menuAuths[0].screenId").value(org.hamcrest.Matchers.startsWith("UI-")));
        assertThat(count("SELECT count(*) FROM TB_SY_ROLE_MENU_AUTH WHERE role_id='R-001'")).isEqualTo(30);
        assertThat(count("SELECT count(*) FROM TB_SY_ROLE_MENU_AUTH WHERE role_id='R-001' AND 'Y' IN (inq_yn,reg_yn,upd_yn,del_yn,upld_yn,dnld_yn,cnfm_yn)")).isZero();
        assertThat(text("SELECT data_scope_cd FROM TB_SY_ROLE WHERE role_id='R-001'")).isEqualTo("PROJ");
        assertThat(text("SELECT reg_user_id FROM TB_SY_ROLE WHERE role_id='R-001'")).isEqualTo("admin");
    }
    @Test
    @DisplayName("시안 변경 이력: 바뀐 항목마다 1줄을 사람이 읽는 이름(데이터 범위·민감 항목·메뉴 · 동작)으로 최근 순")
    void changeHistoryListsEachChangedItemReadably() throws Exception {
        mvc.perform(get("/api/common/roles/EXEC/change-history")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(0));
        var body = update("EXEC", "PART", "N", List.of());
        ((ObjectNode) body.withArray("menuAuths").get(0)).put("createYn", "Y");
        mvc.perform(put("/api/common/roles/EXEC").contentType("application/json").content(body.toString()))
            .andExpect(status().isOk());
        var result = mvc.perform(get("/api/common/roles/EXEC/change-history")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(3)).andReturn();
        var items = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data").path("items");
        var names = new java.util.ArrayList<String>();
        items.forEach(item -> names.add(item.path("item").asText() + "|" + item.path("before").asText() + "|" + item.path("after").asText()));
        assertThat(names).contains("데이터 범위|전체|담당 파트", "민감 항목|보기|안 보기", "대시보드 · 등록|없음|있음");
        assertThat(items.get(0).path("changedAt").asText()).endsWith("Z");
        mvc.perform(get("/api/common/roles/missing/change-history")).andExpect(status().isNotFound());
    }
    @Test void inactiveNamesRemainUniqueAndMissingNameInsertsNothing() throws Exception {
        String id=copy("합성 추가","ADMIN","ALL");
        mvc.perform(post("/api/common/roles/"+id+"/stop")).andExpect(status().isOk());
        mvc.perform(post("/api/common/roles").contentType("application/json")
            .content(json.writeValueAsString(Map.of("sourceRoleId","ADMIN","roleName","합성 추가","dataScopeCd","ALL"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("roleName"));
        mvc.perform(post("/api/common/roles").contentType("application/json").content("{\"sourceRoleId\":\"ADMIN\",\"dataScopeCd\":\"ALL\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("roleName"));
        assertThat(count("SELECT count(*) FROM TB_SY_ROLE")).isEqualTo(6);
        assertThat(count("SELECT count(*) FROM TB_SY_ROLE_MENU_AUTH")).isEqualTo(180);
    }
    @Test void stoppedOrLockedAssignedUsersStillPreventRoleStop() throws Exception {
        String id=copy("합성 배정 역할","PM","PROJ"); assign("owned",List.of(id),null);
        mvc.perform(post("/api/common/roles/"+id+"/stop")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("1명이 이 역할을 쓰고 있습니다. 시스템 › 사용자에서 먼저 빼 주세요."));
        assertThat(text("SELECT use_yn FROM TB_SY_ROLE WHERE role_id=?",id)).isEqualTo("Y");
    }
    @Test
    @DisplayName("side-effect: 역할 PUT은 TB_SY_CHG_HIST와 TB_SY_ACCS_LOG를 같은 트랜잭션에 저장한다")
    void putRecordsEachBeforeAfterItemAndFixedAdminAccessAndNextRequest() throws Exception {
        String id=copy("합성 변경","PM","PROJ");
        assertThat(id).isEqualTo("R-001");
        var body=update(id,"ALL","Y",List.of());
        ((ObjectNode)body.withArray("menuAuths").get(0)).put("downloadYn","Y");
        mvc.perform(put("/api/common/roles/R-001").contentType("application/json").content(body.toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.role.roleId").value("R-001"));
        mvc.perform(get("/api/common/roles/"+id)).andExpect(jsonPath("$.data.role.dataScopeCd").value("ALL"))
            .andExpect(jsonPath("$.data.role.snstvViewYn").value("Y"))
            .andExpect(jsonPath("$.data.menuAuths[0].downloadYn").value("Y"));
        assertThat(text("SELECT mod_user_id FROM TB_SY_ROLE WHERE role_id=?",id)).isEqualTo("admin");
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_tbl_nm='TB_SY_ROLE' AND trgt_key=? AND chg_item_nm='DATA_SCOPE_CD' AND bfr_val='PROJ' AND aft_val='ALL' AND reg_user_id='admin'",id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key=? AND chg_item_nm='SNSTV_VIEW_YN' AND bfr_val='N' AND aft_val='Y'",id)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_tbl_nm='TB_SY_ROLE_MENU_AUTH' AND trgt_key LIKE ? AND chg_item_nm='DNLD_YN' AND bfr_val='N' AND aft_val='Y'",id+":%")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE trgt_desc=? AND act_type_cd='AUTH_CHG' AND scrn_id='UI-24' AND user_id='admin' AND reg_user_id='admin'",id)).isEqualTo(1);
    }
    @Test void cleanupRemovesOnlyConfirmedSingleScopeUsersAndPreservesPmAndOtherActiveRoles() throws Exception {
        String target=copy("합성 프로젝트 역할","PM","PROJ");
        String other=copy("합성 유지 역할","PM","PROJ");
        assign("single",List.of(target),"PROJ"); assign("multiple",List.of(target,other),"PROJ");
        assign("pmowned",List.of(target,"PM"),"PROJ"); assign("emptyowned",List.of(target),null);
        mvc.perform(get("/api/common/roles/"+target+"/change-impact").param("dataScopeCd","ALL"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.projectUserCount").value(1))
            .andExpect(jsonPath("$.data.affectedUserIds[0]").value("single"))
            .andExpect(jsonPath("$.data.preservedPmUserIds[0]").value("pmowned"));
        var body=update(target,"ALL","N",List.of("single"));
        mvc.perform(put("/api/common/roles/"+target).contentType("application/json").content(body.toString())).andExpect(status().isOk());
        assertThat(db.queryForList("SELECT user_id FROM TB_SY_USER_SCOPE ORDER BY user_id",String.class)).containsExactly("multiple","pmowned");
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_tbl_nm='TB_SY_USER_SCOPE' AND trgt_key='single:1' AND chg_type_cd='DEL' AND bfr_val='owned-project' AND reg_user_id='admin'")).isEqualTo(1);
    }
    @Test void editingPmScopeNeverDeletesPmProjectRelationships() throws Exception {
        assign("pmowned",List.of("PM"),"PROJ");
        var body=update("PM","ALL","N",List.of());
        mvc.perform(put("/api/common/roles/PM").contentType("application/json").content(body.toString())).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM TB_SY_USER_SCOPE WHERE user_id='pmowned'")).isEqualTo(1);
    }
    @Test void headquartersCleanupDoesNotTouchMonthLeaderSourcesOrOtherPartScopeRows() throws Exception {
        String target=copy("합성 본사 역할","SALES_LDR","PART");
        String other=copy("합성 파트 유지","SALES_LDR","PART");
        assign("single",List.of(target),"PART"); assign("multiple",List.of(target,other),"PART");
        db.update("INSERT INTO TB_BS_PART_STD(part_id,aply_bgn_ym,part_ldr_user_id,prft_dstrb_rt,sales_exps_amt,reg_user_id) VALUES ('HQ',to_char(clock_timestamp() AT TIME ZONE 'Asia/Seoul','YYYYMM'),'single',0,0,'admin')");
        mvc.perform(put("/api/common/roles/"+target).contentType("application/json").content(update(target,"ALL","N",List.of("single")).toString())).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM TB_SY_USER_SCOPE WHERE user_id='single'")).isZero();
        assertThat(count("SELECT count(*) FROM TB_SY_USER_SCOPE WHERE user_id='multiple'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM TB_BS_PART_STD WHERE part_ldr_user_id='single'")).isEqualTo(1);
    }
    @Test void targetDriftAndExtraConfirmationRollBackAllSettingsScopesAndHistory() throws Exception {
        String target=copy("합성 영향","PM","PROJ"); assign("single",List.of(target),"PROJ");
        var body=update(target,"ALL","Y",List.of("single")); assign("newowned",List.of(target),"PROJ");
        mvc.perform(put("/api/common/roles/"+target).contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("confirmedAffectedUserIds"));
        assertThat(count("SELECT count(*) FROM TB_SY_USER_SCOPE")).isEqualTo(2);
        assertThat(text("SELECT data_scope_cd FROM TB_SY_ROLE WHERE role_id=?",target)).isEqualTo("PROJ");
        assertThat(count("SELECT count(*) FROM TB_SY_ACCS_LOG")).isZero();
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE chg_type_cd='UPD'")).isZero();
    }
    @Test void accessLogFailureRollsBackRoleMatrixScopeAndAllItemHistories() throws Exception {
        String target=copy("합성 원자 변경","PM","PROJ"); assign("single",List.of(target),"PROJ");
        db.execute("CREATE FUNCTION reject_role_access() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'owned failure'; END; $$");
        db.execute("CREATE TRIGGER reject_role_access BEFORE INSERT ON TB_SY_ACCS_LOG FOR EACH ROW EXECUTE FUNCTION reject_role_access()");
        var body=update(target,"ALL","Y",List.of("single")); ((ObjectNode)body.withArray("menuAuths").get(0)).put("downloadYn","Y");
        mvc.perform(put("/api/common/roles/"+target).contentType("application/json").content(body.toString())).andExpect(status().isInternalServerError());
        assertThat(text("SELECT data_scope_cd FROM TB_SY_ROLE WHERE role_id=?",target)).isEqualTo("PROJ");
        assertThat(text("SELECT snstv_view_yn FROM TB_SY_ROLE WHERE role_id=?",target)).isEqualTo("N");
        assertThat(text("SELECT dnld_yn FROM TB_SY_ROLE_MENU_AUTH WHERE role_id=? AND menu_id='M001'",target)).isEqualTo("N");
        assertThat(count("SELECT count(*) FROM TB_SY_USER_SCOPE")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE chg_type_cd!='NEW'")).isZero();
    }
    @Test void preparationSeedsOnlyEmptyTableAndExecQuoteConfirmStartsOff() throws Exception {
        assertThat(text("SELECT cnfm_yn FROM TB_SY_ROLE_MENU_AUTH WHERE role_id='EXEC' AND menu_id='M011'")).isEqualTo("N");
        db.update("UPDATE TB_SY_ROLE_MENU_AUTH SET cnfm_yn='Y' WHERE role_id='EXEC' AND menu_id='M011'");
        new SchemaPreparation(sessions).prepare();
        assertThat(text("SELECT cnfm_yn FROM TB_SY_ROLE_MENU_AUTH WHERE role_id='EXEC' AND menu_id='M011'")).isEqualTo("Y");
        assertThat(count("SELECT count(*) FROM TB_SY_ROLE_MENU_AUTH")).isEqualTo(150);
    }
    @Test void creationHistoryKeepsHighWaterAfterDeletedIdentity() throws Exception {
        String id=copy("합성 이전","PM","PROJ");
        db.update("DELETE FROM TB_SY_ROLE_MENU_AUTH WHERE role_id=?",id);
        db.update("DELETE FROM TB_SY_ROLE WHERE role_id=?",id);
        assertThat(copy("합성 이후","PM","PROJ")).isEqualTo("R-002");
    }
    @Test void failedCreationHistoryRollsBackCopyAndDoesNotIssueNumber() throws Exception {
        db.execute("CREATE FUNCTION reject_role_history() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'owned history failure'; END; $$");
        db.execute("CREATE TRIGGER reject_role_history BEFORE INSERT ON TB_SY_CHG_HIST FOR EACH ROW EXECUTE FUNCTION reject_role_history()");
        mvc.perform(post("/api/common/roles").contentType("application/json")
            .content(json.writeValueAsString(Map.of("sourceRoleId","PM","roleName","합성 실패","dataScopeCd","PROJ"))))
            .andExpect(status().isInternalServerError());
        assertThat(count("SELECT count(*) FROM TB_SY_ROLE")).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM TB_SY_ROLE_MENU_AUTH")).isEqualTo(150);
        db.execute("DROP TRIGGER reject_role_history ON TB_SY_CHG_HIST");
        assertThat(copy("합성 성공","PM","PROJ")).isEqualTo("R-001");
    }
    @Test void concurrentCopiesSerializeAtExistingManagementLockAndIssueDifferentIds() throws Exception {
        var gate = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var futures = List.of("합성 동시 하나", "합성 동시 둘").stream().map(name -> executor.submit(() -> {
                gate.await();
                return service.postCommonRoleCopy(new RoleContracts.Copy("PM",name,"PROJ")).role().roleId();
            })).toList();
            gate.countDown();
            var ids = new java.util.ArrayList<String>();
            for (var future : futures) ids.add(future.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(ids).containsExactlyInAnyOrder("R-001","R-002");
            assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_tbl_nm='TB_SY_ROLE' AND chg_type_cd='NEW'")).isEqualTo(2);
        } finally { executor.shutdownNow(); }
    }
    @Test void basicRoleStopsAndMissingRoleStopPreserveSettingsAndPermissionRows() throws Exception {
        String before = text("SELECT jsonb_agg(to_jsonb(r) ORDER BY r.role_id)::text FROM TB_SY_ROLE r");
        for (String id : List.of("ADMIN","EXEC","FIN","SALES_LDR","PM"))
            mvc.perform(post("/api/common/roles/"+id+"/stop")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/common/roles/missing/stop")).andExpect(status().isNotFound());
        assertThat(text("SELECT jsonb_agg(to_jsonb(r) ORDER BY r.role_id)::text FROM TB_SY_ROLE r")).isEqualTo(before);
        assertThat(count("SELECT count(*) FROM TB_SY_ROLE_MENU_AUTH")).isEqualTo(150);
    }
    @Test void invalidScopeAndProtectedAdminMatrixLeaveDatabaseUntouched() throws Exception {
        var body = update("ADMIN","SELF","N",List.of());
        mvc.perform(put("/api/common/roles/ADMIN").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("dataScopeCd"));
        body.put("dataScopeCd","PART");
        for (var menu : body.withArray("menuAuths")) if (menu.path("menuId").asText().equals("M028"))
            ((ObjectNode)menu).put("updateYn","N");
        mvc.perform(put("/api/common/roles/ADMIN").contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest());
        assertThat(text("SELECT data_scope_cd FROM TB_SY_ROLE WHERE role_id='ADMIN'")).isEqualTo("ALL");
        assertThat(text("SELECT upd_yn FROM TB_SY_ROLE_MENU_AUTH WHERE role_id='ADMIN' AND menu_id='M028'")).isEqualTo("Y");
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST")).isZero();
    }
}
