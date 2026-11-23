package com.company.common.assignments.service;

import com.company.common.assignments.mapper.AssignmentMapper;
import com.company.common.calculation.dto.CalculationRuleContracts.TextResolution;
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
    /** SFR-012-05 미등록 관리번호 문구. */
    static final String UNREGISTERED="등록되지 않은 관리번호입니다. 개발자 풀에 먼저 등록해 주세요.";
    /** 연장 가능성 값 없음의 화면 표기. */
    private static final String UNDECIDED="미정";
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
    public List<String> exampleRow() { return List.of("P-0001", "K-0001", "백엔드 개발", "2026-01-01", "2026-12-31", "1.0", "7000000", "신규", "", "보통"); }
    public void lock() { assignments.lockSources(); }
    public record UploadRow(int number,ObjectNode body,String extension) {}
    public Inspection inspect(List<TabularRow> rows,YearMonth ignored) {
        var records=new ArrayList<UploadRow>();var issues=new ArrayList<Issue>();var seen=new HashSet<List<String>>();
        int success=0,warning=0,error=0;
        for(var row:rows) {
            int column=0;
            try {
                // 엑셀에 같은 줄이 두 번 있으면 같은 배정이 두 건 생기므로 막는다(사용자 결정 2026-11-18, SFR-012-05 「중복도 오류」).
                if(!seen.add(row.values().stream().map(v->v==null?"":v.trim()).toList())) throw CodeFailure.invalid("projectId","같은 내용의 중복 행입니다.");
                ObjectNode body=new ObjectMapper().createObjectNode();
                body.put("projectId",row.values().get(0).trim());
                column=1;var developer=mapper.uploadDeveloper(row.values().get(1).trim());
                if(developer==null) throw CodeFailure.invalid("developerId",UNREGISTERED);
                if(!"Y".equals(developer.useYn())) throw CodeFailure.invalid("developerId","사용 중지된 개발자의 관리번호입니다. 개발자 풀에서 확인해 주세요.");
                if("INDRCT".equals(developer.directClassCode())) throw CodeFailure.invalid("developerId","간접 인력은 투입 배정 대상이 아닙니다.");
                body.put("developerId",developer.id());
                column=2;body.put("jobCode",resolve("JOB",row.values().get(2),row,column,issues));
                // SFR-012-03: 2026-11-19·2026.11.19·2026/11/19 모두 받는다(개발자 업로드와 같은 규칙). 못 읽으면 원문을 넘겨 아래 검사가 오류를 낸다.
                body.put("startDate",uploadDate(row.values().get(3)));body.put("endDate",uploadDate(row.values().get(4)));
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
                    var contract=mapper.uploadDeveloper(row.values().get(8).trim());
                    if(contract==null) throw CodeFailure.invalid("contractDeveloperId",UNREGISTERED);
                    body.put("contractDeveloperId",contract.id());
                }
                column=9;String extension=extension(row.values().get(9),row,column,issues);
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
        return warn(codes.resolve(group,value.trim()),value,row,column,issues);
    }
    /**
     * 연장 가능성(B.5): 명세 표기 「높음·보통·낮음·빈칸」. 코드 이름은 「높음 (80%)」처럼 괄호 설명이 붙어 있어
     * 이름 앞부분으로 읽는다(코드·전체 이름·엑셀 표기 대응표도 받음). 빈칸과 화면 표기 「미정」은 값 없음(미정).
     */
    private String extension(String value,TabularRow row,int column,List<Issue> issues) {
        String text=value.trim();
        if(text.isEmpty() || UNDECIDED.equals(text)) return "";
        return warn(codes.resolveLabel("EXT_PSBL",text),value,row,column,issues);
    }
    private static String warn(TextResolution result,String value,TabularRow row,int column,List<Issue> issues) {
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
    private static String uploadDate(String raw) {
        String text=raw.trim();
        var parsed=com.company.common.developers.service.DeveloperUploadForm.parseDate(text);
        return parsed==null?text:parsed.toString();
    }
}
