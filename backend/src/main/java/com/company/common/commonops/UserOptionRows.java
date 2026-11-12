package com.company.common.commonops;

public final class UserOptionRows {
    private UserOptionRows() {}

    public static class Role {
        private String roleCd;
        private String roleName;
        private boolean assignedProjectScope;

        public String getRoleCd() { return roleCd; }
        public void setRoleCd(String roleCd) { this.roleCd = roleCd; }
        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }
        public boolean isAssignedProjectScope() { return assignedProjectScope; }
        public void setAssignedProjectScope(boolean assignedProjectScope) {
            this.assignedProjectScope = assignedProjectScope;
        }
    }

    public static class Project {
        private String projectId;
        private String projectName;

        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }
    }

    public static class UserState {
        private String code;
        private String name;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
