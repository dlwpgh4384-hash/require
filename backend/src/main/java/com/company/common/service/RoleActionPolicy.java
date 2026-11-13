package com.company.common.service;

import com.company.common.dto.RoleContracts;

public final class RoleActionPolicy {
    private RoleActionPolicy() {}
    public enum Action {
        READ, CREATE, UPDATE, DELETE, UPLOAD, DOWNLOAD,
        MONTH_CLOSE, PART_SETTLEMENT_CONFIRM, PART_SETTLEMENT_CANCEL, QUOTE_APPROVE, QUOTE_REJECT
    }
    public static boolean allows(RoleContracts.MenuInput permission, Action action) {
        String flag = switch (action) {
            case READ -> permission.readYn();
            case CREATE -> permission.createYn();
            case UPDATE -> permission.updateYn();
            case DELETE -> permission.deleteYn();
            case UPLOAD -> permission.uploadYn();
            case DOWNLOAD -> permission.downloadYn();
            case MONTH_CLOSE, PART_SETTLEMENT_CONFIRM, PART_SETTLEMENT_CANCEL, QUOTE_APPROVE, QUOTE_REJECT -> permission.cnfmYn();
        };
        return "Y".equals(flag);
    }
}
