package com.company.common.projects.service;

import com.company.common.codegroups.service.CodeTextResolver;
import com.company.common.projects.dto.ProjectContracts.Project;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.uploads.dto.UploadContracts.*;
import com.company.common.uploads.dto.UploadWorkbookContracts.TabularRow;
import com.company.common.uploads.service.UploadForm;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ProjectDistributionUploadForm implements UploadForm {
    private final ProjectMapper mapper;
    private final CommonProjectService projects;
    private final CodeTextResolver resolver;
    private final CurrentBusinessActor actor;
    public ProjectDistributionUploadForm(ProjectMapper mapper, CommonProjectService projects, CodeTextResolver resolver, CurrentBusinessActor actor) {
        this.mapper=mapper; this.projects=projects; this.resolver=resolver; this.actor=actor;
    }
    public String formTypeCode() { return "B6"; }
    public String targetName() { return "프로젝트 배분표"; }
    public String screenId() { return "UI-01"; }
    public String templateFileName() { return "distribution-template.xlsx"; }
    public String templateNote() { return "구분은 사람 또는 항목. 프로젝트 계약기간 월별 원 단위 금액. 빈칸은 0, 합계는 비교용. 사람은 관리번호·확인 이름 필수, 항목은 항목명 필수."; }
    private static CodeFailure contextError() { return CodeFailure.invalid("projectId","배분표 화면에서 프로젝트와 단계를 선택해 주세요."); }
    public List<String> headers() { throw contextError(); }
    public Inspection inspect(List<TabularRow> rows,YearMonth month) { throw contextError(); }
    public void lock() { mapper.lockSources(); }
    public Map<Integer,String> apply(Inspection inspection,YearMonth month,long uploadId) { throw contextError(); }
    public UploadForm withContext(Context context) {
        if (context==null || context.projectId()==null || context.distributionStepCode()==null) throw contextError();
        ProjectRules.id(context.projectId());
        if (mapper.codeName("DSTRB_STEP",context.distributionStepCode())==null) throw ProjectRules.invalid("distributionStepCode");
        return new Bound(context);
    }
    private record DistributionLine(int row,String type,String developerId,String item,Map<String,BigDecimal> amounts) {}
    private final class Bound implements UploadForm {
        private final Context context;
        Bound(Context context) { this.context=context; }
        public String formTypeCode() { return "B6"; }
        public String targetName() { return ProjectDistributionUploadForm.this.targetName(); }
        public String screenId() { return "UI-01"; }
        public String templateFileName() { return ProjectDistributionUploadForm.this.templateFileName(); }
        public String templateNote() { return ProjectDistributionUploadForm.this.templateNote(); }
        private Project project() {
            Project p=projects.getCommonProject(context.projectId());
            if (!"TK".equals(p.contractTypeCode)) throw contextError();
            return p;
        }
        public List<String> headers() {
            var p=project(); var headers=new ArrayList<>(List.of("구분","관리번호","이름","항목명"));
            for (var m=YearMonth.from(p.startDate);!m.isAfter(YearMonth.from(p.endDate));m=m.plusMonths(1)) headers.add(m.toString());
            headers.add("합계"); return headers;
        }
        public void lock() { mapper.lockSources(); }
        // 목록 「불일치 N」과 같게 사람·구분마다 한 건으로 센다.
        public Integer distributionMismatchCount() { return (int) mapper.mismatches(context.projectId()).stream().map(c -> c.name + "|" + c.kind).distinct().count(); }
        public Inspection inspect(List<TabularRow> rows,YearMonth ignored) {
            var headers=headers(); var issues=new ArrayList<Issue>(); var records=new ArrayList<DistributionLine>();
            int success=0,warnings=0,errors=0;
            var identities = new HashSet<String>();
            for (var r:rows) {
                var local=new ArrayList<Issue>(); var v=r.values();
                try {
                    // 구분: 명세 B.6 표기 「사람」/「항목」, 또는 공통코드 「턴키 배분 줄 유형」 이름·코드.
                    String kind=v.get(0).trim(); String type;
                    if ("사람".equals(kind)) type="DEV";
                    else if ("항목".equals(kind)) type="ITEM";
                    else {
                        var result=resolver.resolve("ROW_TYPE",kind); type=result.code();
                        if(result.warning()!=null) issue(local,r,0,CodeTextResolver.readAs(result.codeName()),"경고",headers);
                    }
                    String dev=null,item=null;
                    if ("DEV".equals(type)) {
                        var person=mapper.uploadDeveloper(v.get(1).trim());
                        if(person==null) issue(local,r,1,"등록된 관리번호를 찾을 수 없습니다.","오류",headers);
                        else { dev=person.id(); if(!person.name().equals(v.get(2).trim())) issue(local,r,2,"관리번호의 이름과 일치하지 않습니다.","오류",headers); }
                    } else if ("ITEM".equals(type)) {
                        item=v.get(3).trim(); if(item.isEmpty() || item.codePointCount(0,item.length())>50) issue(local,r,3,"항목명은 필수이며 50자 이내입니다.","오류",headers);
                    } else issue(local,r,0,"사람 또는 항목을 선택해 주세요.","오류",headers);
                    String identity = type + ":" + ("DEV".equals(type) ? dev : item);
                    if (!identities.add(identity)) issue(local,r,"DEV".equals(type)?1:3,"같은 사람 또는 항목이 중복되었습니다.","오류",headers);
                    var amounts=new LinkedHashMap<String,BigDecimal>(); BigDecimal total=BigDecimal.ZERO;
                    for (int col=4;col<headers.size()-1;col++) {
                        String text=v.get(col).trim().replace(",",""); BigDecimal value=BigDecimal.ZERO; // 천 단위 쉼표는 지우고 읽는다
                        if (!text.isEmpty()) {
                            if(!text.matches("[0-9]{1,15}")) issue(local,r,col,"0 이상의 정수 원 단위 금액을 적어 주세요.","오류",headers);
                            else value=new BigDecimal(text);
                        }
                        amounts.put(headers.get(col).replace("-",""),value); total=total.add(value);
                    }
                    String sum=v.get(headers.size()-1).trim().replace(",","");
                    if(!sum.isEmpty() && (!sum.matches("[0-9]+") || total.compareTo(new BigDecimal(sum))!=0)) issue(local,r,headers.size()-1,"월 금액 합계와 다릅니다. 합계는 저장하지 않습니다.","경고",headers);
                    if(local.stream().noneMatch(i -> "오류".equals(i.severity()))) records.add(new DistributionLine(r.rowNumber(),type,dev,item,amounts));
                } catch(CodeFailure e) { issue(local,r,0,e.getMessage(),"오류",headers); }
                if(local.stream().anyMatch(i -> "오류".equals(i.severity()))) errors++; else if(local.isEmpty()) success++; else warnings++;
                issues.addAll(local);
            }
            return new Inspection(records,issues,success,warnings,errors,rows.size());
        }
        /** 데이터 범위: 배분표의 모든 줄은 화면에서 고른 프로젝트를 쓴다(오류는 구분 칸에 붙인다). */
        public List<ScopeTarget> scopeTargets(Inspection inspection) {
            return inspection.records().stream().map(DistributionLine.class::cast)
                .map(line -> new ScopeTarget(line.row(),0,"PROJ",context.projectId())).toList();
        }
        public Map<Integer,String> apply(Inspection inspection,YearMonth ignored,long uploadId) {
            Project p=project(); if(!p.canEdit) throw CodeFailure.invalid("projectId","읽기 전용 프로젝트입니다.");
            mapper.deleteDistribution(context.projectId(),context.distributionStepCode());
            int sequence=0;
            for(Object value:inspection.records()) {
                DistributionLine line=(DistributionLine)value; sequence++;
                for(var e:line.amounts().entrySet()) mapper.insertDistribution(new ProjectMapper.DistributionCell(context.projectId(),context.distributionStepCode(),sequence,e.getKey(),line.type(),line.developerId(),line.item(),e.getValue(),actor.userId(),uploadId));
            }
            return Map.of();
        }
    }
    private static void issue(List<Issue> issues,TabularRow row,int col,String message,String severity,List<String> headers) {
        issues.add(new Issue(row.rowNumber(),headers.get(col),row.values().get(col),message,severity));
    }
}
