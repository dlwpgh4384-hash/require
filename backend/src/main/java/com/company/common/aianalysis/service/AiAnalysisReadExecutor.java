package com.company.common.aianalysis.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiAnalysisReadExecutor {
    private final List<AiAnalysisFunction> functions;
    private final TransactionTemplate read;
    private final AiAnalysisPseudonymizer pseudonyms;
    private final AiAnalysisExternalGateway external;
    private final AiAnalysisProjection projection;
    public AiAnalysisReadExecutor(List<AiAnalysisFunction> functions, PlatformTransactionManager manager,
        AiAnalysisPseudonymizer pseudonyms, AiAnalysisExternalGateway external, AiAnalysisProjection projection) {
        this.functions = List.copyOf(functions);
        this.pseudonyms = pseudonyms; this.external = external; this.projection = projection;
        this.read = new TransactionTemplate(manager);
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var catalog = AiAnalysisCatalog.FUNCTIONS.stream().map(AiAnalysisCatalog.Entry::id).toList();
        var names = new java.util.HashSet<String>();
        for (var function : functions) for (var name : function.functions()) {
            if (!names.add(name)) throw new IllegalStateException("Duplicate analysis function " + name);
            if (!catalog.contains(name)) throw new IllegalStateException("Analysis function is not in the catalog " + name);
        }
    }
    /** 등록된 조회 함수 이름(시험이 명세 목록과 같은지 본다). */
    public java.util.Set<String> registered() {
        var names = new java.util.TreeSet<String>();
        functions.forEach(f -> names.addAll(f.functions()));
        return names;
    }
    public AiAnalysisFunction.Result read(AiAnalysisInterpreter.Query query, AiAnalysisProgress.Execution execution) {
        return read.execute(status -> {
            var function = functions.stream().filter(f -> f.functions().contains(query.function())).findFirst();
            if (function.isEmpty()) return null;
            execution.stage("계산 중");
            return function.get().read(query);
        });
    }
    public void external(String question, com.company.common.aianalysis.dto.AiAnalysisContracts.Answer answer) {
        pseudonyms.send(external, question, projection.apply(answer));
    }
}
