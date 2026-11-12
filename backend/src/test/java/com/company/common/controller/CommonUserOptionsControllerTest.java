package com.company.common.controller;

import com.company.common.service.CommonUserOptionsService;
import com.company.common.mapper.UserOptionsMapper;
import com.company.common.mapper.UserOptionRows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonUserOptionsController.class)
@ContextConfiguration(classes = {CommonUserOptionsController.class, CommonUserOptionsService.class,
    UserOptionsExceptionHandler.class})
class CommonUserOptionsControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean UserOptionsMapper mapper;

    @Test
    void anonymousRequestReturnsDatabaseOptionsWithPublicNamesOnly() throws Exception {
        var role = new UserOptionRows.Role();
        role.setRoleCd("CUSTOM_PM");
        role.setRoleName("담당 프로젝트 역할");
        role.setAssignedProjectScope(true);
        var project = new UserOptionRows.Project();
        project.setProjectId("project-owned-by-test");
        project.setProjectName("완료 프로젝트도 표시");
        var state = new UserOptionRows.UserState();
        state.setCode("USE");
        state.setName("사용");
        when(mapper.selectRoles()).thenReturn(List.of(role));
        when(mapper.selectProjects()).thenReturn(List.of(project));
        when(mapper.selectUserStates()).thenReturn(List.of(state));

        mvc.perform(get("/api/common/user-options"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/json"))
            .andExpect(content().json("""
                {"success":true,"meta":{},"data":{
                  "roles":[{"roleCd":"CUSTOM_PM","roleName":"담당 프로젝트 역할","assignedProjectScope":true,"assignedPartScope":false}],
                  "projects":[{"projectId":"project-owned-by-test","projectName":"완료 프로젝트도 표시","pmUserId":null,"pmName":null}],
                  "userStates":[{"code":"USE","name":"사용"}]
                }}
                """, true));
        verify(mapper).selectRoles();
        verify(mapper).selectProjects();
        verify(mapper).selectUserStates();
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void emptyTablesReturnArraysWithoutCreatingDefaults() throws Exception {
        when(mapper.selectRoles()).thenReturn(List.of());
        when(mapper.selectProjects()).thenReturn(List.of());
        when(mapper.selectUserStates()).thenReturn(List.of());
        mvc.perform(get("/api/common/user-options"))
            .andExpect(status().isOk())
            .andExpect(content().json("""
                {"success":true,"meta":{},"data":{"roles":[],"projects":[],"userStates":[]}}
                """, true));
        verify(mapper).selectRoles();
        verify(mapper).selectProjects();
        verify(mapper).selectUserStates();
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void nonProjectRoleIsNotPromotedByItsNameOrId() throws Exception {
        var role = new UserOptionRows.Role();
        role.setRoleCd("PM");
        role.setRoleName("프로젝트 관리자");
        role.setAssignedProjectScope(false);
        when(mapper.selectRoles()).thenReturn(List.of(role));
        mvc.perform(get("/api/common/user-options"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roles[0].assignedProjectScope").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"roles", "projects", "states"})
    void databaseFailureNeverLeaksSqlOrReturnsPartialSuccess(String source) throws Exception {
        var failure = new DataAccessResourceFailureException("private SQL password connection details");
        switch (source) {
            case "roles" -> when(mapper.selectRoles()).thenThrow(failure);
            case "projects" -> when(mapper.selectProjects()).thenThrow(failure);
            default -> when(mapper.selectUserStates()).thenThrow(failure);
        }
        mvc.perform(get("/api/common/user-options"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().json("""
                {"success":false,"meta":{},
                 "message":"선택 목록을 불러오지 못했습니다. 다시 시도해 주세요.","errors":[]}
                """, true))
            .andExpect(jsonPath("$.data").doesNotExist());
    }
}
