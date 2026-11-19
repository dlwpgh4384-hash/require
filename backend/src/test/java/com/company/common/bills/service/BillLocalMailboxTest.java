package com.company.common.bills.service;

import com.company.common.bills.mapper.BillRows.Recipient;
import com.company.common.support.service.CodeFailure;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BillLocalMailboxTest {
    /** 시험용 가짜 주소만 쓴다(실제 SMTP 호스트·비밀번호 없음). */
    static final String FROM="billing@example.test";

    @SuppressWarnings("unchecked")
    static ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }
    static BillLocalMailbox local() { return new BillLocalMailbox(provider(null),""); }
    static List<Recipient> one() { return List.of(new Recipient("recipient","시험 수신자","recipient@example.test")); }

    @Test void localDeliveryWritesRealMimeBeforeAcknowledging() throws Exception {
        var mailbox=local();
        var path=mailbox.deliver(one(),"청구 미수 알림");
        try {
            assertThat(path.getParent().getFileName().toString()).isEqualTo("billing-mail");
            assertThat(path.getFileName().toString()).endsWith(".eml");
            String mime=Files.readString(path,StandardCharsets.UTF_8);
            assertThat(mime).contains("X-Billing-Delivery: local-mailbox","To: recipient@example.test",
                "Subject: =?UTF-8?B?"+Base64.getEncoder().encodeToString("청구 미수 알림".getBytes(StandardCharsets.UTF_8))+"?=","Content-Type: text/plain; charset=UTF-8");
            String encoded=mime.substring(mime.indexOf("\r\n\r\n")+4);
            assertThat(new String(Base64.getMimeDecoder().decode(encoded),StandardCharsets.UTF_8)).isEqualTo("청구 미수 알림");
        } finally { mailbox.discard(path); }
        assertThat(Files.exists(path)).isFalse();
    }
    @Test void subjectIsTheFirstNoticeLineWithoutLineBreaks() {
        assertThat(BillLocalMailbox.subject("미수 1단계 알림: 새솔전자 MES 유지보수 10월분 (14일 지남)\r\nBcc: other@example.test"))
            .isEqualTo("미수 1단계 알림: 새솔전자 MES 유지보수 10월분 (14일 지남)");
        assertThat(BillLocalMailbox.subject("가".repeat(250))).hasSize(200);
        assertThat(BillLocalMailbox.subject(null)).isEmpty();
    }
    @Test void emptyRecipientListCannotAcknowledgeDelivery() {
        assertThatThrownBy(() -> local().deliver(List.of(),"알림")).isInstanceOf(CodeFailure.class);
    }
    @Test void headerInjectionCannotWriteMime() {
        assertThatThrownBy(() -> local().deliver(List.of(new Recipient("recipient","수신자","a@example.test\r\nBcc:other@example.test")),"알림")).isInstanceOf(CodeFailure.class);
    }
    @Test void configuredSenderAndFromAddressSendRealMailWithTheSameMessageAndNoFile() {
        var sender=mock(JavaMailSender.class);
        var recipients=List.of(new Recipient("a","가","a@example.test"),new Recipient("b","나","b@example.test"));
        assertThat(new BillLocalMailbox(provider(sender),FROM).deliver(recipients,"청구 미수 알림")).isNull();
        var captor=ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        var message=captor.getValue();
        assertThat(message.getFrom()).isEqualTo(FROM);
        assertThat(message.getTo()).containsExactly("a@example.test","b@example.test");
        assertThat(message.getSubject()).isEqualTo("청구 미수 알림");
        assertThat(message.getText()).isEqualTo("청구 미수 알림");
    }
    @Test void senderFailureIsDeliveryFailureSoNoHistoryIsWritten() {
        var sender=mock(JavaMailSender.class);
        doThrow(new MailSendException("smtp down")).when(sender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> new BillLocalMailbox(provider(sender),FROM).deliver(one(),"알림"))
            .isInstanceOfSatisfying(CodeFailure.class,failure -> {
                assertThat(failure.getField()).isEqualTo("delivery");
                assertThat(failure.getMessage()).isEqualTo("메일을 보내지 못했습니다. 다시 시도해 주세요.");
            });
    }
    @Test void senderWithoutFromAddressKeepsTheLocalMailbox() throws Exception {
        var sender=mock(JavaMailSender.class);
        var mailbox=new BillLocalMailbox(provider(sender),"  ");
        var path=mailbox.deliver(one(),"알림");
        try { assertThat(path.getFileName().toString()).endsWith(".eml"); }
        finally { mailbox.discard(path); }
        verifyNoInteractions(sender);
    }
    @Test void discardIgnoresNothingToRemove() {
        assertThatCode(() -> local().discard(null)).doesNotThrowAnyException();
    }
}
