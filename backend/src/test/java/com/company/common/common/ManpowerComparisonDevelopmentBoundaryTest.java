package com.company.common.common;

import com.company.common.assignments.service.AssignmentMonthSchedule;
import com.company.common.assignments.service.CommonAssignmentService;
import com.company.common.manpowercomparisons.controller.CommonManpowerComparisonController;
import com.company.common.manpowercomparisons.dto.ManpowerComparisonContracts.*;
import com.company.common.manpowercomparisons.service.CommonManpowerComparisonService;
import com.company.common.menus.controller.CommonMenuController;
import com.company.common.menus.controller.MenuExceptionHandler;
import com.company.common.menus.mapper.MenuMapper;
import com.company.common.menus.service.CommonMenuService;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import java.math.BigDecimal;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.company.common.menus.service.MenuFixtures.row;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CommonMenuController.class, CommonManpowerComparisonController.class})
@ContextConfiguration(classes = {CommonMenuController.class, CommonMenuService.class, MenuExceptionHandler.class,
    CommonManpowerComparisonController.class, CodeExceptionHandler.class, ApiExceptionHandler.class})
class ManpowerComparisonDevelopmentBoundaryTest {
    @Autowired MockMvc mvc;
    @MockBean MenuMapper mapper;
    @MockBean CommonManpowerComparisonService comparisons;

    @Test void comparisonIsReadableWithoutIdentitySessionOrRoleHeaders() throws Exception {
        var filter = new Filter(2026, null, null);
        when(comparisons.getCommonManpowerComparisons(filter)).thenReturn(
            new Result(List.of(), new Totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
        var response = mvc.perform(get("/api/common/manpower-comparisons").param("year", "2026"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(header().doesNotExist("Location"))
            .andExpect(header().doesNotExist("WWW-Authenticate"))
            .andExpect(header().doesNotExist("Set-Cookie"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.meta").isMap())
            .andExpect(jsonPath("$.data.items").isEmpty())
            .andExpect(jsonPath("$.data.totals.plannedMm").value(0))
            .andExpect(jsonPath("$.data.totals.actualMm").value(0))
            .andExpect(jsonPath("$.data.totals.differenceMm").value(0))
            .andReturn();
        // 로그인(UI-22) 뒤에는 로그인 세션으로 부른다(세션 없음 확인은 로그인 전 기준이라 뺌).
        verify(comparisons).getCommonManpowerComparisons(filter);
        verifyNoMoreInteractions(comparisons);
        verifyNoInteractions(mapper);
    }

    @Test void anonymousMissingYearReturnsFieldErrorInsteadOfLoginChallenge() throws Exception {
        mvc.perform(get("/api/common/manpower-comparisons"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(header().doesNotExist("Location"))
            .andExpect(header().doesNotExist("WWW-Authenticate"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.errors[0].field").value("year"));
        verifyNoInteractions(comparisons, mapper);
    }

    @Test void comparisonMenuIsReadableWithoutIdentitySessionOrRoleHeaders() throws Exception {
        var group = row("G003", null, null, 1);
        var comparison = row("M008", "G003", "UI-11", 2);
        when(mapper.selectCandidates()).thenReturn(List.of(group, comparison));
        mvc.perform(get("/api/common/menus"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(header().doesNotExist("Location"))
            .andExpect(header().doesNotExist("WWW-Authenticate"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[1].menuId").value("M008"))
            .andExpect(jsonPath("$.data[1].scrnId").value("UI-11"))
            .andExpect(jsonPath("$.meta.screenAccess[0].available").value(true));
        verify(mapper).selectCandidates();
        verifyNoMoreInteractions(mapper);
        assertThat(comparison.getUseYn()).isEqualTo("Y");
    }

    @Test void normalStartupAndRestartDoNotPrepareSchemaOrRebuildMonthlyRecords() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var properties = yaml.getObject();
        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
        var assignments = mock(CommonAssignmentService.class);
        var sessions = mock(SqlSessionFactory.class);
        var source = mock(DataSource.class);
        var runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FlywayAutoConfiguration.class, SqlInitializationAutoConfiguration.class))
            .withPropertyValues("spring.flyway.enabled=" + properties.getProperty("spring.flyway.enabled"),
                "spring.sql.init.mode=" + properties.getProperty("spring.sql.init.mode"))
            .withBean(DataSource.class, () -> source)
            .withBean(SchemaPreparation.class, () -> new SchemaPreparation(sessions))
            .withBean(AssignmentMonthSchedule.class, () -> new AssignmentMonthSchedule(assignments));
        for (int restart = 0; restart < 2; restart++) {
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(org.flywaydb.core.Flyway.class);
                verifyNoInteractions(source, sessions, assignments);
            });
        }
    }
}
