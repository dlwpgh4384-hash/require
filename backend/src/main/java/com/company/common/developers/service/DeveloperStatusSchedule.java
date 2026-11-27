package com.company.common.developers.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** 앱이 켜질 때 근무 상태를 한 번 맞춘다. 매일 02:00 갱신은 일 배치 ①단계(DailyBatch)로 옮겼다(명세 ⑪-1). */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DeveloperStatusSchedule {
    private final CommonDeveloperService service;
    public DeveloperStatusSchedule(CommonDeveloperService service) { this.service=service; }
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() { service.refreshAll(); }
}
