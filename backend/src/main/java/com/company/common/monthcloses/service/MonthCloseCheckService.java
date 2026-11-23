package com.company.common.monthcloses.service;

import com.company.common.billchecks.service.BillCheckService;
import com.company.common.common.MonthCloseCodes;
import com.company.common.commonops.service.PartSettlementCloseService;
import com.company.common.monthcloses.dto.MonthCloseContracts.*;
import com.company.common.monthcloses.mapper.MonthCloseMapper;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MonthCloseCheckService {
    private final MonthCloseMapper mapper;
    private final MonthCloseCodes codes;
    private final MonthCloseAssignmentService assignments;
    private final PartSettlementCloseService settlements;
    private final BillCheckService bills;
    public MonthCloseCheckService(MonthCloseMapper mapper, MonthCloseCodes codes,
        MonthCloseAssignmentService assignments, PartSettlementCloseService settlements, BillCheckService bills) {
        this.mapper = mapper;
        this.codes = codes;
        this.assignments = assignments;
        this.settlements = settlements;
        this.bills = bills;
    }
    public record Result(List<Check> checks, List<String> remaining) {}
    public Result calculate(YearMonth month) {
        String db = month.format(DateTimeFormatter.ofPattern("uuuuMM"));
        var counts = mapper.selectCounts(db);
        int over = assignments.overCount(month);
        var settlement = settlements.check(month.toString());
        var billing = bills.getCommonBillChecks(db);
        int missing = (int) billing.items().stream()
            .filter(row -> "청구 없음".equals(row.judgement()) || "적게 청구".equals(row.judgement())).count();
        var rows = List.of(
            check("PAY", false, counts.payroll() + "명 업로드 완료", null),
            check("MM_OVER", over > 0, over + "명 MM 초과", "UI-12"),
            check("BILL", counts.invoices() > 0, counts.invoices() + "건 번호 미입력", "UI-13"),
            check("FI", false, counts.finance() + "건 입력", null),
            check("SETL", settlement.unconfirmedCount() > 0,
                settlement.totalCount() + "개 중 " + settlement.unconfirmedCount() + "개 미확정", "UI-19"),
            check("BILL_MISS", missing > 0, missing + "건 청구 누락", "UI-13"));
        String warning = codes.name("CHK_STAT", "WARN");
        return new Result(rows, rows.stream().filter(row -> row.status().equals(warning)).map(Check::name).toList());
    }
    private Check check(String code, boolean warning, String description, String screen) {
        return new Check(codes.name("CHK_ITEM", code), codes.name("CHK_STAT", warning ? "WARN" : "OK"),
            description, screen);
    }
}
