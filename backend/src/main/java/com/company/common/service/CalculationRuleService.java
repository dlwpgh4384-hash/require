package com.company.common.service;

import com.company.common.dto.CalculationRuleContracts.Definition;
import com.company.common.dto.CalculationRuleContracts.EffectiveValue;
import com.company.common.dto.CalculationRuleContracts.Source;
import com.company.common.dto.CalculationRuleContracts.Standard;
import com.company.common.dto.CalculationRuleContracts.ValueRequest;
import com.company.common.mapper.CalculationRuleMapper;
import com.company.common.mapper.UserPreferencePeriodSourceMapper;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CalculationRuleService {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("uuuuMM");
    private final CalculationRuleMapper mapper;
    private final UserPreferencePeriodSourceMapper periods;

    public CalculationRuleService(CalculationRuleMapper mapper, UserPreferencePeriodSourceMapper periods) {
        this.mapper = mapper;
        this.periods = periods;
    }

    public Definition definition(String calcId) {
        if (calcId == null || !calcId.matches("식-[0-9]{2}")) {
            throw CodeFailure.invalid("calcId", "식 식별자를 확인해 주세요.");
        }
        var row = mapper.selectDefinition(calcId);
        if (row == null) throw CodeFailure.invalid("calcId", "등록된 식 정의가 없습니다.");
        return row.value();
    }

    public Standard effectiveStandard(String standardCode, YearMonth month) {
        if (standardCode == null || standardCode.isBlank() || standardCode.length() > 30) {
            throw CodeFailure.invalid("standardCode", "운영 기준 코드를 확인해 주세요.");
        }
        if (month == null) throw CodeFailure.invalid("effectiveMonth", "적용 월을 확인해 주세요.");
        var row = mapper.selectEffectiveStandard(standardCode, month.format(MONTH));
        if (row == null) throw CodeFailure.invalid("standardCode", "적용 가능한 운영 기준값이 없습니다.");
        return row.value();
    }

    public EffectiveValue resolve(ValueRequest request) {
        if (request == null || request.month() == null) {
            throw CodeFailure.invalid("effectiveMonth", "적용 월을 확인해 주세요.");
        }
        var base = periods.selectCashBaseYm();
        if (base == null) throw CodeFailure.invalid("effectiveMonth", "현금 기준 월을 확인해 주세요.");
        YearMonth baseMonth;
        try {
            baseMonth = YearMonth.parse(base.toBigIntegerExact().toString(), MONTH);
        } catch (RuntimeException exception) {
            throw CodeFailure.invalid("effectiveMonth", "현금 기준 월을 확인해 주세요.");
        }
        boolean closed = request.month().isBefore(baseMonth)
            || periods.selectClosedMonths().contains(request.month().format(MONTH));
        if (closed) {
            if (request.closedResult() == null) {
                throw CodeFailure.invalid("effectiveMonth", "마감 월의 저장 결과가 필요합니다.");
            }
            return new EffectiveValue(request.closedResult(), Source.CLOSED_RESULT);
        }
        if (request.projectOverride() != null) {
            return new EffectiveValue(request.projectOverride(), Source.PROJECT_OVERRIDE);
        }
        return new EffectiveValue(effectiveStandard(request.standardCode(), request.month()).value(),
            Source.OPERATING_STANDARD);
    }
}
