package com.company.common.common;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/** DB에서 조회한 역할의 DATA_SCOPE_CD와 담당 범위를 합친다. 현재 API의 접근 검사가 아니다. */
public final class UserScopePolicy {
    private UserScopePolicy() {}

    public record Grant(String dataScopeCd, Set<String> partIds, Set<String> projectIds) {
        public Grant {
            partIds = Set.copyOf(partIds);
            projectIds = Set.copyOf(projectIds);
        }
    }

    public record Scope(boolean all, Set<String> partIds, Set<String> projectIds) {
        public Scope {
            partIds = Set.copyOf(partIds);
            projectIds = Set.copyOf(projectIds);
        }
    }

    public static Scope union(Collection<Grant> grants) {
        boolean all = false;
        var parts = new HashSet<String>();
        var projects = new HashSet<String>();
        for (Grant grant : grants) {
            switch (grant.dataScopeCd()) {
                case "ALL" -> all = true;
                case "PART" -> parts.addAll(grant.partIds());
                case "PROJ" -> projects.addAll(grant.projectIds());
                default -> throw new IllegalArgumentException("지원하지 않는 데이터 범위입니다.");
            }
        }
        return new Scope(all, parts, projects);
    }
}
