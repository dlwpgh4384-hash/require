package com.company.common.partsettlements.service;

import com.company.common.common.PartSettlementDefinitions;
import com.company.common.partsettlements.dto.PartSettlementContracts.*;
import com.company.common.partsettlements.mapper.PartSettlementMapper;
import com.company.common.support.service.CodeFailure;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.company.common.partsettlements.service.PartSettlementRules.*;

@Service("partSettlementConfirmationService")
public class PartSettlementCloseService {
    private final PartSettlementMapper mapper;
    private final CommonPartSettlementService settlements;
    private final PartSettlementDefinitions definitions;
    public PartSettlementCloseService(PartSettlementMapper mapper,CommonPartSettlementService settlements,PartSettlementDefinitions definitions) {
        this.mapper=mapper; this.settlements=settlements; this.definitions=definitions;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public CloseCheck getCommonPartSettlementCloseChecks(String yearMonth) {
        var month=month(yearMonth);
        var history=mapper.selectHistory();
        var items=mapper.selectParts(db(month)).stream().filter(p->closeTarget(p,month,history)).map(p->{
            var header=mapper.selectHeader(new Key(p.partId,db(month)));
            return new CloseItem(p.partId,confirmed(header),header==null ? null : header.modifiedAt);
        }).toList();
        return new CloseCheck(yearMonth,items.size(),(int)items.stream().filter(i->!i.confirmed()).count(),items);
    }
    @Transactional
    public CloseResult putCommonPartSettlementCloseConfirm(CloseInput input) {
        var month=month(input.yearMonth());
        if (input.items()==null) throw CodeFailure.invalid("items","정산 점검 결과를 입력해 주세요.");
        settlements.lock();
        var actual=getCommonPartSettlementCloseChecks(input.yearMonth());
        Map<String,CloseItem> received=new HashMap<>();
        var parts=mapper.selectParts(db(month));
        for (var row:input.items()) {
            var p=CommonPartSettlementService.require(parts,row.partId());
            if (!closeTarget(p,month,mapper.selectHistory())) throw CodeFailure.invalid("items","마감 대상이 아닌 파트가 포함되어 있습니다.");
            if (received.put(row.partId(),row)!=null) throw CodeFailure.conflict();
        }
        if (received.size()!=actual.items().size() || !actual.items().stream().allMatch(i->i.equals(received.get(i.partId())))) throw CodeFailure.conflict();
        if (mapper.isClosed(db(month)) && actual.unconfirmedCount()>0) throw CodeFailure.invalid("yearMonth","이미 마감된 달입니다.");
        var rules=new PartSettlementRules(definitions.definitions());
        var prepared=new LinkedHashMap<String,Settlement>();
        for (var target:actual.items()) if (!target.confirmed()) {
            var value=settlements.read(CommonPartSettlementService.require(parts,target.partId()),month,true,rules,parts);
            snapshotCapacity(value.items()); prepared.put(target.partId(),value);
        }
        for (var target:actual.items()) if (!target.confirmed())
            settlements.persistConfirmation(prepared.get(target.partId()),target.modifiedAt(),"AUTO");
        var results=actual.items().stream().map(i->settlements.getCommonPartSettlement(i.partId(),input.yearMonth())).toList();
        return new CloseResult(input.yearMonth(),results,0);
    }
}
