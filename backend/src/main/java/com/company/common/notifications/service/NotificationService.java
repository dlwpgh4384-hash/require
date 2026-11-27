package com.company.common.notifications.service;

import com.company.common.notifications.dto.NotificationContracts.*;
import com.company.common.notifications.mapper.NotificationMapper;
import com.company.common.notifications.mapper.NotificationRows;
import com.company.common.notifications.mapper.NotificationRows.Selection;
import com.company.common.notifications.mapper.NotificationRows.Window;
import com.company.common.support.service.BusinessCalendar;
import java.security.Principal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationService {
    public static final String HIDDEN = "볼 수 없는 알림입니다";
    private final NotificationMapper mapper;
    private final BusinessCalendar calendar;

    public NotificationService(NotificationMapper mapper, BusinessCalendar calendar) {
        this.mapper = mapper;
        this.calendar = calendar;
    }

    private String actor(Principal principal) {
        String id = principal == null ? null : principal.getName();
        if (id == null || id.isBlank() || id.length() > 30 || !mapper.activeAccount(id)) {
            throw new NotificationFailure(401, "session", "다시 로그인해 주세요.");
        }
        return id;
    }

    private Instant cutoff() {
        return calendar.now().atZone(ZoneId.of("Asia/Seoul")).minusDays(30).toInstant();
    }

    public Listing getCommonNotifications(Principal principal) {
        String actor = actor(principal);
        var rows = mapper.selectNotifications(new Window(actor, cutoff()));
        var items = rows.stream().map(row -> {
            var access = mapper.selectAccess(actor, row);
            boolean visible = access != null && access.readAllowed && access.inScope;
            return new Item(row.notificationId, visible ? row.content : HIDDEN,
                visible ? row.screenId : null, visible ? row.targetKey : null,
                row.readYn, row.notifiedAt);
        }).toList();
        return new Listing(items, rows.stream().filter(row -> "N".equals(row.readYn)).count());
    }

    @Transactional
    public void putCommonNotificationsReadAll(Principal principal) {
        mapper.readAll(new Window(actor(principal), cutoff()));
    }

    @Transactional
    public Response<Read> putCommonNotificationRead(Principal principal, long notificationId) {
        String actor = actor(principal);
        var selection = new Selection(actor, notificationId, cutoff());
        var row = mapper.lockNotification(selection);
        if (row == null) {
            throw new NotificationFailure(400, "notificationId", "이 알림의 대상이 더 이상 없습니다.");
        }
        if (!actor.equals(row.recipient)) {
            throw new NotificationFailure(403, "notificationId", "권한이 없습니다.");
        }
        mapper.read(selection);
        row = mapper.selectNotification(selection);
        var access = mapper.selectAccess(actor, row);
        String state = access == null || !access.readAllowed ? "DENIED"
            : !access.targetExists ? "MISSING" : !access.inScope ? "OUT_OF_SCOPE" : "ALLOWED";
        var navigation = new Navigation(state,
            access != null && access.readAllowed ? row.screenId : null,
            access != null && access.pcOnly);
        return new Response<>(true, Map.of("navigation", navigation),
            new Read(row.notificationId, row.readYn, row.readAt));
    }
}
