package com.company.common.menus.service;

public class MenuSettingsFailure extends RuntimeException {
    private final String field;

    public MenuSettingsFailure(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
