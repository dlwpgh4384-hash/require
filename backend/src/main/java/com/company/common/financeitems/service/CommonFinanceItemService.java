package com.company.common.financeitems.service;

import com.company.common.common.FinanceItemDefinitions;
import com.company.common.financeitems.dto.FinanceItemContracts.*;
import com.company.common.financeitems.mapper.FinanceItemMapper;
import com.company.common.support.service.*;
import com.company.common.users.dto.UserContracts.Response;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.function.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.company.common.financeitems.service.FinanceItemRules.*;
import static java.math.BigDecimal.ZERO;

@Service
public class CommonFinanceItemService {
    private final FinanceItemMapper mapper;
    private final FinanceItemDefinitions definitions;
    private final CurrentBusinessActor actor;
    private final BusinessCalendar calendar;
    public CommonFinanceItemService(FinanceItemMapper mapper, FinanceItemDefinitions definitions,
        CurrentBusinessActor actor, BusinessCalendar calendar) {
        this.mapper=mapper; this.definitions=definitions; this.actor=actor; this.calendar=calendar;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Response<Month> getCommonFinanceItems(String value) {
        var rules=definitions.definitions();
        return new Response<>(true,Map.of("calculationRules",rules),read(month(value)));
    }
    @Transactional(readOnly=true)
    public List<TaxPayment> overdueTaxPayments(LocalDate today) {
        // 재무 항목 화면의 법인세 납부 줄(payments: 올해 + 지난해 확정 신고 + 전에 안 낸 것)에서 상태 「확인 필요」만.
        return mapper.payments(String.format("%04d",today.getYear())).stream()
            .filter(p -> "확인 필요".equals(p.status())).toList();
    }
    public TaxEstimate estimate(YearMonth month) {
        var stored=mapper.estimate(String.format("%04d",month.getYear()));
        var annual=stored==null ? ZERO : stored.estimatedTaxAmount();
        var allocated=allocation(month.getYear(),annual,new HashSet<>(mapper.closedMonths()),mapper::fixedTax);
        var monthly=allocated.get(db(month));
        return new TaxEstimate(month.getYear(),annual,monthly,monthly.signum()<0,stored==null ? null : stored.modifiedAt());
    }
    private Summary summary(List<Item> rows, List<Adjustment> adjustments, BigDecimal tax,
        BigDecimal total, BigDecimal cash) {
        return new Summary(sum(rows,"SGA"),sum(rows,"NOPR_IN"),sum(rows,"NOPR_OUT"),tax,
            adjustments.stream().map(Adjustment::amount).reduce(ZERO,BigDecimal::add),total,cash.subtract(total));
    }
    private static BigDecimal sum(List<Item> rows,String classification) {
        return rows.stream().filter(i->classification.equals(i.classification())).map(Item::amount).reduce(ZERO,BigDecimal::add);
    }
    Month read(YearMonth month) {
        String key=db(month);
        var persisted=mapper.items(key);
        var rows=persisted;
        int auto=0;
        if (persisted.isEmpty() && !mapper.isClosed(key)) {
            rows=mapper.items(db(month.minusMonths(1))).stream().filter(i->"Y".equals(i.repeatYn())).map(i->copy(i,month,true)).toList();
            auto=rows.size();
        }
        var cards=mapper.cards(key);
        var aggregate=mapper.cardPayment(key);
        var total=cards.stream().map(Card::amount).reduce(ZERO,BigDecimal::add);
        if (aggregate==null) aggregate=new CardPayment(total,false,null);
        var estimate=estimate(month);
        var adjustments=mapper.adjustments(key);
        var statuses=new ArrayList<MonthStatus>();
        for (int m=1;m<=12;m++) {
            var current=YearMonth.of(month.getYear(),m);
            statuses.add(new MonthStatus(db(current),mapper.isClosed(db(current)),mapper.items(db(current)).size(),current.isAfter(calendar.currentMonth())));
        }
        var closed=new HashSet<>(mapper.closedMonths());
        var next=month;
        while (closed.contains(db(next)) && next.getYear()<9999) next=next.plusMonths(1);
        return new Month(key,mapper.isClosed(key),db(next),statuses,rows,mapper.loans(key),adjustments,
            mapper.transfers(key),cards,estimate,mapper.payments(String.format("%04d",month.getYear())),aggregate,
            summary(rows,adjustments,estimate.monthlyTaxAmount(),total,aggregate.paymentAmount()),
            (int)cards.stream().filter(c->"Y".equals(c.useYn())).count(),auto,mapper.baseline(key));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public PreviousItems getCommonFinancePreviousItems(String value,List<String> existing) {
        var month=month(value);
        var keys=new HashSet<>(existing==null ? Collections.<String>emptyList() : existing);
        mapper.items(value).forEach(i->keys.add(itemKey(i)));
        var rows=mapper.items(db(month.minusMonths(1))).stream().filter(i->!keys.contains(itemKey(i)))
            .map(i->copy(i,month,false)).toList();
        return new PreviousItems(rows,rows.size());
    }
    public void lockAndCheck(String value,Instant version) {
        month(value);
        mapper.lockSources();
        if (mapper.isClosed(value)) throw CodeFailure.invalid("yearMonth","마감된 달은 변경할 수 없습니다.");
        if (!Objects.equals(version,mapper.baseline(value))) throw CodeFailure.conflict();
    }
    public void marker(String month) {
        affected(mapper.history(new History("TB_FI_ITEM",month,"UI-10 월 저장",null,null,actor.userId())));
    }
    private void code(String group,String value,String field) {
        if (value==null || !value.equals(mapper.code(group,value))) throw CodeFailure.invalid(field,"선택 값을 확인해 주세요.");
    }
    private void date(LocalDate date,YearMonth month,String field) {
        if (date==null || !YearMonth.from(date).equals(month)) throw CodeFailure.invalid(field,"선택한 달 안의 날짜를 입력해 주세요.");
    }
    private void validate(MonthInput input,YearMonth month) {
        if (input.items()==null || input.loans()==null || input.adjustments()==null || input.transfers()==null || input.cards()==null
            || input.taxPayments()==null || input.taxEstimate()==null || input.cardPayment()==null)
            throw CodeFailure.invalid("body","두 탭 입력을 모두 보내 주세요.");
        for (int n=0;n<input.items().size();n++) {
            var i=input.items().get(n); String p="items["+n+"].";
            text(i.itemName(),p+"itemName","세부 항목",50,true); amount(i.amount(),p+"amount"); amount(zero(i.vatAmount()),p+"vatAmount");
            code("FIN_CLS",i.classification(),p+"classification");
            if (i.itemCode()!=null) code("SGA_ITEM",i.itemCode(),p+"itemCode");
            if (!Set.of("Y","N").contains(Objects.toString(i.repeatYn(),""))) throw CodeFailure.invalid(p+"repeatYn","반복 여부를 확인해 주세요.");
            text(i.memo(),p+"memo","비고",500,false);
        }
        for (int n=0;n<input.loans().size();n++) {
            var i=input.loans().get(n); String p="loans["+n+"].";
            date(i.date(),month,p+"date"); amount(i.amount(),p+"amount");
            if (i.amount().signum()<=0) throw CodeFailure.invalid(p+"amount","금액은 양수로 입력해 주세요.");
            code("IO",i.direction(),p+"direction"); text(i.counterparty(),p+"counterparty","상대",100,false); text(i.memo(),p+"memo","메모",200,false);
        }
        for (int n=0;n<input.transfers().size();n++) {
            var i=input.transfers().get(n); String p="transfers["+n+"].";
            date(i.date(),month,p+"date"); code("XFER_CLS",i.classification(),p+"classification");
            text(i.description(),p+"description","내용",100,true); amount(i.amount(),p+"amount");
            text(i.payee(),p+"payee","받는 곳",100,false); text(i.memo(),p+"memo","메모",200,false);
        }
        var closed=mapper.closedMonths();
        String latest=closed.stream().max(String::compareTo).orElse(null);
        for (int n=0;n<input.adjustments().size();n++) {
            var i=input.adjustments().get(n); String p="adjustments["+n+"].";
            var target=month(i.targetYearMonth());
            if (latest==null || !closed.contains(db(target)) || target.isBefore(month(latest).minusMonths(11)) || !target.isBefore(month))
                throw CodeFailure.invalid(p+"targetYearMonth","최근 12개월 안의 마감월을 골라 주세요.");
            code("ADJ_ITEM",i.adjustmentItem(),p+"adjustmentItem"); amount(i.amount(),p+"amount"); text(i.reason(),p+"reason","사유",300,true);
        }
        var pairs=new HashSet<String>();
        for (int n=0;n<input.cards().size();n++) {
            var i=input.cards().get(n); String p="cards["+n+"].";
            text(i.cardName(),p+"cardName","카드 이름",100,true);
            if (i.lastFourDigits()==null || !i.lastFourDigits().matches("[0-9]{4}")) throw CodeFailure.invalid(p+"lastFourDigits","숫자 4자리를 입력해 주세요.");
            if (!pairs.add(i.cardName()+":"+i.lastFourDigits()) || mapper.duplicateCard(i))
                throw CodeFailure.invalid(p+"lastFourDigits","같은 카드가 이미 있습니다.");
            if (!Set.of("Y","N").contains(Objects.toString(i.useYn(),""))) throw CodeFailure.invalid(p+"useYn","사용 여부를 확인해 주세요.");
            amount(zero(i.amount()),p+"amount");
        }
        if (input.taxEstimate().taxYear()!=month.getYear()) throw CodeFailure.invalid("taxEstimate.taxYear","과세연도를 확인해 주세요.");
        amount(input.taxEstimate().estimatedTaxAmount(),"taxEstimate.estimatedTaxAmount");
        amount(input.cardPayment().paymentAmount(),"cardPayment.paymentAmount");
    }
    private <T> void versions(List<T> before,List<T> after,Function<T,String> id,Function<T,Instant> version) {
        var old=new HashMap<String,T>();
        before.forEach(i->old.put(id.apply(i),i));
        var seen=new HashSet<String>();
        for (T row:after) {
            String key=id.apply(row);
            if (key==null) { if (version.apply(row)!=null) throw CodeFailure.conflict(); continue; }
            if (!seen.add(key) || !old.containsKey(key) || !Objects.equals(version.apply(old.get(key)),version.apply(row))) throw CodeFailure.conflict();
        }
    }
    private <T> void deleteMissing(String month,List<T> before,List<T> after,Function<T,String> id,
        Function<T,Instant> version,ToIntFunction<Version> remove) {
        var ids=new HashSet<String>(); after.forEach(i->ids.add(id.apply(i)));
        for (T row:before) if (!ids.contains(id.apply(row))) affected(remove.applyAsInt(new Version(month,id.apply(row),version.apply(row))));
    }
    @Transactional
    public Month putCommonFinanceItems(String value,MonthInput input) {
        var month=month(value);
        lockAndCheck(value,input.modifiedAt());
        definitions.definitions();
        validate(input,month);
        var old=read(month);
        // Read stored rows, not the auto-fill drafts exposed by GET.
        var items=mapper.items(value);
        versions(items,input.items(),Item::itemId,Item::modifiedAt);
        versions(old.loans(),input.loans(),Loan::loanId,Loan::modifiedAt);
        versions(old.transfers(),input.transfers(),Transfer::transferId,Transfer::modifiedAt);
        versions(old.adjustments(),input.adjustments(),Adjustment::adjustmentId,Adjustment::modifiedAt);
        for (var c:input.cards()) if (c.cardId()!=null) {
            var previous=old.cards().stream().filter(x->x.cardId().equals(c.cardId())).findFirst().orElseThrow(CodeFailure::conflict);
            if (cardChanged(previous,c) && !Objects.equals(previous.modifiedAt(),c.modifiedAt())) throw CodeFailure.conflict();
        } else if (c.modifiedAt()!=null) throw CodeFailure.conflict();
        if (input.cards().stream().filter(c->c.cardId()!=null).map(Card::cardId).distinct().count()
            !=input.cards().stream().filter(c->c.cardId()!=null).count()) throw CodeFailure.conflict();
        for (var c:input.cards()) if (c.cardId()!=null) {
            var previous=old.cards().stream().filter(x->x.cardId().equals(c.cardId())).findFirst().orElseThrow(CodeFailure::conflict);
            if (!Objects.equals(previous.monthlyModifiedAt(),c.monthlyModifiedAt())) throw CodeFailure.conflict();
        }
        boolean estimateChanged=!same(old.taxEstimate().estimatedTaxAmount(),input.taxEstimate().estimatedTaxAmount());
        if ((estimateChanged && !Objects.equals(old.taxEstimate().modifiedAt(),input.taxEstimate().modifiedAt()))
            || !Objects.equals(old.cardPayment().modifiedAt(),input.cardPayment().modifiedAt())) throw CodeFailure.conflict();
        var paymentIds=new HashSet<String>();
        for (int n=0;n<input.taxPayments().size();n++) {
            var p=input.taxPayments().get(n); String f="taxPayments["+n+"]";
            paymentKey(p.paymentId());
            if (!paymentIds.add(p.paymentId())) throw CodeFailure.conflict();
            var previous=old.taxPayments().stream().filter(x->x.paymentId().equals(p.paymentId())).findFirst()
                .orElseThrow(()->CodeFailure.invalid(f,"납부 항목을 확인해 주세요."));
            if (!paymentChanged(previous,p)) continue;
            if ("Y".equals(previous.paidYn())) throw CodeFailure.invalid(f,"확인한 납부는 변경할 수 없습니다.");
            if (!Objects.equals(previous.modifiedAt(),p.modifiedAt())) throw CodeFailure.conflict();
            if (p.scheduledDate()==null) throw CodeFailure.invalid(f+".scheduledDate","납부일을 입력해 주세요.");
            if (p.paymentAmount()!=null) amount(p.paymentAmount(),f+".paymentAmount");
        }
        String user=actor.userId();
        deleteMissing(value,items,input.items(),Item::itemId,Item::modifiedAt,mapper::deleteItem);
        deleteMissing(value,old.loans(),input.loans(),Loan::loanId,Loan::modifiedAt,mapper::deleteLoan);
        deleteMissing(value,old.transfers(),input.transfers(),Transfer::transferId,Transfer::modifiedAt,mapper::deleteTransfer);
        deleteMissing(value,old.adjustments(),input.adjustments(),Adjustment::adjustmentId,Adjustment::modifiedAt,mapper::deleteAdjustment);
        long next=mapper.nextItem(value);
        for (var i:input.items()) {
            if (i.itemId()!=null) affected(mapper.updateItem(new Write<>(value,i,user)));
            else {
                if (next>99999) throw CodeFailure.invalid("items","항목 수가 저장 범위를 넘었습니다.");
                var row=new Item(Long.toString(next++),null,i.classification(),i.itemCode(),i.itemName(),i.amount(),zero(i.vatAmount()),i.cashDate(),i.repeatYn(),
                    "Y".equals(i.autoFillYn()) && old.items().stream().anyMatch(x->x.itemId()==null && itemKey(x).equals(itemKey(i))) ? "Y" : "N",null,i.memo());
                affected(mapper.insertItem(new Write<>(value,row,user)));
            }
        }
        for (var i:input.loans()) {
            if (i.loanId()!=null) affected(mapper.updateLoan(new Write<>(value,i,user)));
            else affected(mapper.insertLoan(new Write<>(value,new Loan(Long.toString(mapper.nextLoan()),null,i.date(),i.direction(),i.amount(),i.counterparty(),i.memo()),user)));
        }
        for (var i:input.transfers()) {
            if (i.transferId()!=null) affected(mapper.updateTransfer(new Write<>(value,i,user)));
            else affected(mapper.insertTransfer(new Write<>(value,new Transfer(Long.toString(mapper.nextTransfer()),null,i.date(),i.classification(),i.description(),i.payee(),i.amount(),i.memo()),user)));
        }
        for (var i:input.adjustments()) {
            if (i.adjustmentId()!=null) affected(mapper.updateAdjustment(new Write<>(value,i,user)));
            else affected(mapper.insertAdjustment(new Write<>(value,new Adjustment(Long.toString(mapper.nextAdjustment()),null,i.targetYearMonth(),i.adjustmentItem(),i.amount(),i.reason()),user)));
        }
        if (estimateChanged) affected(mapper.updateEstimate(new Write<>(value,input.taxEstimate(),user)));
        for (var p:input.taxPayments()) {
            var previous=old.taxPayments().stream().filter(x->x.paymentId().equals(p.paymentId())).findFirst().orElseThrow();
            if (!paymentChanged(previous,p)) continue;
            affected(mapper.updatePayment(new Write<>(value,p,user)));
            if (!Objects.equals(previous.paymentAmount(),p.paymentAmount())) affected(mapper.history(new History("TB_FI_TAX_PAY",p.paymentId(),"납부 예정액",
                previous.paymentAmount()==null ? null : previous.paymentAmount().toPlainString(),p.paymentAmount()==null ? null : p.paymentAmount().toPlainString(),user)));
        }
        var total=input.cards().stream().map(c->zero(c.amount())).reduce(ZERO,BigDecimal::add);
        var aggregate=new CardPayment(input.cardPayment().manualOverride() ? input.cardPayment().paymentAmount() : total,
            input.cardPayment().manualOverride(),input.cardPayment().modifiedAt());
        affected(mapper.saveCardPayment(new Write<>(value,aggregate,user),total));
        for (var c:input.cards()) {
            var row=c;
            if (c.cardId()==null) {
                row=new Card(UUID.randomUUID().toString().replace("-","").substring(0,20),null,c.cardName(),c.lastFourDigits(),c.useYn(),zero(c.amount()),null);
                affected(mapper.insertCard(new Write<>(value,row,user)));
            } else {
                var previous=old.cards().stream().filter(x->x.cardId().equals(c.cardId())).findFirst().orElseThrow();
                if (cardChanged(previous,c)) affected(mapper.updateCard(new Write<>(value,row,user)));
            }
            affected(mapper.updateCardMonth(new Write<>(value,row,user)));
        }
        reallocate(month.getYear());
        marker(value);
        return read(month);
    }
    private static boolean same(BigDecimal a,BigDecimal b) {
        return a==null ? b==null : b!=null && a.compareTo(b)==0;
    }
    private static boolean cardChanged(Card before,Card after) {
        return !Objects.equals(before.cardName(),after.cardName()) || !Objects.equals(before.lastFourDigits(),after.lastFourDigits())
            || !Objects.equals(before.useYn(),after.useYn());
    }
    private static boolean paymentChanged(TaxPayment before,PaymentInput after) {
        return !Objects.equals(before.scheduledDate(),after.scheduledDate()) || !same(before.paymentAmount(),after.paymentAmount());
    }
    public void reallocate(int year) {
        String key=String.format("%04d",year);
        var annual=mapper.estimate(key);
        if (annual==null) {
            // 그해를 화면에서 열기 전에 엑셀부터 올려도 판관비·영업외가 손익에 바로 들어가게, 화면을 열 때와 같이
            // 연간 예상 줄·납부 일정을 먼저 만든다(2026-10-28 「재무 항목 → 손익 연결」, 2026-11-24 사용자 결정).
            mapper.prepareYear(key,actor.userId());
            mapper.preparePayments(key,actor.userId());
            annual=mapper.estimate(key);
            if (annual==null) return;
        }
        var closed=new HashSet<>(mapper.closedMonths());
        var allocated=allocation(year,annual.estimatedTaxAmount(),closed,mapper::fixedTax);
        for (var entry:allocated.entrySet()) if (!closed.contains(entry.getKey())) {
            var rows=mapper.items(entry.getKey());
            var adjustment=mapper.adjustments(entry.getKey()).stream().map(Adjustment::amount).reduce(ZERO,BigDecimal::add);
            mapper.updateProfit(new Profit(entry.getKey(),sum(rows,"SGA"),sum(rows,"NOPR_IN"),sum(rows,"NOPR_OUT"),entry.getValue(),adjustment),actor.userId());
        }
    }
    @Transactional(readOnly=true)
    public List<CashContribution> cashContributions() { return mapper.cashContributions(); }
}
