package com.company.common.users.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 조회된 계정으로 수신자만 산정한다. 알림 저장, 발송, SMTP 연동은 하지 않는다. */
public final class UserNotificationRecipients {
    private UserNotificationRecipients() {}

    public record Candidate(
        String userId, String userStatCd, Set<String> roleCds, boolean headquarters) {
        public Candidate {
            roleCds = Set.copyOf(roleCds);
        }
    }

    public record Selection(List<String> userIds, boolean executiveFallback) {
        public Selection {
            userIds = List.copyOf(userIds);
        }
    }

    public static Selection select(Collection<Candidate> candidates) {
        var leaders = new TreeSet<String>();
        var executives = new TreeSet<String>();
        for (Candidate candidate : candidates) {
            if (!"USE".equals(candidate.userStatCd())) continue;
            if (candidate.headquarters() && candidate.roleCds().contains("SALES_LDR")) {
                leaders.add(candidate.userId());
            }
            if (candidate.roleCds().contains("EXEC")) executives.add(candidate.userId());
        }
        boolean fallback = leaders.isEmpty();
        return new Selection(List.copyOf(fallback ? executives : leaders), fallback);
    }
}
