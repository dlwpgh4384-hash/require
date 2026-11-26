package com.company.common.mfa.service;

import com.company.common.bills.mapper.BillRows.Recipient;
import com.company.common.bills.service.BillLocalMailbox;
import com.company.common.support.service.CodeFailure;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MfaMailDeliveryTest {
    @SuppressWarnings("unchecked")
    @Test void absentPasswordUsesRealLocalFileEvenWhenSenderAndFromExist() throws Exception {
        ObjectProvider<JavaMailSender> senders = mock(ObjectProvider.class);
        var sender = mock(JavaMailSender.class);
        when(senders.getIfAvailable()).thenReturn(sender);
        var environment = new MockEnvironment().withProperty("SMTP_HOST", "smtp.example.invalid")
            .withProperty("SMTP_USERNAME", "actor@example.invalid");
        var mailbox = new BillLocalMailbox(senders, "actor@example.invalid", environment);
        var path = mailbox.deliver(List.of(new Recipient("actor", "시험 관리자", "actor@example.invalid")), "관리자 인증번호 전달 시험");
        try {
            assertThat(Files.isRegularFile(path)).isTrue();
            assertThat(path.getFileName().toString()).endsWith(".eml");
            if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
                var permissions = Files.getPosixFilePermissions(path);
                assertThat(permissions).doesNotContain(java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                    java.nio.file.attribute.PosixFilePermission.OTHERS_READ);
            }
            verifyNoInteractions(sender);
        } finally { mailbox.discard(path); }
    }
    @SuppressWarnings("unchecked")
    @Test void configuredPasswordWithMissingSenderIdentityFailsRatherThanUsingLocalSuccess() {
        ObjectProvider<JavaMailSender> senders = mock(ObjectProvider.class);
        var environment = new MockEnvironment().withProperty("SMTP_PASSWORD", UUID.randomUUID().toString());
        var mailbox = new BillLocalMailbox(senders, "", environment);
        assertThatThrownBy(() -> mailbox.deliver(List.of(new Recipient("actor", "시험 관리자", "actor@example.invalid")), "전달 시험"))
            .isInstanceOf(CodeFailure.class).hasMessage("메일을 보내지 못했습니다. 다시 시도해 주세요.");
    }
}
