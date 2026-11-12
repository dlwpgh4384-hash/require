package com.company.common.commonops;

import java.util.List;

public record CommonUserOptionsResult(
    List<Role> roles, List<Project> projects, List<UserState> userStates) {
    public record Role(String roleCd, String roleName, boolean assignedProjectScope) {}
    public record Project(String projectId, String projectName) {}
    public record UserState(String code, String name) {}
}
