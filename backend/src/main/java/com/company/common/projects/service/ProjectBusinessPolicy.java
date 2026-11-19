package com.company.common.projects.service;

import com.company.common.users.service.UserScopePolicy;
import java.util.Collection;
import java.util.Set;

public final class ProjectBusinessPolicy {
    private ProjectBusinessPolicy() {}
    public static boolean canManageProgress(Set<String> roles, Collection<UserScopePolicy.Grant> grants,
            String projectId) {
        return canSaveManpower(roles, grants, projectId);
    }
    public static boolean canSaveManpower(Set<String> roles, Collection<UserScopePolicy.Grant> grants,
            String projectId) {
        return roles.contains("ADMIN") || roles.contains("PM")
            && UserScopePolicy.union(grants).projectIds().contains(projectId);
    }
}
