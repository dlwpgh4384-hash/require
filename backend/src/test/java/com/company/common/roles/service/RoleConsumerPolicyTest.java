package com.company.common.roles.service;

import com.company.common.roles.dto.RoleContracts;

import com.company.common.users.service.UserScopePolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RoleConsumerPolicyTest {
    private RoleContracts.Role role(String id, String scope, String sensitive, String active) {
        return new RoleContracts.Role(id,id,scope,sensitive,active,RoleRules.basic(id));
    }
    @Test void allNRemovesSensitiveTitlesValuesAndContributionsRatherThanMasking() {
        var items=List.of(new RoleSensitivePolicy.Item("일반 비용","공개",BigDecimal.ONE,false),
            new RoleSensitivePolicy.Item("급여","sensitive-salary",BigDecimal.TEN,true),
            new RoleSensitivePolicy.Item("지급 단가","sensitive-rate",BigDecimal.TEN,true),
            new RoleSensitivePolicy.Item("개인별 원가","sensitive-cost",BigDecimal.TEN,true),
            new RoleSensitivePolicy.Item("거래처 지급 계좌","sensitive-account",BigDecimal.ZERO,true));
        var roles=List.of(role("PM","PROJ","N","Y"));
        assertThat(RoleSensitivePolicy.omit(roles,items)).extracting(RoleSensitivePolicy.Item::title).containsExactly("일반 비용");
        assertThat(RoleSensitivePolicy.omit(roles,items)).extracting(RoleSensitivePolicy.Item::value).containsExactly("공개");
        assertThat(RoleSensitivePolicy.total(roles,items)).isEqualByComparingTo(BigDecimal.ONE);
    }
    @Test void anyActiveSensitiveGrantWinsAndInactiveGrantsDoNot() {
        assertThat(RoleSensitivePolicy.visible(List.of(role("PM","PROJ","N","Y"),role("EXEC","ALL","Y","Y")))).isTrue();
        assertThat(RoleSensitivePolicy.visible(List.of(role("PM","PROJ","N","Y"),role("R-001","ALL","Y","N")))).isFalse();
        assertThat(RoleSensitivePolicy.visible(List.of())).isFalse();
    }
    @Test void reuseExistingScopeUnionWithAllDominant() {
        var union=UserScopePolicy.union(List.of(new UserScopePolicy.Grant("PART",Set.of("HQ"),Set.of()),
            new UserScopePolicy.Grant("PROJ",Set.of(),Set.of("project-a"))));
        assertThat(union.partIds()).containsExactly("HQ"); assertThat(union.projectIds()).containsExactly("project-a");
        assertThat(union.all()).isFalse();
        assertThat(UserScopePolicy.union(List.of(new UserScopePolicy.Grant("ALL",Set.of(),Set.of()),
            new UserScopePolicy.Grant("PROJ",Set.of(),Set.of("project-a")))).all()).isTrue();
    }
    @Test void confirmationExecutorsNeverUseUpdateAsConfirmationPermission() {
        var editOnly=new RoleContracts.MenuInput("M011","Y","N","Y","N","N","N","N");
        assertThat(RoleActionPolicy.allows(editOnly,RoleActionPolicy.Action.UPDATE)).isTrue();
        for(var action:List.of(RoleActionPolicy.Action.QUOTE_APPROVE,RoleActionPolicy.Action.QUOTE_REJECT,
            RoleActionPolicy.Action.MONTH_CLOSE,RoleActionPolicy.Action.PART_SETTLEMENT_CONFIRM,
            RoleActionPolicy.Action.PART_SETTLEMENT_CANCEL)) {
            assertThat(RoleActionPolicy.allows(editOnly,action)).isFalse();
            var confirmOnly=new RoleContracts.MenuInput("M011","N","N","N","N","N","N","Y");
            assertThat(RoleActionPolicy.allows(confirmOnly,action)).isTrue();
        }
    }
}
