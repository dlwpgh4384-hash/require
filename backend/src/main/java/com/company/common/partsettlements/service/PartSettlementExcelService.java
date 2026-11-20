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
    @Transactional(isolation=Isolation.REPEATABLE_READ)
    public ExcelFile getCommonPartSettlementsExcel(String partId,String yearMonth,String view) {
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
        } else if ("trend".equals(view)) {
            var data=service.getCommonPartSettlementTrends(partId,yearMonth);
            headers=new ArrayList<>(List.of("이름","구분"));
            data.monthlyTotals().forEach(t->headers.add(Integer.parseInt(t.yearMonth().substring(5))+"월"));
            headers.add("합계");
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
                rows.add(row);
            }
            var total=new ArrayList<>(List.of("파트 이익금",""));
            data.monthlyTotals().forEach(t->total.add(t.partProfitAmount().toPlainString()));
            total.add(data.monthlyTotals().stream().map(TrendTotal::partProfitAmount).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add).toPlainString());
            rows.add(total);
        } else throw CodeFailure.invalid("view","이번 달 또는 기간 추이를 골라 주세요.");
        var now=calendar.now();
        byte[] bytes=excel.write("영업 파트 이익금",headers,rows,actor.userId(),now);
        String logPart=partId!=null && partId.matches("[A-Za-z0-9_-]{1,20}") ? partId : "선택 파트";
        CommonPartSettlementService.affected(mapper.insertAccess(actor.userId(),"영업 파트 이익금 "+logPart+" "+yearMonth+" "+view));
        return new ExcelFile(bytes,excel.fileName("영업 파트 이익금",yearMonth,now));
    }
}
