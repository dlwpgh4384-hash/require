package com.company.common.roles.service;

import com.company.common.users.service.CommonUserService;
import com.company.common.roles.mapper.RoleMapper;
import com.company.common.roles.mapper.RoleRows;

import com.company.common.users.dto.UserContracts.ChangeHistory;
import com.company.common.users.dto.UserContracts.History;
import com.company.common.users.mapper.UserMapper;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.company.common.roles.dto.RoleContracts.*;

@Service
@Transactional(readOnly = true)
public class CommonRoleService {
    private final RoleMapper mapper;
    private final UserMapper management;

    public CommonRoleService(RoleMapper mapper, UserMapper management) {
        this.mapper = mapper;
        this.management = management;
    }
    private RoleRows.Role require(String id) {
        if (id == null || id.isBlank() || id.length() > 20)
            throw RoleFailure.invalid("roleId", "역할을 확인해 주세요.");
        var role = mapper.selectRole(id);
        if (role == null) throw RoleFailure.missing();
        return role;
    }
    private void scope(String code) {
        if (code == null || mapper.selectDataScopes().stream().noneMatch(row -> code.equals(row.code)))
            throw RoleFailure.invalid("dataScopeCd", "데이터 범위를 확인해 주세요.");
    }
    public RoleList getCommonRoles() {
        return new RoleList(mapper.selectRoles().stream().map(RoleRows.Role::value).toList());
    }
    public Settings getCommonRole(String id) {
        var role = require(id).value();
        return new Settings(role, mapper.selectMenus(id).stream().map(RoleRows.Menu::value).toList(),
            mapper.selectDataScopes().stream().map(RoleRows.ScopeCode::value).toList());
    }
    private List<RoleRows.Scope> removable(RoleRows.Role old, String next) {
        if (old.dataScopeCd.equals(next) || !List.of("PART", "PROJ").contains(old.dataScopeCd))
            return java.util.Collections.emptyList();
        return mapper.selectRemovableScopes(old.roleId, old.dataScopeCd);
    }
    private Impact impact(RoleRows.Role old, String next, List<RoleRows.Scope> rows) {
        var ids = rows.stream().map(row -> row.userId).distinct().sorted().toList();
        var pm = "PROJ".equals(old.dataScopeCd) && !old.dataScopeCd.equals(next)
            ? mapper.selectPreservedPmUsers(old.roleId) : java.util.Collections.<String>emptyList();
        return new Impact("PART".equals(old.dataScopeCd) ? ids.size() : 0,
            "PROJ".equals(old.dataScopeCd) ? ids.size() : 0, ids, pm);
    }
    public Impact getCommonRoleChangeImpact(String id, String dataScopeCd) {
        var old = require(id);
        scope(dataScopeCd);
        return impact(old, dataScopeCd, removable(old, dataScopeCd));
    }
    @Transactional
    public Settings postCommonRoleCopy(Copy input) {
        management.lockManagement();
        RoleRules.name(input.roleName());
        scope(input.dataScopeCd());
        // 역할 추가(시안): 원본 없이 모든 메뉴 「권한 없음」, 민감 항목 끔. 역할 복사: 원본의 권한 표·민감 항목을 그대로.
        boolean blank = input.sourceRoleId() == null;
        var source = blank ? blankSource() : require(input.sourceRoleId());
        if (!blank && !"Y".equals(source.useYn)) throw RoleFailure.invalid("sourceRoleId", "사용 중인 원본 역할을 선택해 주세요.");
        if (mapper.nameExists(input.roleName())) throw duplicateName();
        BigInteger next = mapper.selectIssuedNumber().add(BigInteger.ONE);
        String suffix = next.toString();
        String id = "R-" + "0".repeat(Math.max(0, 3 - suffix.length())) + suffix;
        int sort = mapper.selectNextSortOrder();
        if (id.length() > 20 || sort > 99999)
            throw RoleFailure.invalid("roleName", "역할을 더 발급할 수 없습니다.");
        try {
            mapper.insertRole(id, input, source, sort);
        } catch (DuplicateKeyException failure) {
            throw duplicateName();
        }
        if (blank) mapper.insertBlankMenus(id);
        else mapper.copyMenus(id, source.roleId);
        mapper.insertHistory(new History("TB_SY_ROLE", id, "NEW", "ROLE_NM", null, input.roleName()));
        return getCommonRole(id);
    }
    private static RoleRows.Role blankSource() {
        var role = new RoleRows.Role();
        role.snstvViewYn = "N";
        role.useYn = "Y";
        return role;
    }
    private static final java.util.Map<String, String> ACTIONS = java.util.Map.of("INQ_YN", "조회", "REG_YN", "등록",
        "UPD_YN", "수정", "DEL_YN", "삭제", "UPLD_YN", "업로드", "DNLD_YN", "다운로드", "CNFM_YN", "확정");
    /** 시안 「권한 변경 이력」: 바뀐 항목마다 1줄(데이터 범위·민감 항목·메뉴별 동작). */
    public HistoryList getCommonRoleChangeHistory(String id) {
        require(id);
        var menus = new java.util.HashMap<String, String>();
        mapper.selectMenus(id).forEach(menu -> menus.put(menu.menuId, menu.menuName));
        var scopes = new java.util.HashMap<String, String>();
        mapper.selectDataScopes().forEach(scope -> scopes.put(scope.code, scope.name));
        return new HistoryList(mapper.selectChanges(id).stream().map(row -> {
            String item = row.item, before = row.before, after = row.after;
            if ("TB_SY_ROLE_MENU_AUTH".equals(row.table)) {
                String menuId = row.key.substring(row.key.indexOf(':') + 1);
                item = menus.getOrDefault(menuId, menuId) + " · " + ACTIONS.getOrDefault(row.item, row.item);
                before = flag(before, "있음", "없음");
                after = flag(after, "있음", "없음");
            } else if ("DATA_SCOPE_CD".equals(row.item)) {
                item = "데이터 범위";
                before = before == null ? null : scopes.getOrDefault(before, before);
                after = after == null ? null : scopes.getOrDefault(after, after);
            } else if ("SNSTV_VIEW_YN".equals(row.item)) {
                item = "민감 항목";
                before = flag(before, "보기", "안 보기");
                after = flag(after, "보기", "안 보기");
            } else if ("ROLE_NM".equals(row.item)) {
                item = "역할 이름";
            }
            return new ChangeHistory(CommonUserService.time(row.changedAt), item, before, after, row.changedBy);
        }).toList());
    }
    private static String flag(String value, String yes, String no) {
        return value == null ? null : "Y".equals(value) ? yes : "N".equals(value) ? no : value;
    }
    private RoleFailure duplicateName() {
        return RoleFailure.invalid("roleName", "이미 쓰고 있는 역할 이름입니다.");
    }
    @Transactional
    public StopResult postCommonRoleStop(String id) {
        management.lockManagement();
        var role = require(id);
        if (RoleRules.basic(id)) throw RoleFailure.invalid("roleId", "기본 역할은 사용 중지할 수 없습니다.");
        long count = mapper.countAssignments(id);
        if (count > 0) throw RoleFailure.invalid("roleId",
            count + "명이 이 역할을 쓰고 있습니다. 시스템 › 사용자에서 먼저 빼 주세요.");
        if (!"N".equals(role.useYn)) mapper.stopRole(id);
        return new StopResult(id, "N");
    }
    @Transactional
    public Settings putCommonRole(String id, Update input) {
        management.lockManagement();
        var old = require(id);
        scope(input.dataScopeCd());
        RoleRules.flag(input.snstvViewYn(), "snstvViewYn");
        var current = mapper.selectMenus(id).stream().map(RoleRows.Menu::value).toList();
        RoleRules.matrix(id, current, input.menuAuths());
        var removals = removable(old, input.dataScopeCd());
        var affected = new HashSet<>(removals.stream().map(row -> row.userId).toList());
        if (input.confirmedAffectedUserIds() == null
            || !affected.equals(new HashSet<>(input.confirmedAffectedUserIds())))
            throw RoleFailure.invalid("confirmedAffectedUserIds", "영향 사용자를 다시 조회하고 확인해 주세요.");

        mapper.updateRole(id, input);
        record(new History("TB_SY_ROLE", id, "UPD", "DATA_SCOPE_CD", old.dataScopeCd, input.dataScopeCd()));
        record(new History("TB_SY_ROLE", id, "UPD", "SNSTV_VIEW_YN", old.snstvViewYn, input.snstvViewYn()));
        var columns = List.of("INQ_YN", "REG_YN", "UPD_YN", "DEL_YN", "UPLD_YN", "DNLD_YN", "CNFM_YN");
        for (var menu : input.menuAuths()) {
            var before = current.stream().filter(row -> row.menuId().equals(menu.menuId())).findFirst().orElseThrow().input();
            if (!before.equals(menu)) {
                mapper.updateMenu(id, menu);
                for (int i = 0; i < columns.size(); i++)
                    record(new History("TB_SY_ROLE_MENU_AUTH", id + ":" + menu.menuId(), "UPD",
                        columns.get(i), before.flags().get(i), menu.flags().get(i)));
            }
        }
        for (var row : removals) {
            if (mapper.deleteScope(row) != 1) throw RoleFailure.invalid("confirmedAffectedUserIds", "영향 사용자를 다시 확인해 주세요.");
            mapper.insertHistory(new History("TB_SY_USER_SCOPE", row.userId + ":" + row.scopeSn,
                "DEL", row.scopeTypeCd, "PART".equals(row.scopeTypeCd) ? row.partId : row.projId, null));
        }
        mapper.insertRoleAccess(id);
        return getCommonRole(id);
    }
    private void record(History history) {
        if (!Objects.equals(history.before(), history.after())) mapper.insertHistory(history);
    }
}
