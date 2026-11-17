package com.company.common.assignments.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class AssignmentMonthSchedule {
    private final CommonAssignmentService service;
    public AssignmentMonthSchedule(CommonAssignmentService service) { this.service=service; }
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() { service.refreshCurrentMonth(); }
    @Scheduled(cron="0 0 2 1 * *",zone="Asia/Seoul")
    public void recalculate() { service.refreshCurrentMonth(); }
}
