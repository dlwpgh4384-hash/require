package com.company.common.users.service;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UserScopePolicyTest {
    @Test
    void assignedProjectsAndAnotherRolesPartsAreUnited() {
        var scope = UserScopePolicy.union(List.of(
            new UserScopePolicy.Grant("PROJ", Set.of(), Set.of("project-a", "project-b")),
            new UserScopePolicy.Grant("PART", Set.of("HQ", "part-a"), Set.of())));
        assertThat(scope.all()).isFalse();
        assertThat(scope.projectIds()).containsExactlyInAnyOrder("project-a", "project-b");
        assertThat(scope.partIds()).containsExactlyInAnyOrder("HQ", "part-a");
    }

    @Test
    void widerAllRoleIsNotNarrowedByAssignedProjectRole() {
        var scope = UserScopePolicy.union(List.of(
            new UserScopePolicy.Grant("PROJ", Set.of(), Set.of("project-a")),
            new UserScopePolicy.Grant("ALL", Set.of(), Set.of())));
        assertThat(scope.all()).isTrue();
        assertThat(scope.projectIds()).containsExactly("project-a");
    }

    @Test
    void repeatedGrantsDoNotDuplicateScope() {
        var grant = new UserScopePolicy.Grant("PROJ", Set.of(), Set.of("project-a"));
        assertThat(UserScopePolicy.union(List.of(grant, grant)).projectIds())
            .containsExactly("project-a");
    }

    @Test
    void noRolesDoesNotInventScope() {
        var scope = UserScopePolicy.union(List.of());
        assertThat(scope.all()).isFalse();
        assertThat(scope.partIds()).isEmpty();
        assertThat(scope.projectIds()).isEmpty();
    }

    @Test
    void unrelatedScopeRowsCannotBroadenRoleScope() {
        var scope = UserScopePolicy.union(List.of(
            new UserScopePolicy.Grant("PART", Set.of("part-a"), Set.of("unrelated-project")),
            new UserScopePolicy.Grant("PROJ", Set.of("unrelated-part"), Set.of("project-a"))));
        assertThat(scope.partIds()).containsExactly("part-a");
        assertThat(scope.projectIds()).containsExactly("project-a");
    }

    @Test
    void unsupportedDataScopeIsNotInterpretedAsAll() {
        assertThatIllegalArgumentException().isThrownBy(() -> UserScopePolicy.union(List.of(
            new UserScopePolicy.Grant("UNKNOWN", Set.of(), Set.of()))));
    }

    @Test
    void returnedScopeCannotBeChangedByCaller() {
        var projects = new java.util.HashSet<>(Set.of("project-a"));
        var grant = new UserScopePolicy.Grant("PROJ", Set.of(), projects);
        projects.add("project-b");
        var scope = UserScopePolicy.union(List.of(grant));
        assertThat(scope.projectIds()).containsExactly("project-a");
        assertThatThrownBy(() -> scope.projectIds().add("project-c"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
