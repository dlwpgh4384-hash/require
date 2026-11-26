package com.company.common.mfa.service;

import com.company.common.bills.mapper.BillRows.Recipient;
import com.company.common.bills.service.BillLocalMailbox;
import com.company.common.mfa.dto.MfaContracts.*;
import com.company.common.mfa.mapper.MfaMapper;
import com.company.common.mfa.mapper.MfaRows.Account;
import com.company.common.support.auth.LoginCompletion;
import com.company.common.support.service.BusinessCalendar;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CommonMfaService {
    public static final String PENDING_ATTRIBUTE = CommonMfaService.class.getName() + ".pending";
    public static final String DELIVERY_FAILURE = "인증번호를 보내지 못했습니다. 잠시 후 [다시 보내기]를 눌러 주세요.";
    public static final String LOGIN_LIMIT = "인증번호를 너무 많이 요청했습니다. 처음부터 다시 로그인해 주세요.";
    public static final String CODE_LENGTH = "인증번호 6자리를 넣어 주세요.";
    public static final String EXPIRED = "인증번호 시간이 지났습니다. [다시 보내기]를 눌러 주세요.";
    public static final String INCORRECT = "인증번호가 맞지 않습니다.";
    public static final String RESEND_WAIT = "인증번호를 보낸 뒤 60초가 지나면 다시 보낼 수 있습니다.";
    private final MfaMapper mapper;
    private final BillLocalMailbox mailbox;
    private final BusinessCalendar calendar;
    private final ObjectProvider<LoginCompletion> completions;
    private final TransactionTemplate transaction;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
    private final SecureRandom random = new SecureRandom();
    private final Object[] locks = new Object[256];
    // Only rate-limit timestamps are cached; account/challenge source of truth remains PostgreSQL.
    private final Map<String, ArrayDeque<Instant>> deliveries = new LinkedHashMap<>();

    public CommonMfaService(MfaMapper mapper, BillLocalMailbox mailbox, BusinessCalendar calendar,
        ObjectProvider<LoginCompletion> completions, PlatformTransactionManager transactions) {
        this.mapper = mapper;
        this.mailbox = mailbox;
        this.calendar = calendar;
        this.completions = completions;
        this.transaction = new TransactionTemplate(transactions);
        for (int index = 0; index < locks.length; index++) locks[index] = new Object();
    }

    private static final class Pending {
        final String userId;
        String hash;
        Instant availableAt;
        int resends;
        int mistakes;
        boolean deliveryFailed;
        Pending(String userId, String hash, Instant availableAt) {
            this.userId = userId;
            this.hash = hash;
            this.availableAt = availableAt;
        }
    }
    private static final class Outcome<T> {
        private final T value;
        private final MfaFailure failure;
        Outcome(T value, MfaFailure failure) { this.value = value; this.failure = failure; }
        T result() { if (failure != null) throw failure; return value; }
    }
    private <T> T serialized(String userId, Supplier<T> operation) {
        synchronized (locks[(userId.hashCode() & Integer.MAX_VALUE) % locks.length]) {
            return operation.get();
        }
    }
    private Pending pending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(PENDING_ATTRIBUTE) instanceof Pending value))
            throw MfaFailure.unauthorized();
        return value;
    }
    private Account account(Pending pending) {
        var row = mapper.lockAccount(pending.userId);
        if (row == null || !row.administrator || !"USE".equals(row.status)
            || !Objects.equals(row.codeHash, pending.hash)) throw MfaFailure.unauthorized();
        return row;
    }

    /** Internal login hook: call only AFTER the password has been verified server-side.
     * Returns null for non-ADMIN; never establishes a business session. */
    public Started startAfterPasswordVerified(HttpServletRequest request, String userId) {
        if (userId == null || userId.isBlank() || userId.length() > 30) throw MfaFailure.unauthorized();
        return serialized(userId, () -> transaction.execute(status -> {
            Account row = mapper.lockAccount(userId);
            if (row == null || !"USE".equals(row.status)) throw MfaFailure.unauthorized();
            if (!row.administrator) {
                mapper.disable(userId);
                request.getSession().removeAttribute(PENDING_ATTRIBUTE);
                return null;
            }
            var pending = new Pending(userId, row.codeHash, calendar.now());
            Started started;
            try {
                started = new Started(send(row, pending, false), null);
            } catch (MfaFailure failure) {
                pending.deliveryFailed = true;
                started = new Started(null, failure.getMessage());
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    var session = request.getSession();
                    if (!pending.deliveryFailed || session.getAttribute(PENDING_ATTRIBUTE) == null)
                        session.setAttribute(PENDING_ATTRIBUTE, pending);
                }
            });
            return started;
        }));
    }

    public Response<Delivery> postCommonMfaResend(HttpServletRequest request) {
        Pending pending = pending(request);
        return serialized(pending.userId, () -> {
            synchronized (pending) {
                Outcome<Response<Delivery>> outcome = transaction.execute(status -> {
                    Account row = account(pending);
                    Instant now = calendar.now();
                    if (pending.resends >= 5) {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override public void afterCommit() {
                                request.getSession(false).removeAttribute(PENDING_ATTRIBUTE);
                            }
                        });
                        return new Outcome<>(null, new MfaFailure(400, "code", LOGIN_LIMIT, true));
                    }
                    if (now.isBefore(pending.availableAt)) throw MfaFailure.invalid(RESEND_WAIT);
                    return new Outcome<>(Response.of(send(row, pending, true)), null);
                });
                return outcome.result();
            }
        });
    }

    private Delivery send(Account row, Pending pending, boolean resend) {
        Instant now = calendar.now();
        ArrayDeque<Instant> timestamps = timestamps(row.userId, now);
        synchronized (deliveries) {
            if (timestamps.size() >= 10) {
                long minutes = Math.max(1, (java.time.Duration.between(now, timestamps.getFirst().plusSeconds(3600)).getSeconds() + 59) / 60);
                throw MfaFailure.invalid("인증번호 요청이 너무 많습니다. " + minutes + "분 뒤 다시 로그인해 주세요.");
            }
        }
        String code;
        do {
            code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        } while (row.codeHash != null && encoder.matches(code, row.codeHash));
        String hash = encoder.encode(code);
        Instant expires = now.plusSeconds(300);
        Path path;
        try {
            path = mailbox.deliver(List.of(new Recipient(row.userId, row.userId, row.email)),
                "관리자 인증번호\n인증번호: " + code + "\n5분 안에 입력해 주세요.");
        } catch (RuntimeException failure) {
            throw MfaFailure.invalid(DELIVERY_FAILURE);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int completionStatus) {
                if (completionStatus != STATUS_COMMITTED) mailbox.discard(path);
            }
            @Override public void afterCommit() {
                pending.hash = hash;
                pending.availableAt = now.plusSeconds(60);
                pending.deliveryFailed = false;
                if (resend) pending.resends++;
                synchronized (deliveries) {
                    deliveries.computeIfAbsent(row.userId, key -> new ArrayDeque<>()).addLast(now);
                }
            }
        });
        mapper.challenge(row.userId, hash, expires);
        return new Delivery(mask(row.email), expires, now.plusSeconds(60));
    }

    private ArrayDeque<Instant> timestamps(String userId, Instant now) {
        synchronized (deliveries) {
            Instant cutoff = now.minusSeconds(3600);
            deliveries.values().forEach(values -> {
                while (!values.isEmpty() && !values.getFirst().isAfter(cutoff)) values.removeFirst();
            });
            deliveries.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            if (!deliveries.containsKey(userId) && deliveries.size() >= 10_000)
                throw MfaFailure.invalid("인증번호 요청이 너무 많습니다. 잠시 후 다시 로그인해 주세요.");
            return deliveries.computeIfAbsent(userId, key -> new ArrayDeque<>());
        }
    }

    public Response<Login> postCommonMfaVerify(HttpServletRequest request, String code) {
        Pending pending = pending(request);
        if (code == null || !code.matches("[0-9]{6}")) throw MfaFailure.invalid(CODE_LENGTH);
        return serialized(pending.userId, () -> {
            synchronized (pending) {
                Outcome<Response<Login>> outcome = transaction.execute(status -> {
                    Account row = account(pending);
                    if (pending.deliveryFailed) throw MfaFailure.invalid(DELIVERY_FAILURE);
                    Instant now = calendar.now();
                    if (row.expiresAt == null || !now.isBefore(row.expiresAt)) throw MfaFailure.invalid(EXPIRED);
                    if (!encoder.matches(code, row.codeHash)) {
                        mapper.wrongCode(row.userId);
                        int mistakes = pending.mistakes + 1;
                        boolean restart = mistakes >= 5;
                        mapper.failureAccess(row.userId, restart ? "인증번호 5회 오류"
                            : "인증번호 오류 (" + mistakes + "/5)", request.getRemoteAddr(), now);
                        if (restart) mapper.clear(row.userId);
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override public void afterCommit() {
                                pending.mistakes = mistakes;
                                if (restart) request.getSession(false).removeAttribute(PENDING_ATTRIBUTE);
                            }
                        });
                        // Return the failure and throw only AFTER commit, preserving MFA_ERR_CNT.
                        return new Outcome<>(null, new MfaFailure(400, "code", restart
                            ? "인증번호를 5번 틀렸습니다. 처음부터 다시 로그인해 주세요."
                            : INCORRECT + " (" + mistakes + "/5)", restart));
                    }
                    LoginCompletion completion = completions.getIfAvailable();
                    if (completion == null) throw MfaFailure.unauthorized();
                    boolean temporary = "Y".equals(row.temporary);
                    var prepared = completion.prepare(row.userId, temporary);
                    mapper.complete(row.userId, now);
                    mapper.loginAccess(row.userId, temporary, request.getRemoteAddr(), now);
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCommit() {
                            request.getSession(false).removeAttribute(PENDING_ATTRIBUTE);
                            request.changeSessionId();
                            completion.establish(request, row.userId, temporary);
                        }
                    });
                    return new Outcome<>(new Response<>(true, prepared.meta(),
                        new Login(temporary ? "UI-40" : prepared.nextScreenId())), null);
                });
                return outcome.result();
            }
        });
    }

    static String mask(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at < 1) return "***";
        return email.substring(0, 1) + "***" + email.substring(at);
    }
}
