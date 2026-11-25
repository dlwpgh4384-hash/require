package com.company.common.cashflowstatements.service;

import com.company.common.cashflowstatements.dto.CashFlowStatementContracts.*;
import com.company.common.cashflowstatements.mapper.CashFlowStatementMapper;
import com.company.common.common.CashFlowStatementDefinitions;
import com.company.common.common.CashFlowStatementDefinitions.PaymentRules;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.financeitems.mapper.FinanceItemMapper;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.profitstructures.mapper.ProfitStructureMapper;
import com.company.common.profitstructures.mapper.ProfitStructureRows.Query;
import com.company.common.support.service.CodeFailure;
import com.company.common.users.dto.UserContracts.Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static java.math.BigDecimal.ZERO;

@Service
public class CommonCashFlowStatementService {
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("uuuuMM");
    private final CashFlowStatementMapper source;
    private final FinanceItemMapper finance;
    private final ProfitStructureMapper people;
    private final UserPreferencePeriodSourceMapper periods;
    private final CashFlowStatementDefinitions definitions;
    private final CalculationRuleService calculations;

    public CommonCashFlowStatementService(CashFlowStatementMapper source, FinanceItemMapper finance,
        ProfitStructureMapper people, UserPreferencePeriodSourceMapper periods,
        CashFlowStatementDefinitions definitions, CalculationRuleService calculations) {
        this.source = source;
        this.finance = finance;
        this.people = people;
        this.periods = periods;
        this.definitions = definitions;
        this.calculations = calculations;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Response<Result> getCommonCashFlowStatements(Filter filter) {
        return read(filter, null);
    }

    /** 동일 계산을 한 번 실행하여 연도를 넘는 구간을 반환한다. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Result forward(YearMonth first, int count) {
        return read(new Filter(first.getYear()), first.plusMonths(count - 1)).data();
    }

    private Response<Result> read(Filter filter, YearMonth through) {
        if (filter.year() == null || filter.year() < 1 || filter.year() > 9999)
            throw CodeFailure.invalid("year", "조회 연도를 확인해 주세요.");
        var rules = definitions.paymentRules();
        validateSemantics();
        YearMonth now = YearMonth.parse(source.currentMonth(), YM);
        BigDecimal minimum = calculations.effectiveStandard("MIN_CASH", now).value();
        if (minimum == null) throw CodeFailure.invalid("standardCode", "최소 운영자금을 확인해 주세요.");
        BigDecimal baseYm = source.immediateStandard("CASH_BASE_YM");
        BigDecimal balance = source.immediateStandard("CASH_BASE_AMT");
        YearMonth base = null;
        if (baseYm != null) {
            try { base = YearMonth.parse(baseYm.toBigIntegerExact().toString(), YM); }
            // 시작 월 값이 잘못되면 값이 없을 때처럼 잔액만 비우고 수입·지출은 보여 준다(46번 점검 2026-11-23).
            catch (RuntimeException invalid) { base = null; }
        }
        if (base == null) balance = null;
        var closed = new HashSet<>(periods.selectClosedMonths());
        // 시작 월(CASH_BASE_YM) 전 달은 옮겨 온 달이라 마감으로 본다(2026-10-31 C16, 기간 기준과 같은 규칙).
        final YearMonth startMonth = base;
        java.util.function.Predicate<String> isClosed = ym -> closed.contains(ym)
            || (startMonth != null && YearMonth.parse(ym, YM).isBefore(startMonth));
        var flows = new TreeMap<String, Map<String, BigDecimal>>();
        for (var row : finance.cashContributions()) {
            if ("OTHER_INCOME".equals(row.category())) continue;
            add(flows, row.yearMonth(), row.category(), row.amount());
        }
        for (var row : source.miscellaneousIncome()) add(flows, row.yearMonth(), "OTHER_INCOME", row.amount());
        for (var row : source.receipts()) {
            if ("REMAINDER".equals(row.category()) && isClosed.test(row.yearMonth())) continue;
            add(flows, row.yearMonth(), "CASH_SALES", row.amount());
        }
        YearMonth first = YearMonth.of(filter.year(), 1);
        YearMonth start = base != null && balance != null && base.isBefore(first) ? base : first;
        YearMonth last = through == null ? YearMonth.of(filter.year(), 12) : through;
        payroll(flows, new PayrollPeriod(start.minusMonths(1), last, now), isClosed, rules);
        var months = new ArrayList<Month>();
        var warnings = new ArrayList<Warning>();
        for (YearMonth month = start; !month.isAfter(last); month = month.plusMonths(1)) {
            String key = month.format(YM);
            boolean supported = base != null && !month.isBefore(base) && balance != null;
            var values = flows.get(key);
            boolean populated = values != null || supported;
            if (values == null) values = Map.of();
            BigDecimal cash = value(values, "CASH_SALES", populated);
            BigDecimal misc = value(values, "OTHER_INCOME", populated);
            BigDecimal loan = value(values, "LOAN", populated);
            BigDecimal regular = value(values, "REGULAR", populated);
            BigDecimal contract = value(values, "CONTRACT", populated);
            BigDecimal vendor = value(values, "VENDOR", populated);
            BigDecimal unpaid = value(values, "UNPAID_LABOR", populated);
            BigDecimal overhead = value(values, "EXPENSE", populated);
            BigDecimal tax = value(values, "CORPORATE_TAX", populated);
            BigDecimal income = CashFlowStatementProjection.sum(Arrays.asList(cash, misc, loan));
            BigDecimal expense = CashFlowStatementProjection.sum(Arrays.asList(regular, contract, vendor, unpaid, overhead, tax));
            BigDecimal opening = supported ? balance : null;
            BigDecimal closing = supported ? opening.add(z(income)).subtract(z(expense)) : null;
            if (supported) balance = closing;
            if (through == null && month.getYear() != filter.year()) continue;
            if (through != null && month.isBefore(first)) continue;
            var result = new Month(key, isClosed.test(key), opening, closing, cash, misc, loan, income,
                regular, contract, vendor, unpaid, overhead, tax, expense);
            months.add(result);
            if (!result.closed() && closing != null && closing.compareTo(minimum) < 0)
                warnings.add(new Warning(key, closing, minimum));
        }
        var report = new Result(filter.year(), minimum, months, warnings, CashFlowStatementProjection.table(months, warnings));
        return new Response<>(true, Map.of("currentYearMonth", now.format(YM)), report);
    }

    private void validateSemantics() {
        var required = Map.of(
            "식-46", "이월금=전월이월금\\+수입[−-]지출[.]?다음달전월이월금=이번달이월금[.]?",
            "식-47", "수입=입금합계\\+잡수익\\+대여금[.]?",
            "식-48", "지출=인건비\\+계약직·프리랜서\\+용역비\\+미지급인건비\\+제경비\\+법인세납부[.]?",
            "식-50", "확정신고예상액=연간예상법인세[−-]중간예납[.]?",
            "식-51", "경고=예정달이월금<최소운영자금[.]?",
            "식-52", "항목합계=기간안월값의합[.]기간전월이월금=첫달전월이월금[.]기간이월금=마지막달이월금[.]?");
        for (var definition : definitions.definitions()) {
            var grammar = required.get(definition.calcId());
            if (grammar == null) continue;
            String formula = definition.formula().replaceAll("(?U)\\s+", "");
            while (formula.matches(".*\\([^()]*\\).*")) formula = formula.replaceAll("\\([^()]*\\)", "");
            if (!formula.matches(grammar))
                throw CodeFailure.invalid("calcId", definition.calcId() + " 자금 계산 정의를 확인해 주세요.");
        }
    }

    private record PayrollPeriod(YearMonth first, YearMonth last, YearMonth now) {}
    /**
     * 급여 지출(식-48). 올린 급여는 그대로 쓰고, 마감 안 한 달 가운데 「이번 달 이후」이거나 「지난달인데 급여를 아직 안 올린 달」은
     * 사람마다 가장 최근에 올린 급여로 예상한다. 한 번도 올리지 않은 사람은 개발자 풀 월 급여·지급액으로 예상한다(46번 점검 2026-11-23).
     */
    private void payroll(Map<String, Map<String, BigDecimal>> flows, PayrollPeriod period,
        java.util.function.Predicate<String> isClosed, PaymentRules rules) {
        YearMonth first = period.first(), last = period.last(), now = period.now();
        var stored = source.payroll(last.format(YM));
        var byMonth = new HashMap<String, Map<String, Pay>>();
        for (var pay : stored) byMonth.computeIfAbsent(pay.yearMonth(), k -> new HashMap<>()).put(pay.developerId(), pay);
        var persons = people.people(new Query(last.getYear(), 12));
        var active = new HashSet<>(source.activeDevelopers());
        var latest = new HashMap<String, Pay>();
        for (var pay : stored) if (pay.yearMonth().compareTo(first.format(YM)) < 0)
            latest.merge(pay.developerId(), pay, (a, b) -> a.yearMonth().compareTo(b.yearMonth()) > 0 ? a : b);
        var withholdingRates = new HashMap<YearMonth, BigDecimal>();
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            String key = month.format(YM);
            var uploaded = byMonth.getOrDefault(key, Map.of());
            for (var pay : uploaded.values()) { payment(flows, pay, rules); latest.put(pay.developerId(), pay); }
            // 급여 업로드는 그 달 전체를 한 파일로 바꾸므로, 지난달에 업로드가 있으면 빠진 사람은 실제로 안 준 것으로 본다.
            if (isClosed.test(key) || (month.isBefore(now) && !uploaded.isEmpty())) continue;
            Map<String, BigDecimal> pool = null;
            for (var person : persons) {
                if (uploaded.containsKey(person.developerId()) || !active.contains(person.developerId())
                    || (person.joinDate() != null && YearMonth.from(person.joinDate()).isAfter(month))
                    || (person.retireDate() != null && YearMonth.from(person.retireDate()).isBefore(month))) continue;
                var basis = latest.get(person.developerId());
                if (basis == null) {
                    if (pool == null) {
                        pool = new HashMap<>();
                        for (var row : source.poolPays(key)) pool.put(row.developerId(), row.amount());
                    }
                    var amount = pool.get(person.developerId());
                    if (amount == null) continue;
                    basis = new Pay(key, person.developerId(), person.employeeTypeCode(), amount, ZERO, ZERO, ZERO, ZERO,
                        ZERO, ZERO, null, "OS".equals(person.employeeTypeCode()) ? amount : ZERO);
                }
                BigDecimal rate = null;
                if ("CT".equals(person.employeeTypeCode())) {
                    rate = withholdingRates.computeIfAbsent(month, m -> {
                        var standard = calculations.effectiveStandard("WTHLD_RT", m);
                        return standard == null ? null : standard.value();
                    });
                }
                payment(flows, forecast(person, basis, month, rate, rules), rules);
            }
        }
    }
    /** 예상 급여. 계약직 원천세는 운영 기준값 WTHLD_RT, 프리랜서는 식-49 비율(명세)로 계산한다. */
    private static Pay forecast(Person person, Pay basis, YearMonth month, BigDecimal withholdingRate, PaymentRules rules) {
        String type = person.employeeTypeCode();
        boolean employee = "FT".equals(type) || "CT".equals(type);
        BigDecimal allowance = z(basis.allowance());
        BigDecimal salary = z(basis.salary());
        BigDecimal company = employee ? z(basis.companyInsurance()) : ZERO;
        BigDecimal self = employee ? z(basis.personalInsurance()) : ZERO;
        BigDecimal tax = employee ? z(basis.incomeTax()) : ZERO;
        BigDecimal withholding = "FL".equals(type) ? rules.freelancerWithholding(salary, allowance)
            : "CT".equals(type) && withholdingRate != null
                ? salary.add(allowance).multiply(withholdingRate).movePointLeft(2).setScale(0, RoundingMode.DOWN) : ZERO;
        return new Pay(month.format(YM), person.developerId(), type, salary, allowance, company, ZERO, self,
            withholding, tax, salary.add(allowance).subtract(self).subtract(withholding).subtract(tax), z(basis.vendorSupply()));
    }
    private static void payment(Map<String, Map<String, BigDecimal>> flows, Pay pay, PaymentRules rules) {
        BigDecimal amount;
        String category;
        switch (pay.employeeTypeCode()) {
            case "FT" -> { category = "REGULAR"; amount = netPay(pay); }
            case "CT" -> { category = "CONTRACT"; amount = netPay(pay); }
            // 프리랜서 지급액은 식-49(급여+수당에서 원천 3.3%를 뺀 금액) 그대로
            case "FL" -> { category = "CONTRACT"; amount = rules.freelancerPayment(z(pay.salary()), z(pay.allowance())); }
            case "OS" -> { category = "VENDOR"; amount = rules.vendorPayment(z(pay.vendorSupply())); }
            default -> throw CodeFailure.invalid("code", "급여 직원 구분을 확인해 주세요.");
        }
        add(flows, pay.yearMonth(), category, amount);
        BigDecimal deduction = z(pay.companyInsurance()).add(z(pay.personalInsurance())).add(z(pay.withholdingTax())).add(z(pay.incomeTax()));
        add(flows, YearMonth.parse(pay.yearMonth(), YM).plusMonths(1).format(YM), "EXPENSE", deduction);
    }
    /** 실지급액(급여 업로드가 저장한 값). 비어 있으면 업로드와 같은 식으로 계산한다: 급여+수당−본인 보험−원천세−근로소득세. */
    private static BigDecimal netPay(Pay pay) {
        if (pay.netPay() != null) return pay.netPay();
        return z(pay.salary()).add(z(pay.allowance())).subtract(z(pay.personalInsurance()))
            .subtract(z(pay.withholdingTax())).subtract(z(pay.incomeTax()));
    }
    private static BigDecimal z(BigDecimal amount) { return amount == null ? ZERO : amount; }
    private static BigDecimal value(Map<String, BigDecimal> values, String category, boolean populated) {
        return values.containsKey(category) ? values.get(category) : populated ? ZERO : null;
    }
    private static void add(Map<String, Map<String, BigDecimal>> flows, String month, String category, BigDecimal amount) {
        if (month == null || amount == null) return;
        flows.computeIfAbsent(month, k -> new HashMap<>()).merge(category, amount, BigDecimal::add);
    }
}
