package com.company.common.roles;

public class RoleFailure extends RuntimeException {
    private final int status;
    private final String field;
    private RoleFailure(int status, String field, String message) {
        super(message);
        this.status = status;
        this.field = field;
    }
    public int getStatus() { return status; }
    public String getField() { return field; }
    public static RoleFailure invalid(String field, String message) {
        return new RoleFailure(400, field, message);
    }
    public static RoleFailure missing() {
        return new RoleFailure(404, "roleId", "역할을 찾을 수 없습니다.");
    }
}
