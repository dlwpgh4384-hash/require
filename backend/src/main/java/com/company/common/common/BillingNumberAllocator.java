package com.company.common.common;

import com.company.common.support.service.CodeFailure;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** All invoice creation paths call within their source-lock/eligibility/insert transaction. */
@Component
public class BillingNumberAllocator {
    private final BillingNumberMapper mapper;
    public BillingNumberAllocator(BillingNumberMapper mapper) { this.mapper=mapper; }
    public record Identity(String billId,int billOrder) {}

    @Transactional(propagation=Propagation.MANDATORY)
    public Identity allocate(String projectId,String month) {
        if (month==null || !month.matches("[0-9]{6}"))
            throw CodeFailure.invalid("billMonth","청구월을 확인해 주세요.");
        mapper.lockBills();
        int number=mapper.nextMonthNumber(month);
        int order=mapper.nextProjectOrder(projectId);
        if (number<1 || number>999)
            throw CodeFailure.invalid("billMonth","월 청구번호 발급 범위를 초과했습니다.");
        if (order<1 || order>999)
            throw CodeFailure.invalid("projectId","프로젝트 청구 차수 범위를 초과했습니다.");
        return new Identity(String.format(Locale.ROOT,"B-%s-%03d",month,number),order);
    }
}
