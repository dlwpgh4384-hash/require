package com.company.common.preferences.service;

public class UserPreferenceFailure extends RuntimeException {
    public static final String CONFLICT = "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.";
    private final int status;
    private final String field;

    public UserPreferenceFailure(int status, String field, String message) {
        super(message);
        this.status = status;
        this.field = field;
    }

    public int getStatus() { return status; }
    public String getField() { return field; }
    public static UserPreferenceFailure invalid(String field, String message) {
        return new UserPreferenceFailure(400, field, message);
    }
    public static UserPreferenceFailure conflict() {
        return new UserPreferenceFailure(409, "modDttm", CONFLICT);
    }
}
