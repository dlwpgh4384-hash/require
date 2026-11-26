package com.company.common.mfa.service;

public final class MfaFailure extends RuntimeException {
    private final int status;
    private final String field;
    private final boolean restart;

    public MfaFailure(int status, String field, String message, boolean restart) {
        super(message);
        this.status = status;
        this.field = field;
        this.restart = restart;
    }
    public int status() { return status; }
    public String field() { return field; }
    public boolean restart() { return restart; }
    public static MfaFailure invalid(String message) {
        return new MfaFailure(400, "code", message, false);
    }
    public static MfaFailure unauthorized() {
        return new MfaFailure(401, "session", "처음부터 다시 로그인해 주세요.", true);
    }
}
