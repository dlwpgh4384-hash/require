package com.company.common.commonops.service;

import com.company.common.commonops.dto.AccountAccessContracts.*;
import com.company.common.commonops.mapper.AccountAccessMapper;
import com.company.common.mfa.service.CommonMfaService;
import com.company.common.support.auth.AccountSessions;

import com.company.common.users.service.TemporaryUserPassword;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Service
public class AccountAccessService {
    public static final String INVALID = "아이디 또는 비밀번호가 맞지 않습니다.";
    public static final String LOCKED = "비밀번호를 5번 틀려 잠긴 계정입니다. 관리자에게 잠금 해제를 요청하세요.";
    public static final String STOPPED = "사용 중지된 계정입니다.";
    public static final String UNUSABLE = "사용할 수 없는 계정입니다. 관리자에게 문의해 주세요.";
    public static final String FIND_MESSAGE = "입력한 정보가 맞으면 이메일로 임시 비밀번호를 보냈습니다.";
    private static final Logger log = LoggerFactory.getLogger(AccountAccessService.class);
    private final AccountAccessMapper mapper;

    private final TemporaryUserPassword passwords;
    private final AccountSessions sessions;
    private final AccountLoginCompletion completion;
    private final CommonMfaService mfa;

    private final TransactionTemplate transaction;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
    private final String absentHash = encoder.encode(java.util.UUID.randomUUID().toString());
    // 비밀번호 찾기는 응답 뒤에 따로 처리해 계정이 맞는지에 따라 응답 시간이 달라지지 않게 한다.
    private volatile ExecutorService ownedFindWork = findWorker();
    private volatile Executor findWork = ownedFindWork;

