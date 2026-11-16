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
        "영업파트","근무지","상태","견적금액","계약금액","예산","팀비","프로젝트경비","특징");
    private static final List<String> FIELDS = List.of("projectId","projectName","customerId","contractTypeCode","startDate","endDate",
        "salesPartId","workplace","statusCode","estimateAmount","contractAmount","budgetAmount","teamExpenseAmount","projectExpenseAmount","feature");
    private final ProjectMapper mapper;
    private final CodeTextResolver resolver;
    private final CommonProjectService projects;
    public ProjectUploadForm(ProjectMapper mapper, CodeTextResolver resolver, CommonProjectService projects) {
        this.mapper=mapper; this.resolver=resolver; this.projects=projects;
    }
    public String formTypeCode() { return "B9"; }
    public String targetName() { return "프로젝트"; }
    public String screenId() { return "UI-01"; }
    public List<String> headers() { return HEADERS; }
    public String templateFileName() { return "project-template.xlsx"; }
    public String templateNote() { return "B.9 신규 등록 전용. 프로젝트ID를 비우면 P-nnnn 자동 발급. 고객사는 상호 또는 사업자번호. 상태는 진행/수주예정. 내부는 고객사·파트·근무지 생략. 수식은 허용하지 않습니다."; }
    public void lock() { mapper.lockSources(); }
    public record Row(int rowNumber, String id, Input input) {}
    public Inspection inspect(List<TabularRow> rows, YearMonth ignored) {
        var used = new HashSet<>(mapper.projectIds());
        var reserved = new HashSet<>(used);
        for (var r : rows) if (!r.values().get(0).isBlank()) reserved.add(r.values().get(0));
        var seen = new HashSet<String>(); var issues = new ArrayList<Issue>(); var records = new ArrayList<Row>();
        var assigned = new LinkedHashMap<Integer,String>(); int warnings=0, errors=0, successes=0, next=1;
        for (var row : rows) {
            var local = new ArrayList<Issue>(); var values = row.values(); String explicit=values.get(0); String id=explicit;
            try {
                if (!explicit.isBlank()) {
                    ProjectRules.id(explicit);
                    if (used.contains(explicit) || !seen.add(explicit)) throw ProjectRules.invalid("projectId");
                } else {
                    while (reserved.contains(String.format(Locale.ROOT,"P-%04d",next))) next++;
                    id=String.format(Locale.ROOT,"P-%04d",next++); reserved.add(id);
                }
                for (int col : row.formulaColumns()) issue(local,row,col,"수식은 값으로 바꾼 뒤 올려 주세요.","오류");
                var body = new ObjectMapper().createObjectNode();
                for (int i=1;i<values.size();i++) if (!values.get(i).isBlank()) {
                    String field=FIELDS.get(i); String value=values.get(i).trim();
                    if (i>=9 && i<=13) {
                        if (!value.matches("[0-9]+")) throw ProjectRules.invalid(field);
                        body.put(field,new java.math.BigDecimal(value));
                    } else body.put(field,value);
                }
                body.put("contractTypeCode", resolve(row,3,"CTRT_TYPE",local));
                body.put("statusCode",resolve(row,8,"PROJ_STAT",local));
                boolean internal="IN".equals(body.path("contractTypeCode").asText());
                if (internal) { body.remove("customerId"); body.remove("salesPartId"); body.remove("workplace"); }
                else {
                    String customer=values.get(2).trim();
                    var matches=mapper.uploadCustomers().stream().filter(c -> c.name().equals(customer) || c.businessNumber().equals(customer)).toList();
                    if (matches.size()!=1) {
                        issue(local,row,2,matches.isEmpty()?"활성 고객사를 찾을 수 없습니다.":"동일 상호가 여러 곳입니다. 사업자번호: "+String.join(", ",matches.stream().map(ProjectMapper.Customer::businessNumber).toList()),"오류");
                    } else body.put("customerId",matches.get(0).id());
                    String part=values.get(6).trim();
                    if (!part.isEmpty()) {
                        var found=mapper.uploadParts().stream().filter(p -> p.code.equals(part) || p.name.equals(part)).toList();
                        if (found.size()!=1) throw ProjectRules.invalid("salesPartId");
                        body.put("salesPartId",found.get(0).code);
                    }
                }
                Input input=ProjectRules.input(body,false);
                if (!internal && input.contractAmount()==null) throw ProjectRules.invalid("contractAmount");
                if (!internal && input.salesPartId()==null) throw ProjectRules.invalid("salesPartId");
                projects.validateRegistration(input);
                if (local.stream().noneMatch(i -> "오류".equals(i.severity()))) { records.add(new Row(row.rowNumber(),id,input)); assigned.put(row.rowNumber(),id); }
            } catch (CodeFailure failure) {
                int col=FIELDS.indexOf(failure.getField()); issue(local,row,Math.max(0,col),failure.getMessage(),"오류");
            }
            boolean failed=local.stream().anyMatch(i -> "오류".equals(i.severity()));
            if (failed) errors++; else if (!local.isEmpty()) warnings++; else successes++;
            issues.addAll(local);
        }
        issues.sort(Comparator.comparingInt(i -> "오류".equals(i.severity())?0:1));
        return new Inspection(records,issues,successes,warnings,errors,rows.size(),assigned);
    }
    private String resolve(TabularRow row,int col,String group,List<Issue> issues) {
        try {
            var result=resolver.resolve(group,row.values().get(col).trim());
            if (result.warning()!=null) issue(issues,row,col,CodeTextResolver.readAs(result.codeName()),"경고");
            return result.code();
        } catch(CodeFailure e) { issue(issues,row,col,e.getMessage(),"오류"); return ""; }
    }
    private static void issue(List<Issue> issues,TabularRow row,int col,String message,String severity) {
        issues.add(new Issue(row.rowNumber(),HEADERS.get(col),row.values().get(col),message,severity));
    }
    public Map<Integer,String> apply(Inspection inspection,YearMonth month,long uploadId) {
        var assigned=new LinkedHashMap<Integer,String>();
        for (Object record : inspection.records()) {
            Row row=(Row)record; assigned.put(row.rowNumber(),projects.create(row.input(),row.id()).projectId);
        }
        return assigned;
    }
}
