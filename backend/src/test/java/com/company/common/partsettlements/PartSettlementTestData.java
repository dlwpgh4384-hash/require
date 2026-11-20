package com.company.common.partsettlements;

import com.company.common.calculation.mapper.*;
import com.company.common.partsettlements.dto.PartSettlementContracts.*;
import com.company.common.partsettlements.mapper.*;
import com.company.common.partsettlements.mapper.PartSettlementRows.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

final class PartSettlementTestData {
    final Map<Key,Header> headers=new LinkedHashMap<>();
    final Map<Key,List<Detail>> details=new LinkedHashMap<>();
    final List<Audit> audits=new ArrayList<>();
    final List<Part> parts=new ArrayList<>();
    final List<Input> inputs=new ArrayList<>();
    final List<History> history=new ArrayList<>();
    boolean closed;
    long revision;
    PartSettlementTestData(PartSettlementMapper mapper) {
        parts.add(part("A")); parts.add(part("B")); var hq=part("HQ");hq.hqYn="Y";parts.add(hq);
        inputs.add(input("D","A"));
        when(mapper.selectParts(anyString())).thenAnswer(call->parts);
        when(mapper.selectInputs(anyString())).thenAnswer(call->inputs);
        when(mapper.selectHistory()).thenAnswer(call->history);
        when(mapper.selectHeader(any())).thenAnswer(call->headers.get(call.getArgument(0)));
        when(mapper.selectDetails(any())).thenAnswer(call->details.getOrDefault(call.getArgument(0),List.of()));
        when(mapper.isClosed(anyString())).thenAnswer(call->closed);
        when(mapper.insertHeader(any())).thenAnswer(call->{
            Mutation m=call.getArgument(0); if (headers.containsKey(m.key())) return 0;
            var h=new Header();h.state="WORK";h.card=n("0");headers.put(m.key(),h);return 1;
        });
        when(mapper.updateCard(any())).thenAnswer(call->{
            Mutation m=call.getArgument(0);var h=headers.get(m.key());
            if (!Objects.equals(h.modifiedAt,m.version())) return 0;
            h.card=m.card();touch(h);return 1;
        });
        when(mapper.confirm(any())).thenAnswer(call->{
            Mutation m=call.getArgument(0);var h=headers.get(m.key());
            if (!Objects.equals(h.modifiedAt,m.version())) return 0;
            h.state=m.state();h.profit=m.profit();h.confirmedBy=m.actor();touch(h);h.confirmedAt=h.modifiedAt;return 1;
        });
        when(mapper.cancel(any(),anyString())).thenAnswer(call->{
            Mutation m=call.getArgument(0);var h=headers.get(m.key());
            if (!Objects.equals(h.modifiedAt,m.version())) return 0;
            h.state="WORK";h.cancelReason=call.getArgument(1);h.confirmedBy=null;h.confirmedAt=null;touch(h);return 1;
        });
        when(mapper.deleteDetails(any())).thenAnswer(call->{details.remove(call.getArgument(0));return 1;});
        when(mapper.insertDetail(any())).thenAnswer(call->{
            Snapshot s=call.getArgument(0);var r=s.row();var d=new Detail();
            d.developerId=r.developerId();d.developerName=r.developerName();d.employeeTypeCode=r.employeeTypeCode();
            d.projectNames=s.getProjectNames();d.monthlyMm=r.monthlyMm();d.unitPrice=r.unitPrice();d.salary=r.salary();
            d.salesExpense=r.salesExpense();d.retirementAmount=r.retirementAmount();d.totalCost=r.totalCost();
            d.profit=r.profit();d.profitShare=r.profitShare();d.notes=s.getNotes();details.computeIfAbsent(s.key(),x->new ArrayList<>()).add(d);return 1;
        });
        when(mapper.insertHistory(any())).thenAnswer(call->{audits.add(call.getArgument(0));return 1;});
        when(mapper.insertAccess(anyString(),anyString())).thenReturn(1);
    }
    void touch(Header h) { h.modifiedAt=Instant.parse("2026-02-01T00:00:00Z").plusSeconds(++revision); }
    Header header(String state) {var h=new Header();h.state=state;h.card=n("100");h.profit=n("999");touch(h);headers.put(new Key("A","202601"),h);return h;}
    static BigDecimal n(String s) {return new BigDecimal(s);}
    static Part part(String id) {var p=new Part();p.partId=id;p.partName="영업 "+id;p.hqYn="N";p.useYn="Y";p.leaderName="파트장";p.distributionRate=n("40");p.salesExpense=n("1500000");return p;}
    static Input input(String developer,String part) {
        var i=new Input();i.developerId=developer;i.developerName="개발자 "+developer;i.partId=part;
        i.employeeTypeCode="FT";i.assignmentId="AS-"+developer;i.projectId="P";i.projectName="프로젝트";
        i.contractType="MO";i.inputType="NEW";i.mm=n("1");i.price=n("7000000");i.monthlySalary=n("4000000");
        i.startDate=LocalDate.of(2025,1,1);i.firstStart=i.startDate;i.uploaded=false;return i;
    }
    static void definitions(CalculationRuleMapper mapper) throws Exception {
        String seed=new ClassPathResource("db/migration/V8__calculation_rule_seed.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (String id:List.of("00","01","35","36","37","39","40","41","42","43","44","45","57","58")) {
            var m=Pattern.compile("\\('식-"+id+"', (?:NULL|'[^']*'), '[^']*', '((?:[^']++|'')*+)', '((?:[^']++|'')*+)',").matcher(seed);
            assertThat(m.find()).isTrue();var row=new CalculationRuleRow();row.calcId="식-"+id;
            row.formula=m.group(1).replace("''","'");row.details=m.group(2).replace("''","'");when(mapper.selectDefinition(row.calcId)).thenReturn(row);
        }
    }
}
