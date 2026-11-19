package com.company.common.bills.service;

import com.company.common.bills.mapper.BillRows.Recipient;
import com.company.common.support.service.CodeFailure;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 미수 알림 메일. 운영자가 SMTP(환경 변수 SPRING_MAIL_HOST 등)와 보내는 주소(BILLING_MAIL_FROM)를 넣으면 실제 메일을
 * 보내고, 없으면 지금처럼 앱 임시 폴더의 로컬 메일함(billing-mail/*.eml)에 쓴다. 제목·본문·받는 사람은 같다.
 * 보내기에 실패하면 오류를 던져 발송 이력을 남기지 않는다(사용자 결정).
 */
@Component
public class BillLocalMailbox {
    /** 메일 제목 = 알림 글 첫 줄(예: 「미수 1단계 알림: 프로젝트 10월분 (14일 지남)」), 줄바꿈 없이 200자까지. */
    static String subject(String content) {
        String line=content==null?"":content.strip().split("\\R",2)[0];
        return line.length()>200?line.substring(0,200):line;
    }
    private final ObjectProvider<JavaMailSender> senders;
    private final String from;

    @Autowired
    public BillLocalMailbox(ObjectProvider<JavaMailSender> senders, @Value("${billing.mail.from:}") String from) {
        this.senders=senders;
        this.from=from==null?"":from.strip();
    }

    /** 메일을 받을 수 있는 주소인지(비었거나 형식이 틀리면 메일만 빼고 시스템 알림은 보낸다). */
    public static boolean deliverable(Recipient recipient) {
        return recipient!=null && recipient.email()!=null && recipient.email().length()<=100
            && recipient.email().matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+");
    }

    /** 실제 메일을 보냈으면 null(되돌릴 파일 없음), 로컬 메일함에 썼으면 그 .eml 파일. */
    public Path deliver(List<Recipient> recipients, String content) {
        if (recipients==null || recipients.isEmpty())
            throw CodeFailure.invalid("delivery","받는 사람이 없습니다.");
        for (var recipient:recipients) {
            if (!deliverable(recipient))
                throw CodeFailure.invalid("delivery","수신자의 메일 주소를 확인해 주세요.");
        }
        String[] to=recipients.stream().map(Recipient::email).toArray(String[]::new);
        JavaMailSender sender=senders==null?null:senders.getIfAvailable();
        if (sender!=null && !from.isEmpty()) {
            var message=new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject(content));
            message.setText(content);
            try {
                sender.send(message);
                return null;
            } catch (MailException exception) {
                throw CodeFailure.invalid("delivery","메일을 보내지 못했습니다. 다시 시도해 주세요.");
            }
        }
        return local(String.join(", ",to),content);
    }

    private Path local(String to, String content) {
        Path staged=null;
        try {
            Path directory=Path.of(System.getProperty("java.io.tmpdir"),"billing-mail");
            Files.createDirectories(directory);
            staged=Files.createTempFile(directory,"bill-",".part");
            String mime="X-Billing-Delivery: local-mailbox\r\nTo: "+to
                +"\r\nSubject: =?UTF-8?B?"+Base64.getEncoder().encodeToString(subject(content).getBytes(StandardCharsets.UTF_8))
                +"?=\r\nMIME-Version: 1.0\r\n"
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
        if (path==null) return;
        try { Files.deleteIfExists(path); }
        catch (IOException | SecurityException exception) {
            // A rollback cannot guarantee removal of an already accepted external/local delivery.
            // No success history is written for this failure.
        }
    }
}
