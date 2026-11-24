package com.company.common.partsettlements.service;

import com.company.common.partsettlements.dto.PartSettlementContracts.*;
import com.company.common.partsettlements.mapper.PartSettlementMapper;
import com.company.common.support.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class PartSettlementExcelService {
    private final CommonPartSettlementService service;
    private final PartSettlementMapper mapper;
    private final ExcelExportService excel;
    private final CurrentBusinessActor actor;
    private final BusinessCalendar calendar;
    public PartSettlementExcelService(CommonPartSettlementService service,PartSettlementMapper mapper,
        ExcelExportService excel,CurrentBusinessActor actor,BusinessCalendar calendar) {
        this.service=service;this.mapper=mapper;this.excel=excel;this.actor=actor;this.calendar=calendar;
    }
    /** 이번 달 표 열 열쇠(화면 ListView 열 key와 같은 순서). */
    static final List<String> MONTHLY_KEYS=List.of("developerName","employeeTypeCode","projectNames","monthlyMm","unitPrice",
        "salary","salesExpense","retirementAmount","totalCost","profit","profitShare","notes");
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public ExcelFile getCommonPartSettlementsExcel(String partId,String yearMonth,String view) {
        return getCommonPartSettlementsExcel(partId,yearMonth,view,null);
    }
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public ExcelFile getCommonPartSettlementsExcel(String partId,String yearMonth,String view,String columns) {
        PartSettlementRules.month(yearMonth);
        List<String> headers;
        List<List<String>> rows=new ArrayList<>();
        if ("monthly".equals(view)) {
            var data=service.getCommonPartSettlement(partId,yearMonth);
            headers=List.of("이름","구분","프로젝트","MM","단가","급여","공통관리비","퇴직금","비용 계","이익",
                "이익금("+data.summary().distributionRate().stripTrailingZeros().toPlainString()+"%)","비고");
            var types=new HashMap<String,String>();
            for (var r:data.items()) rows.add(List.of(r.developerName(),
                types.computeIfAbsent(r.employeeTypeCode(),code->Objects.requireNonNullElse(mapper.selectCodeName("EMP_TYPE",code),code)),
                String.join(", ",r.projectNames()),
                r.monthlyMm().toPlainString(),r.unitPrice().toPlainString(),r.salary().toPlainString(),r.salesExpense().toPlainString(),
                r.retirementAmount().toPlainString(),r.totalCost().toPlainString(),r.profit().toPlainString(),r.profitShare().toPlainString(),String.join(" · ",r.notes())));
            java.util.function.Function<java.util.function.Function<Workforce,java.math.BigDecimal>,String> sum=
                f->data.items().stream().map(f).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add).toPlainString();
            rows.add(List.of("합계","","",sum.apply(Workforce::monthlyMm),sum.apply(Workforce::unitPrice),sum.apply(Workforce::salary),
                sum.apply(Workforce::salesExpense),sum.apply(Workforce::retirementAmount),sum.apply(Workforce::totalCost),
                sum.apply(Workforce::profit),sum.apply(Workforce::profitShare),""));
            rows.add(List.of("법인카드","","","","","","","","","",data.corpCardAmount().negate().toPlainString(),""));
            rows.add(List.of("파트 이익금","","","","","","","","","",data.summary().partProfitAmount().toPlainString(),""));
            // SFR-011: 화면에서 보이는 열만 내보낸다(이름 열은 늘 있음). 열 값이 없으면 모든 열.
            if (columns!=null && !columns.isBlank()) {
                var wanted=new LinkedHashSet<>(Arrays.asList(columns.split(",")));
                for (String key:wanted) if (!MONTHLY_KEYS.contains(key)) throw CodeFailure.invalid("columns","내보낼 열을 확인해 주세요.");
                var keep=new ArrayList<Integer>();
                for (int i=0;i<MONTHLY_KEYS.size();i++) if (i==0 || wanted.contains(MONTHLY_KEYS.get(i))) keep.add(i);
                var full=headers;
                headers=keep.stream().map(full::get).toList();
                rows.replaceAll(row->keep.stream().map(row::get).toList());
            }
        } else if ("trend".equals(view)) {
            var data=service.getCommonPartSettlementTrends(partId,yearMonth);
            var trendHeaders=new ArrayList<>(List.of("이름","구분"));
            data.monthlyTotals().forEach(t->trendHeaders.add(Integer.parseInt(t.yearMonth().substring(5))+"월"));
            trendHeaders.add("합계");
            // 화면·시안처럼 맨 끝 「비고」(기준월 표의 사람별 비고, 사람의 첫 줄에만, 2026-11-24 사용자 결정).
            trendHeaders.add("비고");
            headers=trendHeaders;
            var notes=new HashMap<String,String>();
            for (var r:service.getCommonPartSettlement(partId,yearMonth).items()) notes.put(r.developerId(),String.join(" · ",r.notes()));
            for (var person:data.items()) for (String metric:List.of("M/M","단가","비용","이익","이익금")) {
                var row=new ArrayList<>(List.of(person.developerName(),metric));
                java.math.BigDecimal sum=java.math.BigDecimal.ZERO;
                for (var m:person.months()) {
                    var value=switch(metric) {
                        case "M/M" -> m.monthlyMm(); case "단가" -> m.unitPrice();
                        case "비용" -> m.totalCost(); case "이익" -> m.profit(); default -> m.profitShare();
                    };
                    // 식-44: 투입한 달(MM > 0)만 보이고 더한다. 투입 안 한 달은 화면처럼 「–」.
                    if (m.monthlyMm().signum()>0) { row.add(value.toPlainString()); sum=sum.add(value); } else row.add("–");
                }
                row.add(sum.toPlainString());
                row.add("M/M".equals(metric)?notes.getOrDefault(person.developerId(),""):"");
                rows.add(row);
            }
            var total=new ArrayList<>(List.of("파트 이익금",""));
            data.monthlyTotals().forEach(t->total.add(t.partProfitAmount().toPlainString()));
            total.add(data.monthlyTotals().stream().map(TrendTotal::partProfitAmount).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add).toPlainString());
            total.add("");
            rows.add(total);
        } else throw CodeFailure.invalid("view","이번 달 또는 기간 추이를 골라 주세요.");
        var now=calendar.now();
        byte[] bytes=excel.write("영업 파트 이익금",headers,rows,actor.userId(),now);
        String logPart=partId!=null && partId.matches("[A-Za-z0-9_-]{1,20}") ? partId : "선택 파트";
        CommonPartSettlementService.affected(mapper.insertAccess(actor.userId(),"영업 파트 이익금 "+logPart+" "+yearMonth+" "+view));
        return new ExcelFile(bytes,excel.fileName("영업 파트 이익금",yearMonth,now));
    }
}
