package com.company.common.users;

import java.time.LocalDateTime;

public final class UserRows {
    private UserRows() {}
    public static class Account {
        public String userId;
        public String name;
        public String email;
        public String userStatCd;
        public LocalDateTime modDttm;
        public LocalDateTime lastAccessAt;
    }
    public static class Log {
        public LocalDateTime occurredAt;
        public String action;
        public String target;
    }
    public static class Project {
        public String projectId;
        public String projectName;
    }
    public static class Owner {
        public String userId;
        public String projectId;
        public String projectName;
    }
}
