package com.company.common.parts;

import com.company.common.controller.ApiExceptionHandler;
import com.company.common.controller.CodeExceptionHandler;
import com.company.common.dto.CalculationRuleContracts.Standard;
import com.company.common.mapper.UserMapper;
import com.company.common.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.service.CalculationRuleService;
import com.company.common.service.CurrentBusinessActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.YearMonth;
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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(CommonPartController.class)
@ContextConfiguration(classes = {CommonPartController.class, CommonPartService.class, CurrentBusinessActor.class,
    CodeExceptionHandler.class, ApiExceptionHandler.class, com.company.common.service.ExcelExportService.class,
    PartPersistenceIntegrationTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PartPersistenceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired OwnedSchema owned;
    @MockBean CalculationRuleService calculations;
    JdbcTemplate db;
    String leader;
    record OwnedSchema(String name) {}

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean OwnedSchema ownedSchema() {
            return new OwnedSchema("part_records_" + UUID.randomUUID().toString().replace("-", ""));
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
                    "V3__common_function_seed.sql", "V4__user_management_seed.sql", "V5__role_permissions.sql",
                    "V6__role_schema_contract.sql", "V7__code_definitions_schema.sql", "V8__calculation_rule_seed.sql",
                    "V9__common_entity_contract.sql", "V10__client_part_upload_schema.sql")) {
                    statement.execute(new ClassPathResource("db/migration/" + file).getContentAsString(StandardCharsets.UTF_8));
                }
            }
            var data = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new java.util.Properties(); properties.setProperty("currentSchema", owned.name());
            data.setDriverProperties(properties); return data;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/PartMapper.xml"),
                new ClassPathResource("mapper/UserMapper.xml"),
                new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var config = new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(false);
            config.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(config); return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean PartMapper partMapper(SqlSessionTemplate session) { return session.getMapper(PartMapper.class); }
        @Bean UserMapper userMapper(SqlSessionTemplate session) { return session.getMapper(UserMapper.class); }
        @Bean UserPreferencePeriodSourceMapper periodMapper(SqlSessionTemplate session) {
            return session.getMapper(UserPreferencePeriodSourceMapper.class);
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
    @BeforeEach void setup() {
        db = new JdbcTemplate(source);
        leader = "part_leader_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        db.update("INSERT INTO TB_SY_USER (user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,reg_user_id) "
            + "SELECT ?,?,?,pwd_hash,0,'USE',0,'admin' FROM TB_SY_USER WHERE user_id='admin'", leader, leader, leader + "@example.invalid");
        db.update("INSERT INTO TB_SY_USER_ROLE (user_id,role_id,reg_user_id) VALUES (?,'SALES_LDR','admin')", leader);
        when(calculations.effectiveStandard(anyString(), any())).thenAnswer(call ->
            new Standard(call.getArgument(0), YearMonth.of(2090, 1), new BigDecimal(
                "PART_DSTRB_RT".equals(call.getArgument(0)) ? "37.25" : "1600000")));
    }
    @AfterEach void cleanup() throws Exception {
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA " + owned.name() + " CASCADE");
        }
    }
    String create(String name) throws Exception {
        String body = json.createObjectNode().put("partName", name).put("effectiveMonth", "2090-01")
            .put("leaderUserId", leader).put("distributionRate", 37.25).put("salesExpense", 1600000).toString();
        var result = mvc.perform(post("/api/common/parts").contentType("application/json").content(body))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).at("/data/partId").asText();
    }
    long count(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    String version(String id) throws Exception {
        var result = mvc.perform(get("/api/common/parts/{id}/standards", id)).andExpect(status().isOk()).andReturn();
        var token = json.readTree(result.getResponse().getContentAsString()).at("/data/items/0/modifiedAt");
        return token.isNull() ? null : token.asText();
    }
    void standard(String id, String month, int rate, String token) throws Exception {
        var body = json.createObjectNode().put("effectiveMonth", month).put("leaderUserId", leader)
            .put("distributionRate", rate).put("salesExpense", 1600000);
        if (token == null) body.putNull("modifiedAt"); else body.put("modifiedAt", token);
        mvc.perform(put("/api/common/parts/{id}/standards", id).contentType("application/json").content(body.toString()))
            .andExpect(status().isOk());
    }
    @Test void persistedCreationAllowsConcurrentLeadershipWithoutAssignmentWrites() throws Exception {
        long scopes = count("SELECT count(*) FROM TB_SY_USER_SCOPE");
        String first = create("첫 영업 파트"); String second = create("둘째 영업 파트");
        assertThat(count("SELECT count(*) FROM TB_BS_PART_STD WHERE part_id IN (?,?) AND part_ldr_user_id=?",
            first, second, leader)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM TB_SY_USER_SCOPE")).isEqualTo(scopes);
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key=? AND reg_user_id='admin'", first)).isPositive();
    }
    @Test void excelDownloadKeepsVisibleColumnsAndLeavesAnAccessLog() throws Exception {
        create("엑셀 영업 파트");
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/common/parts/excel")
                .param("columns", "partName", "salesExpense"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse();
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("파트명");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("공통관리비(1인·월)");
        }
        assertThat(count("SELECT count(*) FROM TB_SY_ACCS_LOG WHERE scrn_id='UI-15' AND act_type_cd='DNLD'")).isEqualTo(1);
    }
    @Test void insertingBeforeScheduleKeepsNextAndSameMonthUpdatesWithMoreHistory() throws Exception {
        String id = create("기간 파트");
        standard(id, "2090-12", 50, null);
        standard(id, "2090-10", 45, version(id));
        assertThat(db.queryForObject("SELECT aply_end_ym FROM TB_BS_PART_STD WHERE part_id=? AND aply_bgn_ym='209010'",
            String.class, id)).isEqualTo("209011");
        assertThat(db.queryForObject("SELECT prft_dstrb_rt FROM TB_BS_PART_STD WHERE part_id=? AND aply_bgn_ym='209012'",
            BigDecimal.class, id)).isEqualByComparingTo("50");
        long history = count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key LIKE ?", id + ":%");
        standard(id, "2090-10", 46, version(id));
        assertThat(count("SELECT count(*) FROM TB_BS_PART_STD WHERE part_id=?", id)).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST WHERE trgt_key LIKE ?", id + ":%")).isGreaterThan(history);
    }
    @Test void historyFailureRollsBackParentAndFirstPeriod() throws Exception {
        long masters = count("SELECT count(*) FROM TB_BS_PART");
        long rows = count("SELECT count(*) FROM TB_BS_PART_STD");
        db.execute("CREATE FUNCTION reject_part_history() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
            + "RAISE EXCEPTION 'history unavailable'; END $$");
        db.execute("CREATE TRIGGER reject_part_history BEFORE INSERT ON TB_SY_CHG_HIST FOR EACH ROW EXECUTE FUNCTION reject_part_history()");
        var body = json.createObjectNode().put("partName", "롤백 파트").put("effectiveMonth", "2090-01")
            .put("leaderUserId", leader).put("distributionRate", 37.25).put("salesExpense", 1600000);
        mvc.perform(post("/api/common/parts").contentType("application/json").content(body.toString()))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false));
        assertThat(count("SELECT count(*) FROM TB_BS_PART")).isEqualTo(masters);
        assertThat(count("SELECT count(*) FROM TB_BS_PART_STD")).isEqualTo(rows);
    }
    @Test void confirmedAmountsAreProtectedButLeaderOnlyChangeIsAllowed() throws Exception {
        String id = create("확정 보호 파트");
        db.update("INSERT INTO TB_CO_PART_SETL (part_id,setl_ym,corp_card_amt,part_prft_amt,setl_stat_cd,reg_user_id) "
            + "VALUES (?,'209010',123,456,'CNFM','admin')", id);
        var body = json.createObjectNode().put("effectiveMonth", "2090-10").put("leaderUserId", leader)
            .put("distributionRate", 45).put("salesExpense", 1600000).putNull("modifiedAt");
        mvc.perform(put("/api/common/parts/{id}/standards", id).contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("2090-11")));
        assertThat(count("SELECT count(*) FROM TB_BS_PART_STD WHERE part_id=?", id)).isEqualTo(1);
        String other = leader + "b";
        db.update("INSERT INTO TB_SY_USER (user_id,user_nm,email_addr,pwd_hash,pwd_err_cnt,user_stat_cd,mfa_err_cnt,reg_user_id) "
            + "SELECT ?,?,?,pwd_hash,0,'USE',0,'admin' FROM TB_SY_USER WHERE user_id='admin'", other, other, other + "@example.invalid");
        db.update("INSERT INTO TB_SY_USER_ROLE (user_id,role_id,reg_user_id) VALUES (?,'SALES_LDR','admin')", other);
        body.put("leaderUserId", other).put("distributionRate", 37.25);
        mvc.perform(put("/api/common/parts/{id}/standards", id).contentType("application/json").content(body.toString()))
            .andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT part_prft_amt FROM TB_CO_PART_SETL WHERE part_id=?", BigDecimal.class, id))
            .isEqualByComparingTo("456");
        assertThat(db.queryForObject("SELECT part_ldr_user_id FROM TB_BS_PART_STD WHERE part_id=? AND aply_bgn_ym='209010'",
            String.class, id)).isEqualTo(other);
    }
    @Test void developerReferenceKeepsItsIdAndReadsRenamedPartThenBlocksStop() throws Exception {
        String id = create("인력 소속 파트");
        db.update("INSERT INTO TB_HR_DEV (dev_id,mng_no,dev_nm,emp_type_cd,job_cd,drct_cls_cd,part_id,work_stat_cd,reg_user_id) "
            + "SELECT 'owned_dev','owned_dev','소속 인력',"
            + "(SELECT min(code) FROM TB_SY_CODE WHERE code_grp_id='EMP_TYPE' AND use_yn='Y' AND del_yn='N'),"
            + "(SELECT min(code) FROM TB_SY_CODE WHERE code_grp_id='JOB' AND use_yn='Y' AND del_yn='N'),"
            + "(SELECT min(code) FROM TB_SY_CODE WHERE code_grp_id='DRCT_CLS' AND use_yn='Y' AND del_yn='N'),?,"
            + "(SELECT min(code) FROM TB_SY_CODE WHERE code_grp_id='WORK_STAT' AND use_yn='Y' AND del_yn='N'),'admin'", id);
        mvc.perform(put("/api/common/parts/{id}", id).contentType("application/json")
            .content("{\"partName\":\"새 인력 소속 파트\",\"modifiedAt\":null}"))
            .andExpect(status().isOk());
        assertThat(db.queryForObject("SELECT p.part_nm FROM TB_HR_DEV d JOIN TB_BS_PART p ON p.part_id=d.part_id WHERE d.dev_id='owned_dev'",
            String.class)).isEqualTo("새 인력 소속 파트");
        assertThat(db.queryForObject("SELECT part_id FROM TB_HR_DEV WHERE dev_id='owned_dev'", String.class)).isEqualTo(id);
        var body = json.createObjectNode().put("modifiedAt", version(id));
        mvc.perform(put("/api/common/parts/{id}/stop", id).contentType("application/json").content(body.toString()))
            .andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT use_yn FROM TB_BS_PART WHERE part_id=?", String.class, id)).isEqualTo("Y");
    }
    @Test void staleNullVersionDoesNotWritePartOrHistory() throws Exception {
        String id = create("동시수정 파트"); standard(id, "2090-10", 45, null);
        long history = count("SELECT count(*) FROM TB_SY_CHG_HIST");
        mvc.perform(put("/api/common/parts/{id}", id).contentType("application/json")
            .content("{\"partName\":\"덮어쓰기\",\"modifiedAt\":null}"))
            .andExpect(status().isConflict());
        assertThat(db.queryForObject("SELECT part_nm FROM TB_BS_PART WHERE part_id=?", String.class, id))
            .isEqualTo("동시수정 파트");
        assertThat(count("SELECT count(*) FROM TB_SY_CHG_HIST")).isEqualTo(history);
    }
}
