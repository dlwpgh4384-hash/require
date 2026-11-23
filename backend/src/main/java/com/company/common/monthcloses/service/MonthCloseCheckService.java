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
    /** stored: the same six results as TB_CL_CLOSE_CHK rows (codes, not display names). */
    public record Result(List<Check> checks, List<String> remaining, List<StoredCheck> stored) {
        public Result(List<Check> checks, List<String> remaining) { this(checks, remaining, List.of()); }
    }
    public Result calculate(YearMonth month) {
        String db = month.format(DateTimeFormatter.ofPattern("uuuuMM"));
        var counts = mapper.selectCounts(db);
        int over = assignments.overCount(month);
        var settlement = settlements.check(month.toString());
        var billing = bills.getCommonBillChecks(db);
        int missing = (int) billing.items().stream()
            .filter(row -> "청구 없음".equals(row.judgement()) || "적게 청구".equals(row.judgement())).count();
        var stored = List.of(
            stored("PAY", false, counts.payroll(), counts.payroll() + "명 업로드 완료", null),
            // 설명 문구는 화면 시안(v1.8) closeItems 그대로
            stored("MM_OVER", over > 0, over, over > 0 ? over + "명 — 확인 필요" : "없음", "UI-12"),
            stored("BILL", counts.invoices() > 0, counts.invoices(), counts.invoices() > 0
                ? counts.invoices() + "건 — 계산서 번호를 아직 안 넣은 청구" : "모든 청구에 번호 입력", "UI-13"),
            stored("FI", false, counts.finance(), counts.finance() + "건 입력", null),
            stored("SETL", settlement.unconfirmedCount() > 0, settlement.unconfirmedCount(),
                settlement.unconfirmedCount() > 0
                    ? settlement.totalCount() + "개 파트 중 " + settlement.unconfirmedCount() + "개 미확정"
                    : settlement.totalCount() + "개 파트 모두 확정", "UI-19"),
            stored("BILL_MISS", missing > 0, missing, missing > 0 ? missing + "건 — 투입 대비 청구 확인" : "투입과 청구가 맞음", "UI-13"));
        var rows = stored.stream().map(row -> new Check(codes.name("CHK_ITEM", row.itemCode()),
            codes.name("CHK_STAT", row.statusCode()), row.description(), row.screenId())).toList();
        String warning = codes.name("CHK_STAT", "WARN");
        return new Result(rows, rows.stream().filter(row -> row.status().equals(warning)).map(Check::name).toList(), stored);
    }
    private static StoredCheck stored(String code, boolean warning, int count, String description, String screen) {
        return new StoredCheck(code, warning ? "WARN" : "OK", count, description, screen);
    }
}
