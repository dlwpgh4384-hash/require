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
    public record Login(String nextScreenId) {}
    public record Started(Delivery delivery, String deliveryError) {}
}
