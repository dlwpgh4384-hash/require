package com.company.common.service;

import com.company.common.dto.CalculationRuleContracts.TextResolution;
import com.company.common.mapper.CalculationRuleMapper;
import com.company.common.mapper.CodeTextRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CodeTextResolver {
    private final CalculationRuleMapper mapper;

    public CodeTextResolver(CalculationRuleMapper mapper) { this.mapper = mapper; }

    public TextResolution resolve(String groupId, String text) {
        if (groupId == null || !groupId.matches("[A-Z][A-Z0-9_]{0,29}")) {
            throw CodeFailure.invalid("groupId", "코드 그룹을 확인해 주세요.");
        }
        if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > 100) {
            throw CodeFailure.invalid("excelText", "엑셀 표기는 1~100자로 입력해 주세요.");
        }
        var normal = mapper.selectNormalText(groupId, text);
        for (var row : normal) {
            if (text.equals(row.code)) return result(row, null);
        }
        if (normal.size() == 1) return result(normal.get(0), null);
        if (normal.size() > 1) throw CodeFailure.invalid("excelText", "이름이 같은 코드가 있습니다. 코드로 입력해 주세요.");
        var mapped = mapper.selectMappedText(groupId, text);
        if (mapped == null) throw CodeFailure.invalid("excelText", "등록되지 않은 표기 ‘" + text + "’입니다.");
        return result(mapped, "‘" + text + "’을 ‘" + mapped.codeName + "’으로 바꿈");
    }

    private TextResolution result(CodeTextRow row, String warning) {
        return new TextResolution(row.groupId, row.code, row.codeName, warning);
    }
}
