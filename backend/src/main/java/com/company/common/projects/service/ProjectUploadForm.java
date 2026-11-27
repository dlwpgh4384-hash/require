package com.company.common.projects.service;

import com.company.common.codegroups.service.CodeTextResolver;
import com.company.common.projects.dto.ProjectContracts.Input;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.service.CodeFailure;
import com.company.common.uploads.dto.UploadContracts.*;
import com.company.common.uploads.dto.UploadWorkbookContracts.TabularRow;
import com.company.common.uploads.service.UploadForm;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ProjectUploadForm implements UploadForm {
    public static final List<String> HEADERS = List.of("프로젝트ID","프로젝트명","고객사","계약유형","시작일","종료일",
        "영업 파트","근무지","상태","견적 금액","계약금액","예산","팀비","프로젝트경비","특징",
        // 근무지 지역(2026-11-25): 개발자 거주지 세 칸과 같은 규칙. 칸을 더하기 전 양식도 받도록 맨 뒤에 두고 선택 칸으로 읽는다.
        "근무지 시·도","근무지 시·군·구","근무지 동");
    /** 근무지 지역 세 칸이 처음 나오는 칸 번호. */
    private static final int AREA = 15;
    private static final List<String> FIELDS = List.of("projectId","projectName","customerId","contractTypeCode","startDate","endDate",
        "salesPartId","workplace","statusCode","estimateAmount","contractAmount","budgetAmount","teamExpenseAmount","projectExpenseAmount","feature",
        "workAreaCode","workAreaCode","workAreaCode");
    private final ProjectMapper mapper;
    private final CodeTextResolver resolver;
    private final CommonProjectService projects;
    private final com.company.common.developers.service.UploadAreaResolver areas;
    public ProjectUploadForm(ProjectMapper mapper, CodeTextResolver resolver, CommonProjectService projects,
            com.company.common.developers.service.UploadAreaResolver areas) {
        this.mapper=mapper; this.resolver=resolver; this.projects=projects; this.areas=areas;
    }
    public String formTypeCode() { return "B9"; }
    public String targetName() { return "프로젝트"; }
    public String screenId() { return "UI-01"; }
    public List<String> headers() { return HEADERS; }
    public Set<String> optionalHeaders() { return Set.copyOf(HEADERS.subList(AREA, AREA + 3)); }
    public String templateFileName() { return "project-template.xlsx"; }
    public List<String> exampleRow() { return List.of("", "가온은행 차세대 여신", "가온은행", "턴키", "2026-01-01", "2026-12-31", "영업1파트", "서울 중구", "진행", "1200000000", "1000000000", "", "50000", "50000", "", "서울특별시", "중구", "을지로1가"); }
    public String templateNote() { return "B.9 신규 등록 전용. 프로젝트ID를 비우면 P-nnnn 자동 발급. 고객사는 상호 또는 사업자번호. 상태는 진행/수주예정. 내부는 고객사·파트·근무지 생략. 상태를 비우면 진행. 금액은 원 단위 숫자(쉼표 가능). 근무지 시·도·시·군·구·동은 선택(동 칸에는 법정동 이름만, 예: 을지로1가), 못 찾으면 찾은 단계까지만 저장하고 경고. 근무지 글자는 참고용."; }
    public void lock() { mapper.lockSources(); }
    public record Row(int rowNumber, String id, Input input) {}
    public Inspection inspect(List<TabularRow> rows, YearMonth ignored) {
        var used = new HashSet<>(mapper.projectIds());
        var reserved = new HashSet<>(used);
        for (var r : rows) if (!r.values().get(0).isBlank()) reserved.add(r.values().get(0).trim());
        var customers = mapper.uploadCustomers();
        var parts = mapper.uploadParts();
        var seen = new HashSet<String>(); var issues = new ArrayList<Issue>(); var records = new ArrayList<Row>();
        var assigned = new LinkedHashMap<Integer,String>(); int warnings=0, errors=0, successes=0, next=1;
        for (var row : rows) {
            var local = new ArrayList<Issue>(); var values = row.values();
            String explicit = values.get(0).trim(); String id = explicit;
            if (!explicit.isEmpty()) {
                if (explicit.length() > 20 || explicit.chars().anyMatch(Character::isISOControl)) issue(local,row,0,"프로젝트ID는 20자 이내로 적어 주세요. 비우면 P-0001처럼 자동으로 붙습니다.","오류");
                else if (used.contains(explicit) || !seen.add(explicit)) issue(local,row,0,"이미 있는 프로젝트ID입니다.","오류");
            }
            var body = new ObjectMapper().createObjectNode();
            String name = values.get(1).trim();
            if (name.isEmpty()) issue(local,row,1,"프로젝트명을 입력해 주세요.","오류");
            else if (name.codePointCount(0,name.length()) > 100) issue(local,row,1,"프로젝트명은 100자 이내로 적어 주세요.","오류");
            else body.put("projectName",name);
            // 계약유형: 공통코드 이름(턴키·월단위·파견·내부(자체 개발)). 시안·명세 표기 「내부」도 받는다.
            String typeText = values.get(3).trim();
            String type = "";
            if (typeText.isEmpty()) issue(local,row,3,"계약유형을 입력해 주세요.","오류");
            else if ("내부".equals(typeText)) type = "IN";
            else type = resolve(row,3,"CTRT_TYPE",local);
            boolean internal = "IN".equals(type);
            if (!type.isEmpty()) body.put("contractTypeCode",type);
            if (!internal) {
                String customer = values.get(2).trim();
                if (customer.isEmpty()) issue(local,row,2,"고객사를 입력해 주세요.","오류");
                else {
                    var matches = customers.stream().filter(c -> c.name().equals(customer) || c.businessNumber().equals(customer)).toList();
                    if (matches.isEmpty()) issue(local,row,2,"사용 중인 고객사에 없는 이름입니다. 상호 또는 사업자등록번호를 적어 주세요.","오류");
                    else if (matches.size() > 1) issue(local,row,2,"같은 상호의 거래처가 "+matches.size()+"곳 있습니다. "
                        +String.join(" / ",matches.stream().map(ProjectMapper.Customer::businessNumber).toList())+" 중 하나를 넣어 주세요.","오류");
                    else body.put("customerId",matches.get(0).id());
                }
                String part = values.get(6).trim();
                if (part.isEmpty()) issue(local,row,6,"영업 파트를 입력해 주세요.","오류");
                else if ("본사".equals(part) || "본사(직접 영업)".equals(part)) body.put("salesPartId","HQ");
                else {
                    var found = parts.stream().filter(p -> !"HQ".equals(p.code) && (p.code.equals(part) || p.name.equals(part))).toList();
                    if (found.size() != 1) issue(local,row,6,"등록되지 않은 영업 파트입니다.","오류");
                    else body.put("salesPartId",found.get(0).code);
                }
                String workplace = values.get(7).trim();
                if (workplace.codePointCount(0,workplace.length()) > 200) issue(local,row,7,"근무지는 200자 이내로 적어 주세요.","오류");
                else if (!workplace.isEmpty()) body.put("workplace",workplace);
                // 근무지 지역: 개발자 거주지(B.8)와 같은 규칙. 못 찾으면 찾은 단계까지만 저장하고 경고(오류 아님).
                String area = areas.resolve(cell(row,AREA),cell(row,AREA+1),cell(row,AREA+2),AREA,"근무지",
                    (column,value,reason,label) -> local.add(new Issue(row.rowNumber(),label,clip(value,200),clip(reason,300),"경고")));
                if (area != null) body.put("workAreaCode",area);
            }
            String start = date(row,4,"시작일",local), end = date(row,5,"종료일",local);
            if (start != null) body.put("startDate",start);
            if (end != null) body.put("endDate",end);
            if (start != null && end != null && end.compareTo(start) < 0) issue(local,row,5,"종료일이 시작일보다 앞입니다.","오류");
            // 상태: 비면 진행(명세 B.9). 진행·수주예정만.
            String statusText = values.get(8).trim();
            String status = statusText.isEmpty() ? "ING" : resolve(row,8,"PROJ_STAT",local);
            if (!status.isEmpty() && !"ING".equals(status) && !"PRE".equals(status)) issue(local,row,8,"상태는 진행 또는 수주예정으로 적어 주세요.","오류");
            else if (!status.isEmpty()) body.put("statusCode",status);
            for (int i = 9; i <= 13; i++) {
                if (internal && i == 11) continue;
                var amount = amount(row,i,local);
                if (amount != null) body.put(FIELDS.get(i),amount);
            }
            if (!internal && values.get(10).trim().isEmpty()) issue(local,row,10,"계약금액을 입력해 주세요.","오류");
            String feature = values.get(14).trim();
            if (feature.codePointCount(0,feature.length()) > 500) issue(local,row,14,"특징은 500자 이내로 적어 주세요.","오류");
            else if (!feature.isEmpty()) body.put("feature",feature);
            if (local.stream().noneMatch(i -> "오류".equals(i.severity()))) {
                try {
                    Input input = ProjectRules.input(body,false);
                    projects.validateRegistration(input);
                    // 새 ID는 오류 없는 줄에만 차례로 붙인다(오류 줄이 번호를 건너뛰게 하지 않음).
                    if (id.isEmpty()) {
                        while (reserved.contains(String.format(Locale.ROOT,"P-%04d",next))) next++;
                        id = String.format(Locale.ROOT,"P-%04d",next++); reserved.add(id);
                    }
                    records.add(new Row(row.rowNumber(),id,input)); assigned.put(row.rowNumber(),id);
                } catch (CodeFailure failure) {
                    int col = FIELDS.indexOf(failure.getField()); issue(local,row,Math.max(0,col),failure.getMessage(),"오류");
                }
            }
            boolean failed = local.stream().anyMatch(i -> "오류".equals(i.severity()));
            if (failed) errors++; else if (!local.isEmpty()) warnings++; else successes++;
            issues.addAll(local);
        }
        issues.sort(Comparator.comparingInt(i -> "오류".equals(i.severity())?0:1));
        return new Inspection(records,issues,successes,warnings,errors,rows.size(),assigned);
    }
    /** 데이터 범위: 새 프로젝트 줄은 그 영업 파트(영업 파트 칸)로 본다. 내 파트 밖으로 등록할 수 없다. */
    public List<ScopeTarget> scopeTargets(Inspection inspection) {
        return inspection.records().stream().map(Row.class::cast)
            .map(r -> new ScopeTarget(r.rowNumber(),6,"PART",r.input().salesPartId())).toList();
    }
    /** 칸 글자. 줄이 짧으면(근무지 지역 칸을 더하기 전 모양) 빈칸으로 본다. */
    private static String cell(TabularRow row,int col) {
        return col < row.values().size() ? row.values().get(col) : "";
    }
    private static String clip(String value,int limit) {
        return value.codePointCount(0,value.length()) <= limit ? value : value.substring(0,value.offsetByCodePoints(0,limit));
    }
    /** 날짜 칸: SFR-012-03 — 2026-01-31·2026.01.31·2026/01/31 모두 받는다(개발자 업로드와 같은 규칙). */
    private static String date(TabularRow row,int col,String label,List<Issue> issues) {
        String text = row.values().get(col).trim();
        if (text.isEmpty()) { issue(issues,row,col,label+"을 입력해 주세요.","오류"); return null; }
        var parsed = com.company.common.developers.service.DeveloperUploadForm.parseDate(text);
        if (parsed == null) { issue(issues,row,col,label+"은 2026-01-31처럼 적어 주세요.","오류"); return null; }
        return parsed.toString();
    }
    /** 금액 칸: 원 단위 숫자. 천 단위 쉼표(1,000,000)는 지우고 읽는다. */
    private static java.math.BigDecimal amount(TabularRow row,int col,List<Issue> issues) {
        String text = row.values().get(col).trim().replace(",","");
        if (text.isEmpty()) return null;
        if (!text.matches("[0-9]{1,15}")) { issue(issues,row,col,HEADERS.get(col)+"은 원 단위 숫자로 적어 주세요.","오류"); return null; }
        return new java.math.BigDecimal(text);
    }
    private String resolve(TabularRow row,int col,String group,List<Issue> issues) {
        try {
            var result=resolver.resolve(group,row.values().get(col).trim());
            if (result.warning()!=null) issue(issues,row,col,CodeTextResolver.readAs(result.codeName()),"경고");
            return result.code();
        } catch(CodeFailure e) { issue(issues,row,col,e.getMessage(),"오류"); return ""; }
    }
    private static void issue(List<Issue> issues,TabularRow row,int col,String message,String severity) {
        issues.add(new Issue(row.rowNumber(),HEADERS.get(col),cell(row,col),message,severity));
    }
    public Map<Integer,String> apply(Inspection inspection,YearMonth month,long uploadId) {
        var assigned=new LinkedHashMap<Integer,String>();
        for (Object record : inspection.records()) {
            Row row=(Row)record; assigned.put(row.rowNumber(),projects.create(row.input(),row.id()).projectId);
        }
        return assigned;
    }
}
