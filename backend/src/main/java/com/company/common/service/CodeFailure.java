package com.company.common.service;

public class CodeFailure extends RuntimeException {
    private final String field;
    private final int status;

    public CodeFailure(String field, String message) {
        this(400, field, message);
    }

    public CodeFailure(int status, String field, String message) {
        super(message);
        this.status = status;
        this.field = field;
    }

    public String getField() { return field; }
    public int getStatus() { return status; }
    /** 공통 기능 FR-0106 동시 수정: 읽은 뒤 다른 사람이 바꿨으면 덮어쓰지 않는다. */
    public static CodeFailure conflict() {
        return new CodeFailure(409, "modifiedAt", "다른 사람이 먼저 바꿨습니다. 다시 불러와 주세요.");
    }
    public static CodeFailure invalid(String field, String message) { return new CodeFailure(field, message); }
}
