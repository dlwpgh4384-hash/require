package com.company.common.projectissues.service;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.ProjectBoardSignalRules;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.projectissues.controller.ProjectIssueController;
import com.company.common.projectissues.dto.ProjectIssueContracts.*;
import com.company.common.projectissues.mapper.ProjectIssueMapper;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
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
@WebMvcTest(ProjectIssueController.class)
@ContextConfiguration(classes = {ProjectIssueController.class, ProjectIssueService.class,
    CurrentBusinessActor.class, CodeExceptionHandler.class, ApiExceptionHandler.class, ExcelExportService.class,
    ProjectIssuePersistenceTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ProjectIssuePersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource source;
    @Autowired String ownedSchema;
    @Autowired ProjectIssueMapper mapper;
    @Autowired ProjectIssueService service;
    @Autowired SqlSessionTemplate sessions;
    JdbcTemplate db;
    final LocalDate today = LocalDate.of(2026, 11, 15);

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean String ownedSchema() { return "issues_" + UUID.randomUUID().toString().replace("-", ""); }
        @Bean DataSource dataSource(String ownedSchema) throws Exception {
            String url = System.getenv("NAVIGATION_TEST_DB_URL");
            String user = System.getenv("NAVIGATION_TEST_DB_USER");
            String password = System.getenv("NAVIGATION_TEST_DB_PASSWORD");
            try (var c = DriverManager.getConnection(url, user, password); var s = c.createStatement()) {
                s.execute("CREATE SCHEMA " + ownedSchema);
            }
            var source = new UnpooledDataSource("org.postgresql.Driver", url, user, password);
            var properties = new Properties();
            properties.setProperty("currentSchema", ownedSchema);
            source.setDriverProperties(properties);
            var config = new org.apache.ibatis.session.Configuration(new org.apache.ibatis.mapping.Environment(
                "postgresql", new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), source));
            new SchemaPreparation(new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(config)).prepare();
            return source;
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setMapperLocations(new ClassPathResource("mapper/projectissues/ProjectIssueMapper.xml"),
                new ClassPathResource("mapper/CalculationRuleMapper.xml"),
                new ClassPathResource("mapper/UserPreferencePeriodSourceMapper.xml"));
            var c = new org.apache.ibatis.session.Configuration();
            c.setMapUnderscoreToCamelCase(false);
            c.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            factory.setConfiguration(c);
            return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean ProjectIssueMapper projectIssueMapper(SqlSessionTemplate sessions) { return sessions.getMapper(ProjectIssueMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean BusinessCalendar businessCalendar() { return new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-14T16:00:00Z"), ZoneOffset.UTC)); }
    }

    @BeforeEach void setup() {
        db = new JdbcTemplate(source);
        db.update("INSERT INTO TB_PJ_PROJ (PROJ_ID,PROJ_NM,CTRT_TYPE_CD,PROJ_STAT_CD,CTRT_BGN_DT,CTRT_END_DT,CTRT_AMT,TEAM_EXPS_AMT,PROJ_EXPS_AMT,REG_USER_ID) VALUES ('P-issue','이슈 프로젝트','IN','ING','2026-11-01','2026-12-31',0,0,0,'admin')");
    }
    @AfterEach void cleanup() throws Exception {
        try (var c = source.getConnection(); var s = c.createStatement()) { s.execute("DROP SCHEMA " + ownedSchema + " CASCADE"); }
    }
    com.fasterxml.jackson.databind.node.ObjectNode input() {
        return json.createObjectNode().put("projectId", "P-issue").put("title", "높은 위험 이슈")
            .put("dueDate", "2026-11-14").put("importanceCode", "HIGH").put("statusCode", "OPEN").put("actionText", "점검");
    }
    String create() throws Exception {
        var response = mvc.perform(post("/api/common/project-issues").contentType("application/json").content(input().toString()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.assigneeId").value("admin"))
            .andReturn().getResponse();
        return json.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data").path("issueId").asText();
    }
    List<Map<String, Object>> snapshot() { return db.queryForList("SELECT * FROM TB_PJ_ISSUE ORDER BY ISSUE_ID"); }
    ProjectBoardSignalRules.Result signal() {
        var rows = mapper.selectIssues(new Query(new Filter(null, null, null, "P-issue", null), today));
        long[] overdue = ProjectBoardSignalRules.overdue(rows.stream().map(i ->
            new ProjectBoardSignalRules.Issue(i.issueId, i.importanceCode, i.statusCode, i.dueDate)).toList(), today);
        return new ProjectBoardSignalRules().evaluate(new ProjectBoardSignalRules.Source(
            "IN", null, null, null, today, today.minusDays(10), overdue[0], overdue[1]), today);
    }
    /** 동시 수정 확인 값(지금 DB 값): 수정 본문에 그대로 보낸다. */
    String version(String id) {
        return db.queryForObject("SELECT COALESCE(MOD_DTTM,REG_DTTM) FROM TB_PJ_ISSUE WHERE ISSUE_ID=CAST(? AS numeric)",
            java.sql.Timestamp.class, id).toInstant().toString();
    }

    @Test void committedCreateAndCloseChangeSourceCountsAndIndependentSignalReasons() throws Exception {
        assertThat(signal().signal()).isEqualTo("정상");
        String id = create();
        var original = snapshot().get(0);
        assertThat(signal().signal()).isEqualTo("위험");
        assertThat(signal().signalReasons()).containsExactly("기한 지난 중요(높음) 이슈 1건");
        mvc.perform(get("/api/common/project-issues")).andExpect(jsonPath("$.data.openCount").value(1));
        mvc.perform(put("/api/common/project-issues/" + id).contentType("application/json").content(input().put("statusCode", "CLOSE").put("actionText", "완료").put("modifiedAt", version(id)).toString()))
            .andExpect(status().isOk());
        var changed = snapshot().get(0);
        assertThat(changed.get("issue_id")).isEqualTo(original.get("issue_id"));
        assertThat(changed.get("reg_user_id")).isEqualTo(original.get("reg_user_id"));
        assertThat(changed.get("reg_dttm")).isEqualTo(original.get("reg_dttm"));
        assertThat(changed.get("mod_user_id")).isEqualTo("admin");
        assertThat(changed.get("mod_dttm")).isNotNull();
        assertThat(changed.get("actn_cn")).isEqualTo("완료");
        assertThat(snapshot()).hasSize(1);
        assertThat(signal().signal()).isEqualTo("정상");
        assertThat(signal().signalReasons()).isEmpty();
        mvc.perform(get("/api/common/project-issues")).andExpect(jsonPath("$.data.openCount").value(0));
        mvc.perform(get("/api/common/project-issues").param("statusCode", "CLOSE"))
            .andExpect(jsonPath("$.data.items[0].issueId").value(id));
        // 새 매퍼/서비스 인스턴스에서도 같은 DB 원행을 읽는다.
        var reopened = new ProjectIssueService(mapper, new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC)), new CurrentBusinessActor());
        assertThat(reopened.getCommonProjectBoardIssues(new Filter("CLOSE", null, null, "P-issue", null)).items()).hasSize(1);
    }

    @ParameterizedTest @ValueSource(strings = {"END", "LOSE"})
    void endedAndLostRefuseNewButAllowOriginalIssueClose(String state) throws Exception {
        String id = create();
        db.update("UPDATE TB_PJ_PROJ SET PROJ_STAT_CD=?,CTRT_END_DT='2025-12-31' WHERE PROJ_ID='P-issue'", state);
        var before = snapshot();
        mvc.perform(post("/api/common/project-issues").contentType("application/json").content(input().toString())).andExpect(status().isBadRequest());
        assertThat(snapshot()).isEqualTo(before);
        mvc.perform(put("/api/common/project-issues/" + id).contentType("application/json").content(input().put("statusCode", "CLOSE").put("modifiedAt", version(id)).toString()))
            .andExpect(status().isOk());
        assertThat(snapshot()).hasSize(1);
    }

    @Test void anotherEditorPreservesAssigneeAndRegistrationInRealSql() throws Exception {
        String id = create();
        var original = snapshot().get(0);
        var editor = new CurrentBusinessActor() { @Override public String userId() { return "second-editor"; } };
        // 프록시와 같은 실제 트랜잭션 안에서 대체 실행자 정책을 검증한다.
        new org.springframework.transaction.support.TransactionTemplate(new DataSourceTransactionManager(source)).execute(status -> {
            var edited = new ProjectIssueService(mapper, new BusinessCalendar(), editor)
                .putCommonProjectBoardIssue(id, new Input("P-issue", "다른 편집자", today, "MID", "ACT", "처리",
                    java.time.Instant.parse(version(id))));
            assertThat(edited.assigneeId).isEqualTo("admin");
            assertThat(edited.createdBy).isEqualTo("admin");
            assertThat(edited.updatedBy).isEqualTo("second-editor");
            return null;
        });
        assertThat(snapshot().get(0).get("reg_dttm")).isEqualTo(original.get("reg_dttm"));
    }

    @Test void rejectedCodeAndLengthLeaveEveryRowUnchanged() throws Exception {
        String id = create();
        var before = snapshot();
        for (String field : List.of("importanceCode", "statusCode")) {
            mvc.perform(put("/api/common/project-issues/" + id).contentType("application/json").content(input().put(field, "UNKNOWN").put("modifiedAt", version(id)).toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
            assertThat(snapshot()).isEqualTo(before);
        }
        for (String field : List.of("title", "actionText")) {
            mvc.perform(post("/api/common/project-issues").contentType("application/json").content(input().put(field, "가".repeat(field.equals("title") ? 101 : 501)).toString()))
                .andExpect(status().isBadRequest());
            assertThat(snapshot()).isEqualTo(before);
        }
    }

    @Test void failedSqlWriteRollsBackAndNextCommandCanCommit() throws Exception {
        String id = create();
        var before = snapshot();
        db.execute("CREATE FUNCTION reject_issue() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rejection'; END $$");
        db.execute("CREATE TRIGGER reject_issue BEFORE UPDATE ON TB_PJ_ISSUE FOR EACH ROW EXECUTE FUNCTION reject_issue()");
        mvc.perform(put("/api/common/project-issues/" + id).contentType("application/json").content(input().put("statusCode", "CLOSE").put("modifiedAt", version(id)).toString())).andExpect(status().is5xxServerError());
        assertThat(snapshot()).isEqualTo(before);
        db.execute("DROP TRIGGER reject_issue ON TB_PJ_ISSUE");
        mvc.perform(put("/api/common/project-issues/" + id).contentType("application/json").content(input().put("statusCode", "CLOSE").put("modifiedAt", version(id)).toString())).andExpect(status().isOk());
    }

    @Test void realSqlRejectsStaleEditAndBoardCollectionSkipsEndedProjects() throws Exception {
        String id = create();
        var before = snapshot();
        mvc.perform(put("/api/common/project-issues/" + id).contentType("application/json")
                .content(input().put("title", "늦은 수정").put("modifiedAt", "2026-01-01T00:00:00Z").toString()))
            .andExpect(status().isConflict());
        assertThat(snapshot()).isEqualTo(before);
        assertThat(before.get(0).get("reg_dttm")).isNotNull();
        mvc.perform(get("/api/common/project-issues")).andExpect(jsonPath("$.data.openCount").value(1))
            .andExpect(jsonPath("$.data.items[0].createdByName").isNotEmpty())
            .andExpect(jsonPath("$.data.items[0].modifiedAt").isNotEmpty());
        db.update("UPDATE TB_PJ_PROJ SET PROJ_STAT_CD='LOSE' WHERE PROJ_ID='P-issue'");
        mvc.perform(get("/api/common/project-issues")).andExpect(jsonPath("$.data.openCount").value(0));
        mvc.perform(get("/api/common/project-issues").param("projectId", "P-issue").param("all", "true"))
            .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test void databaseFiltersAndOrderingKeepCountsAndDueTodayConsistent() throws Exception {
        String first = create();
        for (var b : List.of(input().put("importanceCode", "MID"), input().put("dueDate", today.toString()).put("statusCode", "ACT"), input().put("statusCode", "CLOSE")))
            mvc.perform(post("/api/common/project-issues").contentType("application/json").content(b.toString())).andExpect(status().isOk());
        mvc.perform(get("/api/common/project-issues").param("overdue", "true").param("importanceCode", "HIGH").param("projectId", "P-issue"))
            .andExpect(jsonPath("$.data.openCount").value(1)).andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].issueId").value(first));
        mvc.perform(get("/api/common/project-issues").param("overdue", "false"))
            .andExpect(jsonPath("$.data.openCount").value(3)).andExpect(jsonPath("$.data.items[0].issueId").value(first));
    }
}
