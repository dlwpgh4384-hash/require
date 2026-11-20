package com.company.common.common;

import com.company.common.roles.dto.RoleContracts.Role;
import java.util.Collection;
import java.util.Set;

/** UI-22 연동용 독립 정책이며 현재 공개 HTTP 경로에 인증 검사를 추가하지 않는다. */
public final class ProjectCostAnalysisPolicy {
    private static final Set<String> ANALYSIS_ROLES = Set.of("FIN", "EXEC", "ADMIN");

    private ProjectCostAnalysisPolicy() {}

    public static boolean canView(Collection<Role> assignedRoles) {
        return assignedRoles != null && assignedRoles.stream()
            .anyMatch(role -> role != null && "Y".equals(role.useYn())
                && role.roleId() != null && ANALYSIS_ROLES.contains(role.roleId()));
    }
}
