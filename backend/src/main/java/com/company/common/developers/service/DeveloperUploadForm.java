package com.company.common.developers.service;

import com.company.common.codegroups.service.CodeTextResolver;
import com.company.common.developers.dto.DeveloperContracts.Input;
import com.company.common.developers.mapper.DeveloperMapper;
import com.company.common.developers.mapper.DeveloperRows;
import com.company.common.support.service.CodeFailure;
import com.company.common.uploads.dto.UploadContracts.Inspection;
import com.company.common.uploads.dto.UploadContracts.Issue;
import com.company.common.uploads.dto.UploadWorkbookContracts.TabularRow;
import com.company.common.uploads.service.UploadForm;
import com.company.common.users.mapper.UserMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * B.8 개발자 업로드(SFR-102-08): 새로 등록 전용. 관리번호를 비우면 반영할 때 K-nnnn을 붙인다.
 * 코드 칸은 공통코드 이름(또는 코드)으로 적고, 예전 표기는 엑셀 표기 대응표로 바꿔 읽어 경고로 알린다.
 */
@Component
public class DeveloperUploadForm implements UploadForm {
    public static final List<String> HEADERS = List.of("관리번호", "이름", "직원구분", "소속", "등급", "주 직무",
        "보조 직무", "직위", "직접/간접", "영업 파트", "입사일", "월 단가", "월 급여·지급액", "보유 스킬",
        "거주지 시·도", "거주지 시·군·구", "거주지 동");
    private final DeveloperMapper mapper;
    private final CodeTextResolver resolver;
    private final CommonDeveloperService developers;
    private final DeveloperNumberReservations reservations;
    private final UserMapper management;

    public DeveloperUploadForm(DeveloperMapper mapper, CodeTextResolver resolver, CommonDeveloperService developers,
        DeveloperNumberReservations reservations, UserMapper management) {
        this.mapper = mapper; this.resolver = resolver; this.developers = developers;
        this.reservations = reservations; this.management = management;
    }
    @Override public String formTypeCode() { return "B8"; }
    @Override public String targetName() { return "개발자"; }
    @Override public String screenId() { return "UI-02"; }
    @Override public List<String> headers() { return HEADERS; }
    @Override public String templateFileName() { return "developer-template.xlsx"; }
    @Override public String templateNote() {
        return "B.8 개발자 새로 등록 전용(이미 있는 사람을 고치는 건 화면에서, 휴직·퇴사는 [근무 상태 이력]에서). "
            + "이름·직원구분·소속·주 직무·직접/간접·영업 파트 필수. 관리번호를 비우면 반영할 때 K-nnnn이 붙습니다. "
            + "코드 칸은 공통코드 이름으로 적습니다. 소속은 「자사」 또는 협력사 상호, 영업 파트는 파트명 또는 「본사(직접 영업)」. "
            + "보유 스킬은 쉼표(,)로 여러 개. 동 칸에는 동 이름(예: 자양동)만 적어 주세요. "
            + "최대 10MB·첫 시트·데이터 5,000행. 수식 칸은 계산된 결과값으로 읽습니다.";
    }
    @Override public void lock() { management.lockManagement(); mapper.lockSources(); }

    private record Row(int rowNumber, Input input) {}

