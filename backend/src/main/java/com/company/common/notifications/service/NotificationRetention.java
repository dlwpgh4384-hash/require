package com.company.common.notifications.service;

import com.company.common.notifications.mapper.NotificationMapper;
import com.company.common.support.service.BusinessCalendar;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationRetention {
    private final NotificationMapper mapper;
    private final BusinessCalendar calendar;

    public NotificationRetention(NotificationMapper mapper, BusinessCalendar calendar) {
        this.mapper = mapper;
        this.calendar = calendar;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int purgeExpired() {
        return mapper.deleteExpired(calendar.now().atZone(ZoneId.of("Asia/Seoul")).minusDays(30).toInstant());
    }
}
