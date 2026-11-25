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
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
public class CommonBillService {
    static final String SCREEN="청구·입금";
    private final BillMapper mapper;
    private final ProjectMapper projects;
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;
    private final BillingCalculationRules rules;
    private final BillingNumberAllocator numbers;
    private final ExcelExportService excel;

    public CommonBillService(BillMapper mapper, ProjectMapper projects, BusinessCalendar calendar,
        CurrentBusinessActor actor, BillingCalculationRules rules, BillingNumberAllocator numbers,
        ExcelExportService excel) {
        this.mapper=mapper;
        this.projects=projects;
        this.calendar=calendar;
        this.actor=actor;
        this.rules=rules;
        this.numbers=numbers;
        this.excel=excel;
    }

    /** 메뉴 배지(SFR-001-04): 취소 안 된 청구 중 미수 잔액이 남은 건수. 로그인 뒤 데이터 범위는 마지막 로그인(UI-22) 작업에서 더한다. */
    public UnpaidCount getCommonBillUnpaidCount() {
        return new UnpaidCount(mapper.unpaidCount());
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

    /**
     * SFR-011: 목록과 같은 조건(year·status·billMonth·projectId)의 전체 결과를 목록 열 그대로 xlsx로 만든다.
     * 파일 1행에 받은 사람·한국 시각, 이름은 「청구·입금_{해}년_다운로드일시.xlsx」, 접근 기록 DNLD.
     */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ExcelFile getCommonBillsExcel(Filter filter) {
        var items=getCommonBills(filter).items();
        List<List<String>> rows=items.stream().map(b -> List.of(b.billId(),b.projectName(),b.billOrder()+"차",
            b.billDate().toString(),won(b.supplyAmount()),won(b.vatAmount()),won(b.totalAmount()),
            won(b.receiptSumAmount()),won(b.unpaidAmount()),b.payDueDate().toString(),statusName(b.status()),
            dunning(b))).toList();
        var now=calendar.now();
        byte[] bytes=excel.write(SCREEN,List.of("청구번호","프로젝트","차수","청구일","공급가","부가세","합계",
            "입금액","미수금","입금 예정일","상태","미수 알림"),rows,actor.userId(),now);
        mapper.insertAccess(actor.userId(),SCREEN+" "+filter.year()+"년 "+items.size()+"건");
        return new ExcelFile(bytes,excel.fileName(SCREEN,filter.year()+"년",now),items.size());
    }

    private static String won(BigDecimal value) {
        return value==null?"":new DecimalFormat("#,##0").format(value);
    }

    /** 화면 상태 글자(BILL_STAT): 입금 완료·미수·연체·취소됨. */
    private static String statusName(String code) {
        return switch (code) {
            case "PAID" -> "입금 완료";
            case "UNPAID" -> "미수";
            case "LATE" -> "연체";
            case "CNCL" -> "취소됨";
            default -> code;
        };
    }

    /** 화면 미수 알림 칸과 같은 글자: 「n단계 · d일」, 1단계 전이면 「d일 지남」, 그 밖 「–」. */
    private static String dunning(Bill b) {
        if (b.cancelled() || b.unpaidAmount().signum()<=0 || b.daysOverdue()<=0) return "–";
        return b.dunningStep()==null?b.daysOverdue()+"일 지남":b.dunningStep()+"단계 · "+b.daysOverdue()+"일";
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
        boolean thisYear=calendar.fromDatabaseMonth(row.billMonth()).getYear()==calendar.today().getYear();
        boolean editable=!row.cancelled() && !row.closed() && thisYear;
        // 빠른 청구(SFR-113-12): 프로젝트의 가장 최근 청구(청구월이 가장 늦은 것) 줄에만 둔다. 원청구 달의 마감은
        // 상관없고, 새로 만들 다음 달의 마감은 만들 때 본다.
        YearMonth next=calendar.fromDatabaseMonth(row.billMonth()).plusMonths(1);
        boolean quick=!row.cancelled() && thisYear && row.latest() && "ING".equals(row.projectStatus())
            && Set.of("MO","DP","TK").contains(row.projectType())
            && row.projectEnd()!=null && !next.isAfter(YearMonth.from(row.projectEnd()));
        boolean nextAllowed=quick && Set.of("MO","DP").contains(row.projectType());
        return new Bill(row.billId(),row.projectId(),row.projectName(),row.customerName(),
            row.billOrder(),row.billMonth(),row.billDate(),row.supplyAmount(),row.vatRate(),
            row.vatAmount(),row.totalAmount(),row.receiptSumAmount(),row.unpaidAmount(),row.payDueDate(),
            row.taxInvoiceNo(),rules.status(row.cancelled(),row.unpaidAmount(),row.payDueDate(),calendar.today()),
            row.cancelled(),row.cancelReason(),row.cancelledBy(),row.cancelledAt(),
            rules.dunningStep(row.cancelled(),row.unpaidAmount(),row.payDueDate(),calendar.today()),
            (int)Math.max(0,ChronoUnit.DAYS.between(row.payDueDate(),calendar.today())),editable,
            editable && row.normalReceiptCount()==0,receivingOpen && !row.cancelled()
                && row.unpaidAmount().signum()>0,quick,nextAllowed,row.modifiedAt());
    }

    Invoice required(String id, boolean lock) {
        if (id==null || id.isBlank() || id.length()>20 || id.chars().anyMatch(Character::isISOControl))
            throw CodeFailure.invalid("billId","청구번호를 확인해 주세요.");
        Invoice row=lock?mapper.lockInvoice(id):mapper.invoice(id);
        if (row==null) throw new CodeFailure(404,"billId","청구를 찾을 수 없습니다.");
        return row;
    }

    private static String display(String month) {
        return month.substring(0,4)+"-"+month.substring(4);
    }

    private void thisYear(String month) {
        if (calendar.fromDatabaseMonth(month).getYear()!=calendar.today().getYear())
            throw CodeFailure.invalid("billMonth","올해 청구만 등록·수정·취소할 수 있습니다.");
    }

    /** 고칠 청구의 달: 올해이고 마감 전. */
    private void open(String month) {
        thisYear(month);
        if (mapper.closed(month)) throw CodeFailure.invalid("billMonth",display(month)+"은 마감한 달이라 청구를 고칠 수 없습니다.");
    }

    /**
     * 새 청구를 만들 달: 올해이고 마감 전(시안 「YYYY-MM은 마감한 달이라 청구를 만들 수 없습니다.」).
     * 12월에 [다음 달 청구]·[고쳐서 청구]로 만드는 다음 해 1월(이번 달의 다음 달)도 허용한다(2026-11-24 사용자 결정).
     */
    private void creatable(String month) {
        if (!calendar.fromDatabaseMonth(month).equals(calendar.currentMonth().plusMonths(1))) thisYear(month);
        if (mapper.closed(month)) throw CodeFailure.invalid("billMonth",display(month)+"은 마감한 달이라 청구를 만들 수 없습니다.");
    }

    private void mutable(Invoice bill) {
        if (bill.cancelled()) throw CodeFailure.invalid("billId","이미 취소된 청구입니다.");
        open(bill.billMonth());
    }

    /** 00-1 동시 수정: 화면이 읽은 수정 시각과 지금 값이 다르면 409(공통 문구). */
    private static void current(Invoice bill, Instant token) {
        if (!Objects.equals(bill.modifiedAt(),token)) throw CodeFailure.conflict();
    }

    private void eligible(String projectId) {
        var project=mapper.project(projectId);
        if (project==null || !"ING".equals(project.projectStatus())
            || !Set.of("MO","DP","TK").contains(project.projectType()))
            throw CodeFailure.invalid("projectId","진행 중인 비내부 프로젝트만 청구할 수 있습니다.");
    }

    /** 시안 중복 경고: 「이 프로젝트의 YYYY-MM 청구가 이미 있습니다(청구번호 · n차). 같은 달 청구를 하나 더 만들려면 [등록]을 한 번 더 누르세요.」 */
    private String duplicate(String projectId, String month) {
        var same=mapper.sameMonth(projectId,month);
        String list=same==null || same.isEmpty()?"":"("+same.stream().map(b -> b.billId()+" · "+b.billOrder()+"차")
            .collect(Collectors.joining(", "))+")";
        return "이 프로젝트의 "+display(month)+" 청구가 이미 있습니다"+list
            +". 같은 달 청구를 하나 더 만들려면 [등록]을 한 번 더 누르세요.";
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public SaveResponse postCommonBill(JsonNode body) {
        var input=BillInput.create(body,calendar,rules);
        var amounts=rules.amounts(input.values().supplyAmount(),input.values().vatRate());
        projects.lockSources();
        eligible(input.projectId());
        if (input.sourceBillId()!=null) {
            // [고쳐서 청구](시안 billcopy): 원청구를 바탕으로 같은 프로젝트의 다음 달 청구를 새로 만든다. 원청구는 그대로 둔다.
            var source=required(input.sourceBillId(),true);
            if (source.cancelled()) throw CodeFailure.invalid("sourceBillId","취소된 청구는 고쳐서 청구할 수 없습니다.");
            if (!view(source,false).canCopy())
                throw CodeFailure.invalid("sourceBillId","진행 중인 프로젝트의 가장 최근 청구만 고쳐서 청구할 수 있습니다.");
            String next=calendar.toDatabaseMonth(calendar.fromDatabaseMonth(source.billMonth()).plusMonths(1));
            if (!source.projectId().equals(input.projectId()) || !next.equals(input.billMonth()))
                throw CodeFailure.invalid("sourceBillId","고쳐서 청구는 같은 프로젝트의 다음 달("+display(next)+") 청구로만 만듭니다.");
        }
        creatable(input.billMonth());
        if (mapper.exists(input.projectId(),input.billMonth()) && !input.duplicateConfirmed())
            return SaveResponse.warning(duplicate(input.projectId(),input.billMonth()));
        var identity=numbers.allocate(input.projectId(),input.billMonth());
        if (mapper.insert(new Write(identity.billId(),input.projectId(),identity.billOrder(),
            input.billMonth(),input.values(),amounts,BigDecimal.ZERO,actor.userId()))!=1)
            throw CodeFailure.conflict();
        return SaveResponse.saved(getCommonBill(identity.billId()));
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Detail putCommonBill(String id, JsonNode body) {
        var found=required(id,false);
        EditInput edit=BillInput.edit(body,() -> rules.defaultVatRate(calendar.fromDatabaseMonth(found.billMonth())));
        Values input=edit.values();
        var amounts=rules.amounts(input.supplyAmount(),input.vatRate());
        projects.lockSources();
        var bill=required(id,true);
        mutable(bill);
        current(bill,edit.modifiedAt());
        BigDecimal receipts=mapper.normalReceipts(id);
        if (amounts.totalAmount().compareTo(receipts)<0)
            throw CodeFailure.invalid("supplyAmount","합계가 이미 받은 금액("+won(receipts)+"원)보다 작습니다.");
        rules.balance(amounts.totalAmount(),receipts);
        if (mapper.update(new Write(id,bill.projectId(),bill.billOrder(),bill.billMonth(),input,
            amounts,receipts,actor.userId(),edit.modifiedAt()))!=1) throw CodeFailure.conflict();
        return getCommonBill(id);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Detail postCommonBillCancel(String id, JsonNode body) {
        CancelInput input=BillInput.cancel(body);
        projects.lockSources();
        var bill=required(id,true);
        if (bill.cancelled()) throw CodeFailure.invalid("billId","이미 취소된 청구입니다.");
        thisYear(bill.billMonth());
        if (mapper.closed(bill.billMonth()))
            throw CodeFailure.invalid("billMonth",display(bill.billMonth())
                +"는 마감한 달이라 이 청구는 취소할 수 없습니다(고칠 내용은 다음 달 전월 조정).");
        current(bill,input.modifiedAt());
        if (bill.normalReceiptCount()>0)
            throw CodeFailure.invalid("billId","입금이 있는 청구는 취소할 수 없습니다. 입금을 먼저 취소해 주세요.");
        if (mapper.cancel(new Cancellation(id,input.reason(),actor.userId(),input.modifiedAt()))!=1)
            throw CodeFailure.conflict();
        return getCommonBill(id);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Detail postCommonBillNextMonth(String id) {
        projects.lockSources();
        mapper.lockMonthlySources();
        var bill=required(id,true);
        if (bill.cancelled()) throw CodeFailure.invalid("billId","이미 취소된 청구입니다.");
        if (!view(bill,false).canNextMonth())
            throw CodeFailure.invalid("billId","진행 중인 월단위·파견 프로젝트의 가장 최근 청구이고 다음 달이 계약 기간 안이어야 합니다.");
        YearMonth next=calendar.fromDatabaseMonth(bill.billMonth()).plusMonths(1);
        String month=calendar.toDatabaseMonth(next);
        // 원청구의 달이 아니라 새로 만들 다음 달의 마감을 본다.
        creatable(month);
        if (mapper.exists(bill.projectId(),month))
            throw CodeFailure.invalid("billMonth","이 프로젝트의 "+display(month)
                +" 청구가 이미 있습니다. 하나 더 만들려면 [고쳐서 청구]를 쓰세요.");
        var supply=rules.estimate(mapper.monthly(bill.projectId(),month));
        if (supply.signum()<=0)
            throw CodeFailure.invalid("supplyAmount",display(month)+" 투입 기준 금액이 0원입니다. [고쳐서 청구]로 금액을 넣어 주세요.");
        var amounts=rules.amounts(supply,bill.vatRate());
        var values=new Values(supply,bill.vatRate(),calendar.today(),next.plusMonths(1).atDay(25),null);
        var identity=numbers.allocate(bill.projectId(),month);
        if (mapper.insert(new Write(identity.billId(),bill.projectId(),identity.billOrder(),month,
            values,amounts,BigDecimal.ZERO,actor.userId()))!=1) throw CodeFailure.conflict();
        return getCommonBill(identity.billId());
    }
}
