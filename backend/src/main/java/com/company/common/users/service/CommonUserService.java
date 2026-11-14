package com.company.common.users.service;

import com.company.common.users.mapper.UserMapper;
import com.company.common.users.mapper.UserRows;
import com.company.common.users.dto.UserRequests;

import com.company.common.users.mapper.UserOptionsMapper;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.company.common.users.dto.UserContracts.*;

@Service
public class CommonUserService {
    static final String HQ_LABEL = "본사(직접 영업)";
    static final String NONE = "–";
    static final Map<String, String> STATE_NAMES = Map.of("USE", "사용", "LOCK", "잠김", "STOP", "중지");
    private final UserMapper mapper;
    private final UserOptionsMapper options;
    private final TemporaryUserPassword passwords;

    public CommonUserService(UserMapper mapper, UserOptionsMapper options, TemporaryUserPassword passwords) {
        this.mapper = mapper; this.options = options; this.passwords = passwords;
    }
    public static String time(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
    private UserRows.Account require(String id) {
        UserRequests.id(id);
        var row = mapper.selectAccount(id);
        if (row == null) throw UserFailure.invalid("userId", "사용자를 찾을 수 없습니다.");
        return row;
    }
    private UserRows.Account current(String id, Instant token) {
        var row = require(id);
        if (!Objects.equals(row.modDttm, token)) throw UserFailure.conflict();
        return row;
    }
    private void affected(int count) { if (count != 1) throw UserFailure.conflict(); }

    public Account getCommonUser(String id) {
        var row = require(id);
        var projects = projects(id);
        return new Account(row.userId, row.name, row.email, mapper.selectRoles(id), mapper.hasHeadquarters(id),
            projects.stream().map(Project::projectId).toList(), row.userStatCd, time(row.modDttm),
            mapper.selectRoleNames(id), mapper.selectParts(id), projects, mapper.selectLeaders(id),
            "Y".equals(row.tempPwdYn), time(row.lastAccessAt));
    }
    private ListItem item(UserRows.Account row) {
        return new ListItem(row.userId, row.name, mapper.selectRoles(row.userId), row.userStatCd, time(row.modDttm),
            mapper.selectRoleNames(row.userId), mapper.selectParts(row.userId), time(row.lastAccessAt));
    }
    public Page<ListItem> getCommonUsers(Filter filter) {
        var items = mapper.selectUsers(filter).stream().map(this::item).toList();
        return new Page<>(items, filter.page(), filter.pageSize(), mapper.countUsers(filter));
    }
    /** SFR-011 엑셀 다운로드: 같은 조건의 전체 결과. 다운로드는 접근 기록(DNLD)에 남긴다. */
    @Transactional
    public List<ListItem> getCommonUsersForDownload(Filter filter, String downloadedBy) {
        var items = mapper.selectAllUsers(filter).stream().map(this::item).toList();
        mapper.insertAccess(downloadedBy, "DNLD", "사용자 목록 " + items.size() + "건");
        return items;
    }
    public Page<AccessLog> getCommonUserAccessLogs(String id, Filter filter) {
        require(id);
        var items = mapper.selectLogs(id, filter).stream()
            .map(row -> new AccessLog(time(row.occurredAt), row.action, row.target)).toList();
        return new Page<>(items, filter.page(), filter.pageSize(), mapper.countLogs(id));
    }
    public List<ChangeHistory> getCommonUserChangeHistory(String id) {
        require(id);
        return mapper.selectChanges(id).stream()
            .map(row -> new ChangeHistory(time(row.changedAt), row.item, row.before, row.after, row.changedBy)).toList();
    }
    private List<Project> projects(String id) {
        return mapper.selectProjects(id).stream().map(row -> new Project(row.projectId, row.projectName)).toList();
    }
    /** 담당 프로젝트를 가질 수 있는 역할: 데이터 범위 「담당 프로젝트」 역할 또는 PM(2026-11-12 사용자 결정). */
    private boolean projectEligible(List<String> roles) {
        return roles.contains("PM")
            || options.selectRoles().stream().anyMatch(row -> roles.contains(row.getRoleCd()) && row.isAssignedProjectScope());
    }
    /** 본사(직접 영업)를 고를 수 있는 역할: 데이터 범위 「담당 파트」 역할(2026-11-12 사용자 결정). */
    private boolean partEligible(List<String> roles) {
        return options.selectRoles().stream().anyMatch(row -> roles.contains(row.getRoleCd()) && row.isAssignedPartScope());
    }
    private String validateAssignments(List<String> roles, boolean headquarters, List<String> projects) {
        if (roles == null || roles.isEmpty() || new HashSet<>(roles).size() != roles.size())
            throw UserFailure.invalid("roleCds", "역할을 1개 이상 골라 주세요.");
        var allowed = options.selectRoles().stream().map(row -> row.getRoleCd()).toList();
        if (!allowed.containsAll(roles)) throw UserFailure.invalid("roleCds", "역할을 확인해 주세요.");
        if (!projects.isEmpty() && !projectEligible(roles))
            throw UserFailure.invalid("projectIds", "담당 프로젝트는 PM이나 담당 프로젝트 역할이 있어야 고를 수 있습니다.");
        if (new HashSet<>(projects).size() != projects.size()
            || !options.selectProjects().stream().map(row -> row.getProjectId()).toList().containsAll(projects))
            throw UserFailure.invalid("projectIds", "프로젝트를 확인해 주세요.");
        if (!headquarters) return null;
        if (!partEligible(roles))
            throw UserFailure.invalid("headquarters", "본사(직접 영업)는 담당 파트 역할(영업 파트장 등)이 있어야 고를 수 있습니다.");
        var parts = mapper.selectHeadquarters();
        if (parts.size() != 1) throw UserFailure.invalid("headquarters", "본사 구성을 확인해 주세요.");
        return parts.get(0);
    }
    private String leaderReason(String id) {
        var parts = mapper.selectLeaders(id);
        return parts.isEmpty() ? null : String.join(", ", parts) + " 파트장입니다. 영업 파트 탭에서 먼저 파트장을 바꾸세요.";
    }
    private String protectedReason(UserRows.Account row, List<String> roles, long activeAdmins) {
        String leader = leaderReason(row.userId);
        if (leader != null) return leader;
        if ("USE".equals(row.userStatCd) && roles.contains("ADMIN") && activeAdmins <= 1)
            return "관리자가 0명이 되므로 중지할 수 없습니다. 다른 관리자를 먼저 지정하세요.";
        if ("STOP".equals(row.userStatCd)) return "이미 사용 중지된 계정입니다.";
        return null;
    }
    /** 역할이 바뀌어 담당 프로젝트를 가질 수 없게 되면(둘 다 없어지면) 담당 프로젝트 줄을 지운다. */
    private List<Project> removals(String id, List<String> oldRoles, List<String> nextRoles) {
        return projectEligible(oldRoles) && !projectEligible(nextRoles) ? projects(id) : List.of();
    }
    private void confirm(List<Project> projects, List<String> confirmed) {
        if (!new HashSet<>(projects.stream().map(Project::projectId).toList()).equals(new HashSet<>(confirmed)))
            throw UserFailure.invalid("confirmedProjectIds", "영향 프로젝트를 다시 조회하고 확인해 주세요.");
    }
    private static String clip(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 499) + "…";
    }
    /** 변경 이력: 바뀐 항목마다 1줄(시안 「사용자 변경 이력」). */
    private void history(String key, String type, String item, String before, String after) {
        mapper.insertHistory(new History("TB_SY_USER", key, type, item, clip(before), clip(after)));
    }
    private static String names(List<String> values) { return values.isEmpty() ? NONE : String.join(", ", values); }
    private static String projectNames(List<Project> values) {
        return names(values.stream().map(Project::projectName).toList());
    }
    private void removeProject(String id, String project) { mapper.deleteProject(id, project); }
    private List<Project> transfer(String id, List<String> roles, List<String> projects) {
        var transferred = new LinkedHashMap<String, Project>();
        if (roles.contains("PM") && !projects.isEmpty()) {
            var owners = mapper.selectPmOwners(id, projects);
            var before = new LinkedHashMap<String, List<Project>>();
            for (var owner : owners) {
                if (!before.containsKey(owner.userId)) {
                    var old = require(owner.userId);
                    affected(mapper.touch(old.userId, old.modDttm));
                    mapper.insertAccess(old.userId, "AUTH_CHG", "PM 담당범위 이관: " + old.userId);
                    before.put(owner.userId, projects(owner.userId));
                }
                removeProject(owner.userId, owner.projectId);
                transferred.put(owner.projectId, new Project(owner.projectId, owner.projectName));
            }
            before.forEach((owner, list) ->
                history(owner, "UPD", "담당 프로젝트", projectNames(list), projectNames(projects(owner))));
        }
        return List.copyOf(transferred.values());
    }
    private void assignments(String id, List<String> roles, String hq, List<String> nextProjects) {
        var oldRoles = mapper.selectRoles(id);
        if (!new HashSet<>(oldRoles).equals(new HashSet<>(roles))) {
            mapper.deleteRoles(id);
            for (String role : roles) mapper.insertRole(id, role);
        }
        var oldProjects = projects(id).stream().map(Project::projectId).toList();
        for (String project : oldProjects) if (!nextProjects.contains(project)) removeProject(id, project);
        for (String project : nextProjects) if (!oldProjects.contains(project)) mapper.insertScope(id, "PROJ", project);
        boolean oldHq = mapper.hasHeadquarters(id);
        if (oldHq != (hq != null)) {
            mapper.deleteHeadquarters(id);
            if (hq != null) mapper.insertScope(id, "PART", hq);
        }
    }
    @Transactional
    public Save putCommonUsers(Create input) {
        UserRequests.id(input.userId());
        mapper.lockManagement();
        String hq = validateAssignments(input.roleCds(), input.headquarters(), input.projectIds());
        if (mapper.selectAccount(input.userId()) != null) throw UserFailure.invalid("userId", "이미 쓰고 있는 아이디입니다.");
        mapper.insertUser(input, passwords.hashAndDeliver(input.email()));
        var transferred = transfer(input.userId(), input.roleCds(), input.projectIds());
        assignments(input.userId(), input.roleCds(), hq, input.projectIds());
        history(input.userId(), "NEW", "등록", NONE, "신규 등록");
        mapper.insertAccess(input.userId(), "USER_REG", "사용자 등록: " + input.userId());
        return new Save(getCommonUser(input.userId()), transferred);
    }
    @Transactional
    public Save putCommonUser(String id, Update input) {
        mapper.lockManagement();
        var row = current(id, input.modDttm());
        var oldRoles = mapper.selectRoles(id);
        if (oldRoles.contains("SALES_LDR") && !input.roleCds().contains("SALES_LDR")) {
            String reason = leaderReason(id);
            if (reason != null) throw UserFailure.invalid("roleCds", reason);
        }
        if ("USE".equals(row.userStatCd) && oldRoles.contains("ADMIN") && !input.roleCds().contains("ADMIN")
            && mapper.countActiveAdmins() <= 1)
            throw UserFailure.invalid("roleCds", "관리자가 0명이 되므로 이 계정의 시스템 관리자 역할을 뺄 수 없습니다.");
        String hq = validateAssignments(input.roleCds(), input.headquarters(), input.projectIds());
        var removed = removals(id, oldRoles, input.roleCds());
        confirm(removed, input.confirmedProjectIds());
        var removedIds = removed.stream().map(Project::projectId).toList();
        var nextProjects = input.projectIds().stream().filter(project -> !removedIds.contains(project)).toList();
        var before = getCommonUser(id);
        affected(mapper.touch(id, input.modDttm()));
        if (!Objects.equals(row.name, input.name()) || !Objects.equals(row.email, input.email()))
            mapper.updateProfile(id, input.name(), input.email());
        var transferred = transfer(id, input.roleCds(), nextProjects);
        assignments(id, input.roleCds(), hq, nextProjects);
        var after = getCommonUser(id);
        record(id, "이름", before.name(), after.name());
        record(id, "이메일", before.email(), after.email());
        record(id, "역할", names(before.roleNames()), names(after.roleNames()));
        record(id, "고른 본사", before.headquarters() ? HQ_LABEL : NONE, after.headquarters() ? HQ_LABEL : NONE);
        record(id, "담당 프로젝트", projectNames(before.projects()), projectNames(after.projects()));
        if (!new HashSet<>(before.roleCds()).equals(new HashSet<>(after.roleCds()))
            || before.headquarters() != after.headquarters()
            || !new HashSet<>(before.projectIds()).equals(new HashSet<>(after.projectIds())))
            mapper.insertAccess(id, "AUTH_CHG", "권한·담당범위 변경: " + id);
        return new Save(after, transferred);
    }
    private void record(String id, String item, String before, String after) {
        if (!Objects.equals(before, after)) history(id, "UPD", item, before, after);
    }
    public Impact getCommonUserChangeImpact(List<String> ids, String action, List<String> roles) {
        if (ids == null || ids.isEmpty() || new HashSet<>(ids).size() != ids.size())
            throw UserFailure.invalid("userIds", "사용자를 중복 없이 선택해 주세요.");
        ids.forEach(UserRequests::id);
        if (!Set.of("STOP", "ROLE_CHANGE").contains(action)) throw UserFailure.invalid("action", "작업을 확인해 주세요.");
        if (action.equals("ROLE_CHANGE") && (ids.size() != 1 || roles == null))
            throw UserFailure.invalid("roleCds", "대상 사용자와 변경할 역할을 보내 주세요.");
        var projects = new LinkedHashMap<String, Project>();
        var blocked = new ArrayList<Rejected>();
        long admins = mapper.countActiveAdmins();
        for (String id : ids.stream().sorted().toList()) {
            var row = require(id);
            var oldRoles = mapper.selectRoles(id);
            if (action.equals("STOP")) {
                String reason = protectedReason(row, oldRoles, admins);
                if (reason != null) { blocked.add(new Rejected(id, reason)); continue; }
                if ("USE".equals(row.userStatCd) && oldRoles.contains("ADMIN")) admins--;
                if (oldRoles.contains("PM")) for (var project : projects(id)) projects.put(project.projectId(), project);
            } else {
                if (!options.selectRoles().stream().map(role -> role.getRoleCd()).toList().containsAll(roles))
                    throw UserFailure.invalid("roleCds", "역할을 확인해 주세요.");
                for (var project : removals(id, oldRoles, roles)) projects.put(project.projectId(), project);
            }
        }
        return new Impact(List.copyOf(projects.values()), projects.size(), List.copyOf(blocked));
    }
    @Transactional
    public StopResult putCommonUserStop(Stop input) {
        if (input.users() == null || input.users().isEmpty()) throw UserFailure.invalid("users", "사용자를 선택해 주세요.");
        var ids = new HashSet<String>();
        for (var user : input.users()) {
            UserRequests.id(user.userId());
            if (!ids.add(user.userId())) throw UserFailure.invalid("users", "중복 사용자를 제거해 주세요.");
        }
        mapper.lockManagement();
        var accepted = new ArrayList<UserRows.Account>();
        var rejected = new ArrayList<Rejected>();
        var removed = new LinkedHashMap<String, List<Project>>();
        var actualIds = new HashSet<String>();
        long admins = mapper.countActiveAdmins();
        int stale = 0;
        for (var user : input.users().stream().sorted(Comparator.comparing(Version::userId)).toList()) {
            var row = mapper.selectAccount(user.userId());
            String reason = null;
            if (row == null) reason = "사용자를 찾을 수 없습니다.";
            else if (!Objects.equals(row.modDttm, user.modDttm())) { reason = UserFailure.conflict().getMessage(); stale++; }
            else reason = protectedReason(row, mapper.selectRoles(row.userId), admins);
            if (reason != null) { rejected.add(new Rejected(user.userId(), reason)); continue; }
            var roles = mapper.selectRoles(row.userId);
            List<Project> impact = roles.contains("PM") ? projects(row.userId) : List.of();
            impact.forEach(project -> actualIds.add(project.projectId()));
            if (!input.confirmedProjectIds().containsAll(impact.stream().map(Project::projectId).toList())) {
                rejected.add(new Rejected(row.userId, "영향 프로젝트를 다시 조회하고 확인해 주세요."));
                continue;
            }
            removed.put(row.userId, impact);
            accepted.add(row);
            if ("USE".equals(row.userStatCd) && roles.contains("ADMIN")) admins--;
        }
        boolean extra = !actualIds.containsAll(input.confirmedProjectIds());
        var processed = new ArrayList<String>();
        for (var row : accepted) {
            var impact = removed.get(row.userId);
            if (extra || !input.confirmedProjectIds().containsAll(impact.stream().map(Project::projectId).toList())) {
                rejected.add(new Rejected(row.userId, "영향 프로젝트를 다시 조회하고 확인해 주세요."));
                continue;
            }
            affected(mapper.changeState(row.userId, row.modDttm, "STOP", false));
            for (var project : impact) removeProject(row.userId, project.projectId());
            if (!impact.isEmpty()) history(row.userId, "UPD", "담당 프로젝트", projectNames(impact), NONE);
            history(row.userId, "STOP", "상태", STATE_NAMES.getOrDefault(row.userStatCd, row.userStatCd), "중지");
            processed.add(row.userId);
        }
        if (stale == input.users().size()) throw UserFailure.conflict();
        return new StopResult(List.copyOf(processed), rejected.size(), List.copyOf(rejected));
    }
    @Transactional
    public PasswordResult putCommonUserPasswordReset(String id, Instant token) {
        mapper.lockManagement();
        var row = current(id, token);
        affected(mapper.resetPassword(id, token, passwords.hashAndDeliver(row.email)));
        return new PasswordResult(id, "Y", time(require(id).modDttm));
    }
    @Transactional
    public UnlockResult putCommonUserUnlock(String id, Instant token) {
        mapper.lockManagement();
        var row = current(id, token);
        if (!"LOCK".equals(row.userStatCd)) throw UserFailure.invalid("userId", "잠긴 계정만 해제할 수 있습니다.");
        affected(mapper.changeState(id, token, "USE", true));
        return new UnlockResult(id, "USE", 0, time(require(id).modDttm));
    }
    @Transactional
    public Account putCommonUserResume(String id, Instant token) {
        mapper.lockManagement();
        var row = current(id, token);
        if (!"STOP".equals(row.userStatCd)) throw UserFailure.invalid("userId", "중지 계정만 다시 사용할 수 있습니다.");
        affected(mapper.changeState(id, token, "USE", false));
        history(id, "RESUME", "상태", "중지", "사용");
        return getCommonUser(id);
    }
}
