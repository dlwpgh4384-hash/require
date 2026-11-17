package com.company.common.assignments.service;

import com.company.common.assignments.mapper.AssignmentRows.HistoryRow;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.developers.service.DeveloperManMonthRules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AssignmentMonthRules {
    private final CalculationRuleService definitions;
    private final DeveloperManMonthRules mmRules;
    public AssignmentMonthRules(CalculationRuleService definitions, DeveloperManMonthRules mmRules) {
        this.definitions=definitions; this.mmRules=mmRules;
    }
    public BigDecimal mm(BigDecimal contractual, LocalDate start, LocalDate end, YearMonth month) {
        var definition=definitions.definition("식-01");
        String one=normalized(definition.formula(),"식-01");
        if (!one.matches("월MM=계약MM×\\(그달투입일수÷그달달력일수\\),소수(첫째|둘째|셋째)자리반올림")) throw unsupported("식-01");
        String rounding=normalized(definitions.definition("식-58").formula(),"식-58");
        var scale=Pattern.compile("MM=round\\(계산값,소수([1-3])자리\\)\\.금액=반올림한MM×단가").matcher(rounding);
        if (!scale.matches()) throw unsupported("식-58");
        int digits=Integer.parseInt(scale.group(1));
        String details=definition.details();
        if(details==null || !details.contains("투입 시작일과 철수일을 모두 일수에 넣는다")) throw unsupported("식-01");
        return contractual.multiply(BigDecimal.valueOf(days(start,end,month))).divide(BigDecimal.valueOf(month.lengthOfMonth()),digits,RoundingMode.HALF_UP);
    }
    public int days(LocalDate start, LocalDate end, YearMonth month) {
        LocalDate first=start.isAfter(month.atDay(1))?start:month.atDay(1);
        LocalDate last=end.isBefore(month.atEndOfMonth())?end:month.atEndOfMonth();
        return first.isAfter(last)?0:(int)java.time.temporal.ChronoUnit.DAYS.between(first,last)+1;
    }
    public BigDecimal sales(BigDecimal mm, BigDecimal price, boolean billable) {
        require("식-00","(수식 없음, 모든 식에 적용하는 원칙)");
        String formula=normalized(definitions.definition("식-02").formula(),"식-02");
        if (!formula.equals("매출=Σ(월MM×청구단가).무상지원·내부는0.금액은건별로원단위버림후합산")) throw unsupported("식-02");
        return billable?mm.multiply(price).setScale(0,RoundingMode.DOWN):BigDecimal.ZERO;
    }
    public boolean over(BigDecimal total) { return mmRules.over(total); }
    public BigDecimal effective(List<HistoryRow> history, String command, YearMonth month, BigDecimal fallback) {
        return history.stream().filter(h -> ("INPUT".equals(h.type()) || command.equals(h.type()))
                && !YearMonth.from(h.effectiveDate()).isAfter(month)
                && ("UPRC".equals(command)?h.price():h.mm())!=null)
            .max(Comparator.comparing((HistoryRow h) -> YearMonth.from(h.effectiveDate()))
                .thenComparingInt(HistoryRow::sequence))
            .map(h -> "UPRC".equals(command)?h.price():h.mm()).orElse(fallback);
    }
    public void sourceSemantics() {
        require("식-10","정규직 = 급여 + 수당 + 4대보험(회사) + 퇴직금 적립. 계약직 = 급여 + 수당 + 4대보험(회사). 프리랜서 = 그 달 실제 지급액(원천세 차감 전). 외주 = 협력사 공급가액");
        require("식-07","청구서 줄 = 계약상 인력 이름. 개인 원가·수익표의 매출·원가 = 실제 투입 인력");
        require("식-12","원가합계 = 개인 월 인건비 + 그 사람의 직접 경비");
        require("식-16","수주예정 프로젝트 원가 = 선투입 인력의 배분 원가. 실주 시 → 대기 원가로 이동");
        require("식-59","이번 달 전월 조정 = 마감한 달의 정정 차액. 영업이익 = 매출총이익 − 판관비 + 전월 조정");
        if (!normalized(definitions.definition("식-70").formula(),"식-70").contains("인력사정조기철수건수")) throw unsupported("식-70");
    }
    private void require(String id,String expected) {
        if (!normalized(definitions.definition(id).formula(),id).equals(expected.replaceAll("\\s+",""))) throw unsupported(id);
    }
    private static String normalized(String formula,String id) {
        if (formula==null) throw unsupported(id);
        return formula.replaceAll("\\s+","");
    }
    private static com.company.common.support.service.CodeFailure unsupported(String id) { return com.company.common.support.service.CodeFailure.invalid("calculation","지원하지 않는 "+id+" 정의입니다. DB 계산식의 지원 연산을 확인해 주세요."); }
}
