package com.company.common.session.dto;

import java.time.Instant;
import java.util.Map;

public final class SessionContracts {
    private SessionContracts() {}

    public record Remaining(long remainingSeconds, int sessionMinutes, Instant serverRespondedAt) {}
    public record Response(boolean success, Map<String, Object> meta, Remaining data) {
        public static Response of(Remaining remaining) {
            return new Response(true, Map.of(), remaining);
        }
    }
}
