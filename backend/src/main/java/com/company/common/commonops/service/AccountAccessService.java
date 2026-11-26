package com.company.common.commonops.service;

import com.company.common.commonops.dto.AccountAccessContracts.*;
import com.company.common.commonops.mapper.AccountAccessMapper;
import com.company.common.mfa.service.CommonMfaService;
import com.company.common.support.auth.AccountSessions;

import com.company.common.users.service.TemporaryUserPassword;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Objects;
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
    public static final String FIND_MESSAGE = "입력한 정보가 맞으면 이메일로 임시 비밀번호를 보냈습니다.";
    private final AccountAccessMapper mapper;

    private final TemporaryUserPassword passwords;
    private final AccountSessions sessions;
    private final AccountLoginCompletion completion;
    private final CommonMfaService mfa;

    private final TransactionTemplate transaction;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
    private final String absentHash = encoder.encode(java.util.UUID.randomUUID().toString());

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
            var row = mapper.lockAccount(id);
            String error = row == null ? INVALID : "LOCK".equals(row.status) ? LOCKED
                : "STOP".equals(row.status) ? STOPPED : null;
            if (error == null && !encoder.matches(input.password(), row.hash)) {
                mapper.failed(id, completion.standard("LOCK_FAIL_CNT"));
                row = mapper.account(id);
                error = "LOCK".equals(row.status) ? LOCKED : INVALID;
                if ("LOCK".equals(row.status)) runAfterCommit(() -> sessions.endOthers(id, null, "계정 잠김 로그아웃"));
            } else if (row == null) {
                encoder.matches(input.password(), absentHash);
            }
            if (error != null) {
                mapper.access(id, "LOGIN_FAIL", error, request.getRemoteAddr(), completion.now());
                return new Attempt(null, new AccountAccessFailure(401, "password", error));
            }
            // Discard a prior authenticated device context when starting another login on this browser.
            if (sessions.current(request.getSession(false)) != null)
                sessions.end(request.getSession(false), "다시 로그인");
            request.getSession();
            if (mapper.roles(id).contains("ADMIN")) {
                var started = mfa.startAfterPasswordVerified(request, id);
                var meta = new LinkedHashMap<String, Object>();
                if (started != null) {
                    meta.put("mfaDelivery", started.delivery());
                    meta.put("deliveryError", started.deliveryError());
                }
                return new Attempt(new Response<>(true, meta, new LoginResult("UI-39")), null);
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
            return new Attempt(new Response<>(true, prepared.meta(), new LoginResult(prepared.nextScreenId())), null);
        });
        if (result.failure != null) throw result.failure;
        return result.response;
    }
    public AccountSessions.Binding authenticated(HttpServletRequest request) {
        var binding = sessions.current(request.getSession(false));
        if (binding == null) throw AccountAccessFailure.unauthorized();
        var row = mapper.account(binding.userId);
        if (row == null || !"USE".equals(row.status) || !Objects.equals(row.hash, binding.credentialHash)) {
            sessions.endOthers(binding.userId, null, "계정 변경 로그아웃");
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
            new MyInfo(row.userId, row.name, mapper.roles(row.userId), completion.parts(row.userId)));
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
        var observed = mapper.account(id);
        try {
            transaction.executeWithoutResult(status -> {
                var row = mapper.lockAccount(id);
                if (row == null || observed == null || !"USE".equals(row.status)
                    || !Objects.equals(row.modifiedAt, observed.modifiedAt) || !row.email.equals(input.email())) return;
                Instant now = completion.now();
                if ("Y".equals(row.temporary) && row.passwordChangedAt != null
                    && now.isBefore(row.passwordChangedAt.plusSeconds(600))) return;
                String hash = passwords.hashAndDeliver(row.email);
                mapper.password(id, hash, true, now);
                runAfterCommit(() -> sessions.endOthers(id, null, "비밀번호 찾기 로그아웃"));
            });
        } catch (RuntimeException failure) {
            // No account, transport, or transaction outcome is disclosed by this public operation.
        }
        return new Response<>(true, java.util.Map.of(), new FindResult(FIND_MESSAGE));
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
            mapper.password(row.userId, hash, false, now);
            var updated = mapper.account(row.userId);
            var prepared = completion.prepare(row.userId, false);
            runAfterCommit(() -> {
                binding.credentialHash = hash;
                binding.passwordChangeRequired = false;
                sessions.endOthers(row.userId, request.getSession(false), "다른 기기 비밀번호 변경 로그아웃");
            });
            return new Response<>(true, prepared.meta(), new PasswordResult(updated.passwordChangedAt, updated.modifiedAt));
        });
    }
}
