package com.company.common.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class UserContracts {
    private UserContracts() {}
    public record Response<T>(boolean success, Map<String, Object> meta, T data) {
        public static <T> Response<T> of(T data) { return new Response<>(true, Map.of(), data); }
    }
    public record Create(String userId, String name, String email, List<String> roleCds,
        boolean headquarters, List<String> projectIds) {}
    /** 시안 「사용자 수정」: 이름·이메일·역할·담당 파트·담당 프로젝트를 고친다(아이디는 못 바꿈). */
    public record Update(String name, String email, List<String> roleCds, boolean headquarters,
        List<String> projectIds, Instant modDttm, List<String> confirmedProjectIds) {}
    public record Version(String userId, Instant modDttm) {}
    public record Stop(List<Version> users, List<String> confirmedProjectIds) {}
    /** 상세 패널 [계정 정보]. */
    public record Account(String userId, String name, String email, List<String> roleCds,
        boolean headquarters, List<String> projectIds, String userStatCd, String modDttm,
        List<String> roleNames, List<String> assignedParts, List<Project> projects,
        List<String> leaderParts, boolean temporaryPassword, String lastAccessAt) {}
    public record ListItem(String userId, String name, List<String> roleCds, String userStatCd,
        String modDttm, List<String> roleNames, List<String> assignedParts, String lastAccessAt) {}
    public record Page<T>(List<T> items, int page, int pageSize, long total) {}
    public record Project(String projectId, String projectName) {}
    public record Save(Account account, List<Project> transferredProjects) {}
    public record Rejected(String userId, String reason) {}
    public record StopResult(List<String> processedUserIds, int unprocessedCount, List<Rejected> unprocessed) {}
    public record Impact(List<Project> projects, int projectCount, List<Rejected> blockedUsers) {}
    public record AccessLog(String occurredAt, String action, String target) {}
    /** 상세 패널 [변경 이력]: 바뀐 항목마다 1줄, 최신순(SFR-014-01·02). */
    public record ChangeHistory(String changedAt, String item, String before, String after, String changedBy) {}
    public record PasswordResult(String userId, String tempPwdYn, String modDttm) {}
    public record UnlockResult(String userId, String userStatCd, int pwdErrCnt, String modDttm) {}
    /** 목록 조건. includeStopped=false면 상태 조건이 「중지」가 아닐 때 중지 계정을 숨긴다. sortKey·sortDir은 정해진 값만. */
    public record Filter(int page, int pageSize, long offset, String roleCd, String userStatCd, String keyword,
        boolean includeStopped, String sortKey, String sortDir) {}
    public record History(String table, String key, String type, String item, String before, String after) {}
}
