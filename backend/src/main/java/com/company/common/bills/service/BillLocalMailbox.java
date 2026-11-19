package com.company.common.bills.service;

import com.company.common.bills.mapper.BillRows.Recipient;
import com.company.common.support.service.CodeFailure;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Component;

/** Development delivery to an application-owned local MIME mailbox, not Internet SMTP. */
@Component
public class BillLocalMailbox {
    /** 메일을 받을 수 있는 주소인지(비었거나 형식이 틀리면 메일만 빼고 시스템 알림은 보낸다). */
    public static boolean deliverable(Recipient recipient) {
        return recipient!=null && recipient.email()!=null && recipient.email().length()<=100
            && recipient.email().matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+");
    }

    public Path deliver(List<Recipient> recipients, String content) {
        if (recipients==null || recipients.isEmpty())
            throw CodeFailure.invalid("delivery","받는 사람이 없습니다.");
        for (var recipient:recipients) {
            if (recipient.email()==null || recipient.email().length()>100
                || !recipient.email().matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+"))
                throw CodeFailure.invalid("delivery","수신자의 메일 주소를 확인해 주세요.");
        }
        Path staged=null;
        try {
            Path directory=Path.of(System.getProperty("java.io.tmpdir"),"billing-mail");
            Files.createDirectories(directory);
            staged=Files.createTempFile(directory,"bill-",".part");
            String to=String.join(", ",recipients.stream().map(Recipient::email).toList());
            String mime="X-Billing-Delivery: local-mailbox\r\nTo: "+to
                +"\r\nSubject: Billing notification\r\nMIME-Version: 1.0\r\n"
                +"Content-Type: text/plain; charset=UTF-8\r\nContent-Transfer-Encoding: base64\r\n\r\n"
                +Base64.getMimeEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8))+"\r\n";
            Files.writeString(staged,mime,StandardCharsets.UTF_8);
            Path delivered=staged.resolveSibling(staged.getFileName().toString().replace(".part",".eml"));
            return Files.move(staged,delivered,StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | SecurityException exception) {
            if (staged!=null) discard(staged);
            throw CodeFailure.invalid("delivery","로컬 메일함 전달에 실패했습니다. 다시 시도해 주세요.");
        }
    }

    public void discard(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException | SecurityException exception) {
            // A rollback cannot guarantee removal of an already accepted external/local delivery.
            // No success history is written for this failure.
        }
    }
}
