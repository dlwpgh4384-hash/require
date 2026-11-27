package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.roles.mapper.RoleMapper;
import com.company.common.roles.dto.RoleContracts.Role;
import com.company.common.roles.service.RoleSensitivePolicy;
import com.company.common.support.service.CurrentBusinessActor;
import java.util.*;
import org.springframework.stereotype.Component;

/**
 * 민감 칸 빼기는 여기 한 곳(RoleSensitivePolicy). 응답·저장·엑셀·외부 전송이 모두 이 결과를 쓴다.
 * 민감 칸은 표 정의에서 Column.sensitive로 표시한다(이름 글자로 짐작하지 않음).
 */
@Component
public class AiAnalysisProjection {
    static final String HIDDEN_NOTICE = "개인별 원가를 볼 권한이 없어 %s은 뺐습니다.";
    private final RoleMapper roles;
    private final CurrentBusinessActor actor;
    public AiAnalysisProjection(RoleMapper roles, CurrentBusinessActor actor) { this.roles = roles; this.actor = actor; }
    public List<Role> assigned() {
        var ids = actor.roles();
        if (ids == null) return List.of();
        return ids.stream().map(roles::selectRole).filter(Objects::nonNull).map(r -> r.value()).toList();
    }
    /** 요청 중이면 보안 필터가 DB에서 읽은 민감 체크 하나(명세 ⑨-1), 요청 밖(시험·배치)은 역할 줄의 민감 체크. */
    public boolean salaryAllowed() {
        return com.company.common.support.auth.RequestAccess.current() != null
            ? com.company.common.support.auth.RequestAccess.sensitiveVisible() : RoleSensitivePolicy.visible(assigned());
    }
    public Answer apply(Answer answer) {
        var scoped = dataScope(answer);
        if (scoped.tables() == null || scoped.tables().isEmpty()) return scoped;
        var dropped = new LinkedHashSet<String>();
        var projected = new ArrayList<Table>();
        for (var table : scoped.tables()) {
            var items = new ArrayList<RoleSensitivePolicy.Item>();
            for (int i = 0; i < table.columns().size(); i++) {
                var column = table.columns().get(i);
                items.add(new RoleSensitivePolicy.Item(column.key(), i, java.math.BigDecimal.ZERO, column.sensitive()));
            }
            boolean show = salaryAllowed();
            var kept = items.stream().filter(item -> show || !item.sensitive()).map(item -> (Integer) item.value()).toList();
            for (int i = 0; i < table.columns().size(); i++) if (!kept.contains(i)) dropped.add(table.columns().get(i).label());
            projected.add(new Table(table.title(), kept.stream().map(table.columns()::get).toList(),
                pick(table.rows(), kept), pick(table.tones(), kept), table.totalRow() == null ? null : pickRow(table.totalRow(), kept),
                table.hidden()));
        }
        var notes = new ArrayList<>(scoped.assumptions() == null ? List.<String>of() : scoped.assumptions());
        if (!dropped.isEmpty()) notes.add(0, String.format(HIDDEN_NOTICE, String.join("·", dropped)));
        return scoped.projected(List.copyOf(projected), List.copyOf(notes));
    }
    /**
     * 「그 화면 권한이 있는 역할만」 답하는 함수(명세 UI-26 함수 표: F-01·F-05·F-07, P-01은 F-01과 같음, P-03은 F-07과 같음).
     * 나머지 함수는 원래 화면 서비스가 데이터 범위(⑧)로 걸러 준 숫자만 쓴다.
     */
    static final Map<String, String> SCREEN_GATED = Map.of("F-01", "UI-08", "P-01", "UI-08", "F-05", "UI-18",
        "F-07", "UI-21", "P-03", "UI-21");
    /** 이 함수의 답을 볼 수 있는지(화면 조회 권한). 요청 밖(시험·배치)은 막지 않는다. */
    public boolean functionAllowed(String function) {
        String screen = function == null ? null : SCREEN_GATED.get(function);
        return screen == null || com.company.common.support.auth.RequestAccess.screenReadable(screen);
    }
    /**
     * 데이터 범위(명세 ⑧-2 AI 답): 숫자는 원래 화면 서비스가 범위로 거른 값이라 여기서는 다시 세지 않는다.
     * 볼 권한이 없는 화면으로 가는 [○○ 열기] 단추는 뺀다(명세 ⑦-5).
     */
    public Answer dataScope(Answer answer) {
        if (answer.actions() == null || answer.actions().isEmpty()) return answer;
        var actions = answer.actions().stream()
            .filter(a -> a.targetScreenId() == null || com.company.common.support.auth.RequestAccess.screenReadable(a.targetScreenId()))
            .toList();
        if (actions.size() == answer.actions().size()) return answer;
        return new Answer(answer.questionId(), answer.answerStatus(), answer.conclusion(), answer.forecast(), answer.provisional(),
            answer.provisionalMonth(), answer.tables(), answer.chart(), answer.assumptions(), answer.usedData(), answer.baseMonth(),
            actions, answer.excel(), answer.suggestedQuestions(), answer.suggestionLabels());
    }
    private static <T> List<List<T>> pick(List<List<T>> rows, List<Integer> kept) {
        if (rows == null) return null;
        return rows.stream().map(row -> pickRow(row, kept)).toList();
    }
    private static <T> List<T> pickRow(List<T> row, List<Integer> kept) {
        var result = new ArrayList<T>();
        for (int index : kept) result.add(index < row.size() ? row.get(index) : null);
        return Collections.unmodifiableList(result);
    }
}
