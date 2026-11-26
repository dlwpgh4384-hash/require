package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.bills.service.CommonBillService;
import com.company.common.bills.dto.BillContracts.Bill;
import com.company.common.billchecks.service.BillCheckService;
import com.company.common.commonops.service.BillingLookupService;
import com.company.common.support.service.BusinessCalendar;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.company.common.aianalysis.service.AiAnalysisInterpreter.has;
import static com.company.common.aianalysis.service.AiAnalysisTables.*;

/** F-06 청구·미수(청구·입금 화면), P-05 미수 연체 위험(미수 알림 표 그대로). */
@Component
public class AiAnalysisBillFunctions implements AiAnalysisFunction {
    private final CommonBillService bills;
    private final BillCheckService checks;
    private final BillingLookupService lookup;
    private final BusinessCalendar calendar;
    private final AiAnalysisTables tables;
    public AiAnalysisBillFunctions(CommonBillService bills, BillCheckService checks, BillingLookupService lookup,
        BusinessCalendar calendar, AiAnalysisTables tables) {
        this.bills = bills; this.checks = checks; this.lookup = lookup; this.calendar = calendar; this.tables = tables;
    }
    public Set<String> functions() { return Set.of("F-06", "P-05"); }
    public Result read(AiAnalysisInterpreter.Query query) {
        if ("P-05".equals(query.function())) return dunning();
        if (has(query.question(), "누락")) return missing(query);
        return unpaid(query);
    }

    /** 청구 누락 의심: 청구·입금 「청구 누락 점검」 결과 그대로(맞음이 아닌 줄). */
    private Result missing(AiAnalysisInterpreter.Query query) {
        var result = checks.getCommonBillChecks(query.periodGiven() ? query.period().endMonth().replace("-", "") : null);
        var month = month(result.checkMonth());
        var items = result.items().stream().filter(r -> !"맞음".equals(r.judgement())).toList();
        var rows = items.stream().map(r -> Arrays.<Object>asList(r.projectName(), r.judgement(), r.headcount(), r.monthlyMm(),
            r.referenceAmount(), r.billedSupplyAmount(), r.difference(), r.explanation())).toList();
        var table = AiAnalysisTables.table(null, List.of(Column.text("projectName", "프로젝트"), Column.text("judgement", "판정"),
            Column.count("headcount", "인원"), Column.mm("monthlyMm", "MM"), Column.money("referenceAmount", "기준 금액"),
            Column.money("billedSupplyAmount", "청구 공급가"), Column.money("difference", "차이"),
            Column.text("explanation", "설명")), rows, null, null);
        var s = result.summary();
        String conclusion = month + " 청구 누락 의심은 " + bold(count(items.size())) + "입니다."
            + (items.isEmpty() ? "" : " 청구 없음 " + s.missingCount() + "건 · 적게 청구 " + s.underCount() + "건 · 많이 청구 " + s.overCount() + "건.");
        var period = single(month);
        return new Result(conclusion, List.of(table), null, List.of("청구·입금 화면의 청구 누락 점검과 같은 판정입니다.",
            result.plannedBasis() ? "마감 전 달이라 계획 기준으로 점검했습니다." : "마감한 달이라 실적 기준으로 점검했습니다."),
            used(tables.screen("UI-13") + " 청구 누락 점검", period), month.toString(), result.plannedBasis(), false,
            List.of(tables.open("UI-13", period)));
    }

