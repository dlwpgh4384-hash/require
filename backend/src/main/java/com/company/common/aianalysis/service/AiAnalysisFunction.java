package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import java.util.List;
import java.util.Set;

public interface AiAnalysisFunction {
    Set<String> functions();
    Result read(AiAnalysisInterpreter.Query query);
    record Result(Table table, Period period, boolean provisional, String targetScreen,
        List<String> assumptions) {}
}
