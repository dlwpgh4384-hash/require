package com.company.common.users;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class UserContracts {
    private UserContracts() {}
    public record Response<T>(boolean success, Map<String, Object> meta, T data) {
        public static <T> Response<T> of(T data) { return new Response<>(true, Map.of(), data); }
    }
    public record Create(String userId, String name, String email, List<String> roleCds,
        boolean headquarters, List<String> projectIds) {}
    public record Update(List<String> roleCds, boolean headquarters, List<String> projectIds,
        LocalDateTime modDttm, List<String> confirmedProjectIds) {}
    public record Version(String userId, LocalDateTime modDttm) {}
    public record Stop(List<Version> users, List<String> confirmedProjectIds) {}
    public record Account(String userId, String name, String email, List<String> roleCds,
        boolean headquarters, List<String> projectIds, String userStatCd, String modDttm) {}
    public record ListItem(String userId, String name, List<String> roleCds, String userStatCd,
        String modDttm, List<String> roleNames, List<String> assignedParts, String lastAccessAt) {}
    public record Page<T>(List<T> items, int page, int pageSize, long total) {}
    public record Project(String projectId, String projectName) {}
    public record Save(Account account, List<Project> transferredProjects) {}
    public record Rejected(String userId, String reason) {}
    public record StopResult(List<String> processedUserIds, int unprocessedCount, List<Rejected> unprocessed) {}
    public record Impact(List<Project> projects, int projectCount, List<Rejected> blockedUsers) {}
    public record AccessLog(String occurredAt, String action, String target) {}
    public record PasswordResult(String userId, String tempPwdYn, String modDttm) {}
    public record UnlockResult(String userId, String userStatCd, int pwdErrCnt, String modDttm) {}
    public record Filter(int page, int pageSize, long offset, String roleCd, String userStatCd, String keyword) {}
    public record History(String table, String key, String type, String item, String before, String after) {}
}