    /** 미수금: 메뉴 배지·대시보드와 같이 연도와 상관없이 못 받은 청구 전부(SFR-116-10). */
    private Result unpaid(AiAnalysisInterpreter.Query query) {
        var source = bills.getCommonUnpaidBills();
        Bill project = pick(query.question(), source, Bill::projectName, Bill::projectId, "프로젝트");
        Bill customer = project != null ? null : pick(query.question(), source, Bill::customerName, Bill::customerName, "거래처");
        var items = source.stream().filter(b -> !b.cancelled() && b.unpaidAmount().signum() > 0)
            .filter(b -> project == null || project.projectId().equals(b.projectId()))
            .filter(b -> customer == null || Objects.equals(customer.customerName(), b.customerName())).toList();
        var rows = items.stream().map(b -> Arrays.<Object>asList(b.projectName(), b.customerName(), month(b.billMonth()).toString(),
            b.totalAmount(), b.unpaidAmount(), b.payDueDate(), Math.max(0, b.daysOverdue()), "LATE".equals(b.status()) ? "연체" : "미수")).toList();
        BigDecimal total = items.stream().map(Bill::unpaidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var table = AiAnalysisTables.table(null, List.of(Column.text("projectName", "프로젝트"), Column.text("customerName", "거래처"),
            Column.month("billMonth", "청구월"), Column.money("totalAmount", "청구 합계"), Column.money("unpaidAmount", "미수금"),
            Column.date("payDueDate", "입금 예정일"), Column.count("daysOverdue", "지난 일수"), Column.text("status", "상태")),
            rows, null, items.size() > 1 ? Arrays.<Object>asList("합계", null, null, null, total, null, null, null) : null);
        long late = items.stream().filter(b -> "LATE".equals(b.status())).count();
        String target = project != null ? project.projectName() + " " : customer != null ? customer.customerName() + " " : "";
        String conclusion = "지금 " + target + "미수금은 " + bold(count(items.size())) + ", 합계 " + bold(money(total)) + "입니다."
            + (late > 0 ? " 그중 연체는 " + late + "건입니다." : "");
        var today = calendar.currentMonth();
        return new Result(conclusion, List.of(table), null, List.of("취소하지 않은 청구 중 입금되지 않은 금액이 남은 것을 연도와 상관없이 모두 셉니다.",
            "청구·입금 메뉴 배지·대시보드 미수금과 같은 숫자입니다."), used(tables.screen("UI-13"), null), today.toString(), false, false,
            List.of(tables.open("UI-13", single(today))));
    }

    /** 미수 연체 위험: 청구·입금 미수 알림 표 그대로(지난 일수·단계·다음 알림). 새 점수는 만들지 않는다. */
    private Result dunning() {
        var result = lookup.getCommonBillDunning();
        var today = calendar.today();
        var rows = result.items().stream().map(r -> Arrays.<Object>asList(r.projectName(), r.unpaidAmount(), r.payDueDate(),
            r.daysOverdue(), r.currentStep() == null ? "단계 전" : r.currentStep() + "단계", r.nextNotification())).toList();
        BigDecimal total = result.items().stream().map(r -> r.unpaidAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        var table = AiAnalysisTables.table(null, List.of(Column.text("projectName", "프로젝트"), Column.money("unpaidAmount", "미수금"),
            Column.date("payDueDate", "입금 예정일"), Column.count("daysOverdue", "지난 일수"), Column.text("step", "지금 단계"),
            Column.text("nextNotification", "다음 알림")), rows, null, null);
        var steps = new StringBuilder();
        for (int step = 3; step >= 1; step--) {
            int s = step;
            long n = result.items().stream().filter(r -> Integer.valueOf(s).equals(r.currentStep())).count();
            if (n > 0) steps.append(steps.length() == 0 ? " " : " · ").append(step).append("단계 ").append(n).append("건");
        }
        long notYet = bills.getCommonUnpaidBills().stream().filter(b -> !b.cancelled() && b.unpaidAmount().signum() > 0
            && b.payDueDate() != null && !b.payDueDate().isBefore(today)).count();
        String conclusion = "입금 예정일이 지난 미수는 " + bold(count(result.items().size())) + ", 합계 " + bold(money(total)) + "입니다."
            + (steps.length() > 0 ? steps + "." : "") + (notYet > 0 ? " 입금 예정일 전인 미수 " + notYet + "건은 아직 연체가 아닙니다." : "");
        var assumptions = new ArrayList<String>();
        var offsets = result.cards().stream().map(c -> c.step() + "단계 " + c.offsetDays() + "일").toList();
        if (!offsets.isEmpty()) assumptions.add("미수 알림 단계는 입금 예정일에서 " + String.join(" · ", offsets) + " 지난 때입니다.");
        assumptions.add("다음 알림 날짜는 청구·입금 화면의 미수 알림 표와 같습니다.");
        var month = calendar.currentMonth();
        return new Result(conclusion, List.of(table), null, assumptions, used(tables.screen("UI-13") + " 미수 알림", null),
            month.toString(), false, true, List.of(tables.open("UI-13", single(month))));
    }
}
