package com.company.common.dailybatch.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 02:00(한국 시각) 일 배치 하나(PER-004). 앞에 따로 돌던 02:00 근무 상태·매달 1일 투입 MM 배치는 이 안으로 옮겼다(명세 ⑪-1).
 * 09:00 미수 알림(BillDunningSchedule)은 따로 그대로(⑪-2). 준비 명령·배치 한 번 돌리기(웹 아님)에서는 만들지 않는다.
 */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DailyBatchSchedule {
    private final DailyBatch batch;
    public DailyBatchSchedule(DailyBatch batch) { this.batch = batch; }
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void run() { batch.run(); }
}
