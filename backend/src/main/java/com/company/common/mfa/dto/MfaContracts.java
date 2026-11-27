package com.company.common.mfa.dto;

import java.time.Instant;
import java.util.Map;

public final class MfaContracts {
    private MfaContracts() {}

    public record Response<T>(boolean success, Map<String, Object> meta, T data) {
        public static <T> Response<T> of(T data) { return new Response<>(true, Map.of(), data); }
    }
    public record Verify(String code) {}
    public record Delivery(String maskedEmail, Instant expiresAt, Instant resendAvailableAt) {}
    /** userName: 첫 로그인 안내에 쓰는 사용자 이름(로그인 응답 LoginResult와 같은 이름). */
    public record Login(String nextScreenId, String userName) {}
    public record Started(Delivery delivery, String deliveryError) {}
}
