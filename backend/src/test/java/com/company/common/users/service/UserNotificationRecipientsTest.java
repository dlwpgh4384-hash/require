package com.company.common.users.service;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UserNotificationRecipientsTest {
    @Test
    void everyUsableHeadquartersLeaderIsSelectedWithoutSendingAnything() {
        var selection = UserNotificationRecipients.select(List.of(
            candidate("leader2", "USE", true, "SALES_LDR"),
            candidate("leader1", "USE", true, "SALES_LDR"),
            candidate("executive", "USE", false, "EXEC")));
        assertThat(selection.userIds()).containsExactly("leader1", "leader2");
        assertThat(selection.executiveFallback()).isFalse();
    }

    @Test
    void lockedAndStoppedLeadersAreExcluded() {
        var selection = UserNotificationRecipients.select(List.of(
            candidate("locked", "LOCK", true, "SALES_LDR"),
            candidate("stopped", "STOP", true, "SALES_LDR"),
            candidate("usable", "USE", true, "SALES_LDR")));
        assertThat(selection.userIds()).containsExactly("usable");
        assertThat(selection.executiveFallback()).isFalse();
    }

    @Test
    void noUsableLeaderFallsBackToUsableExecutives() {
        var selection = UserNotificationRecipients.select(List.of(
            candidate("locked", "LOCK", true, "SALES_LDR"),
            candidate("stopped", "STOP", true, "SALES_LDR"),
            candidate("exec2", "USE", false, "EXEC"),
            candidate("exec1", "USE", false, "EXEC"),
            candidate("exec3", "STOP", false, "EXEC")));
        assertThat(selection.userIds()).containsExactly("exec1", "exec2");
        assertThat(selection.executiveFallback()).isTrue();
    }

    @Test
    void headquartersWithoutLeadershipAndOtherPartLeadersAreNotRecipients() {
        var selection = UserNotificationRecipients.select(List.of(
            candidate("pmuser", "USE", true, "PM"),
            candidate("partleader", "USE", false, "SALES_LDR"),
            candidate("executive", "USE", false, "EXEC")));
        assertThat(selection.userIds()).containsExactly("executive");
        assertThat(selection.executiveFallback()).isTrue();
    }

    @Test
    void duplicateJoinRowsProduceOneRecipient() {
        var leader = candidate("leader", "USE", true, "SALES_LDR");
        assertThat(UserNotificationRecipients.select(List.of(leader, leader)).userIds())
            .containsExactly("leader");
    }

    @Test
    void noRecipientsStillReportsExecutiveFallbackWithoutInventingAccount() {
        var selection = UserNotificationRecipients.select(List.of());
        assertThat(selection.userIds()).isEmpty();
        assertThat(selection.executiveFallback()).isTrue();
    }

    private UserNotificationRecipients.Candidate candidate(
        String userId, String status, boolean headquarters, String role) {
        return new UserNotificationRecipients.Candidate(userId, status, Set.of(role), headquarters);
    }
}
