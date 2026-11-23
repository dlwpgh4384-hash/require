package com.company.common.developers.service;

import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.developers.mapper.DeveloperMapper;
import com.company.common.developers.mapper.DeveloperRows;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.uploads.dto.UploadContracts.Inspection;
import com.company.common.uploads.dto.UploadContracts.Issue;
import com.company.common.uploads.dto.UploadWorkbookContracts.TabularRow;
import com.company.common.uploads.service.UploadForm;
import com.company.common.users.mapper.UserMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * B.1 월별 급여 업로드(SFR-102-08): 올릴 때 연월을 고르고, 반영하면 그 달 급여 전체를 새 파일로 바꾼다
 * (TB_HR_MON_PAY.PAY_YM 「같은 연월 재업로드 시 덮어씀, 마감월 불가」). 사용 중지·퇴사한 사람도 퇴사한 달까지는 올린다.
 * 수당(식-08)·퇴직금 적립(식-09)·원천세·실지급액(식-17)은 양식에 칸이 없고 올릴 때 계산해 저장한다.
 */
@Component
public class PayrollUploadForm implements UploadForm {
    public static final List<String> HEADERS = List.of("관리번호", "이름", "급여", "4대보험 회사", "4대보험 본인",
        "근로소득세", "외주 공급가");
    private static final DateTimeFormatter DB_MONTH = DateTimeFormatter.ofPattern("uuuuMM");
    private final DeveloperMapper mapper;
    private final CalculationRuleService rules;
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;
    private final UserMapper management;

    public PayrollUploadForm(DeveloperMapper mapper, CalculationRuleService rules, BusinessCalendar calendar,
        CurrentBusinessActor actor, UserMapper management) {
        this.mapper = mapper; this.rules = rules; this.calendar = calendar; this.actor = actor; this.management = management;
    }
    @Override public String formTypeCode() { return "B1"; }
    @Override public String targetName() { return "급여"; }
    @Override public String screenId() { return "UI-02"; }
    @Override public List<String> headers() { return HEADERS; }
    @Override public String templateFileName() { return "payroll-template.xlsx"; }
    @Override public List<String> exampleRow() { return List.of("K-0001", "홍길동", "4000000", "", "", "120000", ""); }
    @Override public boolean monthly() { return true; }
    @Override public String templateNote() {
        return "B.1 월별 급여. 연월은 올릴 때 고릅니다(엑셀 칸 아님). 관리번호 필수, 이름은 확인용. "
            + "급여는 프리랜서면 원천세 차감 전 지급액, 외주는 외주 공급가를 적습니다. 4대보험 회사를 비우면 급여 × 4대보험 부담률로 계산합니다. "
            + "수당·퇴직금 적립·원천세·실지급액과 직원구분·직접/간접은 시스템이 넣습니다. "
            + "같은 달을 다시 올리면 그 달 급여 전체를 새 파일로 바꿉니다. 마감한 달은 올릴 수 없습니다.";
    }
    @Override public void checkMonth(YearMonth month) {
        if (month.isAfter(calendar.currentMonth())) throw CodeFailure.invalid("yearMonth", "아직 오지 않은 달은 올릴 수 없습니다.");
        if (mapper.closedMonth(month.format(DB_MONTH))) throw CodeFailure.invalid("yearMonth", "마감한 달은 올릴 수 없습니다.");
    }
    @Override public void lock() { management.lockManagement(); mapper.lockSources(); }

    public record Pay(String developerId, BigDecimal pay, BigDecimal insuranceCompany, BigDecimal insuranceSelf,
        BigDecimal incomeTax, BigDecimal vendorSupply) {}

    @Override public Inspection inspect(List<TabularRow> rows, YearMonth month) {
        var firstRow = new HashMap<String, Integer>();
        for (var row : rows) {
            String number = row.values().get(0).trim();
            if (!number.isEmpty()) firstRow.putIfAbsent(number, row.rowNumber());
        }
        var issues = new ArrayList<Issue>();
        var records = new ArrayList<Pay>();
        int success = 0, warnings = 0, errors = 0;
        for (var row : rows) {
            var local = new ArrayList<Issue>();
            var v = row.values().stream().map(String::trim).toList();
            String number = v.get(0);
            DeveloperRows.PayTarget target = null;
            if (number.isEmpty()) issue(local, row, 0, "관리번호를 입력해 주세요.", "오류");
            else if (firstRow.get(number) != row.rowNumber())
                issue(local, row, 0, "파일 안에 같은 관리번호가 있습니다. (" + firstRow.get(number) + "행)", "오류");
            else {
                target = mapper.selectPayTarget(number);
                if (target == null) issue(local, row, 0, "개발자 풀에 없는 관리번호입니다.", "오류");
            }
            if (target != null) {
                if (!v.get(1).isEmpty() && !v.get(1).equals(target.name))
                    issue(local, row, 1, "개발자 풀 이름(" + target.name + ")과 다릅니다. 관리번호 기준으로 올립니다.", "경고");
                if (target.retirementDate != null && YearMonth.from(target.retirementDate).isBefore(month))
                    issue(local, row, 0, "퇴사한 달(" + YearMonth.from(target.retirementDate) + ") 뒤의 급여는 올릴 수 없습니다.", "오류");
            }
            boolean outsourced = target != null && "OS".equals(target.employeeTypeCode);
            BigDecimal pay = amount(row, 2, local), insCompany = amount(row, 3, local), insSelf = amount(row, 4, local),
                incomeTax = amount(row, 5, local), supply = amount(row, 6, local);
            if (target != null && !outsourced && v.get(2).isEmpty()) issue(local, row, 2, "급여를 입력해 주세요.", "오류");
            if (outsourced && v.get(6).isEmpty()) issue(local, row, 6, "외주는 외주 공급가를 입력해 주세요.", "오류");
            boolean bad = local.stream().anyMatch(i -> "오류".equals(i.severity()));
            if (bad) errors++;
            else {
                if (local.isEmpty()) success++; else warnings++;
                records.add(new Pay(target.developerId, pay == null ? BigDecimal.ZERO : pay, insCompany, insSelf, incomeTax, supply));
            }
            issues.addAll(local);
        }
        return new Inspection(List.copyOf(records), List.copyOf(issues), success, warnings, errors, rows.size());
    }

