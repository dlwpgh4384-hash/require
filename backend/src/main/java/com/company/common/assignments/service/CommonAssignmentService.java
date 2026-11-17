package com.company.common.assignments.service;

import com.company.common.assignments.dto.AssignmentContracts.*;
import com.company.common.assignments.mapper.AssignmentMapper;
import com.company.common.assignments.mapper.AssignmentRows.*;
import com.company.common.clients.service.ClientRules;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.projects.service.ProjectRules;
import com.company.common.projects.service.ProjectAmountRules;
import com.company.common.support.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class CommonAssignmentService {
    private final AssignmentMapper mapper;
    private final ProjectMapper projects;
    private final BusinessCalendar calendar;
    private final CurrentBusinessActor actor;
    private final AssignmentMonthRules rules;
    private final ProjectAmountRules projectAmounts;
    public CommonAssignmentService(AssignmentMapper mapper, ProjectMapper projects, BusinessCalendar calendar,
            CurrentBusinessActor actor, AssignmentMonthRules rules, ProjectAmountRules projectAmounts) {
        this.mapper=mapper; this.projects=projects; this.calendar=calendar; this.actor=actor;
        this.rules=rules; this.projectAmounts=projectAmounts;
    }
    public Instant now() { return calendar.now(); }
    public String userId() { return actor.userId(); }
    public List<String> roles() { return new ArrayList<>(actor.roles()); }
    public static boolean blank(String s) { return s==null || s.isBlank(); }
    private static String db(YearMonth m) { return m.toString().replace("-",""); }
    private int year(Integer selected) {
        int y=selected==null?calendar.today().getYear():selected;
        if (y<1 || y>9999) throw CodeFailure.invalid("year","연도를 확인해 주세요.");
        return y;
    }
    public Items<Assignment> getCommonAssignments(Filter f) {
        int y=year(f.year());
        if (!blank(f.warning()) && !Set.of("MM 1.0 초과","미계약 선투입").contains(f.warning())) throw CodeFailure.invalid("warning","경고 조건을 확인해 주세요.");
        String q=blank(f.keyword())?"":f.keyword().trim().toLowerCase(Locale.ROOT);
        return new Items<>(mapper.rows().stream().filter(r -> "ACT".equals(r.statusCode()))
            .filter(r -> r.startDate().getYear()<=y && r.endDate().getYear()>=y)
            .filter(r -> blank(f.projectId()) || f.projectId().equals(r.projectId()))
            .filter(r -> blank(f.jobCode()) || f.jobCode().equals(r.jobCode()))
            .filter(r -> q.isEmpty() || (r.name()+" "+r.managementNumber()).toLowerCase(Locale.ROOT).contains(q))
            .map(r -> view(r,y)).filter(r -> blank(f.warning()) || r.warnings().contains(f.warning())).toList());
    }
    public Assignment getCommonAssignment(String id) { return view(required(id),calendar.today().getYear()); }
    private Row required(String id) {
        identifier(id,"assignmentId"); Row row=mapper.row(id);
        if (row==null) throw new CodeFailure(404,"assignmentId","투입 배정을 찾을 수 없습니다.");
        return row;
    }
    private Row locked(String id) {
        identifier(id,"assignmentId"); Row row=mapper.lockRow(id);
        if (row==null) throw CodeFailure.invalid("assignmentId","투입 배정을 찾을 수 없습니다.");
        return row;
    }
    public static void identifier(String id,String field) {
        if (blank(id) || id.length()>20 || id.chars().anyMatch(Character::isISOControl)) throw CodeFailure.invalid(field,"식별자를 확인해 주세요.");
    }
    private Assignment view(Row r,int year) {
        var warnings=new ArrayList<String>();
        BigDecimal total=Objects.requireNonNullElse(mapper.overload(r.developerId(),year),BigDecimal.ZERO);
        if (rules.over(total)) warnings.add("MM 1.0 초과");
        if ("PRE".equals(r.projectStatusCode())) warnings.add("미계약 선투입");
        Person p=mapper.person(r.developerId(),db(YearMonth.from(r.startDate())),r.startDate(),r.endDate());
        if (p!=null && p.onLeave()) warnings.add("휴직 기간에 배정됩니다.");
        if (p!=null && "DP".equals(r.contractTypeCode()) && Set.of("FL","OS").contains(p.employeeTypeCode())) warnings.add("파견 프로젝트에 프리랜서/외주 인력을 배정합니다.");
        if (crossesClose(r.startDate(),r.endDate())) warnings.add("마감월 금액은 유지됩니다. 전월 조정은 UI-10에서 직접 입력해 주세요.");
        var project=projects.selectProject(r.projectId(),calendar.today(),db(calendar.currentMonth()));
        if (p!=null && project!=null && !"IN".equals(r.contractTypeCode()) && !Objects.equals(p.partId(),project.salesPartId))
            warnings.add("영업 파트가 다릅니다: "+p.name()+"님은 "+p.partName()+" 소속, 프로젝트는 "+project.salesPartName+"입니다. 매출은 프로젝트 파트로, 이 사람 이익금은 소속 파트(본사면 파트 정산 제외)로 잡힙니다. 파트를 잘못 골랐다면 개발자 풀에서 고쳐 주세요.");
        if ("SUB".equals(r.inputTypeCode()) && "TK".equals(r.contractTypeCode()) && r.contractDeveloperId()!=null && mapper.distribution(r.projectId(),r.contractDeveloperId())) warnings.add("턴키 배분표는 다시 옮겨 올려 주셔야 합니다. 그 전까지는 배분 불일치 경고가 뜹니다.");
        return new Assignment(r.assignmentId(),r.projectId(),r.projectName(),r.developerId(),r.name(),r.managementNumber(),r.jobCode(),r.jobName(),r.startDate(),r.endDate(),
            AssignmentPricePolicy.direct(roles())?r.billingPrice():null,r.monthlyMm(),Objects.requireNonNullElse(mapper.yearMm(r.assignmentId(),year),BigDecimal.ZERO),
            r.extensionPossibilityCode(),code("EXT_PSBL",r.extensionPossibilityCode(),"미정"),r.inputTypeCode(),code("INPUT_TYPE",r.inputTypeCode(),""),r.extensionCount(),warnings,total,mapper.history(r.assignmentId()).size(),r.modifiedAt());
    }
    private String code(String group,String value,String fallback) {
        return value==null?fallback:Objects.requireNonNullElse(projects.codeName(group,value),value);
    }
    private String validCode(String group,String value,String field) {
        if (blank(value) || projects.codeName(group,value)==null) throw CodeFailure.invalid(field,"등록된 값을 골라 주세요.");
        return value;
    }
    private boolean crossesClose(LocalDate start,LocalDate end) {
        return mapper.closedMonths().stream().anyMatch(m -> m.compareTo(db(YearMonth.from(start)))>=0 && m.compareTo(db(YearMonth.from(end)))<=0);
    }
    public Items<Candidate> getCommonAssignmentCandidates() {
        return new Items<>(mapper.candidates().stream().map(r -> {
            YearMonth month=calendar.fromDatabaseMonth(r.month());
            if (r.months()<1) throw CodeFailure.invalid("candidateId","후보 원천 예정 기간을 확인해 주세요.");
            LocalDate start=month.atDay(1),end=month.plusMonths(r.months()-1).atEndOfMonth();
            if (r.projectStart()!=null && start.isBefore(r.projectStart())) start=r.projectStart();
            if (r.projectEnd()!=null && end.isAfter(r.projectEnd())) end=r.projectEnd();
            if (start.isAfter(end)) throw CodeFailure.invalid("candidateId","후보 원천 예정 기간이 프로젝트 계약 기간과 겹치지 않습니다.");
            return new Candidate(r.candidateId(),r.projectId(),r.projectName(),r.developerId(),r.name(),r.jobCode(),r.jobName(),start,end);
        }).toList());
    }
    @Transactional
    public Removed postCommonAssignmentCandidateRemove(String id) {
        if (id==null || !id.matches("[0-9]{1,12}")) throw CodeFailure.invalid("candidateId","후보 식별자를 확인해 주세요.");
        CandidateRow row=mapper.lockCandidate(id);
        if (row==null) throw CodeFailure.invalid("candidateId","배정 후보를 찾을 수 없습니다.");
        if ("CNVT".equals(row.statusCode())) mapper.removeCandidate(id,userId());
        return new Removed(id);
    }
    public Items<ContractDeveloper> getCommonAssignmentContractDevelopers(String projectId,String developerId,LocalDate start) {
        if (blank(projectId)) return new Items<>(new ArrayList<>());
        identifier(projectId,"projectId");
        var groups=new TreeMap<String,List<Row>>();
        mapper.rows().stream().filter(r -> projectId.equals(r.projectId()) && !Objects.equals(developerId,r.developerId()))
            .forEach(r -> groups.computeIfAbsent(r.developerId(),k -> new ArrayList<>()).add(r));
        return new Items<>(groups.values().stream().map(rows -> {
            var latest=rows.stream().max(Comparator.comparing(Row::endDate).thenComparing(Row::startDate).thenComparing(Row::assignmentId)).orElseThrow();
            return new ContractDeveloper(latest.developerId(),latest.name(),latest.endDate(),AssignmentPricePolicy.direct(roles()) && Set.of("MO","DP").contains(latest.contractTypeCode())?Objects.requireNonNullElse(mapper.latestPrice(latest.assignmentId()),latest.billingPrice()):null,
                start==null?new ArrayList<>():rows.stream().filter(r -> "ACT".equals(r.statusCode()) && r.startDate().isBefore(start) && !r.endDate().isBefore(start))
                    .map(r -> view(r,start.getYear())).toList());
        }).sorted(Comparator.comparing(ContractDeveloper::name).thenComparing(ContractDeveloper::developerId)).toList());
    }
    public Items<History> getCommonAssignmentHistory(String id,String type) {
        required(id);
        if (!blank(type)) validCode("ASGN_CHG_TYPE",type,"changeTypeCode");
        return new Items<>(mapper.history(id).stream().filter(h -> blank(type) || type.equals(h.type())).map(this::historyView).toList());
    }
    private History historyView(HistoryRow h) {
        return new History(Integer.toString(h.sequence()),h.assignmentId(),h.effectiveDate(),h.type(),code("ASGN_CHG_TYPE",h.type(),""),
            Objects.requireNonNullElse(h.beforeValue(),""),Objects.requireNonNullElse(h.afterValue(),""),code("ASGN_CHG_RSN",h.reasonCode(),""),
            Objects.requireNonNullElse(h.memo(),""),h.actor(),h.registeredAt(),h.retroactiveYn(),h.correctedYn(),h.registeredAt());
    }
    public void lockSources() { projects.lockSources(); }
    public List<String> inspectRegistration(JsonNode b) {
        String projectId=ProjectRules.text(b,"projectId",20,true),developerId=ProjectRules.text(b,"developerId",20,true);
        LocalDate start=ProjectRules.date(b,"startDate"),end=ProjectRules.date(b,"endDate");
        var p=projects.selectProject(projectId,calendar.today(),db(calendar.currentMonth()));
        if (p==null || !Set.of("ING","PRE").contains(p.statusCode)) throw CodeFailure.invalid("projectId","종료·실주 또는 없는 프로젝트입니다.");
        Person person=mapper.person(developerId,db(YearMonth.from(start)),start,end);
        if(person==null) throw CodeFailure.invalid("developerId","개발자를 골라 주세요.");
        period(start,end,p.endDate,person,"endDate");
        validCode("JOB",ProjectRules.text(b,"jobCode",10,true),"jobCode");monthly(b,"monthlyMm",null);
        String type=ProjectRules.text(b,"inputTypeCode",10,true);
        if(!Set.of("NEW","ADD","SUB","FREE").contains(type)) throw CodeFailure.invalid("inputTypeCode","투입 구분을 골라 주세요.");
        String contract=ProjectRules.text(b,"contractDeveloperId",20,false);
        if("SUB".equals(type) && (blank(contract) || contract.equals(developerId) || mapper.rows().stream().noneMatch(r -> projectId.equals(r.projectId()) && contract.equals(r.developerId())))) throw CodeFailure.invalid("contractDeveloperId","계약상 인력을 골라 주세요.");
        if(!"SUB".equals(type) && contract!=null) throw CodeFailure.invalid("contractDeveloperId","대체투입에서만 계약상 인력을 선택합니다.");
        AssignmentPricePolicy.registration(roles(),p.contractTypeCode,type,ProjectRules.money(b,"billingPrice",false),person.defaultPrice());
        var warnings=new ArrayList<String>();
        if(!"IN".equals(p.contractTypeCode) && !Objects.equals(p.salesPartId,person.partId())) warnings.add("영업 파트가 다른 배정");
        if(person.onLeave()) warnings.add("휴직 기간에 배정됩니다.");
        if(crossesClose(start,end)) warnings.add("마감월 금액은 유지됩니다. 전월 조정은 UI-10에서 직접 입력해 주세요.");
        return warnings;
    }
    private void lockProject(String id) {
        if (projects.lockProject(id)==null) throw CodeFailure.invalid("projectId","프로젝트를 골라 주세요.");
    }
    @Transactional
    public Assignment postCommonAssignment(JsonNode body) { return create(body,null); }
    @Transactional
    public Assignment create(JsonNode b,String uploadExtension) {
        ClientRules.object(b,Set.of("projectId","developerId","jobCode","startDate","endDate","monthlyMm","inputTypeCode","billingPrice","contractDeveloperId","replacementAction","earlyWithdrawalAssignmentIds","reasonCode","memo"));
        String developer=ProjectRules.text(b,"developerId",20,false);
        if (blank(developer)) throw CodeFailure.invalid("developerId","개발자를 골라 주세요.");
        String projectId=ProjectRules.text(b,"projectId",20,true);
        lockSources(); lockProject(projectId);
        LocalDate start=ProjectRules.date(b,"startDate"),end=ProjectRules.date(b,"endDate");
        var project=projects.selectProject(projectId,calendar.today(),db(calendar.currentMonth()));
        if (project==null || !Set.of("ING","PRE").contains(project.statusCode)) throw CodeFailure.invalid("projectId","종료·실주 프로젝트에는 배정할 수 없습니다.");
        Person person=mapper.person(developer,db(YearMonth.from(start)),start,end);
        if (person==null) throw CodeFailure.invalid("developerId","개발자를 골라 주세요.");
        period(start,end,project.endDate,person,"endDate");
        String job=validCode("JOB",ProjectRules.text(b,"jobCode",10,true),"jobCode");
        String input=ProjectRules.text(b,"inputTypeCode",10,true);
        if (!Set.of("NEW","ADD","SUB","FREE").contains(input)) throw CodeFailure.invalid("inputTypeCode","투입 구분을 골라 주세요.");
        BigDecimal mm=monthly(b,"monthlyMm",null);
        String contract=ProjectRules.text(b,"contractDeveloperId",20,false);
        if ("SUB".equals(input)) {
            if (blank(contract) || contract.equals(developer) || mapper.rows().stream().noneMatch(r -> r.projectId().equals(projectId) && r.developerId().equals(contract))) throw CodeFailure.invalid("contractDeveloperId","계약상 인력을 골라 주세요.");
        } else if (contract!=null) throw CodeFailure.invalid("contractDeveloperId","대체투입에서만 계약상 인력을 선택합니다.");
        BigDecimal requested=ProjectRules.money(b,"billingPrice",false);
        BigDecimal standard=person.defaultPrice();
        if ("SUB".equals(input)) {
            var previous=mapper.rows().stream().filter(r -> r.projectId().equals(projectId) && r.developerId().equals(contract))
                .max(Comparator.comparing(Row::endDate).thenComparing(Row::startDate).thenComparing(Row::assignmentId)).orElseThrow();
            standard=Objects.requireNonNullElse(mapper.latestPrice(previous.assignmentId()),previous.billingPrice());
        }
        BigDecimal price=AssignmentPricePolicy.registration(roles(),project.contractTypeCode,input,requested,standard);
        String memo=ProjectRules.text(b,"memo",200,false),reason=ProjectRules.text(b,"reasonCode",10,false);
        String action=ProjectRules.text(b,"replacementAction",20,false);
        if (action!=null && !Set.of("together","assignment-only").contains(action)) throw CodeFailure.invalid("replacementAction","처리 방식을 골라 주세요.");
        var sources=new TreeSet<String>();
        if (b.has("earlyWithdrawalAssignmentIds")) {
            if (!b.get("earlyWithdrawalAssignmentIds").isArray()) throw CodeFailure.invalid("earlyWithdrawalAssignmentIds","선택 목록을 확인해 주세요.");
            for (var id:b.get("earlyWithdrawalAssignmentIds")) {
                if (!id.isTextual()) throw CodeFailure.invalid("earlyWithdrawalAssignmentIds","선택 목록을 확인해 주세요.");
                identifier(id.textValue(),"earlyWithdrawalAssignmentIds"); sources.add(id.textValue());
            }
        }
        var originals=new ArrayList<Row>();
        if ("together".equals(action)) {
            if (!"SUB".equals(input) || sources.isEmpty()) throw CodeFailure.invalid("earlyWithdrawalAssignmentIds","같이 처리할 배정을 골라 주세요.");
            if (blank(reason)) throw CodeFailure.invalid("reasonCode","철수 사유를 골라 주세요.");
            validCode("ASGN_CHG_RSN",reason,"reasonCode");
            for (String id:sources) {
                Row row=locked(id);
                if (!row.projectId().equals(projectId) || !row.developerId().equals(contract) || !"ACT".equals(row.statusCode()) || !row.startDate().isBefore(start) || row.endDate().isBefore(start)) throw CodeFailure.invalid("earlyWithdrawalAssignmentIds","원배정이 변경되었습니다. 다시 불러와 주세요.");
                originals.add(row);
            }
        } else if (!sources.isEmpty()) throw CodeFailure.invalid("earlyWithdrawalAssignmentIds","같이 처리에서만 원배정을 선택합니다.");
        String extension=uploadExtension==null?null:extension(uploadExtension,"extensionPossibilityCode");
        String id=allocate();
        Row row=new Row(id,projectId,developer,job,start,end,mm,price,input,contract,extension,0,"N","ACT",null,project.projectName,person.name(),null,code("JOB",job,""),project.contractTypeCode,project.statusCode);
        rules.sourceSemantics();
        mapper.insert(new Write(row,userId(),null));
        history(row,new Event("INPUT",start,"","투입",null,memo,price,mm,null));
        for (Row source:originals) {
            Row withdrawn=replace(source,new Values(start.minusDays(1),source.monthlyMm(),source.billingPrice(),source.inputTypeCode(),source.extensionPossibilityCode(),source.extensionCount(),"Y",source.statusCode()));
            update(source,withdrawn);
            history(source,new Event("EARLY",start.minusDays(1),source.endDate().toString(),start.minusDays(1).toString(),reason,memo==null?"대체투입("+person.name()+")":memo,null,null,null));
            recalculate(withdrawn);
        }
        recalculate(row);
        return getCommonAssignment(id);
    }
    private String allocate() {
        Set<String> existing=new HashSet<>(mapper.ids());
        long next=existing.stream().filter(i -> i.matches("A-[0-9]+"))
            .map(i -> { try { return Long.parseLong(i.substring(2)); } catch (NumberFormatException e) { return 0L; } }).max(Long::compareTo).orElse(0L)+1;
        for(int retry=0;retry<100;retry++,next++) {
            String id=String.format(Locale.ROOT,"A-%04d",next);
            if (id.length()>20) throw CodeFailure.invalid("assignmentId","투입 번호 발급 범위를 초과했습니다.");
            if (!existing.contains(id) && mapper.row(id)==null) return id;
        }
        throw CodeFailure.conflict();
    }
    private void period(LocalDate start,LocalDate end,LocalDate projectEnd,Person person,String field) {
        if (end.isBefore(start)) throw CodeFailure.invalid(field,"철수일은 투입일 이후여야 합니다.");
        if (projectEnd!=null && end.isAfter(projectEnd)) throw CodeFailure.invalid(field,"철수일이 프로젝트 계약 종료일("+projectEnd+")보다 늦습니다. 「프로젝트·투입 › 프로젝트」에서 계약 기간을 먼저 연장해 주세요.");
        if (person!=null && person.retirementDate()!=null && end.isAfter(person.retirementDate())) throw CodeFailure.invalid(field,"철수일이 "+person.name()+"님의 퇴사일("+person.retirementDate()+")보다 늦습니다.");
    }
    public static BigDecimal monthly(JsonNode b,String field,BigDecimal fallback) {
        if (!b.has(field) && fallback!=null) return fallback;
        var n=b.get(field);
        if (n==null || !n.isNumber() || n.decimalValue().signum()<=0 || n.decimalValue().compareTo(BigDecimal.ONE)>0 || n.decimalValue().stripTrailingZeros().scale()>2) throw CodeFailure.invalid(field,"월 MM은 0 초과 1.00 이하로 입력해 주세요.");
        return n.decimalValue();
    }
    public static Instant token(JsonNode b) {
        if (!b.has("modifiedAt")) throw CodeFailure.invalid("modifiedAt","수정 시각을 제출해 주세요.");
        if (b.get("modifiedAt").isNull()) return null;
        try { if (!b.get("modifiedAt").isTextual()) throw new IllegalArgumentException(); return Instant.parse(b.get("modifiedAt").textValue()); }
        catch (RuntimeException e) { throw CodeFailure.invalid("modifiedAt","수정 시각을 확인해 주세요."); }
    }
    private void checkVersion(Row row,Instant token) { if (!Objects.equals(row.modifiedAt(),token)) throw CodeFailure.conflict(); }
    private void active(Row r) { if (!"ACT".equals(r.statusCode())) throw new CodeFailure(409,"assignmentId","취소된 배정은 변경할 수 없습니다."); }
    private void update(Row old,Row row) { if (mapper.update(new Write(row,userId(),old.modifiedAt()))!=1) throw CodeFailure.conflict(); }
    private record Values(LocalDate end,BigDecimal mm,BigDecimal price,String input,String extension,int count,String early,String status) {}
    private record Event(String type,LocalDate date,String before,String after,String reason,String memo,BigDecimal price,BigDecimal mm,Integer target) {}
    private static Row replace(Row r,Values v) {
        return new Row(r.assignmentId(),r.projectId(),r.developerId(),r.jobCode(),r.startDate(),v.end(),v.mm(),v.price(),v.input(),r.contractDeveloperId(),v.extension(),v.count(),v.early(),v.status(),r.modifiedAt(),r.projectName(),r.name(),r.managementNumber(),r.jobName(),r.contractTypeCode(),r.projectStatusCode());
    }
    private void history(Row r,Event e) {
        mapper.insertHistory(new HistoryWrite(new HistoryRow(mapper.nextHistory(r.assignmentId()),r.assignmentId(),e.type(),e.date(),e.before(),e.after(),e.reason(),e.memo(),e.price(),e.mm(),e.date().isBefore(calendar.today())?"Y":"N",crossesClose(e.date(),r.endDate())?"Y":"N","N",e.target(),userId(),null),userId()));
    }
    private String extension(String text,String field) {
        if (text==null) throw CodeFailure.invalid(field,"연장 가능성을 골라 주세요.");
        return text.isEmpty()?null:validCode("EXT_PSBL",text,field);
    }
    private String extensionInput(JsonNode b,String field) {
        JsonNode value=b.get(field);
        if (value==null || !value.isTextual() || value.textValue().length()>10) throw CodeFailure.invalid(field,"연장 가능성을 골라 주세요.");
        return extension(value.textValue(),field);
    }
    @Transactional
    public Assignment postCommonAssignmentChange(String id,JsonNode b) {
        ClientRules.object(b,Set.of("modifiedAt","changeTypeCode","effectiveDate","newEndDate","newBillingPrice","newMonthlyMm","newExtensionPossibilityCode","newInputTypeCode","reasonCode","memo"));
        Row initial=required(id); lockSources(); lockProject(initial.projectId()); Row r=locked(id);
        checkVersion(r,token(b)); active(r);
        String type=ProjectRules.text(b,"changeTypeCode",10,true);
        if (!Set.of("EXT","EARLY","UPRC","RATE","EXTP","KIND").contains(type)) throw CodeFailure.invalid("changeTypeCode","변경 종류를 골라 주세요.");
        LocalDate effective=ProjectRules.date(b,"effectiveDate");
        boolean monthOnly=Set.of("UPRC","RATE").contains(type);
        if (monthOnly) effective=effective.withDayOfMonth(1);
        if (monthOnly?YearMonth.from(effective).isBefore(YearMonth.from(r.startDate())):effective.isBefore(r.startDate())) throw CodeFailure.invalid("effectiveDate","적용일은 투입일 이전일 수 없습니다.");
        String memo=ProjectRules.text(b,"memo",200,false),reason=ProjectRules.text(b,"reasonCode",10,false);
        if (("EARLY".equals(type) || effective.isBefore(calendar.today()) || crossesClose(effective,r.endDate())) && blank(reason)) throw CodeFailure.invalid("reasonCode","변경 사유를 골라 주세요.");
        if (!blank(reason)) validCode("ASGN_CHG_RSN",reason,"reasonCode");
        LocalDate end=r.endDate(); BigDecimal mm=r.monthlyMm(),price=r.billingPrice(),newPrice=null,newMm=null;
        String input=r.inputTypeCode(),extension=r.extensionPossibilityCode(),early=r.earlyYn(); int count=r.extensionCount();
        String before,after;
        switch(type) {
            case "EXT", "EARLY" -> {
                end=ProjectRules.date(b,"newEndDate");
                if ("EXT".equals(type)?!end.isAfter(r.endDate()):end.isAfter(r.endDate())) throw CodeFailure.invalid("newEndDate","변경 종료일을 확인해 주세요.");
                var p=projects.selectProject(r.projectId(),calendar.today(),db(calendar.currentMonth()));
                Person person=mapper.person(r.developerId(),db(YearMonth.from(effective)),r.startDate(),end);
                period(r.startDate(),end,p.endDate,person,"newEndDate");
                before=r.endDate().toString(); after=end.toString();
                if ("EXT".equals(type)) { if(count>=999) throw CodeFailure.invalid("newEndDate","연장 횟수 범위를 초과했습니다."); count++; } else early="Y";
            }
            case "UPRC" -> {
                if (!AssignmentPricePolicy.direct(roles()) || !AssignmentPricePolicy.billable(r.contractTypeCode(),r.inputTypeCode())) throw CodeFailure.invalid("newBillingPrice","이 배정은 단가를 직접 변경할 수 없습니다.");
                newPrice=ProjectRules.money(b,"newBillingPrice",true);
                if (newPrice.signum()<=0) throw CodeFailure.invalid("newBillingPrice","새 단가는 양수여야 합니다.");
                before=rules.effective(mapper.history(id),"UPRC",YearMonth.from(effective),r.billingPrice()).toPlainString(); after=newPrice.toPlainString();
            }
            case "RATE" -> {
                newMm=monthly(b,"newMonthlyMm",null);
                before=rules.effective(mapper.history(id),"RATE",YearMonth.from(effective),r.monthlyMm()).toPlainString(); after=newMm.toPlainString();
            }
            case "EXTP" -> {
                extension=extensionInput(b,"newExtensionPossibilityCode");
                before=code("EXT_PSBL",r.extensionPossibilityCode(),"미정"); after=code("EXT_PSBL",extension,"미정");
            }
            case "KIND" -> {
                input=ProjectRules.text(b,"newInputTypeCode",10,true);
                if (!Set.of("NEW","ADD").contains(input) || !Set.of("NEW","ADD").contains(r.inputTypeCode())) throw CodeFailure.invalid("newInputTypeCode","무상지원·대체투입으로 바꾸거나 무상지원·대체투입에서 바꿀 수 없습니다. 조기 철수(또는 배정 취소) 후 새로 배정하세요.");
                before=code("INPUT_TYPE",r.inputTypeCode(),""); after=code("INPUT_TYPE",input,"");
            }
            default -> throw new IllegalStateException();
        }
        boolean numeric=type.equals("UPRC") || type.equals("RATE");
        if (numeric?new BigDecimal(before).compareTo(new BigDecimal(after))==0:before.equals(after)) throw CodeFailure.invalid("changeTypeCode","현재와 다른 값을 입력해 주세요.");
        Row changed=replace(r,new Values(end,mm,price,input,extension,count,early,r.statusCode()));
        update(r,changed);
        history(r,new Event(type,effective,before,after,reason,memo,newPrice,newMm,null));
        refreshCache(mapper.row(id));
        if (Set.of("UPRC","RATE","EXT","EARLY").contains(type)) recalculate(mapper.row(id),YearMonth.from("EARLY".equals(type)?end:"EXT".equals(type)?r.endDate():effective));
        return getCommonAssignment(id);
    }
    @Transactional
    public History postCommonAssignmentHistoryCorrection(String id,String historyId,JsonNode b) {
        ClientRules.object(b,Set.of("modifiedAt","correctedValue","reason"));
        String value=ProjectRules.text(b,"correctedValue",200,true),reason=ProjectRules.text(b,"reason",200,true);
        if (blank(reason)) throw CodeFailure.invalid("reason","정정 사유를 입력해 주세요.");
        if (historyId==null || !historyId.matches("[0-9]{1,5}")) throw CodeFailure.invalid("historyId","이력 식별자를 확인해 주세요.");
        Row original=required(id);
        HistoryRow observed=mapper.history(id).stream().filter(h -> h.sequence()==Integer.parseInt(historyId)).findFirst().orElse(null);
        lockSources(); lockProject(original.projectId()); Row row=locked(id);
        HistoryRow h=mapper.lockHistory(id,Integer.parseInt(historyId));
        if (h==null) throw CodeFailure.invalid("historyId","변경 이력을 찾을 수 없습니다.");
        Instant version=token(b);
        if (!Objects.equals(h.registeredAt(),version)) throw CodeFailure.conflict();
        if (observed!=null && "N".equals(observed.correctedYn()) && "Y".equals(h.correctedYn())) throw CodeFailure.conflict();
        if (Set.of("INPUT","CRCT").contains(h.type()) || "Y".equals(h.correctedYn())) throw CodeFailure.invalid("historyId","투입·정정·이미 정정된 이력은 정정할 수 없습니다.");
        if (Objects.equals(h.afterValue(),value)) throw CodeFailure.invalid("correctedValue","현재와 다른 내용을 입력해 주세요.");
        if (mapper.correct(new Correction(id,h.sequence(),version))!=1) throw CodeFailure.conflict();
        history(row,new Event("CRCT",h.effectiveDate(),h.afterValue(),value,null,reason,null,null,h.sequence()));
        return historyView(mapper.history(id).stream().filter(x -> "CRCT".equals(x.type()) && Objects.equals(x.target(),h.sequence())).max(Comparator.comparingInt(HistoryRow::sequence)).orElseThrow());
    }
    private List<Row> bulkRows(JsonNode body) {
        JsonNode selected=body.get("assignments");
        if (selected==null || !selected.isArray() || selected.isEmpty()) throw CodeFailure.invalid("assignments","배정을 선택해 주세요.");
        var versions=new TreeMap<String,Instant>();
        for (var item:selected) {
            ClientRules.object(item,Set.of("assignmentId","modifiedAt"));
            String id=ProjectRules.text(item,"assignmentId",20,false);
            if (blank(id)) throw CodeFailure.invalid("assignments","선택한 배정 식별자가 필요합니다.");
            identifier(id,"assignments"); Instant token=token(item);
            if (versions.containsKey(id) && !Objects.equals(versions.get(id),token)) throw CodeFailure.conflict();
            versions.put(id,token);
        }
        lockSources();
        versions.keySet().stream().map(id -> {Row r=mapper.row(id);if(r==null)throw CodeFailure.invalid("assignments","선택한 배정을 찾을 수 없습니다.");return r;}).map(Row::projectId).distinct().sorted().forEach(this::lockProject);
        var result=new ArrayList<Row>();
        for(var entry:versions.entrySet()) { Row row=locked(entry.getKey()); checkVersion(row,entry.getValue()); result.add(row); }
        return result;
    }
    @Transactional
    public Items<Assignment> putCommonAssignmentExtensionPossibility(JsonNode b) {
        ClientRules.object(b,Set.of("assignments","extensionPossibilityCode"));
        String value=extensionInput(b,"extensionPossibilityCode");
        var rows=bulkRows(b); rows.forEach(this::active);
        for(Row r:rows) if (!Objects.equals(value,r.extensionPossibilityCode())) {
            update(r,replace(r,new Values(r.endDate(),r.monthlyMm(),r.billingPrice(),r.inputTypeCode(),value,r.extensionCount(),r.earlyYn(),r.statusCode())));
            history(r,new Event("EXTP",calendar.today(),code("EXT_PSBL",r.extensionPossibilityCode(),"미정"),code("EXT_PSBL",value,"미정"),null,null,null,null,null));
        }
        return new Items<>(rows.stream().map(r -> getCommonAssignment(r.assignmentId())).toList());
    }
    @Transactional
    public Items<Assignment> postCommonAssignmentCancel(JsonNode b) {
        ClientRules.object(b,Set.of("assignments")); var rows=bulkRows(b);
        for(Row r:rows) if (!"CNCL".equals(r.statusCode())) {
            Row cancelled=replace(r,new Values(r.endDate(),r.monthlyMm(),r.billingPrice(),r.inputTypeCode(),r.extensionPossibilityCode(),r.extensionCount(),r.earlyYn(),"CNCL"));
            update(r,cancelled); history(r,new Event("CNCL",calendar.today(),"투입","배정 취소",null,null,null,null,null)); recalculate(cancelled);
        }
        return new Items<>(rows.stream().map(r -> getCommonAssignment(r.assignmentId())).toList());
    }
    private void refreshCache(Row r) {
        YearMonth month=calendar.currentMonth().isBefore(YearMonth.from(r.startDate()))?YearMonth.from(r.startDate()):calendar.currentMonth();
        var history=mapper.history(r.assignmentId());
        BigDecimal price=rules.effective(history,"UPRC",month,r.billingPrice()),mm=rules.effective(history,"RATE",month,r.monthlyMm());
        if (price.compareTo(Objects.requireNonNullElse(r.billingPrice(),BigDecimal.ZERO))!=0 || mm.compareTo(r.monthlyMm())!=0)
            update(r,replace(r,new Values(r.endDate(),mm,price,r.inputTypeCode(),r.extensionPossibilityCode(),r.extensionCount(),r.earlyYn(),r.statusCode())));
    }
    private void recalculate(Row r) {
        recalculate(r,YearMonth.from(r.startDate()));
    }
    private void recalculate(Row r,YearMonth from) {
        rules.sourceSemantics();
        var range=new OpenRange(r.assignmentId(),db(from));
        mapper.deleteOpenMonths(range); mapper.deleteOpenCosts(range);
        if (!"ACT".equals(r.statusCode())) return;
        var closed=new HashSet<>(mapper.closedMonths()); var history=mapper.history(r.assignmentId());
        var project=projects.selectProject(r.projectId(),calendar.today(),db(calendar.currentMonth()));
        for(YearMonth m=from.isBefore(YearMonth.from(r.startDate()))?YearMonth.from(r.startDate()):from;!m.isAfter(YearMonth.from(r.endDate()));m=m.plusMonths(1)) {
            String month=db(m); if(closed.contains(month)) continue;
            BigDecimal contractual=rules.effective(history,"RATE",m,r.monthlyMm()),price=rules.effective(history,"UPRC",m,Objects.requireNonNullElse(r.billingPrice(),BigDecimal.ZERO));
            BigDecimal mm=rules.mm(contractual,r.startDate(),r.endDate(),m);
            BigDecimal sales=rules.sales(mm,price,AssignmentPricePolicy.billable(r.contractTypeCode(),r.inputTypeCode()));
            var monthRow=new MonthWrite(r.assignmentId(),month,r.developerId(),r.projectId(),rules.days(r.startDate(),r.endDate(),m),mm,price,sales,userId());
            mapper.saveMonth(monthRow);
            Person person=mapper.person(r.developerId(),month,r.startDate(),r.endDate());
            var direct=projectAmounts.personnelDirectCost(person.employeeTypeCode(),Objects.requireNonNullElse(project.teamExpenseAmount,BigDecimal.ZERO),Objects.requireNonNullElse(project.projectExpenseAmount,BigDecimal.ZERO),mm);
            BigDecimal labor=Objects.requireNonNullElse(mapper.labor(r.developerId(),month),BigDecimal.ZERO).multiply(mm).setScale(0,java.math.RoundingMode.DOWN);
            mapper.saveCost(new CostWrite(monthRow,labor,direct.teamAmount(),direct.projectAmount(),"PRE".equals(r.projectStatusCode())?"PRE":"ALLOC",r.contractTypeCode(),person.partId()));
        }
    }
    @Transactional
    public void refreshCurrentMonth() {
        lockSources();
        var rows=mapper.rows(); rows.stream().map(Row::projectId).distinct().sorted().forEach(this::lockProject);
        rows.stream().filter(r -> "ACT".equals(r.statusCode())).sorted(Comparator.comparing(Row::assignmentId)).forEach(r -> refreshCache(locked(r.assignmentId())));
    }
    @Transactional
    public void recordDownload(int count,int year) { mapper.insertAccess(userId(),"투입 목록 "+year+"년 "+count+"건"); }
}
