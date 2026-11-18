package com.company.common.assignments.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 현재 단가·월 MM은 변경 기록 저장 때와 매달 1일 배치로만 다시 맞춘다(SFR-112-07, PER-004: 매월 1일 02:00 한국 시각). 앱 시작 때는 돌리지 않는다. */
@Component
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class AssignmentMonthSchedule {
    private final CommonAssignmentService service;
    public AssignmentMonthSchedule(CommonAssignmentService service) { this.service=service; }
    @Scheduled(cron="0 0 2 1 * *",zone="Asia/Seoul")
    public void recalculate() { service.refreshCurrentMonth(); }
}
