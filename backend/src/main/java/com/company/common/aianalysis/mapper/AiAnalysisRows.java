package com.company.common.aianalysis.mapper;

public final class AiAnalysisRows {
    private AiAnalysisRows() {}
    public record Write(String id, String actor, String question, String answer,
        String description, String baseMonth, String provisional, String status) {}
    public record Identity(String name, String replacement) {}
}
