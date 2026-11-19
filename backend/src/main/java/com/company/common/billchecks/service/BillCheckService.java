package com.company.common.billchecks.service;

import com.company.common.billchecks.dto.BillCheckContracts.*;
import com.company.common.billchecks.mapper.BillCheckMapper;
import com.company.common.billchecks.mapper.BillCheckSources.*;
import com.company.common.common.BillingNumberAllocator;
import com.company.common.clients.service.ClientRules;
import com.company.common.common.BillingCalculationRules;
import com.company.common.common.BillingCalculationRules.MonthlyInput;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.projects.service.ProjectRules;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class BillCheckService {
    private final BillCheckMapper mapper;
    private final ProjectMapper projects;
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;
    private final BillingCalculationRules rules;
    private final BillingNumberAllocator numbers;

    public BillCheckService(BillCheckMapper mapper, ProjectMapper projects,
        BusinessCalendar calendar, CurrentBusinessActor actor,
        BillingCalculationRules rules, BillingNumberAllocator numbers) {
        this.mapper=mapper;
        this.projects=projects;
        this.calendar=calendar;
        this.actor=actor;
        this.rules=rules;
        this.numbers=numbers;
    }

    public CheckResult getCommonBillChecks(String requestedMonth) {
        String month=requestedMonth;
        if (month==null) {
            month=mapper.lastClosedMonth();
            if (month==null) month=calendar.toDatabaseMonth(calendar.currentMonth());
        }
        month(month);
        boolean closed=mapper.closed(month);
        List<CheckRow> items=rows(month,closed);
        var summary=new Summary(items.size(),count(items,"청구 없음"),count(items,"적게 청구"),
            count(items,"많이 청구"),count(items,"맞음"));
        var formula=rules.definitions().stream().filter(d -> "식-88".equals(d.calcId())).findFirst().orElseThrow();
        return new CheckResult(month,summary,items,formula.formula()+"\n"+formula.details(),!closed);
    }

    private static int count(List<CheckRow> rows,String judgement) {
        return (int)rows.stream().filter(r -> judgement.equals(r.judgement())).count();
    }

    private List<CheckRow> rows(String month,boolean closed) {
        var sources=new LinkedHashMap<String,List<Monthly>>();
        for (var source:mapper.monthly(new MonthFilter(month,closed))) {
            if (Set.of("MO","DP").contains(source.projectType()) && !"FREE".equals(source.inputType()))
                sources.computeIfAbsent(source.projectId(),key -> new ArrayList<>()).add(source);
        }
        var invoices=mapper.invoices(month);
        var result=new ArrayList<CheckRow>();
        for (var entry:sources.entrySet()) {
            var inputs=entry.getValue();
            var first=inputs.get(0);
            var bills=invoices.stream().filter(b -> !b.cancelled && b.projectId.equals(entry.getKey())).toList();
            BigDecimal reference=rules.estimate(inputs.stream().map(m -> new MonthlyInput(
                m.projectType(),m.inputType(),m.monthlyMm(),m.billingPrice())).toList());
            BigDecimal billed=bills.stream().map(b -> b.supplyAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
            var check=rules.check(reference,billed,month(month));
            var latest=bills.stream().max(Comparator.comparingInt((Invoice b) -> b.billOrder)
                .thenComparing(b -> b.billId)).orElse(null);
            String judgement=bills.isEmpty()?"청구 없음":check.judgement();
            String action="NONE";
            if (reference.signum()>0 && bills.isEmpty()) {
                if (closed || month(month).getYear()==calendar.today().getYear()) action="MISSING";
            } else if (latest!=null && !"맞음".equals(judgement)) {
                if (closed) action=check.difference().signum()>0?"ADDITIONAL":"DETAIL";
                else if (month(month).getYear()==calendar.today().getYear()) action="CORRECTION";
            }
            String explanation=explanation(inputs,month,check.difference());
            if (closed && "많이 청구".equals(judgement))
                explanation="마감한 달은 감액할 수 없습니다. 청구 상세를 확인해 주세요.";
            result.add(new CheckRow(first.projectId(),first.projectName(),first.projectType(),judgement,
                (int)inputs.stream().map(Monthly::developerId).distinct().count(),
                inputs.stream().map(Monthly::monthlyMm).reduce(BigDecimal.ZERO,BigDecimal::add),
                reference,billed,check.difference(),explanation,latest==null?null:latest.billId,action));
        }
        result.sort(Comparator.comparing(CheckRow::projectName).thenComparing(CheckRow::projectId));
        return result;
    }

    private String explanation(List<Monthly> inputs,String month,BigDecimal difference) {
        var newcomers=new LinkedHashMap<String,BigDecimal>();
        var names=new HashMap<String,String>();
        for (var input:inputs) {
            if (!calendar.toDatabaseMonth(YearMonth.from(input.inputStart())).equals(month)) continue;
            BigDecimal amount=input.monthlyMm().multiply(input.billingPrice()).setScale(0,RoundingMode.DOWN);
            newcomers.merge(input.developerId(),amount,BigDecimal::add);
            names.put(input.developerId(),input.developerName());
        }
        return newcomers.entrySet().stream().filter(e -> difference.signum()>0 && e.getValue().compareTo(difference)==0)
            .map(e -> "새 투입자 "+names.get(e.getKey())+"의 투입 금액과 차이가 같습니다.")
            .findFirst().orElse("");
    }

    public Input parse(JsonNode body) {
        ClientRules.object(body,Set.of("projectId","checkMonth","vatRate","billDate","payDueDate","taxInvoiceNo"));
        String project=ProjectRules.text(body,"projectId",20,true);
        String selected=ProjectRules.text(body,"checkMonth",6,true);
        month(selected);
        var rate=body.get("vatRate");
        if (rate==null || !rate.isNumber()) throw CodeFailure.invalid("vatRate","부가세율을 입력해 주세요.");
        BillingCalculationRules.rate(rate.decimalValue(),"vatRate");
        LocalDate date=ProjectRules.date(body,"billDate"),due=ProjectRules.date(body,"payDueDate");
        if (due.isBefore(date)) throw CodeFailure.invalid("payDueDate","입금 예정일은 청구일 이후여야 합니다.");
        return new Input(project,selected,rate.decimalValue(),date,due,
            ProjectRules.text(body,"taxInvoiceNo",30,false));
    }

    private YearMonth month(String value) {
        try { return calendar.fromDatabaseMonth(value); }
        catch (RuntimeException exception) { throw CodeFailure.invalid("checkMonth","점검 월은 YYYYMM 형식으로 입력해 주세요."); }
    }

    @Transactional
    public Detail postCommonBillCheckMissing(JsonNode body) { return execute(parse(body),"MISSING"); }
    @Transactional
    public Detail postCommonBillCheckAdditional(JsonNode body) { return execute(parse(body),"ADDITIONAL"); }
    @Transactional
    public Detail postCommonBillCheckCorrection(JsonNode body) { return execute(parse(body),"CORRECTION"); }

    private Detail execute(Input input,String action) {
        // Same first lock as assignment/project writes; table locks cover an empty month/project too.
        projects.lockSources();
        mapper.lockMonthlySources();
        mapper.lockInvoices();
        boolean closed=mapper.closed(input.checkMonth());
        var checked=rows(input.checkMonth(),closed).stream()
            .filter(row -> row.projectId().equals(input.projectId())).findFirst()
            .orElseThrow(() -> CodeFailure.invalid("projectId","월단위·파견의 유상 투입 점검행을 다시 조회해 주세요."));
        if (!action.equals(checked.allowedAction()))
            throw CodeFailure.invalid("checkMonth","점검 조건이 변경되었습니다. 다시 조회해 주세요.");
        if ("ADDITIONAL".equals(action) && (!closed || checked.difference().signum()<=0))
            throw CodeFailure.invalid("checkMonth","마감한 달의 부족 차액만 추가 청구할 수 있습니다.");
        String id;
        int order;
        BigDecimal supply,receipts=BigDecimal.ZERO;
        if ("CORRECTION".equals(action)) {
            if (closed || month(input.checkMonth()).getYear()!=calendar.today().getYear())
                throw CodeFailure.invalid("checkMonth","올해 미마감월만 수정할 수 있습니다.");
            var latest=mapper.invoice(checked.latestBillId());
            if (latest==null || latest.cancelled || !latest.billMonth.equals(input.checkMonth()))
                throw CodeFailure.invalid("checkMonth","최근 청구가 변경되었습니다. 다시 조회해 주세요.");
            id=latest.billId;
            order=latest.billOrder;
            supply=latest.supplyAmount.add(checked.difference());
            receipts=mapper.normalReceipts(id);
        } else {
            supply="MISSING".equals(action)?checked.referenceAmount():checked.difference();
            var identity=numbers.allocate(input.projectId(),input.checkMonth());
            order=identity.billOrder();
            id=identity.billId();
        }
        var amounts=rules.amounts(supply,input.vatRate());
        rules.balance(amounts.totalAmount(),receipts);
        var write=new Write(id,order,input,amounts,receipts,actor.userId());
        int changed="CORRECTION".equals(action)?mapper.correct(write):mapper.insert(write);
        if (changed!=1) throw CodeFailure.conflict();
        return detail(id);
    }

    private Detail detail(String id) {
        var b=mapper.invoice(id);
        if (b==null) throw new IllegalStateException("저장한 청구를 조회하지 못했습니다.");
        LocalDate today=calendar.today();
        boolean editable=!b.cancelled && month(b.billMonth).getYear()==today.getYear() && !mapper.closed(b.billMonth);
        boolean receive=!b.cancelled && b.unpaidAmount.signum()>0
            && !mapper.closed(calendar.toDatabaseMonth(calendar.currentMonth()));
        boolean latest=id.equals(mapper.latestProjectBill(b.projectId));
        boolean quick=editable && latest && "ING".equals(b.projectStatus)
            && Set.of("MO","DP","TK").contains(b.projectType);
        boolean nextAllowed=quick && Set.of("MO","DP").contains(b.projectType)
            && !month(b.billMonth).plusMonths(1).isAfter(YearMonth.from(b.projectEnd));
        var receipts=mapper.receipts(id);
        boolean canCancel=editable && receipts.stream().noneMatch(r -> !r.cancelled());
        var bill=new Bill(b.billId,b.projectId,b.projectName,b.customerName,b.billOrder,b.billMonth,
            b.billDate,b.supplyAmount,b.vatRate,b.vatAmount,b.totalAmount,b.receiptSumAmount,b.unpaidAmount,
            b.payDueDate,b.taxInvoiceNo,rules.status(b.cancelled,b.unpaidAmount,b.payDueDate,today),
            b.cancelled,b.cancelReason,b.cancelledBy,b.cancelledAt,
            rules.dunningStep(b.cancelled,b.unpaidAmount,b.payDueDate,today),
            (int)Math.max(0,ChronoUnit.DAYS.between(b.payDueDate,today)),editable,canCancel,receive,
            quick,nextAllowed);
        return new Detail(bill,receipts,b.taxInvoiceNo==null || b.taxInvoiceNo.isBlank());
    }
}
