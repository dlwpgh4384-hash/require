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
    public boolean salaryAllowed() { return RoleSensitivePolicy.visible(assigned()); }
    public Answer apply(Answer answer) {
        var scoped = dataScope(answer);
        if (scoped.tables() == null || scoped.tables().isEmpty()) return scoped;
        var assigned = assigned();
        var dropped = new LinkedHashSet<String>();
        var projected = new ArrayList<Table>();
        for (var table : scoped.tables()) {
            var items = new ArrayList<RoleSensitivePolicy.Item>();
            for (int i = 0; i < table.columns().size(); i++) {
                var column = table.columns().get(i);
                items.add(new RoleSensitivePolicy.Item(column.key(), i, java.math.BigDecimal.ZERO, column.sensitive()));
            }
            var kept = RoleSensitivePolicy.omit(assigned, items).stream().map(item -> (Integer) item.value()).toList();
            for (int i = 0; i < table.columns().size(); i++) if (!kept.contains(i)) dropped.add(table.columns().get(i).label());
            projected.add(new Table(table.title(), kept.stream().map(table.columns()::get).toList(),
                pick(table.rows(), kept), pick(table.tones(), kept), table.totalRow() == null ? null : pickRow(table.totalRow(), kept),
                table.hidden()));
        }
        var notes = new ArrayList<>(scoped.assumptions() == null ? List.<String>of() : scoped.assumptions());
        if (!dropped.isEmpty()) notes.add(0, String.format(HIDDEN_NOTICE, String.join("·", dropped)));
        return scoped.projected(List.copyOf(projected), List.copyOf(notes));
    }
    /** 데이터 범위(자기 파트·맡은 프로젝트) 거르기 자리. 로그인(UI-22) 작업에서 채운다. 지금은 전체. */
    public Answer dataScope(Answer answer) { return answer; }
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
