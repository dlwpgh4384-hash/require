package com.company.common.users.service;

import com.company.common.users.mapper.UserOptionsMapper;
import com.company.common.users.dto.CommonUserOptionsResponse;
import com.company.common.users.dto.CommonUserOptionsResult;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommonUserOptionsService {
    private final UserOptionsMapper mapper;

    public CommonUserOptionsService(UserOptionsMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public CommonUserOptionsResponse getCommonUserOptions() {
        var roles = mapper.selectRoles().stream()
            .map(row -> new CommonUserOptionsResult.Role(
                row.getRoleCd(), row.getRoleName(), row.isAssignedProjectScope(), row.isAssignedPartScope()))
            .toList();
        var projects = mapper.selectProjects().stream()
            .map(row -> new CommonUserOptionsResult.Project(row.getProjectId(), row.getProjectName(), row.getPmUserId(), row.getPmName()))
            .toList();
        var states = mapper.selectUserStates().stream()
            .map(row -> new CommonUserOptionsResult.UserState(row.getCode(), row.getName()))
            .toList();
        return new CommonUserOptionsResponse(true, Map.of(),
            new CommonUserOptionsResult(roles, projects, states));
    }
}
