package com.company.common.commonops;

import com.company.common.commonops.mapper.AccountAccessMapper;
import com.company.common.commonops.service.AccountSmokeAccounts;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountSmokeAccountsTest {
    @Test void missingAnySlotDoesNotTouchDatabase() {
        var mapper = mock(AccountAccessMapper.class); var manager = mock(PlatformTransactionManager.class);
        var initializer = new AccountSmokeAccounts(mapper, manager);
        initializer.initialize(null, "test-only", "FIN"); initializer.initialize("test-actor", null, "FIN"); initializer.initialize("test-actor", "test-only", null);
        verifyNoInteractions(mapper, manager);
    }
    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EXEC", "FIN", "SALES_LDR", "PM"})
    void suppliedSlotsHashAndAssignExistingRolesAfterAccountInsert(String role) {
        var mapper = mock(AccountAccessMapper.class); var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class)); when(mapper.activeRoleCount(List.of(role))).thenReturn(1);
        when(mapper.insertSmoke(eq("test-actor"), anyString())).thenReturn(1);
        String value = java.util.UUID.randomUUID() + "A1!";
        new AccountSmokeAccounts(mapper, manager).initialize("test-actor", value, role);
        var hash = ArgumentCaptor.forClass(String.class); var order = inOrder(mapper);
        order.verify(mapper).activeRoleCount(List.of(role)); order.verify(mapper).insertSmoke(eq("test-actor"), hash.capture());
        order.verify(mapper).assignSmokeRoles("test-actor", List.of(role));
        assertThat(hash.getValue()).startsWith("$2a$10$"); assertThat(new BCryptPasswordEncoder(10).matches(value, hash.getValue())).isTrue();
        verify(manager).commit(any());
    }
    /** 이미 있는 계정(실제 계정일 수 있음)은 비밀번호·잠금·역할을 바꾸지 않고 건너뛴다. */
    @Test void existingUserIsSkippedWithoutTouchingPasswordLockOrRoles() {
        var mapper = mock(AccountAccessMapper.class); var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class)); when(mapper.activeRoleCount(List.of("ADMIN"))).thenReturn(1);
        when(mapper.insertSmoke(eq("admin"), anyString())).thenReturn(0);
        new AccountSmokeAccounts(mapper, manager).initialize("admin", java.util.UUID.randomUUID() + "A1!", "ADMIN");
        verify(mapper).activeRoleCount(List.of("ADMIN")); verify(mapper).insertSmoke(eq("admin"), anyString());
        verifyNoMoreInteractions(mapper);
        verify(manager).commit(any());
    }
}
