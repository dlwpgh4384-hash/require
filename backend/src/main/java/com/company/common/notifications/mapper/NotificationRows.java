package com.company.common.notifications.mapper;

import java.time.Instant;

public final class NotificationRows {
    private NotificationRows() {}

    public static class Notification {
        public long notificationId;
        public String recipient;
        public String type;
        public String content;
        public String screenId;
        public String targetKey;
        public String readYn;
        public Instant notifiedAt;
        public Instant readAt;
    }

    public static class Access {
        public boolean readAllowed;
        public boolean inScope;
        public boolean targetExists;
        public boolean pcOnly;
    }

    public record Window(String actor, Instant cutoff) {}
    public record Selection(String actor, long notificationId, Instant cutoff) {}
    public record Delivery(String recipient, String type, String content, String screenId,
        String targetKey, Instant occurredAt) {}
}
