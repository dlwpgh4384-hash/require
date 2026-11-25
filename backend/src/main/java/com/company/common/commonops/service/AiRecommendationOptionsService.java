package com.company.common.commonops.service;

import com.company.common.common.AiRecommendationCodes;
import com.company.common.commonops.dto.AiRecommendationOptionsContracts.*;
import com.company.common.commonops.mapper.AiRecommendationOptionsMapper;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AiRecommendationOptionsService {
    private final AiRecommendationOptionsMapper mapper;
    private final AiRecommendationCodes codes;
    private final BusinessCalendar calendar;

    public AiRecommendationOptionsService(AiRecommendationOptionsMapper mapper,
        AiRecommendationCodes codes, BusinessCalendar calendar) {
        this.mapper = mapper;
        this.codes = codes;
        this.calendar = calendar;
    }

    public Options getCommonAiRecommendationOptions(Filter filter) {
        if (filter.targetType() != null && !"project".equals(filter.targetType())
            && !"opportunity".equals(filter.targetType())) {
            throw CodeFailure.invalid("targetType", "프로젝트 또는 영업 기회를 골라 주세요.");
        }
        if (filter.targetId() != null && (filter.targetId().isBlank() || filter.targetId().length() > 20)) {
            throw CodeFailure.invalid("targetId", "진행 중인 대상을 골라 주세요.");
        }
        var rows = mapper.targets(filter);
        if (filter.targetId() != null && rows.isEmpty()) {
            throw CodeFailure.invalid("targetId", "진행 중인 대상을 찾을 수 없습니다.");
        }
        var opportunityIds = rows.stream().filter(row -> "opportunity".equals(row.targetType()))
            .map(AiRecommendationOptionsMapper.TargetRow::targetId).toList();
        var needs = new HashMap<String, List<Need>>();
        if (!opportunityIds.isEmpty()) {
            for (var row : mapper.needs(opportunityIds)) {
                needs.computeIfAbsent(row.opportunityId(), key -> new ArrayList<>()).add(
                    new Need(row.needSn(), row.jobCode(), row.jobName(), row.requiredCount(), month(row.startMonth())));
            }
        }
        var targets = rows.stream().map(row -> new Target(row.targetType(), row.targetId(), row.targetName(),
            month(row.startMonth()), row.endMonth() == null ? null : month(row.endMonth()),
            List.copyOf("opportunity".equals(row.targetType())
                ? needs.getOrDefault(row.targetId(), java.util.Collections.emptyList())
                : java.util.Collections.emptyList()))).toList();
        return new Options(targets, choices("JOB"), choices("GRADE"), choices("RCMD_EXCL"));
    }

    private List<Choice> choices(String group) {
        return codes.selectable(group).stream().map(item -> new Choice(item.code, item.codeName)).toList();
    }

    private String month(String value) {
        return calendar.fromDatabaseMonth(value).toString();
    }
}
