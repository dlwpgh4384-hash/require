package com.company.common.notifications.service;

import com.company.common.notifications.mapper.NotificationMapper;
import com.company.common.notifications.mapper.NotificationRows.Delivery;
import com.company.common.notifications.mapper.NotificationRows.Notification;
import com.company.common.support.service.BusinessCalendar;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Business callers invoke these hooks inside their existing write transaction. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class NotificationPublisher {
    private final NotificationMapper mapper;
    private final BusinessCalendar calendar;

    public NotificationPublisher(NotificationMapper mapper, BusinessCalendar calendar) {
        this.mapper = mapper;
        this.calendar = calendar;
    }

    public boolean overdue(String recipient, String billId, int stage) {
        if (stage < 1 || stage > 3) {
            throw new NotificationFailure(400, "stage", "미수 단계를 확인해 주세요.");
        }
        return publish(recipient, "DUN", "미수 " + stage + "단계 알림을 확인해 주세요.",
            "UI-13", billId, false);
    }

    public boolean withdrawal(String recipient, String assignmentId) {
        if (!mapper.withdrawalDue(assignmentId)) return false;
        return publish(recipient, "WTHDR", "철수 예정인 투입이 있습니다.", "UI-05", assignmentId, true);
    }

    /** Call only for a month whose existing cash projection is below the DB MIN_CASH standard. */
    public boolean cashShortage(String recipient, String yearMonth) {
        if (yearMonth == null || !yearMonth.matches("[0-9]{4}(0[1-9]|1[0-2])")) {
            throw new NotificationFailure(400, "yearMonth", "대상 월을 확인해 주세요.");
        }
        return publish(recipient, "CASH_SHORT", "자금 부족이 예상되는 달이 있습니다.",
            "UI-21", yearMonth, true);
    }

    public boolean uploadCompleted(String uploader, String uploadId, String screenId) {
        Integer warnings = mapper.uploadWarnings(uploader, uploadId);
        if (warnings == null) return false;
        return publish(uploader, "UPLD_DONE", "업로드 완료 (경고 " + warnings + "건)",
            screenId, uploadId, false);
    }

    public boolean quoteRejected(String recipient, String opportunityId) {
        return publish(recipient, "QUOTE_REJ", "견적이 반려되었습니다.", "UI-29", opportunityId, false);
    }

    private boolean publish(String recipient, String type, String content,
        String screenId, String targetKey, boolean daily) {
        if (!mapper.eligibleAccount(recipient)) return false;
        var row = new Notification();
        row.recipient = recipient;
        row.type = type;
        row.screenId = screenId;
        row.targetKey = targetKey;
        var access = mapper.selectAccess(recipient, row);
        if (access == null || !access.readAllowed || !access.inScope || !access.targetExists) return false;
        var delivery = new Delivery(recipient, type, content, screenId, targetKey, calendar.now());
        if (daily) {
            mapper.lockDelivery(recipient + ":" + type + ":" + targetKey);
            if (mapper.deliveredToday(delivery)) return false;
        }
        return mapper.insert(delivery) == 1;
    }
}
