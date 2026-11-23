package com.company.common.financeitems.service;

import com.company.common.financeitems.dto.FinanceItemContracts.*;
import com.company.common.financeitems.mapper.FinanceItemMapper;
import com.company.common.support.service.*;
import com.company.common.uploads.dto.UploadContracts.*;
import com.company.common.uploads.dto.UploadWorkbookContracts.TabularRow;
import com.company.common.uploads.service.UploadForm;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.company.common.financeitems.service.FinanceItemRules.*;

@Component
public class FinanceItemUploadForm implements UploadForm {
    public static final List<String> HEADERS=List.of("구분","판관비 항목","세부 항목","금액","부가세","입출금일","매달 반복","비고");
    private final FinanceItemMapper mapper;
    private final CommonFinanceItemService months;
    private final CurrentBusinessActor actor;
    public FinanceItemUploadForm(FinanceItemMapper mapper,CommonFinanceItemService months,CurrentBusinessActor actor) {
        this.mapper=mapper; this.months=months; this.actor=actor;
    }
    public String formTypeCode() { return "B2"; }
    public String targetName() { return "재무 항목"; }
    public String screenId() { return "UI-10"; }
    public List<String> headers() { return HEADERS; }
    public String templateNote() { return "B.2 재무 항목. 세부 항목*·금액*은 필수입니다. 연월은 화면에서 선택합니다. 부가세 공백은 0입니다."; }
    public String templateFileName() { return "finance-items-template.xlsx"; }
    public List<String> exampleRow() { return List.of("판관비", "임대료", "본사 사무실 임대료", "5000000", "500000", "2026-01-25", "Y", ""); }
    public boolean monthly() { return true; }
    public void lock() { mapper.lockSources(); }
    public void checkMonth(YearMonth month) {
        if (mapper.isClosed(db(month))) throw CodeFailure.invalid("yearMonth","마감한 달은 올릴 수 없습니다.");
    }
    public Inspection inspect(List<TabularRow> rows,YearMonth month) {
        var issues=new ArrayList<Issue>();
        var records=new ArrayList<Item>();
        for (var row:rows) {
            int column=0;
            try {
                if (!row.formulaColumns().isEmpty()) {
                    column=row.formulaColumns().get(0);
                    throw new IllegalArgumentException("수식은 올릴 수 없습니다.");
                }
                var v=row.values();
                String classification=mapper.code("FIN_CLS",v.get(0).trim());
                if (classification==null) throw new IllegalArgumentException("구분을 확인해 주세요.");
                column=1;
                String itemCode=v.get(1).isBlank() ? null : mapper.code("SGA_ITEM",v.get(1).trim());
                if (!v.get(1).isBlank() && itemCode==null) throw new IllegalArgumentException("판관비 항목을 확인해 주세요.");
                column=2; text(v.get(2),"itemName","세부 항목",50,true);
                column=3;
                BigDecimal amount=v.get(3).isBlank() ? null : new BigDecimal(v.get(3).replace(",",""));
                amount(amount,"amount");
                column=4;
                BigDecimal vat=v.get(4).isBlank() ? BigDecimal.ZERO : new BigDecimal(v.get(4).replace(",",""));
                amount(vat,"vatAmount");
                column=5;
                LocalDate date=v.get(5).isBlank() ? null : com.company.common.developers.service.DeveloperUploadForm.parseDate(v.get(5).trim());
                if (!v.get(5).isBlank() && date==null) throw new IllegalArgumentException("날짜는 2024-03-02처럼 적어 주세요.");
                column=6;
                String repeat=v.get(6).isBlank() ? "N" : v.get(6).trim();
                if (!Set.of("Y","N").contains(repeat)) throw new IllegalArgumentException("Y 또는 N을 입력해 주세요.");
                column=7; text(v.get(7),"memo","비고",500,false);
                records.add(new Item(null,null,classification,itemCode,v.get(2).trim(),amount,vat,date,repeat,"N",null,v.get(7)));
            } catch (RuntimeException failure) {
                String message=failure instanceof CodeFailure ? failure.getMessage() :
                    failure instanceof IllegalArgumentException && !(failure instanceof NumberFormatException) ? failure.getMessage() : "입력값을 확인해 주세요.";
                issues.add(new Issue(row.rowNumber(),HEADERS.get(Math.min(column,7)),
                    row.values().get(Math.min(column,7)).substring(0,Math.min(200,row.values().get(Math.min(column,7)).length())),message,"오류"));
            }
        }
        return new Inspection(records,issues,records.size(),0,rows.size()-records.size(),rows.size());
    }
    public Map<Integer,String> apply(Inspection inspection,YearMonth month,long uploadId) {
        String value=db(month);
        checkMonth(month);
        mapper.deleteUploaded(value);
        long next=mapper.nextItem(value);
        for (var record:inspection.records()) {
            var i=(Item)record;
            if (next>99999) throw CodeFailure.invalid("file","항목 수가 저장 범위를 넘었습니다.");
            var row=new Item(Long.toString(next++),null,i.classification(),i.itemCode(),i.itemName(),i.amount(),i.vatAmount(),i.cashDate(),i.repeatYn(),"N",Long.toString(uploadId),i.memo());
            affected(mapper.insertItem(new Write<>(value,row,actor.userId())));
        }
        months.reallocate(month.getYear());
        months.marker(value);
        return Map.of();
    }
}
