package com.company.common.commonops.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AccountAccessContracts {
    private AccountAccessContracts() {}
    public record Response<T>(boolean success, Map<String, Object> meta, T data) {}
    public record LoginInput(String userId, String password) {}
    public record FindInput(String userId, String email) {}
    public record PasswordInput(String currentPassword, String newPassword, Instant modifiedAt) {}
    /** userName: 첫 로그인 안내(「○○님은 임시 비밀번호로 로그인했습니다…」)에 쓰는 사용자 이름(TB_SY_USER.USER_NM). */
    public record LoginResult(String nextScreenId, String userName) {}
    public record FindResult(String message) {}
    public record PasswordResult(Instant passwordChangedAt, Instant modifiedAt) {}
    /** roleNames: roles와 같은 순서의 역할 이름(TB_SY_ROLE.ROLE_NM). */
    public record MyInfo(String userId, String name, List<String> roles, List<String> roleNames, List<String> parts) {}
}