    @Override public Map<Integer, String> apply(Inspection inspection, YearMonth month, long uploadId) {
        // 식 정의는 TB_SY_CALC_RULE에서 CALC_ID로 읽고, 적용 대상(정규직만 등)이 바뀌지 않았는지 확인한다.
        require("식-08", "팀장", "그룹장"); require("식-09", "SEV_RATE", "정규직만"); require("식-17", "계약직·프리랜서만", "실지급액");
        BigDecimal team = codeValue("ALLOW_TEAM"), group = codeValue("ALLOW_GROUP"),
            insRate = codeValue("INS_RATE"), sevRate = codeValue("SEV_RATE"),
            wthldRate = rules.effectiveStandard("WTHLD_RT", month).value();
        String ym = month.format(DB_MONTH);
        mapper.deleteMonthPay(ym);
        for (var record : inspection.records()) {
            var p = (Pay) record;
            var d = mapper.selectPayTargetById(p.developerId());
            String type = d.employeeTypeCode;
            boolean regular = "FT".equals(type), contract = "CT".equals(type), freelancer = "FL".equals(type);
            BigDecimal allowance = (regular || contract)
                ? ("TEAM".equals(d.positionCode) ? team : "GROUP".equals(d.positionCode) ? group : BigDecimal.ZERO)
                : BigDecimal.ZERO;
            BigDecimal base = p.pay().add(allowance);
            BigDecimal insCompany = p.insuranceCompany() != null ? p.insuranceCompany()
                : (regular || contract) ? floor(p.pay().multiply(insRate).movePointLeft(2)) : BigDecimal.ZERO;
            BigDecimal retire = regular ? floor(base.multiply(sevRate).movePointLeft(2)) : BigDecimal.ZERO;
            BigDecimal withholding = (contract || freelancer) ? floor(base.multiply(wthldRate).movePointLeft(2)) : BigDecimal.ZERO;
            BigDecimal real = base.subtract(zero(p.insuranceSelf())).subtract(withholding).subtract(zero(p.incomeTax()));
            mapper.insertMonthPay(new DeveloperRows.MonthPay(ym, p.developerId(), p.pay(), allowance, insCompany,
                p.insuranceSelf(), retire, p.incomeTax(), withholding, real, p.vendorSupply(), type, d.directClassCode,
                uploadId, actor.userId()));
        }
        return Map.of();
    }
    private void require(String calcId, String... words) {
        String formula = rules.definition(calcId).formula();
        for (String word : words) if (!formula.contains(word)) throw new IllegalStateException(calcId + " 정의를 확인해 주세요.");
    }
    private BigDecimal codeValue(String code) {
        BigDecimal value = mapper.selectCodeValue("ALWNC", code);
        if (value == null) throw new IllegalStateException("수당코드 " + code + " 값을 확인해 주세요.");
        return value;
    }
    private static BigDecimal floor(BigDecimal value) { return value.setScale(0, RoundingMode.DOWN); }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal amount(TabularRow row, int column, List<Issue> issues) {
        String text = row.values().get(column).trim();
        if (text.isEmpty()) return null;
        BigDecimal value = DeveloperUploadForm.parseAmount(text);
        if (value == null) issue(issues, row, column, "0 이상의 원 단위 숫자로 적어 주세요.", "오류");
        return value;
    }
    private void issue(List<Issue> issues, TabularRow row, int column, String reason, String severity) {
        String value = row.values().get(column);
        issues.add(new Issue(row.rowNumber(), HEADERS.get(column),
            DeveloperUploadForm.clip(value == null || value.isBlank() ? "(빈칸)" : value, 200),
            DeveloperUploadForm.clip(reason, 300), severity));
    }
}
