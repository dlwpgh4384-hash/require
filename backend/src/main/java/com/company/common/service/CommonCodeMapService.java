package com.company.common.service;

import com.company.common.dto.CodeMapContracts.*;
import com.company.common.dto.UserContracts.History;
import com.company.common.mapper.CodeMapRow;
import com.company.common.mapper.CommonCodeMapMapper;
import com.company.common.mapper.UserMapper;
import java.util.ArrayList;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommonCodeMapService {
    private final CommonCodeMapMapper mapper;
    private final UserMapper management;

    public CommonCodeMapService(CommonCodeMapMapper mapper, UserMapper management) {
        this.mapper = mapper;
        this.management = management;
    }

    public Result getCommonCodeMaps() {
        return new Result(mapper.selectMaps().stream().map(CodeMapRow::value).toList());
    }

    @Transactional
    public Item postCommonCodeMap(Item input) {
        CodeMapRules.validate(input);
        management.lockManagement();
        if (mapper.selectIdentity(input) != null) throw duplicate();
        if (!mapper.selectableCode(input))
            throw new CodeFailure("code", "선택한 그룹에서 사용할 수 있는 코드를 골라 주세요.");
        var row = new CodeMapRow();
        row.excelText = input.excelText();
        row.groupId = input.groupId();
        row.code = input.code();
        try {
            if (mapper.insertMap(row) != 1 || row.mapId == null)
                throw new IllegalStateException("Code map insert failed");
        } catch (DuplicateKeyException failure) {
            throw duplicate();
        }
        history(row, "NEW", null, describe(input));
        return row.value();
    }

    @Transactional
    public DeleteResult postCommonCodeMapDelete(DeleteInput input) {
        CodeMapRules.validate(input);
        management.lockManagement();
        var selected = new ArrayList<CodeMapRow>();
        for (var item : input.items()) {
            var row = mapper.selectIdentity(item);
            if (row == null || !row.value().equals(item))
                throw new CodeFailure("items", "선택한 대응이 바뀌었거나 없습니다. 다시 조회해 주세요.");
            selected.add(row);
        }
        for (var row : selected) {
            if (mapper.deleteMap(row.mapId) != 1)
                throw new IllegalStateException("Code map delete failed");
            history(row, "DEL", describe(row.value()), null);
        }
        return new DeleteResult(selected.stream().map(CodeMapRow::value).toList());
    }

    private CodeFailure duplicate() {
        return new CodeFailure("excelText", "같은 코드 그룹에 이미 등록된 표기입니다.");
    }

    private String describe(Item item) {
        return item.excelText() + " | " + item.groupId() + " | " + item.code();
    }

    private void history(CodeMapRow row, String type, String before, String after) {
        management.insertHistory(new History("TB_SY_CODE_MAP", row.mapId.toString(), type,
            "엑셀 표기 대응", before, after));
    }
}
