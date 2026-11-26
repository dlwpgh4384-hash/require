package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.support.service.CodeFailure;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisProgress {
    public static final class Execution {
        public final CompletableFuture<Answer> answer = new CompletableFuture<>();
        private volatile String stage = "데이터 찾는 중";
        public void stage(String value) { stage = value; }
    }
    public record Claim(Execution execution, boolean owner) {}
    private final Map<String, LinkedHashMap<String, Execution>> actors = new LinkedHashMap<>(16, .75f, true);
    public synchronized Claim claim(String actor, String id) {
        if (!actors.containsKey(actor) && actors.size() >= 128) {
            var removable = actors.entrySet().stream().filter(e -> e.getValue().values().stream().allMatch(x -> x.answer.isDone())).findFirst();
            if (removable.isEmpty()) throw new CodeFailure(429, "requestId", "진행 중인 질문이 많습니다.");
            actors.remove(removable.get().getKey());
        }
        var requests = actors.computeIfAbsent(actor, key -> new LinkedHashMap<>());
        var existing = requests.get(id);
        if (existing != null && !existing.answer.isDone()) return new Claim(existing, false);
        while (requests.size() >= 64) {
            var removable = requests.entrySet().stream().filter(e -> e.getValue().answer.isDone()).findFirst();
            if (removable.isEmpty()) throw new CodeFailure(429, "requestId", "진행 중인 질문이 많습니다.");
            requests.remove(removable.get().getKey());
        }
        while (actors.size() > 128) {
            var removable = actors.entrySet().stream().filter(e -> !e.getKey().equals(actor)
                && e.getValue().values().stream().allMatch(x -> x.answer.isDone())).findFirst();
            if (removable.isEmpty()) break;
            actors.remove(removable.get().getKey());
        }
        var execution = new Execution(); requests.put(id, execution);
        return new Claim(execution, true);
    }
    public synchronized Progress read(String actor, String id) {
        var requests = actors.get(actor);
        var value = requests == null ? null : requests.get(id);
        if (value == null) throw new CodeFailure(404, "requestId", "질문을 찾을 수 없습니다.");
        return new Progress(id, value.stage, value.answer.isDone());
    }
    public synchronized void finish(String actor, String id, Execution execution) {
        var requests = actors.get(actor);
        if (requests == null || requests.get(id) != execution) return;
        var completed = new Execution();
        completed.stage(execution.stage);
        completed.answer.complete(null);
        requests.put(id, completed);
    }
}
