package com.company.common.dto;

import java.util.List;

public record CommonUserOptionsResult(
    List<Role> roles, List<Project> projects, List<UserState> userStates) {
    public record Role(String roleCd, String roleName, boolean assignedProjectScope, boolean assignedPartScope) {}
    /** pmUserId·pmName: 지금 이 프로젝트의 PM(없으면 null). 등록·수정 창 「(지금 PM: 이름)」. */
    public record Project(String projectId, String projectName, String pmUserId, String pmName) {}
    public record UserState(String code, String name) {}
}
