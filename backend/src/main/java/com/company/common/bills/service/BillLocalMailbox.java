package com.company.common.bills.service;

import com.company.common.bills.mapper.BillRows.Recipient;
import com.company.common.support.service.CodeFailure;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 공통 메일 보내기(미수 알림·임시 비밀번호·인증번호). SMTP 값은 application.yml의 smtp.host·port·username·password
 * 한 곳에서만 읽는다(읽는 순서: 환경 변수 SMTP_* > config/application-local.yml > application.yml 기본값 — yml에 적음).
 * 비밀번호가 있으면 실제 메일을 보내고, 없으면 지금처럼 앱 임시 폴더의 로컬 메일함(billing-mail/*.eml, 주인만 읽기)에 쓴다.
 * 제목·본문·받는 사람은 같다. 보내기에 실패하면 오류를 던져 발송 이력을 남기지 않는다(사용자 결정).
 */
@Component
public class BillLocalMailbox {
    private static final Logger LOG=LoggerFactory.getLogger(BillLocalMailbox.class);
    /** 메일 제목 = 알림 글 첫 줄(예: 「미수 1단계 알림: 프로젝트 10월분 (14일 지남)」), 줄바꿈 없이 200자까지. */
    static String subject(String content) {
        String line=content==null?"":content.strip().split("\\R",2)[0];
        return line.length()>200?line.substring(0,200):line;
    }
    private final ObjectProvider<JavaMailSender> senders;
    private final String from;
    private boolean smtpRequired;

    public BillLocalMailbox(ObjectProvider<JavaMailSender> senders, String from) {
        this.senders=senders;
        this.from=from==null?"":from.strip();
    }

    @Autowired
    public BillLocalMailbox(ObjectProvider<JavaMailSender> senders,
        @Value("${billing.mail.from:}") String from, Environment environment) {
        String password=environment.getProperty("smtp.password", "");
        if (password.isBlank()) {
            this.senders=null;
            this.from=from==null?"":from.strip();
            // 비밀번호가 없으면 개발·시험용 로컬 메일함 모드다(⑫-1). 빈(bean)은 하나라 켜질 때 한 번만 남는다.
            LOG.warn("event=mail_local_mailbox_mode reason=smtp_password_absent "
                + "메일 비밀번호(SMTP_PASSWORD)가 없어 실제 메일을 보내지 않고 서버 임시 폴더 billing-mail에 .eml로 씁니다(개발·시험용).");
            return;
        }
        this.smtpRequired=true;
        String host=environment.getProperty("smtp.host", "").strip();
        String port=environment.getProperty("smtp.port", "").strip();
        if (host.isEmpty() || !port.matches("[0-9]{1,5}")) {
            // 비밀번호는 있는데 주소·포트가 비면 로컬 메일함으로 몰래 돌리지 않고, 보낼 때 실패로 알린다.
            this.senders=null;
            this.from=from==null?"":from.strip();
            LOG.warn("event=mail_smtp_config_incomplete hostPresent={} portValid={}", !host.isEmpty(), port.matches("[0-9]{1,5}"));
            return;
        }
        var sender=new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(Integer.parseInt(port));
        sender.setUsername(environment.getProperty("smtp.username", "").strip());
        sender.setPassword(password);
        // 한글 제목·본문이 깨지지 않게 UTF-8로 보낸다(기본값은 플랫폼 인코딩, 2026-11-27 실제 메일에서 깨짐).
        sender.setDefaultEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        sender.getJavaMailProperties().setProperty("mail.mime.charset", java.nio.charset.StandardCharsets.UTF_8.name());
        sender.getJavaMailProperties().setProperty("mail.smtp.auth", "true");
        sender.getJavaMailProperties().setProperty("mail.smtp.starttls.enable", "true");
        sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "10000");
        sender.getJavaMailProperties().setProperty("mail.smtp.timeout", "10000");
        sender.getJavaMailProperties().setProperty("mail.smtp.writetimeout", "10000");
        this.senders=new org.springframework.beans.factory.support.StaticListableBeanFactory(
            java.util.Map.of("billingMailTransport", sender)).getBeanProvider(JavaMailSender.class);
        this.from=from==null || from.isBlank()?sender.getUsername():from.strip();
    }

    /** 메일을 받을 수 있는 주소인지(비었거나 형식이 틀리면 메일만 빼고 시스템 알림은 보낸다). */
    public static boolean deliverable(Recipient recipient) {
        return recipient!=null && recipient.email()!=null && recipient.email().length()<=100
            && recipient.email().matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+");
    }

    /** 종류를 모르는 메일(시험용). 실제로 보내는 곳은 종류를 넘기는 아래 메서드를 쓴다. */
    public Path deliver(List<Recipient> recipients, String content) {
        return deliver(recipients,content,"기타");
    }

    /**
     * 실제 메일을 보냈으면 null(되돌릴 파일 없음), 로컬 메일함에 썼으면 그 .eml 파일.
     * kind = 메일 종류(인증번호·임시 비밀번호·미수 n단계 등). 실패 로그에 종류로 남긴다(F15).
     */
    public Path deliver(List<Recipient> recipients, String content, String kind) {
        if (recipients==null || recipients.isEmpty())
            throw CodeFailure.invalid("delivery","받는 사람이 없습니다.");
        for (var recipient:recipients) {
            if (!deliverable(recipient))
                throw CodeFailure.invalid("delivery","수신자의 메일 주소를 확인해 주세요.");
        }
        String[] to=recipients.stream().map(Recipient::email).toArray(String[]::new);
        JavaMailSender sender=senders==null?null:senders.getIfAvailable();
        if (smtpRequired && (sender==null || from==null || from.isEmpty())) {
            LOG.warn("event=mail_send_failed kind=\"{}\" type=smtp_not_configured recipients={}", kind(kind), accounts(recipients));
            throw CodeFailure.invalid("delivery","메일을 보내지 못했습니다. 다시 시도해 주세요.");
        }
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
                // ⑫-3: 시각(로그 시각)·종류·받는 계정만 남긴다. 본문·번호·비밀번호·메일 주소·예외 문구는 남기지 않는다.
                LOG.warn("event=mail_send_failed kind=\"{}\" type={} recipients={}", kind(kind), exception.getClass().getSimpleName(), accounts(recipients));
                throw CodeFailure.invalid("delivery","메일을 보내지 못했습니다. 다시 시도해 주세요.");
            }
        }
        return local(String.join(", ",to),content);
    }

    /** 로그용 메일 종류(제어 문자·따옴표를 빼고 30자까지, 비면 「기타」). */
    static String kind(String kind) {
        String value=kind==null?"":kind.replaceAll("[\\p{Cntrl}\"]","").strip();
        if (value.isEmpty()) return "기타";
        return value.length()>30?value.substring(0,30):value;
    }

    /** 로그용 받는 계정 아이디(메일 주소는 쓰지 않는다). */
    private static String accounts(List<Recipient> recipients) {
        return recipients.stream().map(recipient -> recipient.userId()==null?"-":recipient.userId()).toList().toString();
    }

    private static final Set<PosixFilePermission> OWNER_DIRECTORY=PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> OWNER_FILE=PosixFilePermissions.fromString("rw-------");

    private Path local(String to, String content) {
        Path staged=null;
        try {
            Path directory=Path.of(System.getProperty("java.io.tmpdir"),"billing-mail");
            boolean posix=Files.getFileStore(Files.createDirectories(directory)).supportsFileAttributeView("posix");
            if (posix) {
                // 인증번호·임시 비밀번호가 든 파일이라 폴더·파일 모두 주인만 읽게 한다(다른 사람 폴더면 바꾸지 못해도 파일은 600).
                try { Files.setPosixFilePermissions(directory,OWNER_DIRECTORY); } catch (IOException | SecurityException ignored) { }
            }
            staged=Files.createTempFile(directory,"bill-",".part");
            if (posix) Files.setPosixFilePermissions(staged,OWNER_FILE);
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
