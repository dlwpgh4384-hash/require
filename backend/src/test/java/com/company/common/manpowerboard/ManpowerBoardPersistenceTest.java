package com.company.common.manpowerboard;

import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.ManpowerBoardDefinitions;
import com.company.common.common.ManpowerBoardFixture;
import com.company.common.manpowerboard.controller.CommonManpowerBoardController;
import com.company.common.manpowerboard.mapper.ManpowerBoardMapper;
import com.company.common.manpowerboard.service.CommonManpowerBoardService;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.controller.*;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.util.*;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
@WebMvcTest(CommonManpowerBoardController.class)
@ContextConfiguration(classes = {CommonManpowerBoardController.class, CommonManpowerBoardService.class,
    ManpowerBoardDefinitions.class, CalculationRuleService.class, CurrentBusinessActor.class,
    ExcelExportService.class, CodeExceptionHandler.class, ApiExceptionHandler.class,
    ManpowerBoardPersistenceTest.Persistence.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ManpowerBoardPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ManpowerBoardFixture fixture;
    @SpyBean ExcelExportService excel;
    Map<String, String> people;
    static final String ROOT = "/api/common/manpower-board";

    @TestConfiguration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class Persistence {
        @Bean(destroyMethod = "close") ManpowerBoardFixture manpowerBoardFixture() throws Exception {
            return new ManpowerBoardFixture();
        }
        @Bean DataSource dataSource(ManpowerBoardFixture fixture) {
            return fixture.factory().getConfiguration().getEnvironment().getDataSource();
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source, ManpowerBoardFixture fixture) throws Exception {
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml"));
            var configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(false);
            configuration.setAutoMappingUnknownColumnBehavior(org.apache.ibatis.session.AutoMappingUnknownColumnBehavior.FAILING);
            configuration.addInterceptor(fixture.counter());
            factory.setConfiguration(configuration);
            return factory.getObject();
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean ManpowerBoardMapper manpowerBoardMapper(SqlSessionTemplate session) { return session.getMapper(ManpowerBoardMapper.class); }
        @Bean CalculationRuleMapper calculationRuleMapper(SqlSessionTemplate session) { return session.getMapper(CalculationRuleMapper.class); }
        @Bean UserPreferencePeriodSourceMapper periodSource(SqlSessionTemplate session) { return session.getMapper(UserPreferencePeriodSourceMapper.class); }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean BusinessCalendar businessCalendar() { return new BusinessCalendar(ManpowerBoardFixture.CLOCK); }
    }

    @BeforeEach void sources() { people = fixture.boardSources(); }

    String snapshot() {
        var result = new StringBuilder();
        for (String table : List.of("TB_HR_DEV", "TB_PJ_ASGN", "TB_PJ_ASGN_MON", "TB_PJ_ASGN_HIST", "TB_SL_TENT_ASGN")) {
            result.append(fixture.db().queryForObject("SELECT COALESCE(jsonb_agg(v ORDER BY v::text)::text,'[]') FROM "
                + "(SELECT to_jsonb(r) v FROM " + table + " r) records", String.class));
        }
        return result.toString();
    }

    @Test void publicDownloadMatchesFilteredAnnualCalendarAndBusinessSnapshot() throws Exception {
        String before = snapshot();
        var board = json.readTree(mvc.perform(get(ROOT).param("year", "2026").param("jobCode", "BE"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
        var response = mvc.perform(get(ROOT + "/excel").param("year", "2026").param("jobCode", "BE"))
            .andExpect(status().isOk()).andExpect(header().string("X-Total-Count", board.path("totalCount").asText()))
            .andReturn().getResponse();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = book.getSheetAt(0);
            // 머리 3줄 + 사람 줄 + 합계 줄 2줄(비는 인원·남는 MM)
            assertThat(sheet.getLastRowNum() - 4).isEqualTo(board.path("totalCount").asInt());
            assertThat(sheet.getRow(sheet.getLastRowNum() - 1).getCell(0).getStringCellValue()).isEqualTo("비는 인원");
            assertThat(sheet.getRow(sheet.getLastRowNum()).getCell(0).getStringCellValue()).isEqualTo("남는 MM");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).contains("admin", "2026-11-15 09:00");
            for (int i = 0; i < board.path("items").size(); i++) {
                assertThat(sheet.getRow(i + 3).getCell(0).getStringCellValue())
                    .isEqualTo(board.path("items").get(i).path("name").asText());
            }
        }
        assertThat(snapshot()).isEqualTo(before);
        var after = json.readTree(mvc.perform(get(ROOT).param("year", "2026").param("jobCode", "BE"))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
        assertThat(after).isEqualTo(board);
    }

    @Test void successfulWorkbookWritesExactlyOneUi28Dnld() throws Exception {
        int before = auditCount();
        mvc.perform(get(ROOT + "/excel").param("year", "2026")).andExpect(status().isOk());
        assertThat(auditCount()).isEqualTo(before + 1);
    }

    int auditCount() {
        return fixture.db().queryForObject("SELECT COUNT(*) FROM TB_SY_ACCS_LOG WHERE SCRN_ID='UI-28' AND ACT_TYPE_CD='DNLD'", Integer.class);
    }

    @Test void invalidYearLeavesAssignmentsAndTentativesUnchanged() throws Exception {
        String before = snapshot();
        mvc.perform(get(ROOT + "/excel").param("year", "2028")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("year"));
        assertThat(snapshot()).isEqualTo(before);
        assertThat(auditCount()).isZero();
    }

    @Test void mixedPastAndFutureExportNeverConfirmsTentativeRows() throws Exception {
        String before = snapshot();
        mvc.perform(get(ROOT + "/excel").param("year", "2026")).andExpect(status().isOk());
        assertThat(snapshot()).isEqualTo(before);
        assertThat(fixture.db().queryForObject("SELECT TENT_STAT_CD FROM TB_SL_TENT_ASGN WHERE DEV_ID=? AND TENT_STAT_CD='ACT'",
            String.class, people.get("tentative"))).isEqualTo("ACT");
    }

    @Test void workbookGenerationFailureCreatesNoSuccessAudit() throws Exception {
        String before = fixture.businessSnapshot();
        doThrow(new IllegalStateException("generation failure")).when(excel).write(anyString(), anyList(), anyList(), anyString(), any());
        mvc.perform(get(ROOT + "/excel").param("year", "2026")).andExpect(status().isInternalServerError());
        assertThat(fixture.businessSnapshot()).isEqualTo(before);
    }

    @Test void databaseAuditFailureRollsBackLocalAccessTransaction() throws Exception {
        fixture.db().execute("""
            CREATE FUNCTION reject_board_audit() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN RAISE EXCEPTION 'audit unavailable'; END $$
            """);
        fixture.db().execute("CREATE TRIGGER reject_board_audit AFTER INSERT ON TB_SY_ACCS_LOG FOR EACH ROW EXECUTE FUNCTION reject_board_audit()");
        String before = fixture.businessSnapshot();
        mvc.perform(get(ROOT + "/excel").param("year", "2026")).andExpect(status().isInternalServerError());
        assertThat(fixture.businessSnapshot()).isEqualTo(before);
    }

    @Test void groupedQueriesAndRealDefinitionReadsStayBoundedAsRosterGrows() throws Exception {
        fixture.counter().reset();
        mvc.perform(get(ROOT).param("year", "2026")).andExpect(status().isOk());
        var small = fixture.counter().calls();
        assertThat(small).containsEntry("com.company.common.calculation.mapper.CalculationRuleMapper.selectDefinition", 8);
        for (int i = 0; i < 100; i++) fixture.person("보드 추가 " + i);
        fixture.counter().reset();
        mvc.perform(get(ROOT).param("year", "2026")).andExpect(status().isOk());
        assertThat(fixture.counter().calls()).isEqualTo(small);
    }

    @Test void rosterRetainsRetireesAndOnlyAvailableExcludesPastAndFutureHire() throws Exception {
        var response = json.readTree(mvc.perform(get(ROOT).param("year", "2026"))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
        var retired = java.util.stream.StreamSupport.stream(response.path("items").spliterator(), false)
            .filter(row -> row.path("developerId").asText().equals(people.get("retired"))).findFirst().orElseThrow();
        assertThat(retired.path("cells").get(11).path("statusName").asText()).isEqualTo("퇴사");
        var filtered = json.readTree(mvc.perform(get(ROOT).param("year", "2026").param("onlyAvailable", "true"))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data").path("items");
        assertThat(java.util.stream.StreamSupport.stream(filtered.spliterator(), false)
            .map(row -> row.path("developerId").asText()).toList()).doesNotContain(people.get("futureHire"), people.get("full"));
    }

    @Test void tentativeOverlayRequiresActiveOngoingSourceAndKeepsConfirmedCapacity() throws Exception {
        var data = json.readTree(mvc.perform(get(ROOT).param("year", "2026"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
        var rows = java.util.stream.StreamSupport.stream(data.path("items").spliterator(), false)
            .collect(java.util.stream.Collectors.toMap(row -> row.path("developerId").asText(), row -> row));
        var overlay = rows.get(people.get("tentative")).path("cells").get(11);
        assertThat(overlay.path("tentative").asBoolean()).isTrue();
        assertThat(overlay.path("winningProbability").decimalValue()).isEqualByComparingTo("30");
        assertThat(overlay.path("totalMm").decimalValue()).isZero();
        assertThat(rows.get(people.get("idle")).path("cells").get(11).path("tentative").asBoolean()).isFalse();
        assertThat(rows.get(people.get("full")).path("cells").get(11).path("tentative").asBoolean()).isFalse();
        assertThat(rows.get(people.get("tentative")).path("cells").get(10).path("tentative").asBoolean()).isFalse();
    }

    @Test void filteredListAndCountUseTheSameDbCodeConditions() throws Exception {
        var data = json.readTree(mvc.perform(get(ROOT).param("year", "2026").param("jobCode", "BE")
            .param("gradeCode", "MD").param("employeeTypeCode", "FT"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
        var expected = fixture.db().queryForObject(
            "SELECT COUNT(*) FROM TB_HR_DEV WHERE JOB_CD='BE' AND GRADE_CD='MD' AND EMP_TYPE_CD='FT' AND DRCT_CLS_CD='DRCT'", Integer.class);
        assertThat(data.path("totalCount").asInt()).isEqualTo(expected);
        assertThat(data.path("items").size()).isEqualTo(expected);
    }

    @Test void indirectStaffAreLeftOutAndNeverAssignedWorkingPersonShowsDashWithNoFreeMm() throws Exception {
        String indirect = fixture.person("간접 인력");
        fixture.db().update("UPDATE TB_HR_DEV SET DRCT_CLS_CD='INDRCT' WHERE DEV_ID=?", indirect);
        String working = fixture.person("투입 이력 없음");
        fixture.db().update("UPDATE TB_HR_DEV SET WORK_STAT_CD='WORK',MNG_NO='K-9901' WHERE DEV_ID=?", working);
        var data = json.readTree(mvc.perform(get(ROOT).param("year", "2026")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
        var rows = java.util.stream.StreamSupport.stream(data.path("items").spliterator(), false)
            .collect(java.util.stream.Collectors.toMap(row -> row.path("developerId").asText(), row -> row));
        assertThat(rows).doesNotContainKey(indirect);
        var none = rows.get(working);
        assertThat(none.path("managementNumber").asText()).isEqualTo("K-9901");
        assertThat(none.path("cells").get(10).path("statusCode").asText()).isEqualTo("none");
        assertThat(none.path("cells").get(10).path("statusName").asText()).isEqualTo("–");
        assertThat(data.path("totalCount").asInt()).isEqualTo(fixture.db().queryForObject(
            "SELECT COUNT(*) FROM TB_HR_DEV WHERE DRCT_CLS_CD='DRCT'", Integer.class));
        // 휴직 칸은 투입을 세지 않는다(시안 cellInfo).
        var leave = rows.get(people.get("leave")).path("cells").get(10);
        assertThat(leave.path("statusCode").asText()).isEqualTo("leave");
        assertThat(leave.path("projects").size()).isZero();
    }
}
