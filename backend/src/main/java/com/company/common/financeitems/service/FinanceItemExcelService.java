package com.company.common.financeitems.service;

import com.company.common.financeitems.mapper.FinanceItemMapper;
import com.company.common.financeitems.dto.FinanceItemContracts.Item;
import com.company.common.support.service.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceItemExcelService {
    private final FinanceItemMapper mapper;
    private final ExcelExportService excel;
    private final CurrentBusinessActor actor;
    private final BusinessCalendar calendar;
    public FinanceItemExcelService(FinanceItemMapper mapper,ExcelExportService excel,CurrentBusinessActor actor,BusinessCalendar calendar) {
        this.mapper=mapper; this.excel=excel; this.actor=actor; this.calendar=calendar;
    }
    public record File(byte[] bytes,String fileName) {}
    private static final Map<String,String> HEADERS;
    static {
        var h=new LinkedHashMap<String,String>();
        h.put("classification","구분"); h.put("itemCode","판관비 항목 코드"); h.put("itemName","세부 항목");
        h.put("amount","금액"); h.put("vatAmount","부가세"); h.put("cashDate","입출금일");
        h.put("repeatYn","매달 반복"); h.put("memo","비고"); HEADERS=Collections.unmodifiableMap(h);
    }
    @Transactional
    public File getCommonFinanceItemsExcel(String month,List<String> columns) {
        FinanceItemRules.month(month);
        if (columns==null || columns.isEmpty() || new HashSet<>(columns).size()!=columns.size() || !HEADERS.keySet().containsAll(columns))
            throw CodeFailure.invalid("columns","표시할 열을 확인해 주세요.");
        var rows=mapper.items(month).stream().map(i->columns.stream().map(c->cell(i,c)).toList()).toList();
        var at=calendar.now();
        var bytes=excel.write("재무 항목",columns.stream().map(HEADERS::get).toList(),rows,actor.userId(),at);
        FinanceItemRules.affected(mapper.access(actor.userId(),"재무 항목 "+month));
        return new File(bytes,excel.fileName("재무 항목",month,at));
    }
    private String cell(Item row,String key) {
        Object value=switch(key) {
            case "classification" -> mapper.codeName("FIN_CLS",row.classification());
            case "itemCode" -> row.itemCode(); case "itemName" -> row.itemName();
            case "amount" -> row.amount().toPlainString(); case "vatAmount" -> row.vatAmount().toPlainString();
            case "cashDate" -> row.cashDate(); case "repeatYn" -> row.repeatYn(); case "memo" -> row.memo();
            default -> throw CodeFailure.invalid("columns","표시할 열을 확인해 주세요.");
        };
        return Objects.toString(value,"");
    }
}
