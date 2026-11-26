package com.company.common.session.service;

public class SessionFailure extends RuntimeException {
    private final int status;

    public SessionFailure(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() { return status; }
}
