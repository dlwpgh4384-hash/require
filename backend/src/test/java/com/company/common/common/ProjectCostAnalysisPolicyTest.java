package com.company.common.common;

import com.company.common.roles.dto.RoleContracts.Role;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class ProjectCostAnalysisPolicyTest {
    private Role role(String id, String active, String sensitive) {
        return new Role(id, id, "ALL", sensitive, active, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FIN", "EXEC", "ADMIN"})
    void permitsEachApprovedActiveAnalysisRole(String id) {
        assertThat(ProjectCostAnalysisPolicy.canView(List.of(role(id, "Y", "Y")))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FIN", "EXEC", "ADMIN"})
    void inactiveAnalysisGrantDoesNotPermitFinancialAccess(String id) {
        assertThat(ProjectCostAnalysisPolicy.canView(List.of(role(id, "N", "Y")))).isFalse();
    }

    @Test
    void pmCannotViewAnalysisEvenWithSensitiveFlagAndAllScope() {
        assertThat(ProjectCostAnalysisPolicy.canView(List.of(role("PM", "Y", "Y")))).isFalse();
    }

    @Test
    void salesLeaderIsNotAnAnalysisRoleEvenWithSensitiveFlag() {
        assertThat(ProjectCostAnalysisPolicy.canView(List.of(role("SALES_LDR", "Y", "Y")))).isFalse();
    }

    @Test
    void unknownRoleDoesNotInheritAnalysisAccessFromSensitiveFlag() {
        assertThat(ProjectCostAnalysisPolicy.canView(List.of(role("R-001", "Y", "Y")))).isFalse();
    }

    @Test
    void activeApprovedGrantWinsInAMultiRoleAssignment() {
        assertThat(ProjectCostAnalysisPolicy.canView(List.of(
            role("PM", "Y", "N"), role("FIN", "Y", "Y")))).isTrue();
    }

    @Test
    void inactiveApprovedGrantDoesNotElevatePm() {
        assertThat(ProjectCostAnalysisPolicy.canView(List.of(
            role("PM", "Y", "N"), role("ADMIN", "N", "Y")))).isFalse();
    }

    @Test
    void absentOrMalformedAssignmentsFailClosed() {
        assertThat(ProjectCostAnalysisPolicy.canView(null)).isFalse();
        assertThat(ProjectCostAnalysisPolicy.canView(List.of())).isFalse();
        assertThat(ProjectCostAnalysisPolicy.canView(Arrays.asList(null, role(null, "Y", "Y"))))
            .isFalse();
    }
}
