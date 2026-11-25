package com.company.common.partsettlements.service;

import com.company.common.common.PartSettlementDefinitions;
import com.company.common.partsettlements.dto.PartSettlementContracts.*;
import com.company.common.partsettlements.mapper.PartSettlementMapper;
import com.company.common.partsettlements.mapper.PartSettlementRows.*;
import com.company.common.support.service.*;
import com.company.common.users.mapper.UserMapper;
import com.company.common.users.dto.UserContracts.Response;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.company.common.partsettlements.service.PartSettlementRules.*;
import static java.math.BigDecimal.ZERO;

@Service
public class CommonPartSettlementService {
    private final PartSettlementMapper mapper;
    private final UserMapper management;
    private final PartSettlementDefinitions definitions;
    private final CurrentBusinessActor actor;
    public CommonPartSettlementService(PartSettlementMapper mapper, UserMapper management,
        PartSettlementDefinitions definitions, CurrentBusinessActor actor) {
        this.mapper=mapper; this.management=management; this.definitions=definitions; this.actor=actor;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Response<Tabs> getCommonPartSettlements(String yearMonth) {
        YearMonth month=month(yearMonth);
        var loaded=definitions.definitions();
        var rules=new PartSettlementRules(loaded);
        var parts=mapper.selectParts(db(month));
        var tabs=new ArrayList<Tab>();
        // 탭은 월 마감 정산 대상과 같은 파트(그 달에 하루라도 사용 중, 2026-11-24 사용자 결정).
        var history=mapper.selectHistory();
        for (Part p:parts) if (PartSettlementRules.closeTarget(p,month,history)) {
            var detail=read(p,month,false,rules,parts);
            tabs.add(new Tab(p.partId,p.partName,detail.summary()));
        }
        return new Response<>(true,Map.of("calculationRules",loaded),new Tabs(yearMonth,tabs,tabs.isEmpty()
            ? "맡은 영업 파트가 없습니다. 본사(직접 영업)는 파트 이익금 정산 대상이 아닙니다." : null));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Response<Settlement> detailResponse(String id,String yearMonth) {
        var loaded=definitions.definitions();
        var month=month(yearMonth);
        var parts=mapper.selectParts(db(month));
        return new Response<>(true,Map.of("calculationRules",loaded),read(require(parts,id),month,false,new PartSettlementRules(loaded),parts));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Settlement getCommonPartSettlement(String id,String yearMonth) {
        return detailResponse(id,yearMonth).data();
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Trends getCommonPartSettlementTrends(String id,String yearMonth) {
        var until=month(yearMonth);
        var rules=new PartSettlementRules(definitions.definitions());
        Map<String,Map<String,Workforce>> people=new LinkedHashMap<>();
        Map<String,String> names=new LinkedHashMap<>();
        var totals=new ArrayList<TrendTotal>();
        for (int number=1;number<=until.getMonthValue();number++) {
            var month=YearMonth.of(until.getYear(),number);
            var parts=mapper.selectParts(db(month));
            var p=requirePart(parts,id);
            Settlement value;
            if (p.distributionRate==null) {
                value=null;
                totals.add(new TrendTotal(month.toString(),ZERO));
            } else {
                value=read(p,month,false,rules,parts);
                totals.add(new TrendTotal(month.toString(),value.summary().partProfitAmount()));
                for (var row:value.items()) if (row.monthlyMm().signum()>0) {
                    names.put(row.developerId(),row.developerName());
                    people.computeIfAbsent(row.developerId(),x->new LinkedHashMap<>()).put(month.toString(),row);
                }
            }
        }
        var rows=new ArrayList<TrendPerson>();
        for (var person:people.entrySet()) {
            var cells=new ArrayList<TrendMonth>();
            for (var total:totals) {
                var row=person.getValue().get(total.yearMonth());
                cells.add(row==null ? new TrendMonth(total.yearMonth(),ZERO,ZERO,ZERO,ZERO,ZERO)
                    : new TrendMonth(total.yearMonth(),row.monthlyMm(),row.unitPrice(),row.totalCost(),row.profit(),row.profitShare()));
            }
            rows.add(new TrendPerson(person.getKey(),names.get(person.getKey()),cells));
        }
        return new Trends(id,yearMonth,rows,totals);
    }
    Settlement read(Part part,YearMonth month,boolean historical,PartSettlementRules rules,List<Part> parts) {
        var key=new Key(part.partId,db(month));
        Header header=mapper.selectHeader(key);
        List<Workforce> rows;
        var inputs=mapper.selectInputs(db(month));
        if (confirmed(header)) rows=mapper.selectDetails(key).stream().map(d -> new Workforce(d.developerId,d.developerName,
            d.employeeTypeCode,split(d.projectNames,", "),d.monthlyMm,d.unitPrice,d.salary,d.salesExpense,d.retirementAmount,
            d.totalCost,d.profit,d.profitShare,split(d.notes," · "),d.notes!=null && d.notes.contains(MISSING_SALARY))).toList();
        else {
            // 확정 전 화면도 확정과 같이 그 달 말일의 영업 파트로 모은다(2026-11-21, 확정 전후 숫자가 같게).
            var history=mapper.selectHistory();
            var people=inputs.stream().collect(Collectors.groupingBy(i->i.developerId,LinkedHashMap::new,Collectors.toList()));
            var calculated=new ArrayList<Workforce>();
            for (var person:people.values()) {
                var member=membership(person.get(0),month,parts,history);
                if (part.partId.equals(member.partId())) calculated.addAll(rules.calculate(part,month,person,
                    member.warning()==null ? List.of() : List.of(member.warning())));
            }
            rows=calculated.stream().sorted(Comparator.comparing(Workforce::developerName).thenComparing(Workforce::developerId)).toList();
        }
        BigDecimal card=header==null ? ZERO : header.card;
        BigDecimal profit=rows.stream().map(Workforce::profit).reduce(ZERO,BigDecimal::add);
        BigDecimal share=confirmed(header) ? header.profit : rows.stream().map(Workforce::profitShare).reduce(ZERO,BigDecimal::add).subtract(card);
        var summary=new Summary(Objects.toString(part.leaderName,""),part.distributionRate,part.salesExpense,rows.size(),profit,share);
        return new Settlement(part.partId,part.partName,month.toString(),summary,rows,card,confirmed(header),mapper.isClosed(db(month)),
            header==null ? null : header.confirmedBy,header==null ? null : header.confirmedByName,header==null ? null : header.confirmedAt,
            header!=null && "AUTO".equals(header.state) ? "마감 시 자동 확정" : null,header==null ? null : header.modifiedAt,
            turnkey(inputs,rows));
    }
    /** 이 파트 줄 가운데 그 달 턴키 프로젝트에 투입한 사람(화면 「턴키」 표시). */
    private static List<String> turnkey(List<Input> inputs,List<Workforce> rows) {
        var people=rows.stream().map(Workforce::developerId).collect(Collectors.toSet());
        return inputs.stream().filter(i->"TK".equals(i.contractType) && nz(i.mm).signum()>0 && people.contains(i.developerId))
            .map(i->i.developerId).distinct().sorted().toList();
    }
    public record Performance(String partId, String partName, BigDecimal revenue, BigDecimal profitRate) {}
    /** 同一 인력 계산/확정 스냅샷. 파트별 DB 반복 조회 없이 입력을 공유한다. */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Performance> performance(String yearMonth) {
        var month = month(yearMonth);
        var rules = new PartSettlementRules(definitions.definitions());
        var parts = mapper.selectParts(db(month));
        var history = mapper.selectHistory();
        var inputs = mapper.selectInputs(db(month));
        var headers = mapper.selectHeaders(db(month)).stream().collect(Collectors.toMap(h -> h.partId, h -> h));
        var details = mapper.selectAllDetails(db(month)).stream().collect(Collectors.groupingBy(d -> d.partId));
        var people = inputs.stream().collect(Collectors.groupingBy(i -> i.developerId, LinkedHashMap::new, Collectors.toList()));
        var memberships = new HashMap<String, String>();
        for (var entry : people.entrySet()) memberships.put(entry.getKey(), membership(entry.getValue().get(0), month, parts, history).partId());
        var result = new ArrayList<Performance>();
        for (var part : parts) {
            if (!closeTarget(part, month, history)) continue;
            BigDecimal revenue = ZERO, profit = ZERO;
            if (confirmed(headers.get(part.partId))) {
                for (var detail : details.getOrDefault(part.partId, Collections.emptyList())) {
                    revenue = revenue.add(detail.unitPrice);
                    profit = profit.add(detail.profit);
                }
            } else {
                for (var entry : people.entrySet()) if (part.partId.equals(memberships.get(entry.getKey()))) {
                    for (var row : rules.calculate(part, month, entry.getValue(), List.of())) {
                        revenue = revenue.add(row.unitPrice());
                        profit = profit.add(row.profit());
                    }
                }
            }
            result.add(new Performance(part.partId, part.partName, revenue, revenue.signum() == 0 ? null
                : profit.multiply(BigDecimal.valueOf(100)).divide(revenue, 1, java.math.RoundingMode.HALF_UP)));
        }
        return result;
    }

    private static List<String> split(String value,String delimiter) {
        return value==null || value.isEmpty() ? List.of() : Arrays.asList(value.split(java.util.regex.Pattern.quote(delimiter),-1));
    }
    static Part requirePart(List<Part> parts,String id) {
        if (id==null || id.isBlank() || id.length()>20) throw CodeFailure.invalid("partId","영업 파트를 확인해 주세요.");
        Part part=parts.stream().filter(p->p.partId.equals(id)).findFirst().orElseThrow(()->new CodeFailure(404,"partId","영업 파트를 찾을 수 없습니다."));
        if ("Y".equals(part.hqYn)) throw CodeFailure.invalid("partId","본사는 파트 이익금 정산 대상이 아닙니다.");
        return part;
    }
    static Part require(List<Part> parts,String id) {
        var part=requirePart(parts,id);
        if (part.distributionRate==null) throw CodeFailure.invalid("partId","해당월 파트 기준이 없습니다.");
        return part;
    }
    public void lock() { management.lockManagement(); mapper.lockSources(); }
    Header current(Key key,Instant version) {
        Header row=mapper.selectHeader(key);
        if (!Objects.equals(row==null ? null : row.modifiedAt,version)) throw CodeFailure.conflict();
        return row;
    }
    private void open(Key key) {
        if (mapper.isClosed(key.month())) throw CodeFailure.invalid("yearMonth","마감된 달은 변경할 수 없습니다. 정정 금액은 다음 달에 반영해 주세요.");
    }
    static void affected(int value) { if (value!=1) throw CodeFailure.conflict(); }
    @Transactional
    public Settlement putCommonPartSettlementCard(String id,String yearMonth,CardInput input) {
        var month=month(yearMonth);
        if (input.corpCardAmount()==null || input.corpCardAmount().scale()>0 && input.corpCardAmount().stripTrailingZeros().scale()>0
            || input.corpCardAmount().signum()<0 || input.corpCardAmount().compareTo(new BigDecimal("999999999999999"))>0)
            throw CodeFailure.invalid("corpCardAmount","법인카드 금액은 0~999999999999999 사이의 정수로 입력해 주세요.");
        lock();
        require(mapper.selectParts(db(month)),id);
        var key=new Key(id,db(month));
        var old=current(key,input.modifiedAt());
        open(key);
        if (confirmed(old)) throw CodeFailure.invalid("corpCardAmount","확정된 정산의 법인카드는 변경할 수 없습니다.");
        var mutation=new Mutation(key,input.modifiedAt(),input.corpCardAmount(),null,"WORK",actor.userId());
        if (old==null) affected(mapper.insertHeader(mutation));
        affected(mapper.updateCard(mutation));
        affected(mapper.insertHistory(new Audit(id+":"+db(month),"법인카드",old==null ? "0" : old.card.toPlainString(),input.corpCardAmount().toPlainString(),null,actor.userId())));
        return getCommonPartSettlement(id,yearMonth);
    }
    @Transactional
    public Settlement putCommonPartSettlementConfirm(String id,String yearMonth,Instant version) {
        var month=month(yearMonth); lock();
        var parts=mapper.selectParts(db(month)); var part=require(parts,id); var key=new Key(id,db(month));
        var old=current(key,version);
        if (confirmed(old)) return getCommonPartSettlement(id,yearMonth);
        open(key);
        var value=read(part,month,true,new PartSettlementRules(definitions.definitions()),parts);
        snapshotCapacity(value.items());
        persistConfirmation(value,version,"CNFM");
        return getCommonPartSettlement(id,yearMonth);
    }
    void persistConfirmation(Settlement value,Instant version,String state) {
        var key=new Key(value.partId(),db(month(value.yearMonth())));
        var old=mapper.selectHeader(key);
        var mutation=new Mutation(key,version,value.corpCardAmount(),value.summary().partProfitAmount(),state,actor.userId());
        if (value.summary().partProfitAmount().abs().compareTo(new BigDecimal("999999999999999"))>0)
            throw CodeFailure.invalid("partProfitAmount","파트 이익금이 저장 가능한 범위를 넘었습니다.");
        if (old==null) affected(mapper.insertHeader(mutation));
        affected(mapper.confirm(mutation));
        mapper.deleteDetails(key);
        for (var row:value.items()) affected(mapper.insertDetail(new Snapshot(key,row,actor.userId())));
        affected(mapper.insertHistory(new Audit(value.partId()+":"+key.month(),"정산 상태","WORK",state,null,actor.userId())));
    }
    @Transactional
    public Settlement putCommonPartSettlementCancel(String id,String yearMonth,CancelInput input) {
        var month=month(yearMonth);
        String reason=input.cancelReason();
        if (reason==null || reason.isBlank() || reason.codePointCount(0,reason.length())>500)
            throw CodeFailure.invalid("cancelReason","확정 취소 사유를 1~500자로 입력해 주세요.");
        lock(); require(mapper.selectParts(db(month)),id);
        var key=new Key(id,db(month)); var old=current(key,input.modifiedAt()); open(key);
        if (!confirmed(old)) throw CodeFailure.invalid("cancelReason","확정된 정산만 취소할 수 있습니다.");
        String previousState=old.state;
        var mutation=new Mutation(key,input.modifiedAt(),old.card,old.profit,"WORK",actor.userId());
        affected(mapper.cancel(mutation,reason));
        affected(mapper.insertHistory(new Audit(id+":"+key.month(),"정산 상태",previousState,"WORK",reason,actor.userId())));
        return getCommonPartSettlement(id,yearMonth);
    }
}
