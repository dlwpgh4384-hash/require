package com.company.common.notifications.service;

import com.company.common.notifications.mapper.NotificationMapper;
import com.company.common.notifications.mapper.NotificationRows.Delivery;
import com.company.common.notifications.mapper.NotificationRows.Notification;
import com.company.common.support.service.BusinessCalendar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 만들기(SFR-015, 명세 ⑩). 받는 사람 = 사용 중 + 대상 화면 조회 권한 + 범위 안(만들 때 검사, ⑩-4).
 * 알림 글에는 민감 값(급여·단가·원가)을 넣지 않는다. 부르는 쪽은 자기 쓰기 트랜잭션 안에서 부른다.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class NotificationPublisher {
    /** 철수 알림 대상 한 건(투입·개발자). 「n명」은 개발자 수로 센다(시안 「철수 30일 전: 6명」). */
    public record WithdrawalTarget(String assignmentId, String developerId) {}

    private final NotificationMapper mapper;
    private final BusinessCalendar calendar;

    public NotificationPublisher(NotificationMapper mapper, BusinessCalendar calendar) {
        this.mapper = mapper;
        this.calendar = calendar;
    }

    /**
     * ⑩-4 검사만 한다(만들지는 않음). 미수 메일·견적 반려처럼 자기 길로 알림을 쓰는 곳이 받는 사람을 거를 때 쓴다.
     * targetKey: 미수 = 청구번호, 견적 반려 = 영업 기회 번호.
     */
    @Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
    public boolean canReceive(String recipient, String type, String screenId, String targetKey) {
        if (recipient == null || !mapper.eligibleAccount(recipient)) return false;
        return allowed(recipient, type, screenId, targetKey);
    }

    public boolean overdue(String recipient, String billId, int stage) {
        if (stage < 1 || stage > 3) {
            throw new NotificationFailure(400, "stage", "미수 단계를 확인해 주세요.");
        }
        return publish(recipient, "DUN", "미수 " + stage + "단계 알림을 확인해 주세요.",
            "UI-13", billId, false);
    }

    /**
     * 철수 n일 전(⑩-5): 투입 종료일이 정확히 n일 뒤인 투입 가운데 받는 사람 범위 안의 것만 세어 한 줄로 만든다.
     * 글은 시안 그대로 「철수 {n}일 전: {k}명」, 연결 키는 종료일(YYYY-MM-DD). 같은 날 다시 돌려도 한 번만(⑪ 재실행).
     */
    public boolean withdrawalSummary(String recipient, LocalDate endDate, int days, List<WithdrawalTarget> due) {
        if (recipient == null || endDate == null || days < 0 || due == null || due.isEmpty()) return false;
        if (!mapper.eligibleAccount(recipient)) return false;
        long people = due.stream()
            .filter(item -> allowed(recipient, "WTHDR", "UI-05", item.assignmentId()))
            .map(WithdrawalTarget::developerId).distinct().count();
        if (people == 0) return false;
        return publish(recipient, "WTHDR", "철수 " + days + "일 전: " + people + "명", "UI-05",
            endDate.toString(), true);
    }

    /**
     * 자금 부족 예상(⑩-5·⑩-6): 자금수지표의 부족 예상 달마다, 같은 달은 하루 1번까지. 글은 시안 그대로
     * 「2026-11 자금 부족 예상 (최소 운영자금 10억 원 미만)」 — 예상 잔액은 넣지 않는다.
     */
    public boolean cashShortage(String recipient, String yearMonth, BigDecimal minimumCash) {
        if (yearMonth == null || !yearMonth.matches("[0-9]{4}(0[1-9]|1[0-2])")) {
            throw new NotificationFailure(400, "yearMonth", "대상 월을 확인해 주세요.");
        }
        String month = yearMonth.substring(0, 4) + "-" + yearMonth.substring(4);
        String content = month + " 자금 부족 예상"
            + (minimumCash == null ? "" : " (최소 운영자금 " + amount(minimumCash) + " 미만)");
        return publish(recipient, "CASH_SHORT", content, "UI-21", yearMonth, true);
    }

    /** 업로드 반영 완료(⑩-6): 올린 사람(TB_SY_UPLD.REG_USER_ID)만, 「{대상} 업로드 완료 (경고 n건)」. */
    public boolean uploadCompleted(String uploadId, String screenId, String targetName) {
        String uploader = mapper.uploadOwner(uploadId);
        if (uploader == null) return false;
        Integer warnings = mapper.uploadWarnings(uploader, uploadId);
        if (warnings == null) return false;
        String prefix = targetName == null || targetName.isBlank() ? "" : targetName + " ";
        return publish(uploader, "UPLD_DONE", prefix + "업로드 완료 (경고 " + warnings + "건)",
            screenId, uploadId, false);
    }

    public boolean quoteRejected(String recipient, String opportunityId) {
        return publish(recipient, "QUOTE_REJ", "견적이 반려되었습니다.", "UI-29", opportunityId, false);
    }

    /** 큰 금액 표기(SFR-006-02 「○.○억 원/○만 원」): 10억 → 「10억 원」. */
    static String amount(BigDecimal value) {
        BigDecimal hundredMillion = new BigDecimal("100000000"), tenThousand = new BigDecimal("10000");
        var format = new java.text.DecimalFormat("#,##0.#");
        if (value.abs().compareTo(hundredMillion) >= 0)
            return format.format(value.divide(hundredMillion, 1, RoundingMode.DOWN)) + "억 원";
        if (value.abs().compareTo(tenThousand) >= 0)
            return format.format(value.divide(tenThousand, 0, RoundingMode.DOWN)) + "만 원";
        return format.format(value.setScale(0, RoundingMode.DOWN)) + "원";
    }

    private boolean allowed(String recipient, String type, String screenId, String targetKey) {
        var row = new Notification();
        row.recipient = recipient;
        row.type = type;
        row.screenId = screenId;
        row.targetKey = targetKey;
        var access = mapper.selectAccess(recipient, row);
        return access != null && access.readAllowed && access.inScope && access.targetExists;
    }

    private boolean publish(String recipient, String type, String content,
        String screenId, String targetKey, boolean daily) {
        if (!mapper.eligibleAccount(recipient)) return false;
        if (!allowed(recipient, type, screenId, targetKey)) return false;
        var delivery = new Delivery(recipient, type, content, screenId, targetKey, calendar.now());
        if (daily) {
            mapper.lockDelivery(recipient + ":" + type + ":" + targetKey);
            if (mapper.deliveredToday(delivery)) return false;
        }
        return mapper.insert(delivery) == 1;
    }
}
