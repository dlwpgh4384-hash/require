package com.company.common.billchecks.service;

import com.company.common.billchecks.dto.BillCheckContracts.*;
import com.company.common.billchecks.mapper.BillCheckMapper;
import com.company.common.billchecks.mapper.BillCheckSources.*;
import com.company.common.common.BillingNumberAllocator;
import com.company.common.common.BillingCalculationRules;
import com.company.common.common.BillingCalculationRules.MonthlyInput;
import com.company.common.common.BillingInputRules;
import com.company.common.projects.mapper.ProjectMapper;
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
        String lastClosed=mapper.lastClosedMonth();
        String month=requestedMonth;
        if (month==null) {
            month=lastClosed;
            if (month==null) month=calendar.toDatabaseMonth(calendar.currentMonth());
        }
        month(month);
        boolean closed=mapper.closed(month);
        List<CheckRow> items=rows(month,closed);
        var summary=new Summary(items.size(),count(items,"청구 없음"),count(items,"적게 청구"),
            count(items,"많이 청구"),count(items,"맞음"));
        var formula=rules.definitions().stream().filter(d -> "식-88".equals(d.calcId())).findFirst().orElseThrow();
        // 시안 점검 월 단추: 올해 청구가 있는 달 중 이번 달까지(+ 지금 고른 달)
        String current=calendar.toDatabaseMonth(calendar.currentMonth());
        var months=new TreeSet<String>();
        var found=mapper.billMonths(current.substring(0,4),current);
        if (found!=null) months.addAll(found);
        months.add(month);
        return new CheckResult(month,summary,items,formula.formula()+"\n"+formula.details(),!closed,
            List.copyOf(months),lastClosed);
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
        boolean thisYear=month(month).getYear()==calendar.today().getYear();
        var result=new ArrayList<CheckRow>();
        for (var entry:sources.entrySet()) {
            var inputs=entry.getValue();
            var first=inputs.get(0);
            var bills=invoices.stream().filter(b -> !b.cancelled && b.projectId.equals(entry.getKey())).toList();
            BigDecimal reference=rules.estimate(inputs.stream().map(m -> new MonthlyInput(
                m.projectType(),m.inputType(),m.monthlyMm(),m.billingPrice())).toList());
            BigDecimal billed=bills.stream().map(b -> b.supplyAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
            var check=rules.check(reference,billed,month(month));
            // 시안 bchkFix: 고칠 청구는 그 달 청구 중 청구일이 가장 늦은 것
            var latest=bills.stream().max(Comparator.comparing((Invoice b) -> b.billDate)
                .thenComparingInt(b -> b.billOrder).thenComparing(b -> b.billId)).orElse(null);
            String judgement=bills.isEmpty()?"청구 없음":check.judgement();
            String action="NONE";
            // 마감한 달의 예외 처리(추가 청구·상세 안내)를 포함해 [청구 등록]·[청구 고치기]는 올해 달만
            if (thisYear) {
                if (reference.signum()>0 && bills.isEmpty()) action="MISSING";
                else if (latest!=null && !"맞음".equals(judgement)) {
                    if (closed) action=check.difference().signum()>0?"ADDITIONAL":"DETAIL";
                    else action="CORRECTION";
                }
            }
            result.add(new CheckRow(first.projectId(),first.projectName(),first.projectType(),judgement,
                (int)inputs.stream().map(Monthly::developerId).distinct().count(),
                inputs.stream().map(Monthly::monthlyMm).reduce(BigDecimal.ZERO,BigDecimal::add),
                reference,billed,check.difference(),explanation(judgement,inputs,month,check),
                latest==null?null:latest.billId,action));
        }
        result.sort(Comparator.comparing(CheckRow::projectName).thenComparing(CheckRow::projectId));
        return result;
    }

    /**
     * 시안 billCheck 설명: 청구 없음 「투입은 있는데 이 달 청구가 없습니다」, 금액 차이는 이 달 새로 투입한 사람의
     * 금액이 차이와 판정 기준 안으로 같으면 그 사람들(이름(n.nn MM))을, 아니면 적게/많이 청구 안내.
     * 판정 기준은 판정(식-88)과 같은 값: max(운영 기준값 BILL_MISS_MIN, 투입 기준 금액 × BILL_MISS_RT%), 그 달 적용분.
     */
    private String explanation(String judgement,List<Monthly> inputs,String month,BillingCalculationRules.CheckResult check) {
        if ("청구 없음".equals(judgement)) return "투입은 있는데 이 달 청구가 없습니다";
        if ("맞음".equals(judgement)) return "";
        BigDecimal difference=check.difference();
        BigDecimal tolerance=check.threshold();
        var hits=inputs.stream().filter(input -> calendar.toDatabaseMonth(YearMonth.from(input.inputStart())).equals(month))
            .filter(input -> input.monthlyMm().multiply(input.billingPrice()).setScale(0,RoundingMode.DOWN)
                .subtract(difference).abs().compareTo(tolerance)<=0)
            .map(input -> input.developerName()+"("+input.monthlyMm().setScale(2,RoundingMode.HALF_UP).toPlainString()+" MM)")
            .toList();
        if (!hits.isEmpty()) return "이 달 새로 투입한 "+String.join(", ",hits)+"이 청구에서 빠진 것으로 보입니다";
        return difference.signum()>0?"투입 기준보다 적게 청구했습니다":"투입 기준보다 많이 청구했습니다";
    }

    /** correction=[청구 고치기]: 화면이 읽은 고칠 청구의 수정 시각(modifiedAt)이 꼭 있어야 한다. */
    public Input parse(JsonNode body, boolean correction) {
        var fields=new HashSet<>(Set.of("projectId","checkMonth","vatRate","billDate","payDueDate","taxInvoiceNo","supplyAmount"));
        if (correction) fields.add("modifiedAt");
        BillingInputRules.object(body,fields);
        String project=BillingInputRules.text(body,"projectId","프로젝트",20,true);
        String selected=BillingInputRules.text(body,"checkMonth","점검 월",6,true);
        month(selected);
        var rate=body.get("vatRate");
        if (rate==null || !rate.isNumber()) throw CodeFailure.invalid("vatRate","부가세율을 입력해 주세요.");
        BillingCalculationRules.rate(rate.decimalValue(),"vatRate");
        LocalDate date=BillingInputRules.date(body,"billDate","청구일");
        LocalDate due=BillingInputRules.date(body,"payDueDate","입금 예정일");
        if (due.isBefore(date)) throw CodeFailure.invalid("payDueDate","입금 예정일이 청구일보다 빠릅니다.");
        BigDecimal supply=null;
        if (body.hasNonNull("supplyAmount")) {
            supply=BillingInputRules.money(body,"supplyAmount","공급가",true);
            if (supply.signum()==0) throw CodeFailure.invalid("supplyAmount",BillingInputRules.required("공급가"));
            BillingCalculationRules.positiveMoney(supply,"supplyAmount");
        }
        return new Input(project,selected,rate.decimalValue(),date,due,
            BillingInputRules.text(body,"taxInvoiceNo","세금계산서 번호",30,false),supply,
            correction?BillingInputRules.token(body):null);
    }

    private YearMonth month(String value) {
        try { return calendar.fromDatabaseMonth(value); }
        catch (RuntimeException exception) { throw CodeFailure.invalid("checkMonth","점검 월은 YYYYMM 형식으로 입력해 주세요."); }
    }

    @Transactional
    public Detail postCommonBillCheckMissing(JsonNode body) { return execute(parse(body,false),"MISSING"); }
    @Transactional
    public Detail postCommonBillCheckAdditional(JsonNode body) { return execute(parse(body,false),"ADDITIONAL"); }
    @Transactional
    public Detail postCommonBillCheckCorrection(JsonNode body) { return execute(parse(body,true),"CORRECTION"); }

    private Detail execute(Input input,String action) {
        // Same first lock as assignment/project writes; table locks cover an empty month/project too.
        projects.lockSources();
        mapper.lockMonthlySources();
        mapper.lockInvoices();
        boolean closed=mapper.closed(input.checkMonth());
        var checked=rows(input.checkMonth(),closed).stream()
            .filter(row -> row.projectId().equals(input.projectId())).findFirst()
            .orElseThrow(() -> CodeFailure.invalid("projectId","월단위·파견의 유상 투입 점검행을 다시 조회해 주세요."));
        if ("CORRECTION".equals(action) && checked.latestBillId()!=null) {
            // 00-1 동시 수정: 화면이 읽은 뒤 고칠 청구가 바뀌었으면(다른 청구가 최근이 된 경우 포함) 덮어쓰지 않는다.
            var current=mapper.invoice(checked.latestBillId());
            if (current==null || !Objects.equals(current.modifiedAt,input.modifiedAt())) throw CodeFailure.conflict();
        }
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
            supply=input.supplyAmount()!=null?input.supplyAmount():latest.supplyAmount.add(checked.difference());
            receipts=mapper.normalReceipts(id);
        } else {
            // 창에서 고친 공급가를 받는다(시안 「…고쳐서 등록할 수 있습니다」). 비우면 투입 기준 금액·차액.
            supply=input.supplyAmount()!=null?input.supplyAmount()
                :"MISSING".equals(action)?checked.referenceAmount():checked.difference();
            var identity=numbers.allocate(input.projectId(),input.checkMonth());
            order=identity.billOrder();
            id=identity.billId();
        }
        var amounts=rules.amounts(supply,input.vatRate());
        if (amounts.totalAmount().compareTo(receipts)<0)
            // 청구 [수정]과 같은 문구(천 단위 쉼표, 시안 won())
            throw CodeFailure.invalid("supplyAmount","합계가 이미 받은 금액("
                +new java.text.DecimalFormat("#,##0").format(receipts)+"원)보다 작습니다.");
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
        boolean thisYear=month(b.billMonth).getYear()==today.getYear();
        boolean editable=!b.cancelled && thisYear && !mapper.closed(b.billMonth);
        boolean receive=!b.cancelled && b.unpaidAmount.signum()>0
            && !mapper.closed(calendar.toDatabaseMonth(calendar.currentMonth()));
        boolean latest=id.equals(mapper.latestProjectBill(b.projectId));
        boolean quick=!b.cancelled && thisYear && latest && "ING".equals(b.projectStatus)
            && Set.of("MO","DP","TK").contains(b.projectType) && b.projectEnd!=null
            && !month(b.billMonth).plusMonths(1).isAfter(YearMonth.from(b.projectEnd));
        boolean nextAllowed=quick && Set.of("MO","DP").contains(b.projectType);
        var receipts=mapper.receipts(id);
        boolean canCancel=editable && receipts.stream().noneMatch(r -> !r.cancelled());
        var bill=new Bill(b.billId,b.projectId,b.projectName,b.customerName,b.billOrder,b.billMonth,
            b.billDate,b.supplyAmount,b.vatRate,b.vatAmount,b.totalAmount,b.receiptSumAmount,b.unpaidAmount,
            b.payDueDate,b.taxInvoiceNo,rules.status(b.cancelled,b.unpaidAmount,b.payDueDate,today),
            b.cancelled,b.cancelReason,b.cancelledBy,b.cancelledAt,
            rules.dunningStep(b.cancelled,b.unpaidAmount,b.payDueDate,today),
            (int)Math.max(0,ChronoUnit.DAYS.between(b.payDueDate,today)),editable,canCancel,receive,
            quick,nextAllowed,b.modifiedAt);
        return new Detail(bill,receipts,b.taxInvoiceNo==null || b.taxInvoiceNo.isBlank());
    }
}
