package com.company.common.developers.service;

import com.company.common.developers.dto.DeveloperContracts.*;
import com.company.common.developers.mapper.DeveloperMapper;
import com.company.common.developers.mapper.DeveloperRows;
import com.company.common.developers.mapper.DeveloperRows.*;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.CurrentBusinessActor;
import com.company.common.users.mapper.UserMapper;
import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommonDeveloperService {
    private final DeveloperMapper mapper;
    private final UserMapper management;
    private final CurrentBusinessActor actor;
    private final BusinessCalendar calendar;
    private final DeveloperNumberReservations reservations;
    private final DeveloperManMonthRules calculations;
    public CommonDeveloperService(DeveloperMapper mapper, UserMapper management, CurrentBusinessActor actor,
        BusinessCalendar calendar, DeveloperNumberReservations reservations, DeveloperManMonthRules calculations) {
        this.mapper=mapper; this.management=management; this.actor=actor; this.calendar=calendar; this.reservations=reservations;
        this.calculations=calculations;
    }
    private String month() { return calendar.toDatabaseMonth(calendar.currentMonth()); }
    private void lock() { management.lockManagement(); mapper.lockSources(); }
    private Developer require(String id) {
        if (id == null || id.isBlank() || id.length() > 20) throw DeveloperRules.invalid("developerId");
        Developer row = mapper.lockDeveloper(id, month());
        if (row == null) throw CodeFailure.invalid("developerId", "개발자를 찾을 수 없습니다.");
        return row;
    }
    public void refreshAll() {
        lock();
        for (Developer row : mapper.selectDevelopers(new Filter(null,null,null,null,null,true),month())) refresh(row, false);
    }
    private void refresh(Developer row, boolean resume) {
        List<State> statuses = mapper.selectStatuses(row.developerId);
        LocalDate retire = DeveloperStatusRules.retirement(statuses), today=calendar.today();
        String code = "IDLE";
        if (DeveloperBusinessPolicy.retired(retire,today)) code="RETIRE";
        else if (statuses.stream().anyMatch(s -> DeveloperStatusRules.activeLeave(s,today))) code="LEAVE";
        else {
            var active = mapper.selectAssignments(row.developerId).stream()
                .filter(a -> !a.startDate.isAfter(today) && !a.endDate.isBefore(today)).toList();
            if (!active.isEmpty()) {
                LocalDate last=active.stream().map(a -> a.endDate).max(LocalDate::compareTo).orElseThrow();
                code=calculations.withdrawalSoon(last,today) ? "WTHDR" : "INPUT";
            }
        }
        // 상태가 바뀌어 저장했으면 수정 시각도 새로 읽는다(동시 수정 막기에 쓰는 modifiedAt).
        if (mapper.updateDerived(row.developerId,retire,code,resume,today)>0) {
            var fresh=mapper.selectDeveloper(row.developerId,month());
            if (fresh!=null) row.modifiedAt=fresh.modifiedAt;
        }
        row.retirementDate=retire; row.retirementScheduled=retire!=null && !retire.isBefore(today);
        row.workStatusCode=code; row.workStatusName=statusName(code);
        if ("RETIRE".equals(code)) row.useYn="N"; else if (resume) row.useYn="Y";
    }
    private static String statusName(String code) {
        return switch(code) { case "INPUT" -> "투입중"; case "WTHDR" -> "철수예정";
            case "LEAVE" -> "휴직"; case "RETIRE" -> "퇴사"; default -> "대기"; };
    }
    private Developer detail(Developer row) {
        List<DeveloperRows.Code> codes=mapper.selectCodes("SKILL");
        row.skillNames=mapper.selectSkills(row.developerId);
        row.skillCodes=row.skillNames.stream().map(name -> {
            var matches=codes.stream().filter(c -> c.name.equals(name)).toList();
            return matches.size()==1 ? matches.get(0).code : name;
        }).toList();
        return row;
    }
    public Developer getCommonDeveloper(String id) { lock(); Developer row=require(id); refresh(row,false); return detail(row); }
    public Items<ListRow> getCommonDevelopers(Filter requested) {
        Filter f=DeveloperRules.filter(requested); lock(); var items=new ArrayList<ListRow>();
        // 「담당 파트」 역할(전체 없음)은 자기 파트 인력만(명세 ⑧-3, 시안 devInScope). PM은 거르지 않는다.
        boolean partOnly=com.company.common.support.auth.RequestAccess.developerPartOnly();
        var parts=com.company.common.support.auth.RequestAccess.scopeParts();
        for (Developer row : mapper.selectDevelopers(f,month())) {
            if (partOnly && (row.salesPartId==null || !parts.contains(row.salesPartId))) continue;
            refresh(row,false);
            if (f.workStatusName()!=null && !f.workStatusName().equals(row.workStatusName)) continue;
            if (!f.includeStopped() && !"퇴사".equals(f.workStatusName()) && (!"Y".equals(row.useYn) || "퇴사".equals(row.workStatusName))) continue;
            items.add(listRow(row));
        }
        return new Items<>(items);
    }
    private ListRow listRow(Developer d) {
        var r=new ListRow();
        r.developerId=d.developerId; r.managementNumber=d.managementNumber; r.name=d.name; r.employeeTypeCode=d.employeeTypeCode;
        r.organizationId=d.organizationId; r.gradeCode=d.gradeCode; r.primaryJobCode=d.primaryJobCode;
        r.secondaryJobCode=d.secondaryJobCode; r.positionCode=d.positionCode; r.directClassCode=d.directClassCode;
        r.salesPartId=d.salesPartId; r.hireDate=d.hireDate; r.retirementDate=d.retirementDate;
        r.organizationName=d.organizationName; r.employeeTypeName=d.employeeTypeName; r.gradeName=d.gradeName;
        r.primaryJobName=d.primaryJobName; r.workStatusName=d.workStatusName; r.useYn=d.useYn;
        r.monthlyRate=d.monthlyRate; r.retirementScheduled=d.retirementScheduled;
        var assignments=mapper.selectAssignments(d.developerId);
        var active=assignments.stream().filter(a -> !a.startDate.isAfter(calendar.today()) && !a.endDate.isBefore(calendar.today()))
            .sorted(Comparator.comparing((Assignment a) -> a.startDate).thenComparing(a -> a.projectId)).toList();
        if (!active.isEmpty()) {
            r.currentProjectName=active.get(0).projectName;
            r.currentProjectId=active.get(0).projectId;
            r.additionalProjectCount=Math.toIntExact(active.stream().map(a -> a.projectId).distinct().count()-1);
            r.withdrawalDate=active.stream().map(a -> a.endDate).min(LocalDate::compareTo).orElse(null);
        }
        r.currentMonthMm=currentMm(assignments); r.overOneMm=calculations.over(r.currentMonthMm);
        return r;
    }
    private BigDecimal currentMm(List<Assignment> assignments) {
        LocalDate first=calendar.currentMonth().atDay(1),last=calendar.currentMonth().atEndOfMonth();
        boolean history=mapper.hasAssignmentHistory(); BigDecimal total=BigDecimal.ZERO;
        for (Assignment a : assignments) {
            LocalDate start=a.startDate.isAfter(first)?a.startDate:first, end=a.endDate.isBefore(last)?a.endDate:last;
            if (end.isBefore(start)) continue;
            BigDecimal weighted=BigDecimal.ZERO;
            if (history) {
                // 날마다 따로 읽지 않고 그 기간의 날별 MM을 한 번에 읽는다(같은 규칙, 결과 같음).
                for (BigDecimal mm : mapper.selectAssignmentDailyMms(a.assignmentId,start,end)) weighted=weighted.add(Objects.requireNonNull(mm));
            } else {
                for (LocalDate day=start; !day.isAfter(end); day=day.plusDays(1)) weighted=weighted.add(a.mm);
            }
            total=total.add(calculations.prorate(weighted,first.lengthOfMonth()));
        }
        return total;
    }
    public Items<Assignment> getCommonDeveloperAssignments(String id) {
        lock(); Developer row=require(id); refresh(row,false); var assignments=mapper.selectAssignments(id);
        if (mapper.hasAssignmentHistory()) for (Assignment a:assignments) a.mm=mapper.selectAssignmentMm(a.assignmentId,calendar.today());
        return new Items<>(assignments);
    }
    /** 월 단가·월 급여 변경 줄은 민감 체크가 없으면 뺀다(변경 이력 글에서도 빼기, 명세 ⑨-2). */
    static final Set<String> SENSITIVE_HISTORY = Set.of("월 단가", "월 급여·지급액");
    public Items<DeveloperRows.Change> getCommonDeveloperChangeHistory(String id) {
        lock(); require(id); var rows=mapper.selectChanges(id);
        if (!actor.sensitive()) rows=rows.stream().filter(row -> !SENSITIVE_HISTORY.contains(row.fieldName)).toList();
        return new Items<>(rows);
    }
    public Items<Area> getCommonDeveloperAreas(String parent) {
        if (parent!=null && (!parent.matches("[0-9]{1,10}") || mapper.selectAddress(parent)==null)) throw DeveloperRules.invalid("parentAreaCode");
        return new Items<>(mapper.selectAreas(parent));
    }
    private void code(String group,String value,String field) {
        if (value!=null && mapper.selectCodes(group).stream().noneMatch(c -> c.code.equals(value))) throw DeveloperRules.invalid(field);
    }
    private Input prepare(Input in, Developer previous, String number) {
        code("EMP_TYPE",in.employeeTypeCode(),"employeeTypeCode"); code("JOB",in.primaryJobCode(),"primaryJobCode");
        code("JOB",in.secondaryJobCode(),"secondaryJobCode"); code("GRADE",in.gradeCode(),"gradeCode");
        String position=in.positionCode();
        if (position==null) position=mapper.selectCodes("JBPS").stream().filter(c -> "팀원".equals(c.name)).map(c -> c.code).findFirst()
            .orElseThrow(() -> DeveloperRules.invalid("positionCode"));
        code("JBPS",position,"positionCode"); code("DRCT_CLS",in.directClassCode(),"directClassCode");
        if (!in.organizationId().isEmpty() && (previous==null || !in.organizationId().equals(previous.organizationId))
            && !mapper.selectableOrganization(in.organizationId())) throw DeveloperRules.invalid("organizationId");
        if ((previous==null || !in.salesPartId().equals(previous.salesPartId)) && !mapper.selectablePart(in.salesPartId())) throw DeveloperRules.invalid("salesPartId");
        Set<String> names=new HashSet<>();
        for (String skill : in.skillCodes()) {
            code("SKILL",skill,"skillCodes");
            String name=mapper.selectCodes("SKILL").stream().filter(c -> c.code.equals(skill)).findFirst().orElseThrow().name;
            if (name.length()>50 || !names.add(name)) throw CodeFailure.invalid("skillCodes","스킬 이름이 중복됩니다. 공통코드를 확인해 주세요.");
        }
        Address address=in.homeAreaCode()==null?null:mapper.selectAddress(in.homeAreaCode());
        // SFR-021-02: 없어진 구역(USE_YN='N')은 목록에 안 나오지만 이미 저장된 거주지는 그대로 둔다
        boolean unchanged=previous!=null && in.homeAreaCode()!=null && in.homeAreaCode().equals(previous.homeAreaCode);
        if (in.homeAreaCode()!=null && address==null && !unchanged) throw DeveloperRules.invalid("homeAreaCode");
        String homeAddress=address!=null?address.address:unchanged?previous.homeAddress:null;
        // 민감 체크가 없으면 월 단가·월 급여 칸을 받지 않고 저장된 값을 그대로 둔다(화면에 칸이 없음, 명세 ⑨-2).
        boolean sensitive=actor.sensitive();
        BigDecimal rate=sensitive?in.monthlyRate():previous==null?null:previous.monthlyRate;
        BigDecimal pay=sensitive?in.monthlyPay():previous==null?null:previous.monthlyPay;
        return new Input(number,in.name(),in.employeeTypeCode(),in.organizationId(),in.gradeCode(),in.primaryJobCode(),
            in.secondaryJobCode(),position,in.directClassCode(),in.salesPartId(),in.hireDate(),rate,pay,
            in.skillCodes(),in.homeAreaCode(),homeAddress);
    }
    public Developer postCommonDeveloper(Input request) {
        lock(); var used=new HashSet<>(mapper.selectManagementNumbers()); used.addAll(reservations.numbers());
        String number=request.managementNumber();
        if (number==null) { int n=1; do { number=String.format(Locale.ROOT,"K-%04d",n++); } while(used.contains(number)); }
        else if (used.contains(number)) throw CodeFailure.invalid("managementNumber","이미 등록된 관리번호입니다.");
        Input input=prepare(request,null,number);
        String id="D"+UUID.randomUUID().toString().replace("-","").substring(0,19);
        mapper.insertDeveloper(new Write(id,input,actor.userId()));
        saveSkills(id,input); savePrice(id,input);
        mapper.insertHistory(new Audit(id,"INS","개발자 등록",null,input.name(),null,actor.userId()));
        Developer created=require(id); refresh(created,false); return detail(created);
    }
    private void saveSkills(String id,Input input) {
        mapper.deleteSkills(id); int order=1;
        for (String skill : input.skillCodes()) {
            String name=mapper.selectCodes("SKILL").stream().filter(c -> c.code.equals(skill)).findFirst().orElseThrow().name;
            mapper.insertSkill(id,name,order++);
        }
    }
    private void savePrice(String id,Input input) {
        var price=new PriceWrite(id,month(),calendar.toDatabaseMonth(calendar.currentMonth().minusMonths(1)),input.monthlyRate(),input.monthlyPay(),actor.userId());
        mapper.closePreviousPrice(price); mapper.savePrice(price);
    }
    public Developer putCommonDeveloper(String id,Input request,java.time.Instant modifiedAt) {
        lock(); Developer old=detail(require(id));
        if (!Objects.equals(old.modifiedAt,modifiedAt)) throw CodeFailure.conflict();
        Input input=prepare(request,old,old.managementNumber);
        var before=named(fields(old)); var after=named(fields(input));
        boolean priceChanged = !Objects.equals(old.monthlyPay,input.monthlyPay())
            || !Objects.equals(old.monthlyRate,input.monthlyRate());
        mapper.updateDeveloper(new Write(id,input,actor.userId())); saveSkills(id,input);
        if (priceChanged) savePrice(id,input);
        for (String key: before.keySet()) if (!Objects.equals(before.get(key),after.get(key)))
            mapper.insertHistory(new Audit(id,"UPD",key,before.get(key),after.get(key),null,actor.userId()));
        Developer saved=require(id); refresh(saved,false); return detail(saved);
    }
    private Map<String,String> fields(Developer d) {
        return fields(new Input(d.managementNumber,d.name,d.employeeTypeCode,d.organizationId,d.gradeCode,d.primaryJobCode,d.secondaryJobCode,
            d.positionCode,d.directClassCode,d.salesPartId,d.hireDate,d.monthlyRate,d.monthlyPay,d.skillCodes,d.homeAreaCode,d.homeAddress));
    }
    private Map<String,String> fields(Input d) {
        var map=new LinkedHashMap<String,String>();
        map.put("이름",d.name()); map.put("직원구분",d.employeeTypeCode()); map.put("소속",d.organizationId()); map.put("등급",d.gradeCode());
        map.put("주 직무",d.primaryJobCode()); map.put("보조 직무",d.secondaryJobCode()); map.put("직위",d.positionCode());
        map.put("직접/간접",d.directClassCode()); map.put("영업 파트",d.salesPartId()); map.put("입사일",text(d.hireDate()));
        map.put("월 단가",text(d.monthlyRate())); map.put("월 급여·지급액",text(d.monthlyPay())); map.put("보유 스킬",String.join(",",d.skillCodes()));
        map.put("거주지",d.homeAddress()); return map;
    }
    /** 변경 이력은 코드 대신 화면에 보이는 이름으로 남긴다(예: FT → 정규직, HQ → 본사(직접 영업)). */
    private Map<String,String> named(Map<String,String> map) {
        var result=new LinkedHashMap<String,String>(map);
        name(result,"직원구분","EMP_TYPE"); name(result,"등급","GRADE"); name(result,"주 직무","JOB"); name(result,"보조 직무","JOB");
        name(result,"직위","JBPS"); name(result,"직접/간접","DRCT_CLS");
        String org=result.get("소속"); result.put("소속",org==null||org.isEmpty()?"자사":mapper.selectClientName(org));
        String part=result.get("영업 파트"); if (part!=null) result.put("영업 파트",mapper.selectPartName(part));
        String skills=result.get("보유 스킬");
        if (skills!=null && !skills.isEmpty()) {
            var codes=mapper.selectCodes("SKILL");
            result.put("보유 스킬",String.join(", ",java.util.Arrays.stream(skills.split(",")).map(s -> codes.stream()
                .filter(c -> c.code.equals(s)).map(c -> c.name).findFirst().orElse(s)).toList()));
        }
        return result;
    }
    private void name(Map<String,String> map,String key,String group) {
        String code=map.get(key); if (code==null) return;
        map.put(key,mapper.selectCodes(group).stream().filter(c -> c.code.equals(code)).map(c -> c.name).findFirst().orElse(code));
    }
    private static String text(Object value) { return value==null?null:value.toString(); }
    public StatusList getCommonDeveloperStatuses(String id) {
        lock(); Developer d=require(id); refresh(d,false); return statuses(d);
    }
    private StatusList statuses(Developer d) {
        var rows=new ArrayList<Status>(); var states=mapper.selectStatuses(d.developerId);
        LocalDate today=calendar.today(), retire=DeveloperStatusRules.retirement(states);
        // 시안: 시작일이 늦은 줄부터, 맨 아래 입사일의 재직 줄
        for (State s: states.stream().sorted(Comparator.comparing((State x) -> x.startDate).thenComparing(x -> x.sequence).reversed()).toList()) {
            boolean closedByReturn="LEAVE".equals(s.code) && states.stream().anyMatch(w -> "WORK".equals(w.code) && Integer.valueOf(s.sequence).equals(w.closedLeaveSequence));
            boolean monthClosed=!"LEAVE".equals(s.code) && closed(s.startDate);
            LocalDate restore=!"WORK".equals(s.code)?null
                :retire!=null && (s.originalEndDate==null || !s.originalEndDate.isBefore(retire))?retire.minusDays(1):s.originalEndDate;
            rows.add(new Status(Integer.toString(s.sequence),
                "WORK".equals(s.code)?"재직":statusName(s.code),s.startDate,s.endDate,s.reason,s.registeredBy,
                DeveloperStatusRules.activeLeave(s,today),"RETIRE".equals(s.code) && !s.startDate.isBefore(today),
                closedByReturn,monthClosed,restore));
        }
        if (d.hireDate!=null) rows.add(new Status(null,"재직",d.hireDate,null,"입사",null,false,false,false,false,null));
        var roles=actor.roles();
        return new StatusList(rows,DeveloperBusinessPolicy.canManageStatuses(roles),roles.contains("ADMIN"));
    }
    private boolean closed(LocalDate date) { return mapper.closedMonth(calendar.toDatabaseMonth(YearMonth.from(date))); }
    public StatusList postCommonDeveloperStatus(String id,StatusInput input) {
        lock(); Developer d=require(id); var original=mapper.selectStatuses(id);
        LocalDate retire=DeveloperStatusRules.retirement(original);
        if (DeveloperBusinessPolicy.retired(retire,calendar.today())) throw CodeFailure.invalid("date","퇴사한 개발자는 상태를 등록할 수 없습니다.");
        if ("퇴사".equals(input.statusName()) && retire==null) {
            if (closed(input.date())) {
                // 시안: 「마감한 달(마지막 마감 달 이전)로는…」
                YearMonth last=YearMonth.from(input.date());
                while (last.isBefore(calendar.currentMonth()) && closed(last.plusMonths(1).atDay(1))) last=last.plusMonths(1);
                throw CodeFailure.invalid("date","마감한 달("+last+" 이전)로는 퇴사일을 넣을 수 없습니다.");
            }
            if (d.hireDate!=null && input.date().isBefore(d.hireDate)) throw CodeFailure.invalid("date","입사일("+d.hireDate+")보다 앞선 날짜입니다.");
            var left=mapper.selectAssignments(id).stream().filter(a -> a.endDate.isAfter(input.date())).toList();
            if (!left.isEmpty()) throw CodeFailure.invalid("date","퇴사일 이후 투입 배정이 "+left.size()+"건 있습니다("
                +String.join(", ",left.stream().map(a -> a.projectName+" ~"+a.endDate).toList())+"). 「프로젝트·투입 › 투입 배정」에서 먼저 조기 철수해 주세요.");
        }
        var next=DeveloperStatusRules.copy(original); State added=DeveloperStatusRules.append(next,input,id,actor.userId(),calendar.today());
        persistEnds(original,next); mapper.insertStatus(added);
        // 시안 변경 이력: 재직 = 근무 상태 휴직 → 재직(복귀일 복귀), 퇴사 = 퇴사일 – → 날짜
        if ("WORK".equals(added.code)) mapper.insertHistory(new Audit(id,"INS","근무 상태","휴직","재직("+added.startDate+" 복귀)",added.reason,actor.userId()));
        else if ("RETIRE".equals(added.code)) mapper.insertHistory(new Audit(id,"INS","퇴사일",null,text(added.startDate),added.reason,actor.userId()));
        else mapper.insertHistory(new Audit(id,"INS","근무 상태",null,input.statusName(),input.reason(),actor.userId()));
        refresh(d,false); return statuses(d);
    }
    private void persistEnds(List<State> before,List<State> next) {
        for(State s:next) if (before.stream().anyMatch(old -> old.sequence==s.sequence &&
            (!Objects.equals(old.endDate,s.endDate) || !Objects.equals(old.closedLeaveSequence,s.closedLeaveSequence)
                || !Objects.equals(old.originalEndDate,s.originalEndDate)))) mapper.updateStatus(s);
    }
    public StatusList putCommonDeveloperStatusEndDate(String id,String statusId,LocalDate endDate) {
        lock(); Developer d=require(id); var before=mapper.selectStatuses(id); var next=DeveloperStatusRules.copy(before);
        State leave=DeveloperStatusRules.target(next,statusId);
        if (!"LEAVE".equals(leave.code)) throw DeveloperRules.invalid("statusId");
        if (next.stream().anyMatch(s -> "WORK".equals(s.code) && Integer.valueOf(leave.sequence).equals(s.closedLeaveSequence)))
            throw CodeFailure.invalid("endDate","재직(복귀)으로 닫힌 휴직입니다. 바꾸려면 재직을 먼저 취소하세요.");
        // 시안: 진행 중이거나 아직 시작 안 한 휴직만 고친다
        if (leave.endDate!=null && leave.endDate.isBefore(calendar.today())) throw CodeFailure.invalid("endDate","끝난 휴직은 수정할 수 없습니다.");
        if (endDate==null || endDate.isBefore(leave.startDate)) throw CodeFailure.invalid("endDate","시작일 이후 날짜를 넣어 주세요.");
        LocalDate retire=DeveloperStatusRules.retirement(next);
        if (retire!=null && !endDate.isBefore(retire)) throw CodeFailure.invalid("endDate","퇴사일("+retire+") 전날까지만 넣을 수 있습니다.");
        leave.endDate=endDate; DeveloperStatusRules.validate(next); persistEnds(before,next);
        mapper.insertHistory(new Audit(id,"UPD","휴직 종료일",text(DeveloperStatusRules.target(before,statusId).endDate),text(endDate),null,actor.userId()));
        refresh(d,false); return statuses(d);
    }
    public StatusList postCommonDeveloperReturnCancel(String id,String statusId) { return cancel(id,statusId,null,false); }
    public StatusList postCommonDeveloperRetirementCancel(String id,String statusId,String reason) {
        if(reason==null || reason.isBlank()) throw DeveloperRules.invalid("reason");
        return cancel(id,statusId,reason,true);
    }
    private StatusList cancel(String id,String statusId,String reason,boolean retirement) {
        lock(); Developer d=require(id); var before=mapper.selectStatuses(id); var next=DeveloperStatusRules.copy(before);
        State target=DeveloperStatusRules.target(next,statusId);
        if (!(retirement?"RETIRE":"WORK").equals(target.code)) throw DeveloperRules.invalid("statusId");
        if (retirement && target.startDate.isBefore(calendar.today()) && !actor.roles().contains("ADMIN"))
            throw CodeFailure.invalid("statusId","퇴사일이 지난 퇴사는 시스템 관리자만 취소할 수 있습니다.");
        if (closed(target.startDate)) throw CodeFailure.invalid("statusId",retirement?
            "퇴사한 달("+YearMonth.from(target.startDate)+")이 마감되어 취소할 수 없습니다. 다시 쓰려면 새로 등록하세요.":"복귀한 달이 마감되어 취소할 수 없습니다.");
        DeveloperStatusRules.cancel(next,target);
        persistEnds(before,next); mapper.deleteStatus(id,target.sequence);
        mapper.insertHistory(new Audit(id,"UPD",retirement?"퇴사 취소":"재직 취소",text(target.startDate),null,reason,actor.userId()));
        refresh(d,retirement); return statuses(d);
    }
    public UseResult putCommonDeveloperUseStatus(UseInput input) {
        lock(); var changed=new ArrayList<String>(); var rejected=new ArrayList<UseIssue>();
        for (String id : input.developerIds().stream().distinct().sorted().toList()) {
            Developer d=require(id); refresh(d,false); String reason=null;
            // 이미 그 상태면 바꾸지 않는다(같은 값의 변경 이력을 남기지 않음, 시안 「이미 사용 중지된 개발자입니다.」)
            if (input.useYn().equals(d.useYn)) continue;
            // SFR-102-08·시안 devStop 문구
            if ("N".equals(input.useYn()) && mapper.selectAssignments(id).stream().anyMatch(a -> !a.endDate.isBefore(calendar.today())))
                reason="투입 중인 배정이 있어 중지할 수 없습니다. 먼저 투입 배정의 [변경 기록]에서 조기 철수로 배정을 끝내 주세요.";
            if ("Y".equals(input.useYn()) && DeveloperBusinessPolicy.retired(d.retirementDate,calendar.today()))
                reason="퇴사한 개발자는 다시 사용할 수 없습니다. 새로 등록하세요.";
            if (reason!=null) { rejected.add(new UseIssue(id,d.name,reason)); continue; }
            mapper.updateUse(id,input.useYn(),actor.userId());
            mapper.insertHistory(new Audit(id,"UPD","사용 여부",d.useYn,input.useYn(),null,actor.userId())); changed.add(id);
        }
        return new UseResult(changed,rejected);
    }
    public List<ListRow> getCommonDevelopersForDownload(Filter filter) {
        var items=getCommonDevelopers(filter).items(); mapper.insertAccess(actor.userId(),items.size()); return items;
    }
    public String currentUserId() { return actor.userId(); }
    public java.time.Instant now() { return calendar.now(); }
}
