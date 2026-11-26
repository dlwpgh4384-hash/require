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
    public record LoginResult(String nextScreenId) {}
    public record FindResult(String message) {}
    public record PasswordResult(Instant passwordChangedAt, Instant modifiedAt) {}
    public record MyInfo(String userId, String name, List<String> roles, List<String> parts) {}
}
