package com.company.common.commonops.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.common.BillingCalculationRules;
import com.company.common.commonops.dto.BillingLookupContracts.*;
import com.company.common.commonops.mapper.BillingLookupMapper;
import com.company.common.commonops.mapper.BillingLookupMapper.*;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class BillingLookupService {
    private final BillingLookupMapper mapper;
    private final BillingCalculationRules rules;
    private final CalculationRuleService metadata;
    private final BusinessCalendar calendar;

    public BillingLookupService(BillingLookupMapper mapper, BillingCalculationRules rules,
        CalculationRuleService metadata, BusinessCalendar calendar) {
        this.mapper=mapper; this.rules=rules; this.metadata=metadata; this.calendar=calendar;
    }

    public Options getCommonBillOptions(String projectId, String billMonth) {
        LocalDate today=calendar.today();
        YearMonth month=YearMonth.from(today);
        if (billMonth!=null) {
            try { month=calendar.fromDatabaseMonth(billMonth); }
            catch (RuntimeException exception) { throw CodeFailure.invalid("billMonth","청구월은 YYYYMM 형식으로 입력해 주세요."); }
        }
        if (projectId!=null && (projectId.isBlank() || projectId.length()>20))
            throw CodeFailure.invalid("projectId","프로젝트를 확인해 주세요.");
        var filter=new OptionFilter(projectId,calendar.toDatabaseMonth(month));
        var projects=mapper.projects(filter);
        BigDecimal estimate=null;
        if (projectId!=null) {
            var project=projects.stream().filter(p -> p.projectId().equals(projectId)).findFirst()
                .orElseThrow(() -> CodeFailure.invalid("projectId","청구 가능한 진행 중 프로젝트를 선택해 주세요."));
            if (Set.of("MO","DP").contains(project.projectType())) estimate=rules.estimate(mapper.monthly(filter));
        }
        metadata.definition("식-00");
        metadata.definition("식-05");
        metadata.definition("식-10");
        BigDecimal rate=metadata.effectiveStandard("VAT_RT",month).value();
        BillingCalculationRules.rate(rate,"vatRate");
        return new Options(projects,mapper.statuses(),filter.month(),today,month.plusMonths(1).atDay(25),estimate,rate);
    }

    public DunningResult getCommonBillDunning() {
        LocalDate today=calendar.today();
        var month=YearMonth.from(today);
        metadata.definition("식-00");
        metadata.definition("식-89");
        int[] thresholds=new int[3];
        for (int i=0;i<thresholds.length;i++) {
            try { thresholds[i]=metadata.effectiveStandard("DUN_D"+(i+1),month).value().intValueExact(); }
            catch (ArithmeticException exception) { throw CodeFailure.invalid("standardCode","미수 알림 일수는 정수여야 합니다."); }
        }
        if (thresholds[0]<1 || thresholds[0]>=thresholds[1] || thresholds[1]>=thresholds[2])
            throw CodeFailure.invalid("standardCode","미수 알림 단계 기준을 확인해 주세요.");
        var history=new HashMap<String,List<HistoryRow>>();
        for (var row:mapper.history(today)) history.computeIfAbsent(row.billId(),key -> new ArrayList<>()).add(row);
        var items=new ArrayList<DunningRow>();
        for (var bill:mapper.overdue(today)) {
            if (bill.cancelled() || bill.unpaidAmount().signum()<=0 || !bill.payDueDate().isBefore(today)) continue;
            Integer step=rules.dunningStep(false,bill.unpaidAmount(),bill.payDueDate(),today);
            var sent=history.getOrDefault(bill.billId(),List.of()).stream()
                .sorted(Comparator.comparing(HistoryRow::sentDate).thenComparingInt(HistoryRow::sequence)).toList();
            boolean resent=step!=null && sent.stream().anyMatch(h -> h.step()==step && h.resend());
            String next=null;
            if (step!=null && sent.stream().noneMatch(h -> h.step()==step && !h.resend()))
                next=today+" "+step+"단계 자동 알림 예정";
            else if (step==null || step<3) {
                int index=step==null?0:step;
                next=bill.payDueDate().plusDays(thresholds[index])+" "+(index+1)+"단계 예정";
            }
            items.add(new DunningRow(bill.billId(),bill.projectId(),bill.projectName(),bill.unpaidAmount(),
                bill.payDueDate(),Math.toIntExact(ChronoUnit.DAYS.between(bill.payDueDate(),today)),step,
                sent.stream().map(HistoryRow::value).toList(),next,step!=null && !resent,
                resent?"이 단계 다시 알림 보냄":null));
        }
        var roles=new LinkedHashMap<String,String>();
        for (var role:mapper.recipientRoles()) roles.put(role.code(),role.name());
        var cards=new ArrayList<Card>();
        for (int step=1;step<=3;step++) {
            var recipients=new ArrayList<String>();
            for (var role:roles.entrySet()) {
                if (role.getKey().equals("PM") || (step>=2 && role.getKey().equals("SALES_LDR"))
                    || (step==3 && Set.of("EXEC","FIN").contains(role.getKey()))) recipients.add(role.getValue());
            }
            cards.add(new Card(step,recipients));
        }
        return new DunningResult(cards,items);
    }
}
