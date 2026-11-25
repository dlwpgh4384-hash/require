package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.roles.mapper.RoleMapper;
import com.company.common.roles.dto.RoleContracts.Role;
import com.company.common.roles.service.RoleSensitivePolicy;
import com.company.common.support.service.CurrentBusinessActor;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisProjection {
    private final RoleMapper roles;
    private final CurrentBusinessActor actor;
    private static final Pattern SENSITIVE = Pattern.compile(
        ".*(salary|payroll|allowance|insurance|retire|withholding|netpay|personal.*cost|unitprice|costpermm|totalcost|지급단가|개인원가|급여).*",
        Pattern.CASE_INSENSITIVE);
    public AiAnalysisProjection(RoleMapper roles, CurrentBusinessActor actor) { this.roles = roles; this.actor = actor; }
    public List<Role> assigned() {
        return actor.roles().stream().map(roles::selectRole).filter(Objects::nonNull).map(r -> r.value()).toList();
    }
    public boolean salaryAllowed() { return RoleSensitivePolicy.visible(assigned()); }
    public Answer apply(Answer answer) {
        if (answer.table() == null) return answer;
        var table = answer.table();
        var items = new ArrayList<RoleSensitivePolicy.Item>();
        for (int i = 0; i < table.columns().size(); i++) {
            var column = table.columns().get(i);
            items.add(new RoleSensitivePolicy.Item(column.key(), i, java.math.BigDecimal.ZERO,
                SENSITIVE.matcher(column.key()).matches() || SENSITIVE.matcher(column.label()).matches()));
        }
        var kept = RoleSensitivePolicy.omit(assigned(), items).stream().map(item -> (Integer) item.value()).toList();
        var rows = new ArrayList<List<Object>>();
        for (var row : table.rows()) {
            var projected = new ArrayList<Object>();
            kept.forEach(index -> projected.add(row.get(index)));
            rows.add(Collections.unmodifiableList(projected));
        }
        return new Answer(answer.questionId(), answer.answerStatus(), answer.conclusion(),
            new Table(kept.stream().map(table.columns()::get).toList(), List.copyOf(rows)),
            answer.assumptions(), answer.usedData(), answer.baseMonth(), answer.provisional(), answer.actions(), answer.suggestedQuestions());
    }
}
