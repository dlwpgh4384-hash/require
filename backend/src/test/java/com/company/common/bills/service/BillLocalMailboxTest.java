package com.company.common.bills.service;

import com.company.common.bills.mapper.BillRows.Recipient;
import com.company.common.support.service.CodeFailure;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
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

    /** application.yml + 내 PC 파일(config/application-local.yml) + 환경 변수를 실제 순서대로 쌓은 설정. */
    static StandardEnvironment layered(Map<String,Object> env, Map<String,Object> localFile) throws Exception {
        var environment=new StandardEnvironment();
        var sources=environment.getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.addLast(new SystemEnvironmentPropertySource("testEnv",env));
        sources.addLast(new MapPropertySource("localFile",localFile));
        for (var source:new YamlPropertySourceLoader().load("application",new ClassPathResource("application.yml")))
            sources.addLast(source);
        return environment;
    }
    @Test void applicationYmlHoldsOnlyHostAndPortDefaults() throws Exception {
        var environment=layered(Map.of(),Map.of());
        assertThat(environment.getProperty("smtp.host")).isEqualTo("smtp-relay.brevo.com");
        assertThat(environment.getProperty("smtp.port")).isEqualTo("587");
        assertThat(environment.getProperty("smtp.username")).isEmpty();
        assertThat(environment.getProperty("smtp.password")).isEmpty();
    }
    @Test void localFileOldSpringMailKeysOverrideYmlDefaults() throws Exception {
        var environment=layered(Map.of(),Map.of("spring.mail.host","local.example.test","spring.mail.port","2525",
            "spring.mail.username","local-user","spring.mail.password","local-value"));
        assertThat(environment.getProperty("smtp.host")).isEqualTo("local.example.test");
        assertThat(environment.getProperty("smtp.port")).isEqualTo("2525");
        assertThat(environment.getProperty("smtp.username")).isEqualTo("local-user");
        assertThat(environment.getProperty("smtp.password")).isEqualTo("local-value");
    }
    @Test void environmentVariablesWinOverLocalFile() throws Exception {
        var environment=layered(Map.of("SMTP_HOST","env.example.test","SMTP_PORT","465","SMTP_USERNAME","env-user","SMTP_PASSWORD","env-value"),
            Map.of("spring.mail.host","local.example.test","spring.mail.username","local-user","spring.mail.password","local-value"));
        assertThat(environment.getProperty("smtp.host")).isEqualTo("env.example.test");
        assertThat(environment.getProperty("smtp.port")).isEqualTo("465");
        assertThat(environment.getProperty("smtp.username")).isEqualTo("env-user");
        assertThat(environment.getProperty("smtp.password")).isEqualTo("env-value");
    }
    @Test void noPasswordKeepsOwnerOnlyLocalMailboxAndWarnsOnce(CapturedOutput output) throws Exception {
        var sender=mock(JavaMailSender.class);
        var mailbox=new BillLocalMailbox(provider(sender),FROM,layered(Map.of(),Map.of()));
        assertThat(output.getOut()+output.getErr()).containsOnlyOnce("event=mail_local_mailbox_mode");
        var path=mailbox.deliver(one(),"인증번호 전달 시험");
        try {
            assertThat(path.getFileName().toString()).endsWith(".eml");
            if (Files.getFileStore(path).supportsFileAttributeView("posix"))
                assertThat(Files.getPosixFilePermissions(path)).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE);
        } finally { mailbox.discard(path); }
        verifyNoInteractions(sender);
    }
    @Test void sendFailureLogsTypeAndAccountButNoAddressOrBody(CapturedOutput output) {
        var sender=mock(JavaMailSender.class);
        doThrow(new MailSendException("smtp down for recipient@example.test")).when(sender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> new BillLocalMailbox(provider(sender),FROM).deliver(one(),"비밀 본문 123456","인증번호"))
            .isInstanceOf(CodeFailure.class);
        String log=output.getOut()+output.getErr();
        assertThat(log).contains("event=mail_send_failed kind=\"인증번호\" type=MailSendException recipients=[recipient]");
        assertThat(log).doesNotContain("@example.test","123456","smtp down");
    }
    /** F15: 실패 로그에 메일 종류(임시 비밀번호·미수 n단계)를 남기고, 줄바꿈으로 로그를 꾸밀 수 없다. */
    @Test void sendFailureLogsMailKindForEachSender(CapturedOutput output) {
        var sender=mock(JavaMailSender.class);
        doThrow(new MailSendException("down")).when(sender).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> new BillLocalMailbox(provider(sender),FROM).deliver(one(),"임시 비밀번호: Ab1!xyz","임시 비밀번호"))
            .isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> new BillLocalMailbox(provider(sender),FROM).deliver(one(),"미수 2단계 알림","미수 2단계\r\nfake=1"))
            .isInstanceOf(CodeFailure.class);
        String log=output.getOut()+output.getErr();
        assertThat(log).contains("kind=\"임시 비밀번호\" type=MailSendException","kind=\"미수 2단계fake=1\" type=MailSendException");
        assertThat(log).doesNotContain("Ab1!xyz","미수 2단계 알림");
        assertThat(BillLocalMailbox.kind(null)).isEqualTo("기타");
    }
    /** 내 PC 설정 예시는 빈 칸만, 실제 파일은 git에서 뺀다(⑫-1). 저장소 뿌리가 없는 곳에서는 건너뛴다. */
    @Test void localExampleIsBlankAndRealLocalFileIsGitIgnored() throws Exception {
        Path root=Path.of("..").toAbsolutePath().normalize();
        Path example=root.resolve("config/application-local.example.yml");
        Path ignore=root.resolve(".gitignore");
        assumeTrue(Files.isRegularFile(example) && Files.isRegularFile(ignore));
        assertThat(Files.readAllLines(ignore,StandardCharsets.UTF_8)).map(String::strip)
            .contains("config/application-local.yml","backend/config/application-local.yml");
        var loaded=new YamlPropertySourceLoader().load("example",new FileSystemResource(example));
        for (var source:loaded) for (String name:((MapPropertySource)source).getPropertyNames())
            assertThat(String.valueOf(source.getProperty(name)).strip()).as(name).isEmpty();
    }
}
