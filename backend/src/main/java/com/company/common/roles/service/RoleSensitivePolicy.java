package com.company.common.roles.service;

import com.company.common.roles.dto.RoleContracts;

import java.util.Collection;
import java.util.List;

/** Consumer adapter policy; real financial/download/AI adapters are connected with their own features. */
public final class RoleSensitivePolicy {
    private RoleSensitivePolicy() {}
    public record Item(String title, Object value, java.math.BigDecimal contribution, boolean sensitive) {}
    public static boolean visible(Collection<RoleContracts.Role> assignedRoles) {
        return assignedRoles.stream().anyMatch(role -> "Y".equals(role.useYn()) && "Y".equals(role.snstvViewYn()));
    }
    public static List<Item> omit(Collection<RoleContracts.Role> assignedRoles, List<Item> items) {
        boolean show = visible(assignedRoles);
        return items.stream().filter(item -> show || !item.sensitive()).toList();
    }
    public static java.math.BigDecimal total(Collection<RoleContracts.Role> assignedRoles, List<Item> items) {
        return omit(assignedRoles, items).stream().map(Item::contribution)
            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }
}
