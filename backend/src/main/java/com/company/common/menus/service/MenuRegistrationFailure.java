package com.company.common.menus.service;

public class MenuRegistrationFailure extends RuntimeException {
    private final String field;

    public MenuRegistrationFailure(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
