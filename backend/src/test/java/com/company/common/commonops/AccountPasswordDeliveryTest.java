package com.company.common.commonops;

import com.company.common.users.service.TemporaryUserPassword;
import com.company.common.bills.service.BillLocalMailbox;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountPasswordDeliveryTest {
    @Test void localDeliveryContainsRealGeneratedPasswordWithStrengthTenHash() throws Exception {
        var mailbox = spy(new BillLocalMailbox(null, ""));
        List<Path> mail = new ArrayList<>();
        doAnswer(call -> { Path path = (Path) call.callRealMethod(); mail.add(path); return path; }).when(mailbox).deliver(anyList(), anyString(), anyString());
        try {
            String hash = new TemporaryUserPassword(mailbox).hashAndDeliver("test@example.invalid");
            assertThat(mail).hasSize(1); verify(mailbox).deliver(anyList(), anyString(), eq("임시 비밀번호"));
            String mime = Files.readString(mail.get(0), StandardCharsets.UTF_8);
            String body = new String(Base64.getMimeDecoder().decode(mime.substring(mime.indexOf("\r\n\r\n") + 4)), StandardCharsets.UTF_8);
            var pattern = java.util.regex.Pattern.compile("임시 비밀번호: ([^\\r\\n]+)").matcher(body);
            assertThat(pattern.find()).isTrue();
            // Never put the generated credential in assertion diagnostics.
            boolean valid = new BCryptPasswordEncoder().matches(pattern.group(1), hash);
            assertThat(valid).isTrue(); assertThat(hash).startsWith("$2a$10$");
        } finally { mail.forEach(mailbox::discard); }
    }
    @Test void transportFailureDoesNotReturnAHash() {
        var mailbox = mock(BillLocalMailbox.class);
        when(mailbox.deliver(anyList(), anyString(), anyString())).thenThrow(new IllegalStateException("mail unavailable"));
        assertThatThrownBy(() -> new TemporaryUserPassword(mailbox).hashAndDeliver("test@example.invalid"))
            .isInstanceOf(IllegalStateException.class).hasMessage("mail unavailable");
    }
    @Test void rollbackCompensatesLocalDelivery() {
        var mailbox = mock(BillLocalMailbox.class); var path = Path.of("rollback-test.eml");
        when(mailbox.deliver(anyList(), anyString(), anyString())).thenReturn(path);
        TransactionSynchronizationManager.initSynchronization();
        try {
            new TemporaryUserPassword(mailbox).hashAndDeliver("test@example.invalid");
            for (var sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(mailbox).discard(path);
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