    @Override public Inspection inspect(List<TabularRow> rows, YearMonth month) {
        Set<String> used = new HashSet<>(mapper.selectManagementNumbers());
        used.addAll(reservations.numbers());
        var firstRow = new HashMap<String, Integer>();
        for (var row : rows) {
            String number = row.values().get(0).trim();
            if (!number.isEmpty()) firstRow.putIfAbsent(number, row.rowNumber());
        }
        Set<String> fileNumbers = firstRow.keySet();
        var skillCodes = mapper.selectCodes("SKILL");
        var issues = new ArrayList<Issue>();
        var records = new ArrayList<Row>();
        var assigned = new LinkedHashMap<Integer, String>();
        int success = 0, warnings = 0, errors = 0, next = 1;
        for (var row : rows) {
            var local = new ArrayList<Issue>();
            var v = row.values().stream().map(String::trim).toList();
            String number = v.get(0);
            if (!number.isEmpty()) {
                if (!number.matches("[A-Za-z0-9-]{1,20}"))
                    issue(local, row, 0, "관리번호는 영문·숫자·하이픈 20자 이내로 적어 주세요.", "오류");
                else if (used.contains(number)) issue(local, row, 0, "이미 등록된 관리번호입니다.", "오류");
                else if (firstRow.get(number) != row.rowNumber())
                    issue(local, row, 0, "파일 안에 같은 관리번호가 있습니다. (" + firstRow.get(number) + "행)", "오류");
            }
            String name = v.get(1);
            if (name.isEmpty()) issue(local, row, 1, "이름을 입력해 주세요.", "오류");
            else if (name.codePointCount(0, name.length()) > 50) issue(local, row, 1, "50자 이내로 입력해 주세요.", "오류");
            String type = code(row, 2, "EMP_TYPE", true, "직원구분", local);
            String organization = organization(row, local);
            String grade = code(row, 4, "GRADE", false, "등급", local);
            String job = code(row, 5, "JOB", true, "주 직무", local);
            String subJob = code(row, 6, "JOB", false, "보조 직무", local);
            String position = code(row, 7, "JBPS", false, "직위", local);
            String direct = code(row, 8, "DRCT_CLS", true, "직접/간접", local);
            String part = part(row, local);
            LocalDate hire = date(row, 10, local);
            BigDecimal rate = amount(row, 11, local), pay = amount(row, 12, local);
            List<String> skills = skills(row, skillCodes, local);
            String area = area(row, local);
            if (!name.isEmpty() && hire != null) {
                String same = mapper.selectSameNameHire(name, hire);
                if (same != null) issue(local, row, 1, name + "·" + hire, "같은 이름·입사일의 개발자가 이미 있습니다(" + same + ").", "경고", "이름·입사일");
            }
            boolean bad = local.stream().anyMatch(i -> "오류".equals(i.severity()));
            if (bad) errors++;
            else {
                if (local.isEmpty()) success++; else warnings++;
                String finalNumber = number;
                if (number.isEmpty()) {
                    do { finalNumber = String.format(Locale.ROOT, "K-%04d", next++); }
                    while (used.contains(finalNumber) || fileNumbers.contains(finalNumber));
                    assigned.put(row.rowNumber(), finalNumber);
                }
                records.add(new Row(row.rowNumber(), new Input(finalNumber, name, type, organization, grade, job, subJob,
                    position, direct, part, hire, rate, pay, skills, area, null)));
            }
            issues.addAll(local);
        }
        return new Inspection(List.copyOf(records), List.copyOf(issues), success, warnings, errors, rows.size(), assigned);
    }

    @Override public Map<Integer, String> apply(Inspection inspection, YearMonth month, long uploadId) {
        var result = new LinkedHashMap<Integer, String>();
        for (var record : inspection.records()) {
            var row = (Row) record;
            var created = developers.postCommonDeveloper(row.input());
            if (inspection.assigned().containsKey(row.rowNumber())) result.put(row.rowNumber(), created.managementNumber);
        }
        return result;
    }

