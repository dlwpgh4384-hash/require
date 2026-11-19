package com.company.common.bills.service;

import com.company.common.bills.dto.BillContracts.*;
import com.company.common.bills.mapper.BillMapper;
import com.company.common.bills.mapper.BillRows.*;
import com.company.common.common.BillingCalculationRules;
import com.company.common.common.BillingNumberAllocator;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class CommonBillService {
    private final BillMapper mapper;
    private final ProjectMapper projects;
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;
    private final BillingCalculationRules rules;
    private final BillingNumberAllocator numbers;

    public CommonBillService(BillMapper mapper, ProjectMapper projects, BusinessCalendar calendar,
        CurrentBusinessActor actor, BillingCalculationRules rules, BillingNumberAllocator numbers) {
        this.mapper=mapper;
        this.projects=projects;
        this.calendar=calendar;
        this.actor=actor;
        this.rules=rules;
        this.numbers=numbers;
    }

    public BillList getCommonBills(Filter filter) {
        if (filter.year()==null || filter.year()<1 || filter.year()>9999)
            throw CodeFailure.invalid("year","연도를 확인해 주세요.");
        if (filter.status()!=null && !Set.of("PAID","UNPAID","LATE","CNCL").contains(filter.status()))
            throw CodeFailure.invalid("status","청구 상태를 확인해 주세요.");
        if (filter.billMonth()!=null) BillInput.month(filter.billMonth(),calendar);
        if (filter.projectId()!=null && (filter.projectId().isBlank() || filter.projectId().length()>20))
            throw CodeFailure.invalid("projectId","프로젝트를 확인해 주세요.");
        boolean current=filter.year()==calendar.today().getYear();
        boolean canReceive=!mapper.closed(calendar.toDatabaseMonth(calendar.currentMonth()));
        var all=mapper.yearRows(filter.year()).stream().map(r -> view(r,canReceive)).toList();
        var active=all.stream().filter(b -> !b.cancelled()).toList();
        var unpaid=active.stream().filter(b -> b.unpaidAmount().signum()>0).toList();
        var summary=new Summary(active.size(),sum(active,Bill::totalAmount),
            current?sum(active.stream().filter(b -> b.billMonth().equals(
                calendar.toDatabaseMonth(calendar.currentMonth()))).toList(),Bill::totalAmount):BigDecimal.ZERO,
            unpaid.size(),sum(unpaid,Bill::unpaidAmount),
            (int)active.stream().filter(b -> "LATE".equals(b.status())).count());
        var items=all.stream().filter(b -> filter.status()==null || filter.status().equals(b.status()))
            .filter(b -> filter.billMonth()==null || filter.billMonth().equals(b.billMonth()))
            .filter(b -> filter.projectId()==null || filter.projectId().equals(b.projectId())).toList();
        var included=items.stream().filter(b -> !b.cancelled()).toList();
        return new BillList(items,summary,new Totals(sum(included,Bill::supplyAmount),
            sum(included,Bill::vatAmount),sum(included,Bill::totalAmount),
            sum(included,Bill::receiptSumAmount),sum(included,Bill::unpaidAmount)),!current);
    }

    private static BigDecimal sum(List<Bill> rows, Function<Bill,BigDecimal> value) {
        return rows.stream().map(value).reduce(BigDecimal.ZERO,BigDecimal::add);
    }

    public Detail getCommonBill(String id) {
        var row=required(id,false);
        return new Detail(view(row,!mapper.closed(calendar.toDatabaseMonth(calendar.currentMonth()))),
            mapper.receipts(id),row.taxInvoiceNo()==null || row.taxInvoiceNo().isBlank());
    }

    private Bill view(Invoice row, boolean receivingOpen) {
        boolean editable=!row.cancelled() && !row.closed()
            && calendar.fromDatabaseMonth(row.billMonth()).getYear()==calendar.today().getYear();
        boolean quick=editable && row.latest() && "ING".equals(row.projectStatus())
            && Set.of("MO","DP","TK").contains(row.projectType());
        YearMonth next=calendar.fromDatabaseMonth(row.billMonth()).plusMonths(1);
        boolean nextAllowed=quick && Set.of("MO","DP").contains(row.projectType())
            && !next.isAfter(YearMonth.from(row.projectEnd()));
        return new Bill(row.billId(),row.projectId(),row.projectName(),row.customerName(),
            row.billOrder(),row.billMonth(),row.billDate(),row.supplyAmount(),row.vatRate(),
            row.vatAmount(),row.totalAmount(),row.receiptSumAmount(),row.unpaidAmount(),row.payDueDate(),
            row.taxInvoiceNo(),rules.status(row.cancelled(),row.unpaidAmount(),row.payDueDate(),calendar.today()),
            row.cancelled(),row.cancelReason(),row.cancelledBy(),row.cancelledAt(),
            rules.dunningStep(row.cancelled(),row.unpaidAmount(),row.payDueDate(),calendar.today()),
            (int)Math.max(0,ChronoUnit.DAYS.between(row.payDueDate(),calendar.today())),editable,
            editable && row.normalReceiptCount()==0,receivingOpen && !row.cancelled()
                && row.unpaidAmount().signum()>0,quick,nextAllowed);
    }

    Invoice required(String id, boolean lock) {
        if (id==null || id.isBlank() || id.length()>20 || id.chars().anyMatch(Character::isISOControl))
            throw CodeFailure.invalid("billId","청구번호를 확인해 주세요.");
        Invoice row=lock?mapper.lockInvoice(id):mapper.invoice(id);
        if (row==null) throw new CodeFailure(404,"billId","청구를 찾을 수 없습니다.");
        return row;
    }

    private void open(String month) {
        if (calendar.fromDatabaseMonth(month).getYear()!=calendar.today().getYear())
            throw CodeFailure.invalid("billMonth","올해 청구만 등록·수정·취소할 수 있습니다.");
        if (mapper.closed(month)) throw CodeFailure.invalid("billMonth",month+"은 마감한 달입니다. 청구 내용을 변경할 수 없습니다.");
    }

    private void mutable(Invoice bill) {
        if (bill.cancelled()) throw CodeFailure.invalid("billId","이미 취소된 청구입니다.");
        open(bill.billMonth());
    }

    private void eligible(String projectId) {
        var project=mapper.project(projectId);
        if (project==null || !"ING".equals(project.projectStatus())
            || !Set.of("MO","DP","TK").contains(project.projectType()))
            throw CodeFailure.invalid("projectId","진행 중인 비내부 프로젝트만 청구할 수 있습니다.");
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public SaveResponse postCommonBill(JsonNode body) {
        var input=BillInput.create(body,calendar);
        var amounts=rules.amounts(input.values().supplyAmount(),input.values().vatRate());
        projects.lockSources();
        eligible(input.projectId());
        open(input.billMonth());
        if (input.sourceBillId()!=null) {
            var source=required(input.sourceBillId(),true);
            mutable(source);
            var sourceView=view(source,false);
            if (!sourceView.canCopy()) throw CodeFailure.invalid("sourceBillId","최근 진행 중 청구만 고쳐서 청구할 수 있습니다.");
            if (!source.projectId().equals(input.projectId()) || !source.billMonth().equals(input.billMonth()))
                throw CodeFailure.invalid("sourceBillId","원청구의 프로젝트와 청구월은 바꿀 수 없습니다.");
        }
        if (mapper.exists(input.projectId(),input.billMonth()) && !input.duplicateConfirmed())
            return SaveResponse.warning("같은 프로젝트·청구월의 청구가 있습니다. 다시 등록하면 추가 청구됩니다.");
        var identity=numbers.allocate(input.projectId(),input.billMonth());
        if (mapper.insert(new Write(identity.billId(),input.projectId(),identity.billOrder(),
            input.billMonth(),input.values(),amounts,BigDecimal.ZERO,actor.userId()))!=1)
            throw CodeFailure.conflict();
        return SaveResponse.saved(getCommonBill(identity.billId()));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Detail putCommonBill(String id, JsonNode body) {
        Values input=BillInput.edit(body);
        var amounts=rules.amounts(input.supplyAmount(),input.vatRate());
        projects.lockSources();
        var bill=required(id,true);
        mutable(bill);
        BigDecimal receipts=mapper.normalReceipts(id);
        if (amounts.totalAmount().compareTo(receipts)<0)
            throw CodeFailure.invalid("supplyAmount","청구 합계는 정상 입금 합계보다 작을 수 없습니다.");
        rules.balance(amounts.totalAmount(),receipts);
        if (mapper.update(new Write(id,bill.projectId(),bill.billOrder(),bill.billMonth(),input,
            amounts,receipts,actor.userId()))!=1) throw CodeFailure.conflict();
        return getCommonBill(id);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Detail postCommonBillCancel(String id, JsonNode body) {
        String reason=BillInput.reason(body);
        projects.lockSources();
        var bill=required(id,true);
        mutable(bill);
        if (bill.normalReceiptCount()>0)
            throw CodeFailure.invalid("billId","입금이 있습니다. 먼저 입금을 취소해 주세요.");
        if (mapper.cancel(new Cancellation(id,reason,actor.userId()))!=1) throw CodeFailure.conflict();
        return getCommonBill(id);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Detail postCommonBillNextMonth(String id) {
        projects.lockSources();
        mapper.lockMonthlySources();
        var bill=required(id,true);
        mutable(bill);
        if (!view(bill,false).canNextMonth())
            throw CodeFailure.invalid("billId","최근 진행 중 월단위·파견 청구이며 다음 달이 계약기간 이내여야 합니다.");
        YearMonth next=calendar.fromDatabaseMonth(bill.billMonth()).plusMonths(1);
        String month=calendar.toDatabaseMonth(next);
        open(month);
        if (mapper.exists(bill.projectId(),month))
            throw CodeFailure.invalid("billMonth","다음 달 청구가 이미 있습니다.");
        var supply=rules.estimate(mapper.monthly(bill.projectId(),month));
        if (supply.signum()<=0) throw CodeFailure.invalid("supplyAmount","다음 달 투입 기준 공급가가 0원입니다.");
        var amounts=rules.amounts(supply,bill.vatRate());
        var values=new Values(supply,bill.vatRate(),calendar.today(),next.plusMonths(1).atDay(25),null);
        var identity=numbers.allocate(bill.projectId(),month);
        if (mapper.insert(new Write(identity.billId(),bill.projectId(),identity.billOrder(),month,
            values,amounts,BigDecimal.ZERO,actor.userId()))!=1) throw CodeFailure.conflict();
        return getCommonBill(identity.billId());
    }
}
