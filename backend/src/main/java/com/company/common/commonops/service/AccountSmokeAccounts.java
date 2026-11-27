package com.company.common.commonops.service;

import com.company.common.commonops.mapper.AccountAccessMapper;
import java.util.Arrays;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 도구·시험 실행 전용 시험 계정. 환경 변수 AIOPS_SMOKE_LOGIN_ID·AIOPS_SMOKE_LOGIN_PASSWORD·AIOPS_SMOKE_ROLE_CODES가
 * 모두 있을 때만 돈다. 새 계정만 넣고, 같은 아이디가 이미 있으면 WARN만 남기고 건너뛴다
 * (이미 있는 계정의 비밀번호·잠금·역할은 바꾸지 않는다 — 보안 검토 결정).
 */
@Component
public class AccountSmokeAccounts implements ApplicationRunner {
    private final AccountAccessMapper mapper;
    private final TransactionTemplate transaction;
    public AccountSmokeAccounts(AccountAccessMapper mapper, PlatformTransactionManager manager) {
        this.mapper = mapper; this.transaction = new TransactionTemplate(manager);
    }
    @Override public void run(ApplicationArguments arguments) {
        // 표 준비(--prepare-schema) 실행은 표가 아직 없을 수 있다. 시험 계정은 준비가 끝난 뒤 보통 실행에서 만든다.
        if (arguments.containsOption("prepare-schema")) return;
        initialize(System.getenv("AIOPS_SMOKE_LOGIN_ID"), System.getenv("AIOPS_SMOKE_LOGIN_PASSWORD"), System.getenv("AIOPS_SMOKE_ROLE_CODES"));
    }
    public void initialize(String id, String password, String roleCodes) {
        if (id == null || id.isBlank() || password == null || password.isBlank() || roleCodes == null || roleCodes.isBlank()) return;
        List<String> roles = Arrays.stream(roleCodes.split(",")).map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
        if (!id.matches("[A-Za-z0-9._-]{1,30}") || roles.isEmpty() || roles.stream().anyMatch(role -> !role.matches("[A-Z_0-9]{1,20}")))
            throw new IllegalStateException("시험 계정 설정 형식을 확인해 주세요.");
        String hash = new BCryptPasswordEncoder(10).encode(password);
        transaction.executeWithoutResult(status -> {
            if (mapper.activeRoleCount(roles) != roles.size()) throw new IllegalStateException("시험 계정 역할을 확인해 주세요.");
            if (mapper.insertSmoke(id, hash) == 0) {
                LoggerFactory.getLogger(AccountSmokeAccounts.class)
                    .warn("event=smoke_account_skipped reason=existing_user userId={}", id);
                return;
            }
            mapper.assignSmokeRoles(id, roles);
        });
    }
}
