package com.company.common.airecommendations.dto;

import java.util.List;

public final class AiRecommendationOptionsContracts {
    private AiRecommendationOptionsContracts() {}

    public record Filter(String targetType, String targetId) {}
    public record Choice(String code, String name) {}
    public record Need(int needSn, String jobCode, String jobName, int requiredCount, String startMonth) {}
    public record Target(String targetType, String targetId, String targetName,
        String startMonth, String endMonth, List<Need> needs) {}
    public record Options(List<Target> targets, List<Choice> jobs, List<Choice> grades,
        List<Choice> exclusionReasons) {}
}
