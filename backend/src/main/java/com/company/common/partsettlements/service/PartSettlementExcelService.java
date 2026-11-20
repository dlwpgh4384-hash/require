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
            headers=List.of("이름","직원구분","프로젝트","MM","단가","급여","공통관리비","퇴직금","비용 계","이익","이익금","비고");
            for (var r:data.items()) rows.add(List.of(r.developerName(),r.employeeTypeCode(),String.join(", ",r.projectNames()),
                r.monthlyMm().toPlainString(),r.unitPrice().toPlainString(),r.salary().toPlainString(),r.salesExpense().toPlainString(),
                r.retirementAmount().toPlainString(),r.totalCost().toPlainString(),r.profit().toPlainString(),r.profitShare().toPlainString(),String.join(" · ",r.notes())));
            rows.add(List.of("합계","","","","","","","","",data.summary().profitTotal().toPlainString(),data.summary().partProfitAmount().toPlainString(),"법인카드 "+data.corpCardAmount().toPlainString()));
        } else if ("trend".equals(view)) {
            var data=service.getCommonPartSettlementTrends(partId,yearMonth);
            headers=new ArrayList<>(List.of("이름","항목"));
            data.monthlyTotals().forEach(t->headers.add(t.yearMonth()));
            for (var person:data.items()) for (String metric:List.of("M/M","단가","비용","이익","이익금")) {
                var row=new ArrayList<>(List.of(person.developerName(),metric));
                for (var m:person.months()) row.add(switch(metric) {
                    case "M/M" -> m.monthlyMm().toPlainString(); case "단가" -> m.unitPrice().toPlainString();
                    case "비용" -> m.totalCost().toPlainString(); case "이익" -> m.profit().toPlainString(); default -> m.profitShare().toPlainString();
                });
                rows.add(row);
            }
            var total=new ArrayList<>(List.of("월별 파트 이익금 합계",""));
            data.monthlyTotals().forEach(t->total.add(t.partProfitAmount().toPlainString())); rows.add(total);
        } else throw CodeFailure.invalid("view","이번 달 또는 기간 추이를 골라 주세요.");
        var now=calendar.now();
        byte[] bytes=excel.write("영업 파트 이익금",headers,rows,actor.userId(),now);
        String logPart=partId!=null && partId.matches("[A-Za-z0-9_-]{1,20}") ? partId : "선택 파트";
        CommonPartSettlementService.affected(mapper.insertAccess(actor.userId(),"영업 파트 이익금 "+logPart+" "+yearMonth+" "+view));
        return new ExcelFile(bytes,excel.fileName("영업 파트 이익금",yearMonth,now));
    }
}
