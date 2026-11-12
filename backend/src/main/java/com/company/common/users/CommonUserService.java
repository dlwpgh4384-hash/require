package com.company.common.users;

import com.company.common.commonops.UserOptionsMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.company.common.users.UserContracts.*;

@Service
public class CommonUserService {
    private final UserMapper mapper;
    private final UserOptionsMapper options;
    private final TemporaryUserPassword passwords;

    public CommonUserService(UserMapper mapper, UserOptionsMapper options, TemporaryUserPassword passwords) {
        this.mapper = mapper; this.options = options; this.passwords = passwords;
    }
    static String time(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
    private UserRows.Account require(String id) {
        UserRequests.id(id);
        var row = mapper.selectAccount(id);
        if (row == null) throw UserFailure.invalid("userId", "사용자를 찾을 수 없습니다.");
        return row;
    }
    private UserRows.Account current(String id, LocalDateTime token) {
        var row = require(id);
        if (!Objects.equals(row.modDttm, token)) throw UserFailure.conflict();
        return row;
    }
    private void affected(int count) { if (count != 1) throw UserFailure.conflict(); }
    public Account getCommonUser(String id) {
        var row = require(id);
        return new Account(row.userId, row.name, row.email, mapper.selectRoles(id), mapper.hasHeadquarters(id),
            projects(id).stream().map(Project::projectId).toList(), row.userStatCd, time(row.modDttm));
    }
    public Page<ListItem> getCommonUsers(Filter filter) {
        var items = mapper.selectUsers(filter).stream().map(row -> new ListItem(row.userId, row.name,
            mapper.selectRoles(row.userId), row.userStatCd, time(row.modDttm), mapper.selectRoleNames(row.userId),
            mapper.selectParts(row.userId), time(row.lastAccessAt))).toList();
        return new Page<>(items, filter.page(), filter.pageSize(), mapper.countUsers(filter));
    }
    public Page<AccessLog> getCommonUserAccessLogs(String id, Filter filter) {
        require(id);
        var items = mapper.selectLogs(id, filter).stream()
            .map(row -> new AccessLog(time(row.occurredAt), row.action, row.target)).toList();
        return new Page<>(items, filter.page(), filter.pageSize(), mapper.countLogs(id));
    }
    private List<Project> projects(String id) {
        return mapper.selectProjects(id).stream().map(row -> new Project(row.projectId, row.projectName)).toList();
    }
    private boolean eligible(List<String> roles) {
        return options.selectRoles().stream().anyMatch(row -> roles.contains(row.getRoleCd()) && row.isAssignedProjectScope());
    }
    private String validateAssignments(List<String> roles, boolean headquarters, List<String> projects) {
        if (roles == null || roles.isEmpty() || new HashSet<>(roles).size() != roles.size())
            throw UserFailure.invalid("roleCds", "역할을 1개 이상 선택해 주세요.");
        var allowed = options.selectRoles().stream().map(row -> row.getRoleCd()).toList();
        if (!allowed.containsAll(roles)) throw UserFailure.invalid("roleCds", "역할을 확인해 주세요.");
        if (!projects.isEmpty() && !eligible(roles))
            throw UserFailure.invalid("projectIds", "담당 프로젝트 역할을 먼저 선택해 주세요.");
        if (new HashSet<>(projects).size() != projects.size()
            || !options.selectProjects().stream().map(row -> row.getProjectId()).toList().containsAll(projects))
            throw UserFailure.invalid("projectIds", "프로젝트를 확인해 주세요.");
        if (!headquarters) return null;
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
            return "마지막 사용 중 시스템 관리자는 중지할 수 없습니다.";
        if ("STOP".equals(row.userStatCd)) return "이미 사용 중지한 계정입니다.";
        return null;
    }
    private List<Project> removals(String id, List<String> oldRoles, List<String> nextRoles) {
        return (oldRoles.contains("PM") && !nextRoles.contains("PM"))
            || (eligible(oldRoles) && !eligible(nextRoles)) ? projects(id) : projects(id).stream().limit(0).toList();
    }
    private void confirm(List<Project> projects, List<String> confirmed) {
        if (!new HashSet<>(projects.stream().map(Project::projectId).toList()).equals(new HashSet<>(confirmed)))
            throw UserFailure.invalid("confirmedProjectIds", "영향 프로젝트를 다시 조회하고 확인해 주세요.");
    }
    private void history(String table, String key, String type, String item, String before, String after) {
        mapper.insertHistory(new History(table, key, type, item, before, after));
    }
    private void removeProject(String id, String project) {
        mapper.deleteProject(id, project);
        history("TB_SY_USER_SCOPE", id + ":" + project, "UPD", "PROJ", project, null);
    }
    private List<Project> transfer(String id, List<String> roles, List<String> projects) {
        var transferred = new LinkedHashMap<String, Project>();
        if (roles.contains("PM") && !projects.isEmpty()) {
            var owners = mapper.selectPmOwners(id, projects);
            var touched = new HashSet<String>();
            for (var owner : owners) {
                if (touched.add(owner.userId)) {
                    var old = require(owner.userId);
                    affected(mapper.touch(old.userId, old.modDttm));
                    mapper.insertAccess(old.userId, "AUTH_CHG", "PM 담당범위 이관: " + old.userId);
                }
                removeProject(owner.userId, owner.projectId);
                transferred.put(owner.projectId, new Project(owner.projectId, owner.projectName));
            }
        }
        return List.copyOf(transferred.values());
    }
    private void assignments(String id, List<String> roles, String hq, List<String> nextProjects) {
        var oldRoles = mapper.selectRoles(id);
        if (!new HashSet<>(oldRoles).equals(new HashSet<>(roles))) {
            for (String role : oldRoles) if (!roles.contains(role)) history("TB_SY_USER_ROLE", id + ":" + role, "UPD", "ROLE_ID", role, null);
            for (String role : roles) if (!oldRoles.contains(role)) history("TB_SY_USER_ROLE", id + ":" + role, "UPD", "ROLE_ID", null, role);
            mapper.deleteRoles(id);
            for (String role : roles) mapper.insertRole(id, role);
        }
        var oldProjects = projects(id).stream().map(Project::projectId).toList();
        for (String project : oldProjects) if (!nextProjects.contains(project)) removeProject(id, project);
        for (String project : nextProjects) if (!oldProjects.contains(project)) {
            mapper.insertScope(id, "PROJ", project);
            history("TB_SY_USER_SCOPE", id + ":" + project, "UPD", "PROJ", null, project);
        }
        boolean oldHq = mapper.hasHeadquarters(id);
        if (oldHq != (hq != null)) {
            mapper.deleteHeadquarters(id);
            if (hq != null) mapper.insertScope(id, "PART", hq);
            history("TB_SY_USER_SCOPE", id, "UPD", "headquarters", String.valueOf(oldHq), String.valueOf(hq != null));
        }
    }
    @Transactional
    public Save putCommonUsers(Create input) {
        UserRequests.id(input.userId());
        mapper.lockManagement();
        String hq = validateAssignments(input.roleCds(), input.headquarters(), input.projectIds());
        if (mapper.selectAccount(input.userId()) != null) throw UserFailure.invalid("userId", "이미 등록된 아이디입니다.");
        mapper.insertUser(input, passwords.hashAndDeliver(input.email()));
        var transferred = transfer(input.userId(), input.roleCds(), input.projectIds());
        assignments(input.userId(), input.roleCds(), hq, input.projectIds());
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
            && mapper.countActiveAdmins() <= 1) throw UserFailure.invalid("roleCds", "마지막 시스템 관리자 역할을 제거할 수 없습니다.");
        String hq = validateAssignments(input.roleCds(), input.headquarters(), input.projectIds());
        var removed = removals(id, oldRoles, input.roleCds());
        confirm(removed, input.confirmedProjectIds());
        var removedIds = removed.stream().map(Project::projectId).toList();
        var nextProjects = input.projectIds().stream().filter(project -> !removedIds.contains(project)).toList();
        var before = getCommonUser(id);
        affected(mapper.touch(id, input.modDttm()));
        var transferred = transfer(id, input.roleCds(), nextProjects);
        assignments(id, input.roleCds(), hq, nextProjects);
        if (!new HashSet<>(before.roleCds()).equals(new HashSet<>(input.roleCds()))
            || before.headquarters() != input.headquarters()
            || !new HashSet<>(before.projectIds()).equals(new HashSet<>(nextProjects)))
            mapper.insertAccess(id, "AUTH_CHG", "권한·담당범위 변경: " + id);
        return new Save(getCommonUser(id), transferred);
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
            var impact = roles.contains("PM") ? projects(row.userId) : projects(row.userId).stream().limit(0).toList();
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
            history("TB_SY_USER", row.userId, "STOP", "USER_STAT_CD", row.userStatCd, "STOP");
            processed.add(row.userId);
        }
        if (stale == input.users().size()) throw UserFailure.conflict();
        return new StopResult(List.copyOf(processed), rejected.size(), List.copyOf(rejected));
    }
    @Transactional
    public PasswordResult putCommonUserPasswordReset(String id, LocalDateTime token) {
        mapper.lockManagement();
        var row = current(id, token);
        affected(mapper.resetPassword(id, token, passwords.hashAndDeliver(row.email)));
        return new PasswordResult(id, "Y", time(require(id).modDttm));
    }
    @Transactional
    public UnlockResult putCommonUserUnlock(String id, LocalDateTime token) {
        mapper.lockManagement();
        var row = current(id, token);
        if (!"LOCK".equals(row.userStatCd)) throw UserFailure.invalid("userId", "잠긴 계정만 해제할 수 있습니다.");
        affected(mapper.changeState(id, token, "USE", true));
        return new UnlockResult(id, "USE", 0, time(require(id).modDttm));
    }
    @Transactional
    public Account putCommonUserResume(String id, LocalDateTime token) {
        mapper.lockManagement();
        var row = current(id, token);
        if (!"STOP".equals(row.userStatCd)) throw UserFailure.invalid("userId", "중지 계정만 다시 사용할 수 있습니다.");
        affected(mapper.changeState(id, token, "USE", false));
        history("TB_SY_USER", id, "RESUME", "USER_STAT_CD", "STOP", "USE");
        return getCommonUser(id);
    }
}
