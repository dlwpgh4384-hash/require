package com.company.common.mfa.service;

import com.company.common.bills.service.BillLocalMailbox;
import com.company.common.mfa.mapper.MfaMapper;
import com.company.common.mfa.mapper.MfaRows.Account;
import com.company.common.support.auth.LoginCompletion;
import com.company.common.support.service.BusinessCalendar;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommonMfaServiceTest {
    MfaMapper mapper;
    BillLocalMailbox mailbox;
    BusinessCalendar calendar;
    LoginCompletion completion;
    CommonMfaService service;
    MockHttpServletRequest request;
    Account account;
    Instant now;
    TestTransactions transactions;
    List<Path> mail = new ArrayList<>();

    @SuppressWarnings("unchecked")
    @BeforeEach void setup() {
        mapper = mock(MfaMapper.class);
        calendar = mock(BusinessCalendar.class);
        now = Instant.parse("2026-01-02T00:00:00Z");
        when(calendar.now()).thenAnswer(call -> now);
        ObjectProvider<org.springframework.mail.javamail.JavaMailSender> senders = mock(ObjectProvider.class);
        mailbox = spy(new BillLocalMailbox(senders, ""));
        doAnswer(call -> {
            Path path = (Path) call.callRealMethod();
            mail.add(path);
            return path;
        }).when(mailbox).deliver(anyList(), anyString());
        completion = mock(LoginCompletion.class);
        when(completion.prepare(anyString(), anyBoolean())).thenReturn(
            new LoginCompletion.Prepared("UI-34", Map.of("modifiedAt", now.toString(), "passwordChangeDays", 90)));
        ObjectProvider<LoginCompletion> completions = mock(ObjectProvider.class);
        when(completions.getIfAvailable()).thenReturn(completion);
        transactions = new TestTransactions();
        service = new CommonMfaService(mapper, mailbox, calendar, completions, transactions);
        request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        account = new Account();
        account.userId = "mfa-admin";
        account.email = "mfa-admin@example.invalid";
        account.status = "USE";
        account.temporary = "N";
        account.administrator = true;
        when(mapper.lockAccount(account.userId)).thenReturn(account);
        doAnswer(call -> {
            account.codeHash = call.getArgument(1);
            account.expiresAt = call.getArgument(2);
            return null;
        }).when(mapper).challenge(anyString(), anyString(), nullable(Instant.class));
        doAnswer(call -> { account.errors++; return null; }).when(mapper).wrongCode(anyString());
        doAnswer(call -> { account.codeHash = null; account.expiresAt = null; return null; }).when(mapper).clear(anyString());
    }
    @AfterEach void cleanup() { mail.forEach(mailbox::discard); }
    void start() { service.startAfterPasswordVerified(request, account.userId); }
    void advance(long seconds) { now = now.plusSeconds(seconds); }
    String code() throws Exception {
        String mime = Files.readString(mail.get(mail.size() - 1), StandardCharsets.UTF_8);
        String body = new String(Base64.getMimeDecoder().decode(mime.substring(mime.indexOf("\r\n\r\n") + 4)), StandardCharsets.UTF_8);
        var match = java.util.regex.Pattern.compile("인증번호: ([0-9]{6})").matcher(body);
        if (!match.find()) throw new AssertionError("인증번호 메일 본문 형식이 다릅니다.");
        return match.group(1);
    }
    String wrong(String correct) { return correct.equals("000000") ? "000001" : "000000"; }

    @Test void initialDeliveryIsRealMimeAndOnlyBcryptIsStored() throws Exception {
        var result = service.startAfterPasswordVerified(request, account.userId);
        assertThat(mail).hasSize(1);
        assertThat(result.delivery().maskedEmail()).isEqualTo("m***@example.invalid");
        assertThat(result.delivery().expiresAt()).isEqualTo(now.plusSeconds(300));
        assertThat(account.codeHash).startsWith("$2a$10$");
        assertThat(new BCryptPasswordEncoder().matches(code(), account.codeHash)).isTrue();
        verifyNoInteractions(completion);
        verify(mapper, never()).complete(anyString(), any());
    }
    @Test void nonAdministratorDoesNotReceiveChallenge() {
        account.administrator = false;
        assertThat(service.startAfterPasswordVerified(request, account.userId)).isNull();
        verify(mapper).disable(account.userId);
        assertThat(mail).isEmpty();
        assertThatThrownBy(() -> service.postCommonMfaResend(request)).isInstanceOf(MfaFailure.class);
    }
    @Test void resendWithoutPendingDoesNotReadOrWriteDatabaseOrMail() {
        assertThatThrownBy(() -> service.postCommonMfaResend(request)).isInstanceOfSatisfying(MfaFailure.class,
            error -> assertThat(error.status()).isEqualTo(401));
        verifyNoInteractions(mapper, mailbox, completion);
    }
    @Test void verifyWithoutPendingCannotEstablishSession() {
        assertThatThrownBy(() -> service.postCommonMfaVerify(request, "123456")).isInstanceOfSatisfying(MfaFailure.class,
            error -> assertThat(error.status()).isEqualTo(401));
        assertThat(request.getSession(false)).isNull();
        verifyNoInteractions(mapper, completion);
    }
    @Test void resendBeforeSixtySecondsDoesNotChangeHashOrSend() {
        start(); String hash = account.codeHash; advance(59);
        assertThatThrownBy(() -> service.postCommonMfaResend(request)).hasMessage(CommonMfaService.RESEND_WAIT);
        assertThat(account.codeHash).isEqualTo(hash);
        assertThat(mail).hasSize(1);
    }
    @Test void resendAtSixtySecondsReplacesHashAndOnlyNewCodeCompletes() throws Exception {
        start(); String oldCode = code(); String oldHash = account.codeHash; advance(60);
        var delivery = service.postCommonMfaResend(request);
        assertThat(mail).hasSize(2);
        assertThat(account.codeHash).isNotEqualTo(oldHash);
        assertThat(delivery.data().expiresAt()).isEqualTo(now.plusSeconds(300));
        String newCode = code();
        assertThatThrownBy(() -> service.postCommonMfaVerify(request, oldCode)).hasMessage("인증번호가 맞지 않습니다. (1/5)");
        assertThat(service.postCommonMfaVerify(request, newCode).data().nextScreenId()).isEqualTo("UI-34");
        verify(mapper).complete(account.userId, now);
        verify(mapper).loginAccess(account.userId, false, "127.0.0.1", now);
        verify(completion).establish(request, account.userId, false);
    }
    @Test void sixthResendInOneLoginIsRejectedWithoutDelivery() {
        start();
        for (int i = 0; i < 5; i++) { advance(60); service.postCommonMfaResend(request); }
        String hash = account.codeHash; advance(60);
        assertThatThrownBy(() -> service.postCommonMfaResend(request)).hasMessage(CommonMfaService.LOGIN_LIMIT);
        assertThat(mail).hasSize(6);
        assertThat(account.codeHash).isEqualTo(hash);
        assertThat(request.getSession(false).getAttribute(CommonMfaService.PENDING_ATTRIBUTE)).isNull();
        assertThatThrownBy(() -> service.postCommonMfaVerify(request, "123456"))
            .isInstanceOfSatisfying(MfaFailure.class, error -> assertThat(error.status()).isEqualTo(401));
        verifyNoInteractions(completion);
    }
    @Test void eleventhDeliveryInRollingHourIsRejectedAcrossLogins() {
        for (int i = 0; i < 10; i++) {
            request = new MockHttpServletRequest();
            assertThat(service.startAfterPasswordVerified(request, account.userId).deliveryError()).isNull();
            advance(60);
        }
        request = new MockHttpServletRequest();
        var result = service.startAfterPasswordVerified(request, account.userId);
        assertThat(result.deliveryError()).isEqualTo("인증번호 요청이 너무 많습니다. 50분 뒤 다시 로그인해 주세요.");
        assertThat(mail).hasSize(10);
    }
    @Test void rollingHourAllowsDeliveryAtExactCutoff() {
        for (int i = 0; i < 10; i++) { request = new MockHttpServletRequest(); start(); }
        advance(3600);
        request = new MockHttpServletRequest();
        assertThat(service.startAfterPasswordVerified(request, account.userId).deliveryError()).isNull();
        assertThat(mail).hasSize(11);
    }
    @Test void fiveDigitValidationDoesNotIncrementMistakes() {
        start();
        assertThatThrownBy(() -> service.postCommonMfaVerify(request, "12345")).hasMessage(CommonMfaService.CODE_LENGTH);
        verify(mapper, never()).wrongCode(anyString());
    }
    @Test void codeExpiresAtExactlyFiveMinutes() throws Exception {
        start(); String code = code(); advance(300);
        assertThatThrownBy(() -> service.postCommonMfaVerify(request, code)).hasMessage(CommonMfaService.EXPIRED);
        verifyNoInteractions(completion);
    }
    @Test void fifthWrongCodeCommitsCounterAndReturnsToLoginWithoutAccountLock() throws Exception {
        start(); String wrong = wrong(code());
        for (int i = 1; i < 5; i++) {
            assertThatThrownBy(() -> service.postCommonMfaVerify(request, wrong))
                .hasMessage("인증번호가 맞지 않습니다. (" + i + "/5)");
        }
        assertThatThrownBy(() -> service.postCommonMfaVerify(request, wrong)).isInstanceOfSatisfying(MfaFailure.class,
            error -> { assertThat(error.restart()).isTrue(); assertThat(error.getMessage()).contains("5번 틀렸습니다"); });
        assertThat(account.errors).isEqualTo(5);
        assertThat(account.status).isEqualTo("USE");
        assertThat(account.codeHash).isNull();
        assertThatThrownBy(() -> service.postCommonMfaResend(request)).isInstanceOf(MfaFailure.class);
        assertThat(transactions.commits).isEqualTo(6);
        verifyNoInteractions(completion);
    }
    @Test void laterDeviceInvalidatesPreviousSessionEvenWithLatestCode() throws Exception {
        start(); MockHttpServletRequest old = request;
        request = new MockHttpServletRequest(); start(); String latest = code();
        assertThatThrownBy(() -> service.postCommonMfaVerify(old, latest)).isInstanceOfSatisfying(MfaFailure.class,
            error -> assertThat(error.status()).isEqualTo(401));
        verifyNoInteractions(completion);
        service.postCommonMfaVerify(request, latest);
        verify(completion).establish(request, account.userId, false);
    }
    @Test void temporaryLoginRecordsLoginOnceAndEstablishesRestrictedContextAfterRotation() throws Exception {
        account.temporary = "Y"; start(); String oldId = request.getSession().getId();
        var response = service.postCommonMfaVerify(request, code());
        assertThat(response.data().nextScreenId()).isEqualTo("UI-40");
        assertThat(response.meta()).containsKey("modifiedAt");
        assertThat(request.getSession().getId()).isNotEqualTo(oldId);
        verify(mapper).complete(account.userId, now);
        verify(mapper).loginAccess(account.userId, true, "127.0.0.1", now);
        verify(completion).prepare(account.userId, true);
        verify(completion).establish(request, account.userId, true);
        assertThatThrownBy(() -> service.postCommonMfaVerify(request, "123456")).isInstanceOf(MfaFailure.class);
        verifyNoMoreInteractions(completion);
    }
    @Test void stoppedAccountCannotFinishOrResend() throws Exception {
        start(); String code = code(); account.status = "STOP"; advance(60);
        assertThatThrownBy(() -> service.postCommonMfaVerify(request, code)).isInstanceOf(MfaFailure.class);
        assertThatThrownBy(() -> service.postCommonMfaResend(request)).isInstanceOf(MfaFailure.class);
        assertThat(mail).hasSize(1);
        verifyNoInteractions(completion);
    }
    @Test void initialMailFailureLeavesOnlyRetryAndNoLogin() {
        doThrow(new IllegalStateException("transport unavailable")).when(mailbox).deliver(anyList(), anyString());
        var result = service.startAfterPasswordVerified(request, account.userId);
        assertThat(result.deliveryError()).isEqualTo(CommonMfaService.DELIVERY_FAILURE);
        assertThat(account.expiresAt).isNull();
        verify(mapper, never()).challenge(anyString(), anyString(), nullable(Instant.class));
        assertThatThrownBy(() -> service.postCommonMfaVerify(request, "123456")).hasMessage(CommonMfaService.DELIVERY_FAILURE);
        verifyNoInteractions(completion);
    }
    @Test void resendMailFailureDoesNotReplaceHashOrAuthenticate() throws Exception {
        start(); String previous = code(); advance(60); String hash = account.codeHash;
        Instant expires = account.expiresAt;
        Object pending = request.getSession(false).getAttribute(CommonMfaService.PENDING_ATTRIBUTE);
        clearInvocations(mapper, completion);
        doThrow(new IllegalStateException("transport unavailable")).when(mailbox).deliver(anyList(), anyString());
        assertThatThrownBy(() -> service.postCommonMfaResend(request)).hasMessage(CommonMfaService.DELIVERY_FAILURE);
        assertThat(account.codeHash).isEqualTo(hash);
        assertThat(account.expiresAt).isEqualTo(expires);
        assertThat(request.getSession(false).getAttribute(CommonMfaService.PENDING_ATTRIBUTE)).isSameAs(pending);
        verify(mapper, never()).challenge(anyString(), anyString(), nullable(Instant.class));
        verifyNoInteractions(completion);
        service.postCommonMfaVerify(request, previous);
        verify(completion).establish(request, account.userId, false);
    }
    @Test void failedResendCanBeRetriedWithoutChangingCooldownOrConsumingAllowance() {
        start(); advance(60);
        doThrow(new IllegalStateException("transport unavailable"))
            .doAnswer(call -> {
                Path path = (Path) call.callRealMethod();
                mail.add(path);
                return path;
            }).when(mailbox).deliver(anyList(), anyString());
        assertThatThrownBy(() -> service.postCommonMfaResend(request)).hasMessage(CommonMfaService.DELIVERY_FAILURE);
        assertThatCode(() -> service.postCommonMfaResend(request)).doesNotThrowAnyException();
        for (int attempt = 0; attempt < 4; attempt++) {
            advance(60);
            service.postCommonMfaResend(request);
        }
        advance(60);
        assertThatThrownBy(() -> service.postCommonMfaResend(request)).hasMessage(CommonMfaService.LOGIN_LIMIT);
    }
    @Test void failedNewDeviceDeliveryPreservesPreviousChallengeAndPendingSession() throws Exception {
        start(); String previous = code(); String hash = account.codeHash;
        var previousRequest = request;
        Object pending = request.getSession(false).getAttribute(CommonMfaService.PENDING_ATTRIBUTE);
        Instant expires = account.expiresAt;
        request = new MockHttpServletRequest();
        clearInvocations(mapper);
        doThrow(new IllegalStateException("transport unavailable")).when(mailbox).deliver(anyList(), anyString());
        assertThat(service.startAfterPasswordVerified(request, account.userId).deliveryError())
            .isEqualTo(CommonMfaService.DELIVERY_FAILURE);
        assertThat(account.codeHash).isEqualTo(hash);
        assertThat(account.expiresAt).isEqualTo(expires);
        assertThat(previousRequest.getSession(false).getAttribute(CommonMfaService.PENDING_ATTRIBUTE)).isSameAs(pending);
        verify(mapper, never()).challenge(anyString(), anyString(), nullable(Instant.class));
        assertThatThrownBy(() -> service.postCommonMfaVerify(request, previous)).hasMessage(CommonMfaService.DELIVERY_FAILURE);
        verifyNoInteractions(completion);
        service.postCommonMfaVerify(previousRequest, previous);
        verify(completion).establish(previousRequest, account.userId, false);
    }
    @Test void failedInitialDeliveryCanBeRetriedAndOnlyDeliveredCodeCanComplete() throws Exception {
        doThrow(new IllegalStateException("transport unavailable"))
            .doAnswer(call -> {
                Path path = (Path) call.callRealMethod();
                mail.add(path);
                return path;
            }).when(mailbox).deliver(anyList(), anyString());
        assertThat(service.startAfterPasswordVerified(request, account.userId).deliveryError())
            .isEqualTo(CommonMfaService.DELIVERY_FAILURE);
        assertThat(account.codeHash).isNull();
        service.postCommonMfaResend(request);
        assertThat(new BCryptPasswordEncoder().matches(code(), account.codeHash)).isTrue();
        service.postCommonMfaVerify(request, code());
        verify(completion).establish(request, account.userId, false);
    }
    @Test void failedWrongCodeCommitDoesNotConsumePendingMistake() throws Exception {
        start(); String wrong = wrong(code());
        transactions.failCommit = true;
        assertThatThrownBy(() -> service.postCommonMfaVerify(request, wrong))
            .isInstanceOf(TransactionSystemException.class);
        transactions.failCommit = false;
        assertThatThrownBy(() -> service.postCommonMfaVerify(request, wrong))
            .hasMessage("인증번호가 맞지 않습니다. (1/5)");
        verifyNoInteractions(completion);
    }
    @Test void commitFailureCompensatesLocalDelivery() {
        transactions.failCommit = true;
        assertThatThrownBy(this::start).isInstanceOf(TransactionSystemException.class);
        assertThat(mail).hasSize(1);
        assertThat(Files.exists(mail.get(0))).isFalse();
        assertThat(request.getSession(false)).isNull();
        verifyNoInteractions(completion);
    }

    static class TestTransactions extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;
        boolean failCommit;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) {
            if (failCommit) throw new TransactionSystemException("test commit failure");
            commits++;
        }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
