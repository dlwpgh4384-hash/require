package com.company.common.clients.service;

import com.company.common.support.service.CurrentBusinessActor;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class ClientPartPolicyTest {
    @ParameterizedTest @ValueSource(strings = {"CUST", "VNDR", "BOTH"})
    void administratorCanRegisterEveryCategory(String category) {
        assertThat(ClientPartPolicy.canCreateClient(Set.of("ADMIN"), category)).isTrue();
    }
    @ParameterizedTest @ValueSource(strings = {"CUST", "VNDR", "BOTH"})
    void financeCanRegisterEveryCategory(String category) {
        assertThat(ClientPartPolicy.canCreateClient(Set.of("FIN"), category)).isTrue();
    }
    @Test void salesLeaderCanRegisterCustomer() {
        assertThat(ClientPartPolicy.canCreateClient(Set.of("SALES_LDR"), "CUST")).isTrue();
    }
    @ParameterizedTest @ValueSource(strings = {"VNDR", "BOTH"})
    void salesLeaderCannotRegisterSupplierCategories(String category) {
        assertThat(ClientPartPolicy.canCreateClient(Set.of("SALES_LDR"), category)).isFalse();
    }
    @Test void unrelatedRoleCannotRegister() {
        assertThat(ClientPartPolicy.canCreateClient(Set.of("PM"), "CUST")).isFalse();
    }
    @Test void unsupportedCategoryDoesNotBecomeAllowedForAdmin() {
        assertThat(ClientPartPolicy.canCreateClient(Set.of("ADMIN"), "CLIENT")).isFalse();
    }
    @ParameterizedTest @ValueSource(strings = {"ADMIN", "FIN"})
    void administratorAndFinanceCanCreatePart(String role) {
        assertThat(ClientPartPolicy.canCreatePart(Set.of(role))).isTrue();
    }
    @Test void salesLeaderCannotCreatePart() {
        assertThat(ClientPartPolicy.canCreatePart(Set.of("SALES_LDR"))).isFalse();
    }
    @Test void fullAccountRequiresFinanceCapability() {
        assertThat(ClientPartPolicy.canReadPaymentAccount(Set.of("FIN"))).isTrue();
    }
    @ParameterizedTest @ValueSource(strings = {"ADMIN", "SALES_LDR", "PM"})
    void withoutFinanceNoFullAccountIsAllowed(String role) {
        assertThat(ClientPartPolicy.canReadPaymentAccount(Set.of(role))).isFalse();
    }
    @Test void developmentActorIsAdminWithAllCapabilitiesWithoutLogin() throws Exception {
        // 로그인(UI-22) 뒤 현재 사용자는 로그인 정보에서만 온다: 로그인 전 개발 사용자(admin)로 로그인한 상태로 본다.
        try (var login = com.company.common.support.test.AuthenticatedMockMvcCustomizerFactory.loginDevelopmentAdmin()) {
            var actor = new CurrentBusinessActor();
            assertThat(actor.userId()).isEqualTo("admin");
            assertThat(ClientPartPolicy.canCreateClient(actor.roles(), "BOTH")).isTrue();
            assertThat(ClientPartPolicy.canCreatePart(actor.roles())).isTrue();
            assertThat(ClientPartPolicy.canReadPaymentAccount(actor.roles())).isTrue();
            assertThatThrownBy(() -> actor.roles().clear()).isInstanceOf(UnsupportedOperationException.class);

        }
    }
}
