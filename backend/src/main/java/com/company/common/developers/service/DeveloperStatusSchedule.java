package com.company.common.developers.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DeveloperStatusSchedule {
    private final CommonDeveloperService service;
    public DeveloperStatusSchedule(CommonDeveloperService service) { this.service=service; }
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() { service.refreshAll(); }
    @Scheduled(cron="0 0 2 * * *",zone="Asia/Seoul")
    public void recalculate() { service.refreshAll(); }
}
