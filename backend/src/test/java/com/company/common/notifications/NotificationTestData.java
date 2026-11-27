package com.company.common.notifications;

import com.company.common.notifications.mapper.NotificationMapper;
import com.company.common.notifications.mapper.NotificationRows.*;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Test-owned mapper boundary, matching the users feature's stateful fixture pattern. */
final class NotificationTestData {
    static final Instant NOW = Instant.parse("2026-11-26T01:00:00Z");
    final Map<Long, Notification> rows = new LinkedHashMap<>();
    final Map<Long, Access> access = new LinkedHashMap<>();

    NotificationTestData(NotificationMapper mapper) {
        when(mapper.activeAccount("reader")).thenReturn(true);
        when(mapper.selectNotifications(any())).thenAnswer(call -> {
            Window window = call.getArgument(0);
            return rows.values().stream().filter(row -> row.recipient.equals(window.actor()))
                .filter(row -> !row.notifiedAt.isBefore(window.cutoff()))
                .sorted(Comparator.comparing((Notification row) -> row.notifiedAt).reversed()
                    .thenComparing(Comparator.comparingLong((Notification row) -> row.notificationId).reversed()))
                .toList();
        });
        when(mapper.lockNotification(any())).thenAnswer(call -> {
            Selection selection = call.getArgument(0);
            var row = rows.get(selection.notificationId());
            return row == null || row.notifiedAt.isBefore(selection.cutoff()) ? null : row;
        });
        when(mapper.selectNotification(any())).thenAnswer(call -> rows.get(((Selection) call.getArgument(0)).notificationId()));
        when(mapper.selectAccess(anyString(), any())).thenAnswer(call -> access.get(((Notification) call.getArgument(1)).notificationId));
        when(mapper.read(any())).thenAnswer(call -> {
            Selection selection = call.getArgument(0);
            var row = rows.get(selection.notificationId());
            if (row == null || !row.recipient.equals(selection.actor()) || !"N".equals(row.readYn)) return 0;
            row.readYn = "Y";
            row.readAt = NOW;
            return 1;
        });
        when(mapper.readAll(any())).thenAnswer(call -> {
            Window window = call.getArgument(0);
            int changed = 0;
            for (var row : rows.values()) {
                if (row.recipient.equals(window.actor()) && "N".equals(row.readYn)
                    && !row.notifiedAt.isBefore(window.cutoff())) {
                    row.readYn = "Y";
                    row.readAt = NOW;
                    changed++;
                }
            }
            return changed;
        });
        add(1, "reader", NOW.minusSeconds(60));
        add(2, "reader", NOW);
        add(3, "other", NOW);
    }

    Notification add(long id, String recipient, Instant notifiedAt) {
        var row = new Notification();
        row.notificationId = id;
        row.recipient = recipient;
        row.type = "DUN";
        row.content = "본인 알림 " + id;
        row.screenId = "UI-13";
        row.targetKey = "owned-bill";
        row.readYn = "N";
        row.notifiedAt = notifiedAt;
        rows.put(id, row);
        var grant = new Access();
        grant.readAllowed = true;
        grant.inScope = true;
        grant.targetExists = true;
        access.put(id, grant);
        return row;
    }
}
