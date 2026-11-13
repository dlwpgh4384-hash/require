package com.company.common.codegroups.service;

import com.company.common.codegroups.dto.CodeContracts.*;
import com.company.common.codegroups.mapper.CodeGroupMapper;
import com.company.common.codegroups.mapper.CodeReference;
import com.company.common.codegroups.mapper.CodeRows;
import com.company.common.dto.UserContracts.History;
import com.company.common.mapper.UserMapper;
import com.company.common.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.company.common.codegroups.service.CodeGroupRules.*;

@Service
@Transactional(readOnly = true)
public class CommonCodeService {
    private final CodeGroupMapper mapper;
    private final UserMapper management;
    public CommonCodeService(CodeGroupMapper mapper, UserMapper management) {
        this.mapper = mapper;
        this.management = management;
    }
    public Groups getCommonCodeGroups() {
        return new Groups(mapper.selectGroups().stream().map(CodeRows.Group::value).toList());
    }
    private CodeRows.Group group(String id) {
        groupId(id);
        return mapper.selectGroups().stream().filter(row -> row.groupId.equals(id)).findFirst()
            .orElseThrow(() -> new CodeFailure("groupId", "등록된 코드 그룹이 없습니다."));
    }
    public Codes getCommonCodes(String id) {
        group(id);
        return new Codes(mapper.selectCodes(id).stream().map(CodeRows.Code::value).toList());
    }
    @Transactional
    public Group postCommonCodeGroup(JsonNode body) {
        management.lockManagement();
        object(body, Set.of("groupId", "groupName"));
        String id = groupId(text(body, "groupId", 30, true));
        String name = text(body, "groupName", 50, true);
        if (mapper.selectGroups().stream().anyMatch(row -> row.groupId.equals(id)))
            throw new CodeFailure("groupId", "이미 등록된 그룹 ID입니다.");
        var row = new CodeRows.Group();
        row.groupId = id;
        row.groupName = name;
        row.sysCodeYn = "N";
        row.valCodeYn = "N";
        row.sortOrd = mapper.nextGroupOrder();
        if (row.sortOrd < 1 || row.sortOrd > 99999) throw invalid("sortOrd");
        if (mapper.insertGroup(row) != 1) throw invalid("groupId");
        management.insertHistory(new History("TB_SY_CODE_GRP", id, "NEW", "CODE_GRP_NM", null, name));
        return row.value();
    }
    @Transactional
    public Item postCommonCode(String id, JsonNode body) {
        management.lockManagement();
        var group = group(id);
        business(group);
        var row = parse(id, body, null, mapper.nextCodeOrder(id), "Y".equals(group.valCodeYn));
        if (mapper.selectCode(id, row.code) != null)
            throw new CodeFailure("code", "이미 등록된 코드입니다. 삭제한 코드는 다시 사용할 수 없습니다.");
        if (mapper.insertCode(row) != 1) throw invalid("code");
        management.insertHistory(new History("TB_SY_CODE", key(row), "NEW", "CODE_NM", null, row.codeName));
        return row.value();
    }
    @Transactional
    public Codes putCommonCodes(String id, JsonNode body) {
        management.lockManagement();
        var group = group(id);
        object(body, Set.of("items"));
        var items = body.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) throw invalid("items");
        var seen = new HashSet<String>();
        var before = new ArrayList<CodeRows.Code>();
        var after = new ArrayList<CodeRows.Code>();
        for (var input : items) {
            if (input == null || !input.isObject()) throw invalid("items");
            String code = code(text(input, "code", 20, true), false);
            if (!seen.add(code)) throw invalid("code");
            var old = mapper.selectCode(id, code);
            if (old == null || !"N".equals(old.delYn)) throw new CodeFailure("code", "등록된 코드를 선택해 주세요.");
            // FR-0106 동시 수정: 읽은 시각이 지금과 다르면 덮어쓰지 않는다.
            var read = input.get("modifiedAt");
            if (read != null && !read.isNull()) {
                java.time.Instant readAt;
                try {
                    readAt = java.time.Instant.parse(read.asText());
                } catch (java.time.format.DateTimeParseException failure) {
                    throw invalid("modifiedAt");
                }
                if (!readAt.equals(old.changedAt)) throw CodeFailure.conflict();
            }
            var next = parse(id, input, old, old.sortOrd, "Y".equals(group.valCodeYn));
            if ("Y".equals(group.sysCodeYn)) {
                var changed = fields(next);
                fields(old).forEach((field, value) -> {
                    if (!field.equals("CODE_NM") && !equal(value, changed.get(field)))
                        throw new CodeFailure(publicField(field), "시스템 코드는 이름만 바꿀 수 있습니다.");
                });
            }
            before.add(old);
            after.add(next);
        }
        for (int i = 0; i < after.size(); i++) {
            var old = before.get(i);
            var next = after.get(i);
            var changes = fields(next);
            if (fields(old).entrySet().stream().allMatch(e -> equal(e.getValue(), changes.get(e.getKey())))) continue;
            if (mapper.updateCode(next) != 1) throw invalid("code");
            fields(old).forEach((field, value) -> {
                if (!equal(value, changes.get(field))) management.insertHistory(new History("TB_SY_CODE", key(old),
                    "UPD", field, string(value), string(changes.get(field))));
            });
        }
        return getCommonCodes(id);
    }
    @Transactional
    public Deleted postCommonCodeDelete(String id, String code) {
        management.lockManagement();
        groupId(id);
        code(code, false);
        business(group(id));
        var references = CodeReference.forGroup(id);
        var tables = new TreeSet<String>();
        tables.add("TB_SY_CODE");
        tables.add("TB_SY_CHG_HIST");
        tables.add("TB_SY_CODE_MAP");
        references.forEach(ref -> tables.add(ref.getTable()));
        var present = new HashSet<String>();
        for (String table : tables) {
            if (mapper.relationExists(table)) {
                mapper.lockRelation(table);
                present.add(table);
            }
        }
        var row = mapper.selectCode(id, code);
        if (row == null || !"N".equals(row.delYn)) throw invalid("code");
        boolean used = mapper.additionalReferenceExists(row)
            || (present.contains("TB_SY_CODE_MAP") && mapper.mappedReferenceExists(row));
        for (var ref : references) {
            if ((present.contains(ref.getTable()) && mapper.referenceExists(ref, row))
                || mapper.historicalReferenceExists(ref, row)) used = true;
        }
        if (used) throw new CodeFailure("code", "사용된 코드라 삭제할 수 없습니다. 사용을 끄세요.");
        if (mapper.deleteCode(id, code) != 1) throw invalid("code");
        management.insertHistory(new History("TB_SY_CODE", key(row), "DEL", "DEL_YN", "N", "Y"));
        return new Deleted(id, code, "Y");
    }
    private void business(CodeRows.Group group) {
        if ("Y".equals(group.sysCodeYn)) throw new CodeFailure("code", "시스템 코드는 추가하거나 삭제할 수 없습니다.");
    }
    private String key(CodeRows.Code row) { return row.groupId + ":" + row.code; }
    private String string(Object value) { return value == null ? null : value.toString(); }
    private boolean equal(Object a, Object b) {
        if (a instanceof java.math.BigDecimal x && b instanceof java.math.BigDecimal y) return x.compareTo(y) == 0;
        return Objects.equals(a, b);
    }
    private String publicField(String field) {
        return switch (field) {
            case "SORT_ORD" -> "sortOrd";
            case "USE_YN" -> "useYn";
            case "CODE_VAL" -> "codeVal";
            case "UNIT_NM" -> "unitNm";
            default -> "code";
        };
    }
    private Map<String, Object> fields(CodeRows.Code row) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("CODE_NM", row.codeName);
        fields.put("SORT_ORD", row.sortOrd);
        fields.put("USE_YN", row.useYn);
        fields.put("CODE_VAL", row.codeVal);
        fields.put("UNIT_NM", row.unitNm);
        fields.put("ADD_VAL1", row.addVal1);
        fields.put("ADD_VAL2", row.addVal2);
        fields.put("ADD_VAL3", row.addVal3);
        return fields;
    }
}
