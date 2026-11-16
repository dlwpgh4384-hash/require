package com.company.common.projects.service;

import com.company.common.projects.service.ProjectAmountRules;
import com.company.common.projects.dto.ProjectContracts.*;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.service.*;
import com.company.common.users.service.UserScopePolicy.Grant;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectBusinessPolicyTest {
    @ParameterizedTest @ValueSource(strings={"ADMIN","PM"}) void administratorOrResponsiblePmMaySave(String role) {
        assertThat(ProjectBusinessPolicy.canSaveManpower(Set.of(role),List.of(new Grant("PROJ",Set.of(),Set.of("P-owned"))),"P-owned")).isTrue();
    }
    @Test void multipleScopeGrantsAreUnionNotIntersection() {
        assertThat(ProjectBusinessPolicy.canSaveManpower(Set.of("PM"),List.of(new Grant("PROJ",Set.of(),Set.of("one")),new Grant("PROJ",Set.of(),Set.of("two"))),"two")).isTrue();
    }
    @ParameterizedTest @ValueSource(strings={"PM","FIN"}) void wrongPmOrFinanceCannotWritePlanEvenWithAValidProject(String role) {
        var mapper=mock(ProjectMapper.class);var actor=mock(CurrentBusinessActor.class);var calendar=new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"),ZoneOffset.UTC));
        when(actor.userId()).thenReturn("actor");when(actor.roles()).thenReturn(Set.of(role));when(mapper.roles("actor")).thenReturn(List.of(role));
        when(mapper.projectScopes("actor")).thenReturn(List.of("P-other"));when(mapper.lockProject("P-owned")).thenReturn("P-owned");
        var p=new Project();p.projectId="P-owned";p.startDate=LocalDate.of(2026,1,1);p.endDate=LocalDate.of(2026,12,31);p.statusCode="ING";
        when(mapper.selectProject(eq("P-owned"),any(),anyString())).thenReturn(p);
        var service=new CommonProjectService(mapper,calendar,actor,mock(ProjectAmountRules.class),new ExcelExportService(),mock(ProjectProgressRules.class));
        assertThatThrownBy(()->service.putCommonProjectManpowerPlan("P-owned",new ManpowerInput(List.of(new JobInput("BE",BigDecimal.ONE))))).isInstanceOfSatisfying(CodeFailure.class,e->assertThat(e.getStatus()).isEqualTo(403));
        verify(mapper,never()).savePlan(any());
    }
}
