package com.company.common.notifications.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class NotificationContracts {
    private NotificationContracts() {}

    public record Response<T>(boolean success, Map<String, Object> meta, T data) {
        public static <T> Response<T> of(T data) {
            return new Response<>(true, Map.of(), data);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(long notificationId, String content, String screenId, String targetKey,
        String readYn, Instant notifiedAt) {}

    public record Listing(List<Item> items, long unreadCount) {}
    public record Read(long notificationId, String readYn, Instant readAt) {}
    public record Navigation(String status, String screenId, boolean pcOnly) {}
}