    private String code(TabularRow row, int column, String group, boolean required, String label, List<Issue> issues) {
        String text = row.values().get(column).trim();
        if (text.isEmpty()) {
            if (required) issue(issues, row, column, label + "을(를) 입력해 주세요.", "오류");
            return null;
        }
        try {
            var result = resolver.resolve(group, text);
            if (result.warning() != null) issue(issues, row, column, "‘" + result.codeName() + "’으로 바꿔 읽었습니다.", "경고");
            return result.code();
        } catch (CodeFailure failure) {
            issue(issues, row, column, failure.getMessage(), "오류");
            return null;
        }
    }
    private String organization(TabularRow row, List<Issue> issues) {
        String text = row.values().get(3).trim();
        if (text.isEmpty()) { issue(issues, row, 3, "소속을 입력해 주세요.", "오류"); return null; }
        if ("자사".equals(text)) return "";
        var ids = mapper.selectVendorIdsByName(text);
        if (ids.size() == 1) return ids.get(0);
        issue(issues, row, 3, ids.isEmpty() ? "사용 중인 협력사에 없는 상호입니다. 「자사」 또는 협력사 상호를 적어 주세요."
            : "같은 상호의 협력사가 여러 곳입니다. 화면에서 등록해 주세요.", "오류");
        return null;
    }
    private String part(TabularRow row, List<Issue> issues) {
        String text = row.values().get(9).trim();
        if (text.isEmpty()) { issue(issues, row, 9, "영업 파트를 입력해 주세요.", "오류"); return null; }
        if ("본사".equals(text) || "본사(직접 영업)".equals(text)) return "HQ";
        var ids = mapper.selectPartIdsByName(text);
        if (ids.size() == 1) return ids.get(0);
        issue(issues, row, 9, "사용 중인 영업 파트에 없는 이름입니다. 파트명 또는 「본사(직접 영업)」을 적어 주세요.", "오류");
        return null;
    }
    static LocalDate parseDate(String text) {
        var m = java.util.regex.Pattern.compile("([0-9]{2}|[0-9]{4})\\s*[-./]\\s*([0-9]{1,2})\\s*[-./]\\s*([0-9]{1,2})\\.?").matcher(text);
        if (!m.matches()) return null;
        int year = Integer.parseInt(m.group(1));
        if (m.group(1).length() == 2) year += 2000;
        try { return LocalDate.of(year, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))); }
        catch (RuntimeException failure) { return null; }
    }
    private LocalDate date(TabularRow row, int column, List<Issue> issues) {
        String text = row.values().get(column).trim();
        if (text.isEmpty()) return null;
        LocalDate value = parseDate(text);
        if (value == null) issue(issues, row, column, "날짜는 2024-03-02처럼 적어 주세요.", "오류");
        return value;
    }
    static BigDecimal parseAmount(String text) {
        String plain = text.replace(",", "").replace("원", "").trim();
        if (!plain.matches("[0-9]{1,15}")) return null;
        return new BigDecimal(plain);
    }
    private BigDecimal amount(TabularRow row, int column, List<Issue> issues) {
        String text = row.values().get(column).trim();
        if (text.isEmpty()) return null;
        BigDecimal value = parseAmount(text);
        if (value == null) issue(issues, row, column, "0 이상의 원 단위 숫자로 적어 주세요.", "오류");
        return value;
    }
    private List<String> skills(TabularRow row, List<DeveloperRows.Code> codes, List<Issue> issues) {
        String text = row.values().get(13).trim();
        if (text.isEmpty()) return List.of();
        var result = new ArrayList<String>();
        for (String part : text.split(",")) {
            String name = part.trim();
            if (name.isEmpty()) continue;
            var found = codes.stream().filter(c -> c.name.equals(name) || c.code.equals(name)).findFirst();
            if (found.isEmpty()) { issue(issues, row, 13, name, "공통코드 「스킬」에 없는 이름입니다: " + name, "오류", null); continue; }
            if (!result.contains(found.get().code)) result.add(found.get().code);
        }
        return List.copyOf(result);
    }
    /** SFR-021-04: 시·도 정식·줄임 이름 같이 봄, 행정동은 법정동으로 바꿈, 못 찾으면 찾은 단계까지만 저장하고 경고. */
    private String area(TabularRow row, List<Issue> issues) {
        String sido = row.values().get(14).trim(), sgg = row.values().get(15).trim(), dong = row.values().get(16).trim();
        if (sido.isEmpty()) {
            if (!sgg.isEmpty() || !dong.isEmpty()) issue(issues, row, 14, "시·도를 먼저 적어 주세요.", "오류");
            return null;
        }
        var sidoRows = mapper.selectAreasByName("SIDO", null, sido);
        if (sidoRows.isEmpty()) { issue(issues, row, 14, "시·도를 찾을 수 없습니다.", "오류"); return null; }
        var top = sidoRows.get(0);
        if (sgg.isEmpty()) {
            if (!dong.isEmpty()) return emd(row, top.areaCode, top.areaName, dong, issues);
            return top.areaCode;
        }
        var sggRows = mapper.selectAreasByName("SGG", top.areaCode, sgg);
        if (sggRows.isEmpty()) {
            issue(issues, row, 15, sgg, "시·군·구를 못 찾아 비웠습니다. 「" + top.areaName + "」까지 저장합니다.", "경고", "거주지(시·군·구)");
            return top.areaCode;
        }
        var middle = sggRows.get(0);
        if (dong.isEmpty()) return middle.areaCode;
        return emd(row, middle.areaCode, middle.areaName, dong, issues);
    }
    private String emd(TabularRow row, String parent, String parentName, String dong, List<Issue> issues) {
        var direct = mapper.selectAreasByName("EMD", parent, dong);
        if (direct.size() == 1) return direct.get(0).areaCode;
        String normalized = dong.replaceAll("제([0-9])", "$1");
        var mapped = mapper.selectHdongAreas(parent, normalized);
        if (mapped.size() == 1) {
            issue(issues, row, 16, dong, "‘" + mapped.get(0).areaName + "’으로 바꿔 읽었습니다.", "경고", "거주지(동)");
            return mapped.get(0).areaCode;
        }
        issue(issues, row, 16, dong, "동을 못 찾아 비웠습니다. 「" + parentName + "」까지 저장합니다.", "경고", "거주지(동)");
        return parent;
    }
    private void issue(List<Issue> issues, TabularRow row, int column, String reason, String severity) {
        issue(issues, row, column, row.values().get(column), reason, severity, null);
    }
    private void issue(List<Issue> issues, TabularRow row, int column, String value, String reason, String severity, String label) {
        String text = value == null || value.isBlank() ? "(빈칸)" : value;
        issues.add(new Issue(row.rowNumber(), label == null ? HEADERS.get(column) : label, clip(text, 200), clip(reason, 300), severity));
    }
    static String clip(String value, int limit) {
        return value.codePointCount(0, value.length()) <= limit ? value : value.substring(0, value.offsetByCodePoints(0, limit));
    }
}
