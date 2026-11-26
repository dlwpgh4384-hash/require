package com.company.common.notifications.service;

public class NotificationFailure extends RuntimeException {
    private final int status;
    private final String field;

    public NotificationFailure(int status, String field, String message) {
        super(message);
        this.status = status;
        this.field = field;
    }

    public int getStatus() { return status; }
    public String getField() { return field; }
}
