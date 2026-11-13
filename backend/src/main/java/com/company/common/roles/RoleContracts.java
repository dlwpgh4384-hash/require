package com.company.common.roles;

import java.util.List;

public final class RoleContracts {
    private RoleContracts() {}

    public record Role(String roleId, String roleName, String dataScopeCd,
        String snstvViewYn, String useYn, boolean basicRole) {}
    public record RoleList(List<Role> items) {}
    public record DataScope(String code, String name) {}
    public record MenuAuth(String menuId, String menuName, String groupName,
        String readYn, String createYn, String updateYn, String deleteYn,
        String uploadYn, String downloadYn, String cnfmYn) {
        public MenuInput input() {
            return new MenuInput(menuId, readYn, createYn, updateYn, deleteYn, uploadYn, downloadYn, cnfmYn);
        }
    }
    public record Settings(Role role, List<MenuAuth> menuAuths, List<DataScope> dataScopes) {}
    public record Copy(String sourceRoleId, String roleName, String dataScopeCd) {}
    public record MenuInput(String menuId, String readYn, String createYn, String updateYn,
        String deleteYn, String uploadYn, String downloadYn, String cnfmYn) {
        public List<String> flags() {
            return java.util.Arrays.asList(readYn, createYn, updateYn, deleteYn, uploadYn, downloadYn, cnfmYn);
        }
    }
    public record Update(String dataScopeCd, String snstvViewYn,
        List<MenuInput> menuAuths, List<String> confirmedAffectedUserIds) {}
    public record Impact(int headquartersUserCount, int projectUserCount,
        List<String> affectedUserIds, List<String> preservedPmUserIds) {}
    public record StopResult(String roleId, String useYn) {}
}
