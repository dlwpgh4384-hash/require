package com.company.common.bills.service;

import com.company.common.bills.mapper.BillMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class BillDunningSchedule {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(BillDunningSchedule.class);
    private final BillMapper mapper;
    private final BillDunningService delivery;
    public BillDunningSchedule(BillMapper mapper, BillDunningService delivery) {
        this.mapper=mapper;
        this.delivery=delivery;
    }
    @Scheduled(cron="0 0 9 * * *",zone="Asia/Seoul")
    public void notifyOutstanding() {
        for (String id:mapper.outstandingIds()) {
            try { delivery.sendAutomatic(id); }
            catch (RuntimeException exception) {
                // Do not log recipient addresses, message content, or untrusted exception text.
                LOG.warn("Billing local notification failed; the next scheduled run may retry.");
            }
        }
    }
}
