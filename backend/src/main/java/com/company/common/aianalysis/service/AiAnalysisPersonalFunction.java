package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.aianalysis.mapper.AiAnalysisMapper;
import com.company.common.airecommendations.service.AiRecommendationCodes;
import com.company.common.personalcostprofits.dto.PersonalCostProfitContracts;
import com.company.common.personalcostprofits.service.CommonPersonalCostProfitService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisPersonalFunction implements AiAnalysisFunction {
    private final CommonPersonalCostProfitService personal;
    private final AiAnalysisTables tables;
    private final AiRecommendationCodes codes;
    private final AiAnalysisMapper mapper;
    public AiAnalysisPersonalFunction(CommonPersonalCostProfitService personal, AiAnalysisTables tables,
        AiRecommendationCodes codes, AiAnalysisMapper mapper) {
        this.personal = personal; this.tables = tables; this.codes = codes; this.mapper = mapper;
    }
    public Set<String> functions() { return Set.of("F-04"); }
    public Result read(AiAnalysisInterpreter.Query query) {
        var first = YearMonth.parse(query.period().startMonth());
        var end = YearMonth.parse(query.period().endMonth());
        if (first.getYear() != end.getYear()) return null;
        var input = new LinkedHashMap<String, Object>();
        input.put("year", end.getYear()); input.put("baseMonth", end.getMonthValue());
        input.put("displayMode", "연누계"); input.put("includeTurnkey", true);
        if (query.question().contains("적자")) input.put("view", "적자만");
        var jobs = codes.selectable("JOB").stream().filter(c -> query.question().contains(c.codeName)).toList();
        if (jobs.size() > 1) return null;
        if (jobs.size() == 1) input.put("jobCode", jobs.get(0).code);
        var names = mapper.identities().stream().filter(i -> !i.replacement().isBlank()
            && query.question().contains(i.name())).toList();
        if (names.size() > 1) return null;
        if (names.size() == 1) input.put("name", names.get(0).name());
        var calculation = personal.calculate(tables.filter(input, PersonalCostProfitContracts.Filter.class), first.getMonthValue());
        var rows = calculation.result().items();
        if (names.size() == 1) {
            rows = rows.stream().filter(row -> names.get(0).name().equals(row.get("developerName"))).toList();
            if (rows.size() != 1) return null;
        }
        var threshold = Pattern.compile("이익률\\s*([0-9]+(?:\\.[0-9]+)?)\\s*%\\s*미만").matcher(query.question());
        if (threshold.find()) {
            var limit = new BigDecimal(threshold.group(1));
            rows = rows.stream().filter(r -> r.get("profitRate") instanceof BigDecimal value && value.compareTo(limit) < 0).toList();
        }
        return new Result(query.salary()
            ? tables.table(rows, "developerName", "이름", "monthlySalary", "급여", "personalCost", "개인원가")
            : tables.table(rows, "developerName", "이름", "unitPrice", "매출", "operatingProfit", "영업이익", "profitRate", "이익률"),
            query.period(), true, "UI-03", List.of("CommonPersonalCostProfitService.calculate의 동일 기간 계산입니다."));
    }
}
