package com.company.common.assignments.service;

import com.company.common.assignments.mapper.AssignmentMapper;
import com.company.common.codegroups.service.CodeTextResolver;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.service.CodeFailure;
import com.company.common.uploads.dto.UploadContracts.*;
import com.company.common.uploads.dto.UploadWorkbookContracts.TabularRow;
import com.company.common.uploads.service.UploadForm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AssignmentUploadForm implements UploadForm {
    public static final List<String> HEADERS=List.of("프로젝트ID","관리번호","직무","투입일","철수일","월 MM","청구 단가","투입 구분","계약상 인력 관리번호","연장 가능성");
    private static final List<String> FIELDS=List.of("projectId","developerId","jobCode","startDate","endDate","monthlyMm","billingPrice","inputTypeCode","contractDeveloperId","extensionPossibilityCode");
    private final CommonAssignmentService assignments;
    private final ProjectMapper projects;
    private final AssignmentMapper mapper;
    private final CodeTextResolver codes;
    public AssignmentUploadForm(CommonAssignmentService assignments,ProjectMapper projects,AssignmentMapper mapper,CodeTextResolver codes) {
        this.assignments=assignments;this.projects=projects;this.mapper=mapper;this.codes=codes;
    }
    public String formTypeCode() { return "B5"; }
    public String targetName() { return "투입 배정"; }
    public String screenId() { return "UI-12"; }
    public List<String> headers() { return HEADERS; }
    public String templateNote() { return "B.5 새 배정 등록 전용. 월 MM은 비우면 1.0입니다. 월단위·파견 청구 단가는 비우면 개발자 월 단가를 사용합니다. 기존 배정을 변경하거나 조기 철수하지 않습니다."; }
    public String templateFileName() { return "assignment-template.xlsx"; }
    public void lock() { assignments.lockSources(); }
    public record UploadRow(int number,ObjectNode body,String extension) {}
    public Inspection inspect(List<TabularRow> rows,YearMonth ignored) {
        var records=new ArrayList<UploadRow>();var issues=new ArrayList<Issue>();var seen=new HashSet<List<String>>();
        int success=0,warning=0,error=0;
        for(var row:rows) {
            int column=0;
            try {
                if(!seen.add(List.copyOf(row.values()))) throw CodeFailure.invalid("projectId","같은 내용의 중복 행입니다.");
                ObjectNode body=new ObjectMapper().createObjectNode();
                body.put("projectId",row.values().get(0).trim());
                column=1;var developer=projects.uploadDeveloper(row.values().get(1).trim());
                if(developer==null) throw CodeFailure.invalid("developerId","개발자 풀에 없는 관리번호입니다.");
                body.put("developerId",developer.id());
                column=2;body.put("jobCode",resolve("JOB",row.values().get(2),row,column,issues));
                body.put("startDate",row.values().get(3).trim());body.put("endDate",row.values().get(4).trim());
                column=5;body.put("monthlyMm",row.values().get(5).isBlank()?BigDecimal.ONE:number(row.values().get(5),"monthlyMm"));
                column=6;BigDecimal price=row.values().get(6).isBlank()?null:number(row.values().get(6),"billingPrice");
                if(price==null) {
                    var start=com.company.common.projects.service.ProjectRules.date(body,"startDate");
                    var end=com.company.common.projects.service.ProjectRules.date(body,"endDate");
                    var person=mapper.person(developer.id(),YearMonth.from(start).toString().replace("-",""),start,end);
                    if(person!=null) price=person.defaultPrice();
                }
                if(price!=null) body.put("billingPrice",price);
                column=7;body.put("inputTypeCode",resolve("INPUT_TYPE",row.values().get(7),row,column,issues));
                column=8;if(!row.values().get(8).isBlank()) {
                    var contract=projects.uploadDeveloper(row.values().get(8).trim());
                    if(contract==null) throw CodeFailure.invalid("contractDeveloperId","계약상 인력 관리번호를 확인해 주세요.");
                    body.put("contractDeveloperId",contract.id());
                }
                column=9;String extension=row.values().get(9).isBlank()?"":resolve("EXT_PSBL",row.values().get(9),row,column,issues);
                List<String> warnings=assignments.inspectRegistration(body);
                for(String text:warnings) issues.add(new Issue(row.rowNumber(),HEADERS.get(1),row.values().get(1),text,"경고"));
                records.add(new UploadRow(row.rowNumber(),body,extension));
                if(issues.stream().anyMatch(i -> i.row()==row.rowNumber())) warning++;else success++;
            } catch(CodeFailure failure) {
                int index=FIELDS.indexOf(failure.getField());column=index<0?column:index;
                issues.removeIf(i -> i.row()==row.rowNumber() && i.column().equals(HEADERS.get(FIELDS.indexOf(failure.getField())<0?0:FIELDS.indexOf(failure.getField()))));
                issues.add(new Issue(row.rowNumber(),HEADERS.get(column),row.values().get(column),failure.getMessage(),"오류"));error++;
            }
        }
        issues.sort(Comparator.comparingInt(i -> "오류".equals(i.severity())?0:1));
        return new Inspection(records,issues,success,warning,error,rows.size());
    }
    private String resolve(String group,String value,TabularRow row,int column,List<Issue> issues) {
        var result=codes.resolve(group,value.trim());
        if(result.warning()!=null) issues.add(new Issue(row.rowNumber(),HEADERS.get(column),value,CodeTextResolver.readAs(result.codeName()),"경고"));
        return result.code();
    }
    private static BigDecimal number(String text,String field) {
        String value=text.trim().replace(",","");
        if(!value.matches("[0-9]{1,15}(\\.[0-9]{1,2})?")) throw CodeFailure.invalid(field,"숫자를 확인해 주세요.");
        return new BigDecimal(value);
    }
    public Map<Integer,String> apply(Inspection inspection,YearMonth ignored,long uploadId) {
        if(inspection.errorCount()>0) throw CodeFailure.invalid("uploadId","오류가 있는 파일은 반영할 수 없습니다.");
        var result=new LinkedHashMap<Integer,String>();
        for(Object value:inspection.records()) {
            UploadRow row=(UploadRow)value;
            result.put(row.number(),assignments.create(row.body(),row.extension()).assignmentId());
        }
        return result;
    }
}
