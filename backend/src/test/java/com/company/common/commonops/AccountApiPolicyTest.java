package com.company.common.commonops;

import com.company.common.commonops.mapper.AccountAccessMapper.Permission;
import com.company.common.commonops.service.AccountApiPolicy;
import com.company.common.commonops.service.AccountAccessFailure;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AccountApiPolicyTest {
    final AccountApiPolicy policy = new AccountApiPolicy();
    Permission permission(String menu) {
        var p = new Permission(); p.menuId = menu; p.readYn = "Y"; p.createYn = "Y"; p.updateYn = "Y";
        p.deleteYn = "Y"; p.uploadYn = "Y"; p.downloadYn = "Y"; p.cnfmYn = "N"; return p;
    }
    @Test void administratorMustHaveReadAndRequestedAction() {
        var p = permission("M027"); p.readYn = "N";
        assertThatThrownBy(() -> policy.check("PUT", "/api/common/users/target", null, List.of(p), List.of("ADMIN"))).isInstanceOf(AccountAccessFailure.class);
        p.readYn = "Y"; p.updateYn = "N";
        assertThatThrownBy(() -> policy.check("PUT", "/api/common/users/target", null, List.of(p), List.of("ADMIN"))).isInstanceOf(AccountAccessFailure.class);
        p.updateYn = "Y"; policy.check("PUT", "/api/common/users/target", null, List.of(p), List.of("ADMIN"));
    }
    @Test void creationUsesCreateAndNotUpdate() {
        var p = permission("M027"); p.updateYn = "N";
        policy.check("PUT", "/api/common/users", null, List.of(p), List.of());
        assertThatThrownBy(() -> policy.check("PUT", "/api/common/users/user-a", null, List.of(p), List.of())).isInstanceOf(AccountAccessFailure.class);
    }
    @Test void billCancellationDoesNotRequireConfirmationPermission() {
        policy.check("POST", "/api/common/bills/bill-a/cancel", null, List.of(permission("M013")), List.of());
    }
    @Test void quoteApprovalRequiresConfirmation() {
        var p = permission("M011");
        assertThatThrownBy(() -> policy.check("PUT", "/api/common/sales-opportunities/opp-a/approve", null, List.of(p), List.of())).isInstanceOf(AccountAccessFailure.class);
        p.cnfmYn = "Y"; policy.check("PUT", "/api/common/sales-opportunities/opp-a/approve", null, List.of(p), List.of());
    }
    @Test void selectorExceptionDoesNotAuthorizeDetailsOrExports() {
        policy.check("GET", "/api/common/projects", null, List.of(), List.of());
        assertThatThrownBy(() -> policy.check("GET", "/api/common/projects/project-a", null, List.of(), List.of())).isInstanceOf(AccountAccessFailure.class);
        assertThatThrownBy(() -> policy.check("GET", "/api/common/projects/excel", null, List.of(), List.of())).isInstanceOf(AccountAccessFailure.class);
    }
    @Test void staticExcelRouteWinsOverIdentifierRoute() {
        var p = permission("M004"); p.downloadYn = "N";
        assertThatThrownBy(() -> policy.check("GET", "/api/common/projects/excel", null, List.of(p), List.of())).isInstanceOf(AccountAccessFailure.class);
    }
    @Test void accessLogsAreAdministratorOnlyEvenWithMenuRead() {
        var p = permission("M027");
        assertThatThrownBy(() -> policy.check("GET", "/api/common/users/user-a/access-logs", null, List.of(p), List.of("FIN"))).isInstanceOf(AccountAccessFailure.class);
        policy.check("GET", "/api/common/users/user-a/access-logs", null, List.of(p), List.of("ADMIN"));
    }
    @Test void uploadDispatchUsesOwningMenu() {
        assertThatThrownBy(() -> policy.check("POST", "/api/common/uploads", "B7", List.of(permission("M003")), List.of("ADMIN"))).isInstanceOf(AccountAccessFailure.class);
        policy.check("POST", "/api/common/uploads", "B7", List.of(permission("M002")), List.of());
    }
    @Test void b11RequiresBothAdministratorAndUploadGrant() {
        var p = permission("M020");
        assertThatThrownBy(() -> policy.check("POST", "/api/common/uploads", "B11", List.of(p), List.of("FIN"))).isInstanceOf(AccountAccessFailure.class);
        policy.check("POST", "/api/common/uploads", "B11", List.of(p), List.of("ADMIN"));
        p.uploadYn = "N";
        assertThatThrownBy(() -> policy.check("POST", "/api/common/uploads", "B11", List.of(p), List.of("ADMIN"))).isInstanceOf(AccountAccessFailure.class);
    }
    @Test void zeroRoleIdentityAndPasswordOperationsAreAllowedAfterAuthentication() {
        policy.check("GET", "/api/common/my-info", null, List.of(), List.of());
        policy.check("PUT", "/api/common/my-password", null, List.of(), List.of());
        policy.check("POST", "/api/common/logout", null, List.of(), List.of());
    }
    @Test void unknownOperationIsDenied() {
        assertThatThrownBy(() -> policy.check("POST", "/api/common/unknown", null, List.of(), List.of("ADMIN"))).isInstanceOf(AccountAccessFailure.class);
    }
}
