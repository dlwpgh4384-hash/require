package com.company.common.operatingprofitstatements.service;

import com.company.common.operatingprofitstatements.mapper.OperatingProfitStatementMapper;
import com.company.common.operatingprofitstatements.mapper.OperatingProfitStatementMapper.ImportedMonth;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.uploads.dto.UploadContracts.*;
import com.company.common.uploads.dto.UploadWorkbookContracts.TabularRow;
import com.company.common.uploads.service.UploadForm;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OperatingProfitStatementUploadForm implements UploadForm {
    public static final List<String> HEADERS = List.of("연월", "매출", "매출원가", "판관비 합계", "영업이익",
        "배분원가", "대기원가", "직접경비", "매출총이익", "판관비 입력", "간접인건비", "연구개발비",
        "전월조정", "영업외수익", "영업외비용", "법인세", "당기순이익");
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("uuuuMM");
    private final OperatingProfitStatementMapper mapper;
    private final UserPreferencePeriodSourceMapper periods;
    private final CurrentBusinessActor actor;
    public OperatingProfitStatementUploadForm(OperatingProfitStatementMapper mapper,
        UserPreferencePeriodSourceMapper periods, CurrentBusinessActor actor) {
        this.mapper = mapper;
        this.periods = periods;
        this.actor = actor;
    }
    public static boolean projectedUploadAllows(Set<String> roles) { return roles != null && roles.contains("ADMIN"); }
    public String formTypeCode() { return "B11"; }
    public String targetName() { return "전년 손익"; }
    public String screenId() { return "UI-08"; }
    public List<String> headers() { return HEADERS; }
    public String templateFileName() { return "previous-profit-template.xlsx"; }
    public String templateNote() {
        return "연월·매출·매출원가·판관비 합계·영업이익은 필수입니다. 나머지 열은 빈칸을 유지합니다. 시작월의 전년 1월부터 시작월 직전월까지 일부 달만 올려도 됩니다. 같은 연월은 덮어씁니다.";
    }
    public void lock() { mapper.lockSources(); }
    private YearMonth start() {
        var value = periods.selectCashBaseYm();
        try { return YearMonth.parse(value.toBigIntegerExact().toString(), YM); }
        catch (RuntimeException failure) { throw CodeFailure.invalid("file", "운영 시작월을 확인해 주세요."); }
    }
    private YearMonth parseMonth(String value) {
        String compact = value.trim().replace("-", "");
        if (!compact.matches("[0-9]{4}(0[1-9]|1[0-2])")) throw new IllegalArgumentException("연월을 확인해 주세요.");
        return YearMonth.parse(compact, YM);
    }
    public Inspection inspect(List<TabularRow> rows, YearMonth ignored) {
        YearMonth start = start(), min = YearMonth.of(start.getYear() - 1, 1);
        var counts = new HashMap<YearMonth, Integer>();
        for (var row : rows) {
            try { counts.merge(parseMonth(row.values().get(0)), 1, Integer::sum); }
            catch (RuntimeException invalid) { /* The row pass records its field error. */ }
        }
        var records = new ArrayList<ImportedMonth>();
        var issues = new ArrayList<Issue>();
        for (var row : rows) {
            int column = 0;
            try {
                var values = row.values();
                var month = parseMonth(values.get(0));
                if (counts.get(month) > 1) throw new IllegalArgumentException("같은 파일의 연월이 중복되었습니다.");
                if (month.isBefore(min) || !month.isBefore(start))
                    throw new IllegalArgumentException("시작월의 전년 1월부터 시작월 직전월까지만 올릴 수 있습니다.");
                var amounts = new BigDecimal[17];
                for (column = 1; column < 17; column++) {
                    String text = values.get(column).trim().replace(",", "");
                    if (text.isEmpty()) {
                        if (column <= 4) throw new IllegalArgumentException("필수 금액을 입력해 주세요.");
                        continue;
                    }
                    if (!text.matches("[+-]?[0-9]+")) throw new IllegalArgumentException("금액은 부호 있는 정수 원 단위로 입력해 주세요.");
                    var amount = new BigDecimal(text);
                    if (amount.abs().compareTo(new BigDecimal("999999999999999")) > 0)
                        throw new IllegalArgumentException("금액은 15자리 이내로 입력해 주세요.");
                    amounts[column] = amount;
                }
                var record = new ImportedMonth();
                record.month = month.format(YM);
                record.revenue = amounts[1]; record.costOfSales = amounts[2]; record.sga = amounts[3];
                record.operatingProfit = amounts[4]; record.allocatedCost = amounts[5]; record.idleCost = amounts[6];
                record.directExpense = amounts[7]; record.grossProfit = amounts[8];
                record.sellingAdministrativeInput = amounts[9]; record.indirectLaborCost = amounts[10];
                record.internalResearchCost = amounts[11]; record.previousAdjustment = amounts[12];
                record.income = amounts[13]; record.expense = amounts[14]; record.tax = amounts[15]; record.netProfit = amounts[16];
                records.add(record);
            } catch (RuntimeException failure) {
                int c = Math.min(column, 16);
                String input = row.values().get(c);
                issues.add(new Issue(row.rowNumber(), HEADERS.get(c), input.substring(0, Math.min(input.length(), 200)),
                    failure instanceof IllegalArgumentException ? failure.getMessage() : "입력값을 확인해 주세요.", "오류"));
            }
        }
        return new Inspection(records, issues, records.size(), 0, rows.size() - records.size(), rows.size());
    }
    public Map<Integer, String> apply(Inspection inspection, YearMonth ignored, long uploadId) {
        if (inspection.errorCount() != 0) throw CodeFailure.invalid("file", "오류가 있는 파일은 반영할 수 없습니다.");
        for (var value : inspection.records()) {
            var row = (ImportedMonth) value;
            row.userId = actor.userId();
            if (mapper.upsertImported(row) != 1) throw new IllegalStateException("손익을 저장하지 못했습니다.");
        }
        return Map.of();
    }
}
