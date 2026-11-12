package com.company.common.users;

public class UserFailure extends RuntimeException {
    private final int status;
    private final String field;
    public UserFailure(int status, String field, String message) {
        super(message);
        this.status = status;
        this.field = field;
    }
    public int getStatus() { return status; }
    public String getField() { return field; }
    public static UserFailure invalid(String field, String message) { return new UserFailure(400, field, message); }
    public static UserFailure conflict() {
        return new UserFailure(409, "modDttm", "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.");
    }
}
