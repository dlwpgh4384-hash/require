package com.company.common.roles;

public final class RoleRows {
    private RoleRows() {}
    public static class Role {
        public String roleId;
        public String roleName;
        public String dataScopeCd;
        public String snstvViewYn;
        public String useYn;
        public RoleContracts.Role value() {
            return new RoleContracts.Role(roleId, roleName, dataScopeCd, snstvViewYn, useYn,
                RoleRules.basic(roleId));
        }
    }
    public static class Menu {
        public String menuId;
        public String menuName;
        public String groupName;
        public String readYn;
        public String createYn;
        public String updateYn;
        public String deleteYn;
        public String uploadYn;
        public String downloadYn;
        public String cnfmYn;
        public RoleContracts.MenuAuth value() {
            return new RoleContracts.MenuAuth(menuId, menuName, groupName, readYn,
                createYn, updateYn, deleteYn, uploadYn, downloadYn, cnfmYn);
        }
    }
    public static class ScopeCode {
        public String code;
        public String name;
        public RoleContracts.DataScope value() { return new RoleContracts.DataScope(code, name); }
    }
    public static class Scope {
        public String userId;
        public int scopeSn;
        public String scopeTypeCd;
        public String partId;
        public String projId;
    }
}
