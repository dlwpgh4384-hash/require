package com.company.common.financeitems.service;

import com.company.common.financeitems.dto.FinanceItemContracts.*;
import com.company.common.financeitems.mapper.FinanceItemMapper;
import com.company.common.support.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.company.common.financeitems.service.FinanceItemRules.*;

@Service
public class FinanceTaxService {
    private final FinanceItemMapper mapper;
    private final CommonFinanceItemService months;
    private final CurrentBusinessActor actor;
    public FinanceTaxService(FinanceItemMapper mapper,CommonFinanceItemService months,CurrentBusinessActor actor) {
        this.mapper=mapper; this.months=months; this.actor=actor;
    }
    @Transactional
    public TaxYear postCommonFinanceTaxYear(String value) {
        int year=year(value);
        mapper.lockSources();
        mapper.prepareYear(value,actor.userId());
        mapper.preparePayments(value,actor.userId());
        return new TaxYear(months.estimate(java.time.YearMonth.of(year,1)),mapper.payments(value));
    }
    @Transactional
    public TaxPayment postCommonFinanceTaxPaymentConfirm(String id,ConfirmInput input) {
        var key=paymentKey(id);
        if (input.actualPaymentDate()==null) throw CodeFailure.invalid("actualPaymentDate","실제 납부일을 입력해 주세요.");
        amount(input.actualPaymentAmount(),"actualPaymentAmount");
        mapper.lockSources();
        var previous=mapper.payment(key);
        if (previous==null) throw CodeFailure.invalid("paymentId","납부 항목을 찾을 수 없습니다.");
        if ("Y".equals(previous.paidYn())) throw CodeFailure.invalid("paymentId","이미 확인한 납부는 변경할 수 없습니다.");
        affected(mapper.confirm(new PaymentWrite(key,input,actor.userId())));
        return mapper.payment(key);
    }
}
