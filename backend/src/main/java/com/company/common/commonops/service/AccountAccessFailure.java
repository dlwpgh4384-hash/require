package com.company.common.commonops.service;

public final class AccountAccessFailure extends RuntimeException {
    public final int status;
    public final String field;
    public AccountAccessFailure(int status, String field, String message) {
        super(message);
        this.status = status;
        this.field = field;
    }
    public static AccountAccessFailure invalid(String field, String message) {
        return new AccountAccessFailure(400, field, message);
    }
    public static AccountAccessFailure unauthorized() {
        return new AccountAccessFailure(401, "session", "다시 로그인해 주세요.");
    }
}
