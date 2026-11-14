package com.company.common.users.mapper;

import java.time.Instant;

public final class UserRows {
    private UserRows() {}
    public static class Account {
        public String userId;
        public String name;
        public String email;
        public String userStatCd;
        public Instant modDttm;
        public Instant lastAccessAt;
        public String tempPwdYn;
    }
    public static class Log {
        public Instant occurredAt;
        public String action;
        public String target;
    }
    public static class Change {
        public Instant changedAt;
        public String item;
        public String before;
        public String after;
        public String changedBy;
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
