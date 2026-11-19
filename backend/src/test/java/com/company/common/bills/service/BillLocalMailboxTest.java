package com.company.common.bills.service;

import com.company.common.bills.mapper.BillRows.Recipient;
import com.company.common.support.service.CodeFailure;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BillLocalMailboxTest {
    @Test void localDeliveryWritesRealMimeBeforeAcknowledging() throws Exception {
        var mailbox=new BillLocalMailbox();
        var path=mailbox.deliver(List.of(new Recipient("recipient","시험 수신자","recipient@example.test")),"청구 미수 알림");
        try {
            assertThat(path.getParent().getFileName().toString()).isEqualTo("billing-mail");
            assertThat(path.getFileName().toString()).endsWith(".eml");
            String mime=Files.readString(path,StandardCharsets.UTF_8);
            assertThat(mime).contains("X-Billing-Delivery: local-mailbox","To: recipient@example.test","Content-Type: text/plain; charset=UTF-8");
            String encoded=mime.substring(mime.indexOf("\r\n\r\n")+4);
            assertThat(new String(Base64.getMimeDecoder().decode(encoded),StandardCharsets.UTF_8)).isEqualTo("청구 미수 알림");
        } finally { mailbox.discard(path); }
        assertThat(Files.exists(path)).isFalse();
    }
    @Test void emptyRecipientListCannotAcknowledgeDelivery() {
        assertThatThrownBy(() -> new BillLocalMailbox().deliver(List.of(),"알림")).isInstanceOf(CodeFailure.class);
    }
    @Test void headerInjectionCannotWriteMime() {
        assertThatThrownBy(() -> new BillLocalMailbox().deliver(List.of(new Recipient("recipient","수신자","a@example.test\r\nBcc:other@example.test")),"알림")).isInstanceOf(CodeFailure.class);
    }
}
