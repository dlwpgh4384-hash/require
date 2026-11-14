package com.company.common.users.mapper;

public final class UserOptionRows {
    private UserOptionRows() {}

    public static class Role {
        private String roleCd;
        private String roleName;
        private boolean assignedProjectScope;
        private boolean assignedPartScope;

        public boolean isAssignedPartScope() { return assignedPartScope; }
        public void setAssignedPartScope(boolean assignedPartScope) { this.assignedPartScope = assignedPartScope; }

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
        private String pmUserId;
        private String pmName;

        public String getPmUserId() { return pmUserId; }
        public void setPmUserId(String pmUserId) { this.pmUserId = pmUserId; }
        public String getPmName() { return pmName; }
        public void setPmName(String pmName) { this.pmName = pmName; }

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
