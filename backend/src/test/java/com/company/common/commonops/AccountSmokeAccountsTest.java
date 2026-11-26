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
        String value = java.util.UUID.randomUUID() + "A1!";
        new AccountSmokeAccounts(mapper, manager).initialize("test-actor", value, role);
        var hash = ArgumentCaptor.forClass(String.class); var order = inOrder(mapper);
        order.verify(mapper).activeRoleCount(List.of(role)); order.verify(mapper).upsertSmoke(eq("test-actor"), hash.capture());
        order.verify(mapper).clearSmokeRoles("test-actor"); order.verify(mapper).assignSmokeRoles("test-actor", List.of(role));
        assertThat(hash.getValue()).startsWith("$2a$10$"); assertThat(new BCryptPasswordEncoder(10).matches(value, hash.getValue())).isTrue();
        verify(manager).commit(any());
    }
}