    public AccountAccessService(AccountAccessMapper mapper, TemporaryUserPassword passwords,
        AccountSessions sessions, AccountLoginCompletion completion, CommonMfaService mfa,
        PlatformTransactionManager transactions) {
        this.mapper = mapper;

        this.passwords = passwords;
        this.sessions = sessions;
        this.completion = completion;
        this.mfa = mfa;

        this.transaction = new TransactionTemplate(transactions);
    }
    private static ExecutorService findWorker() {
        // 한 줄로만 처리하고 대기 100건을 넘으면 받지 않는다(공개 API 남용 대비).
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(100), work -> {
            var thread = new Thread(work, "password-find");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
    }
    /** 시험에서 같은 스레드로 돌리는 등 처리기를 바꿀 때만 쓴다(이름 accountFindExecutor 빈). */
    @Autowired(required = false)
    void findExecutor(@Qualifier("accountFindExecutor") Executor executor) {
        if (ownedFindWork != null) ownedFindWork.shutdown();
        ownedFindWork = null;
        findWork = executor;
    }
    @PreDestroy void stopFindWork() { if (ownedFindWork != null) ownedFindWork.shutdownNow(); }
    private record Attempt(Response<LoginResult> response, AccountAccessFailure failure) {}
    private static void runAfterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
    private void required(String value, String field, String message) {
        if (value == null || value.isBlank()) throw AccountAccessFailure.invalid(field, message);
    }
    private String id(String value) {
        required(value, "userId", "아이디를 넣어 주세요.");
        if (value.length() > 30 || !value.matches("[A-Za-z0-9._-]+"))
            throw AccountAccessFailure.invalid("userId", "아이디를 확인해 주세요.");
        return value;
    }
    public Response<LoginResult> login(LoginInput input, HttpServletRequest request) {
        String id = id(input.userId());
        required(input.password(), "password", "비밀번호를 입력해 주세요.");
        if (input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw AccountAccessFailure.invalid("password", "비밀번호를 확인해 주세요.");
        Attempt result = transaction.execute(status -> {
            // 잠금은 DB 행 잠금(FOR UPDATE) 하나뿐이다. 인증번호 확인·다시 보내기도 같은 행 잠금만 쓴다.
            var row = mapper.lockAccount(id);
            // 비밀번호를 늘 먼저 확인한다. 잠김·중지 문구는 비밀번호가 맞을 때만 보여 계정 상태를 드러내지 않는다.
            boolean matched = encoder.matches(input.password(), row == null ? absentHash : row.hash);
            String error = null;
            String audit = null;
            if (row == null) {
                error = INVALID;
                audit = "비밀번호 오류";
            } else if ("LOCK".equals(row.status)) {
                error = matched ? LOCKED : INVALID;
                audit = "계정 잠김";
            } else if ("STOP".equals(row.status)) {
                error = matched ? STOPPED : INVALID;
                audit = "사용 중지 계정";
            } else if (!matched) {
                int threshold = completion.standard("LOCK_FAIL_CNT");
                mapper.failed(id, threshold);
                row = mapper.account(id);
                boolean locked = "LOCK".equals(row.status);
                error = locked ? LOCKED : INVALID;
                audit = locked ? "계정 잠김" : "비밀번호 오류 (" + row.failures + "/" + threshold + ")";
                if (locked) runAfterCommit(() -> sessions.endOthers(id, null, AccountSessions.ACCOUNT_ENDED));
            }
            if (error != null) {
                mapper.access(id, "LOGIN_FAIL", audit, request.getRemoteAddr(), completion.now());
                return new Attempt(null, new AccountAccessFailure(401, "password", error));
            }
            // Discard a prior authenticated device context when starting another login on this browser.
            if (sessions.current(request.getSession(false)) != null)
                sessions.end(request.getSession(false), "다시 로그인");
            request.getSession();
            if (mapper.roles(id).contains("ADMIN")) {
                // 비밀번호 단계에서도 세션 번호를 바꾼다(인증번호 대기 정보는 새 번호 세션에 남는다).
                runAfterCommit(request::changeSessionId);
                var started = mfa.startAfterPasswordVerified(request, id);
                var meta = new LinkedHashMap<String, Object>();
                if (started != null) {
                    meta.put("mfaDelivery", started.delivery());
                    meta.put("deliveryError", started.deliveryError());
                }
                return new Attempt(new Response<>(true, meta, new LoginResult("UI-39", row.name)), null);
            }
            boolean temporary = "Y".equals(row.temporary);
            mapper.complete(id, completion.now());
            mapper.access(id, "LOGIN", temporary ? "임시 비밀번호 로그인" : "로그인",
                request.getRemoteAddr(), completion.now());
            var prepared = completion.prepare(id, temporary);
            runAfterCommit(() -> {
                request.changeSessionId();
                completion.establish(request, id, temporary);
            });
            return new Attempt(new Response<>(true, prepared.meta(), new LoginResult(prepared.nextScreenId(), row.name)), null);
        });
        if (result.failure != null) throw result.failure;
        return result.response;
    }
    public AccountSessions.Binding authenticated(HttpServletRequest request) {
        var binding = sessions.current(request.getSession(false));
        if (binding == null) throw AccountAccessFailure.unauthorized();
        var row = mapper.account(binding.userId);
        if (row == null || !"USE".equals(row.status)) {
            // 중지·잠김은 그 계정 세션을 모두 끝낸다.
            sessions.endOthers(binding.userId, null, AccountSessions.ACCOUNT_ENDED);
            throw row == null ? AccountAccessFailure.unauthorized() : new AccountAccessFailure(401, "session", UNUSABLE);
        }
        if (!binding.accepts(row.hash)) {
            // 비밀번호가 바뀐 뒤 옛 자격으로 온 요청은 그 요청의 세션만 끝낸다.
            sessions.end(request.getSession(false), AccountSessions.ACCOUNT_ENDED);
            throw AccountAccessFailure.unauthorized();
        }
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(binding.userId, null,
                mapper.roles(binding.userId).stream().map(role -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)).toList()));
        return binding;
    }
    public Response<MyInfo> myInfo(HttpServletRequest request) {
        var binding = authenticated(request);
        if (binding.passwordChangeRequired) throw new AccountAccessFailure(403, "password", "비밀번호를 먼저 변경해 주세요.");
        var row = mapper.account(binding.userId);
        return new Response<>(true, completion.prepare(row.userId, false).meta(),
            new MyInfo(row.userId, row.name, mapper.roles(row.userId), mapper.roleNames(row.userId), completion.parts(row.userId)));
    }
    public void logout(HttpServletRequest request) {
        authenticated(request);
        sessions.end(request.getSession(false), "직접 로그아웃");
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
    public Response<FindResult> find(FindInput input) {
        String id = id(input.userId());
        if (input.email() == null || input.email().length() > 100
            || !input.email().matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"))
            throw AccountAccessFailure.invalid("email", "이메일 형식이 맞지 않습니다.");
        String email = input.email();
        try {
            findWork.execute(() -> deliverTemporary(id, email));
        } catch (RejectedExecutionException full) {
            log.warn("event=password_find_skipped reason=queue_full");
        }
        // 계정이 맞든 안 맞든 같은 응답을 바로 돌려준다.
        return new Response<>(true, java.util.Map.of(), new FindResult(FIND_MESSAGE));
    }
    private void deliverTemporary(String id, String email) {
        try {
            var observed = mapper.account(id);
            transaction.executeWithoutResult(status -> {
                var row = mapper.lockAccount(id);
                if (row == null || observed == null || !"USE".equals(row.status)
                    || !Objects.equals(row.modifiedAt, observed.modifiedAt) || !row.email.equals(email)) return;
                Instant now = completion.now();
                if ("Y".equals(row.temporary) && row.passwordChangedAt != null
                    && now.isBefore(row.passwordChangedAt.plusSeconds(600))) return;
                String hash = passwords.hashAndDeliver(row.email);
                mapper.password(id, hash, true, now);
                runAfterCommit(() -> sessions.endOthers(id, null, "비밀번호 찾기 로그아웃"));
            });
        } catch (RuntimeException failure) {
            // 공개 API라 결과는 알리지 않는다. 받는 사람·비밀번호는 남기지 않는다.
            log.warn("event=password_find_failed reason={}", failure.getClass().getSimpleName());
        }
    }
    public Response<PasswordResult> change(PasswordInput input, HttpServletRequest request) {
        var binding = authenticated(request);
        required(input.currentPassword(), "currentPassword", "현재 비밀번호를 입력해 주세요.");
        if (input.currentPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw AccountAccessFailure.invalid("currentPassword", "현재 비밀번호가 맞지 않습니다.");
        if (input.newPassword() == null || input.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
            || !input.newPassword().matches("(?s)^(?=.*[A-Za-z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).{8,}$"))
            throw AccountAccessFailure.invalid("newPassword", "비밀번호는 8자 이상 영문·숫자·특수문자를 조합해 주세요.");
        if (input.modifiedAt() == null) throw AccountAccessFailure.invalid("modifiedAt", "저장 시각을 확인해 주세요.");
        return transaction.execute(status -> {
            var row = mapper.lockAccount(binding.userId);
            if (row == null || !"USE".equals(row.status) || !Objects.equals(row.hash, binding.credentialHash))
                throw AccountAccessFailure.unauthorized();
            if (!Objects.equals(row.modifiedAt, input.modifiedAt()))
                throw new AccountAccessFailure(409, "modifiedAt", "다른 사용자가 먼저 변경했습니다. 다시 조회해 주세요.");
            if (!encoder.matches(input.currentPassword(), row.hash))
                throw AccountAccessFailure.invalid("currentPassword", "현재 비밀번호가 맞지 않습니다.");
            if (encoder.matches(input.newPassword(), row.hash))
                throw AccountAccessFailure.invalid("newPassword", "Y".equals(row.temporary)
                    ? "임시 비밀번호와 다르게 정해 주세요." : "현재 비밀번호와 다르게 정해 주세요.");
            Instant now = completion.now();
            String hash = encoder.encode(input.newPassword());
            // 커밋 직후 세션 갱신 전에 온 같은 세션 요청(다른 탭)도 새 해시로 통과하게 한다.
            binding.pendingHash = hash;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    binding.credentialHash = hash;
                    binding.passwordChangeRequired = false;
                    sessions.endOthers(row.userId, request.getSession(false), AccountSessions.ACCOUNT_ENDED);
                }
                @Override public void afterCompletion(int completionStatus) { binding.pendingHash = null; }
            });
            mapper.password(row.userId, hash, false, now);
            var updated = mapper.account(row.userId);
            var prepared = completion.prepare(row.userId, false);
            return new Response<>(true, prepared.meta(), new PasswordResult(updated.passwordChangedAt, updated.modifiedAt));
        });
    }
}
