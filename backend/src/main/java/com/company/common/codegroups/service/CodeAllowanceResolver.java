package com.company.common.codegroups.service;

import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.preferences.mapper.UserPreferencePeriodSourceMapper;
import com.company.common.support.service.CodeFailure;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CodeAllowanceResolver {
    public record Request(String code, YearMonth month, BigDecimal projectOverride, BigDecimal closedResult) {}
    public record Value(BigDecimal value, Source source) {}
    public enum Source { CLOSED_RESULT, PROJECT_OVERRIDE, ALLOWANCE }
    private final CodeGroupMapper mapper;
    private final UserPreferencePeriodSourceMapper periods;
    public CodeAllowanceResolver(CodeGroupMapper mapper, UserPreferencePeriodSourceMapper periods) {
        this.mapper = mapper;
        this.periods = periods;
    }
    public Value resolve(Request request) {
        if (request == null || request.month() == null) throw CodeGroupRules.invalid("effectiveMonth");
        CodeGroupRules.code(request.code(), false);
        var format = DateTimeFormatter.ofPattern("uuuuMM");
        var base = periods.selectCashBaseYm();
        if (base == null) throw CodeGroupRules.invalid("effectiveMonth");
        YearMonth baseMonth;
        try { baseMonth = YearMonth.parse(base.toBigIntegerExact().toString(), format); }
        catch (RuntimeException failure) { throw CodeGroupRules.invalid("effectiveMonth"); }
        if (request.month().isBefore(baseMonth) || periods.selectClosedMonths().contains(request.month().format(format))) {
            if (request.closedResult() == null) throw new CodeFailure("effectiveMonth", "마감 월의 저장 결과가 필요합니다.");
            return new Value(request.closedResult(), Source.CLOSED_RESULT);
        }
        if (request.projectOverride() != null) return new Value(request.projectOverride(), Source.PROJECT_OVERRIDE);
        var row = mapper.selectCode("ALWNC", request.code());
        if (row == null || !"N".equals(row.delYn) || !"Y".equals(row.useYn) || row.codeVal == null)
            throw new CodeFailure("code", "적용 가능한 수당코드가 없습니다.");
        return new Value(row.codeVal, Source.ALLOWANCE);
    }
}
