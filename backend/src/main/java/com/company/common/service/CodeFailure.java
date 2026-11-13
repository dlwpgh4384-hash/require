package com.company.common.service;

public class CodeFailure extends RuntimeException {
    private final String field;

    public CodeFailure(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
    public static CodeFailure invalid(String field, String message) { return new CodeFailure(field, message); }
}
