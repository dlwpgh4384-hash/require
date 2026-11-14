package com.company.common.users.service;

import com.company.common.users.mapper.UserMapper;
import com.company.common.users.mapper.UserRows;

import com.company.common.users.mapper.UserOptionRows;
import com.company.common.users.mapper.UserOptionsMapper;
import java.time.Instant;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.company.common.users.dto.UserContracts.*;

/** Test-owned mapper boundary; production SQL is covered separately by PostgreSQL integration checks. */
public final class UserTestData {
    public static final Instant TOKEN = java.time.Instant.parse("2026-01-02T03:04:05.123456Z");
    public final Map<String, UserRows.Account> accounts = new HashMap<>();
    public final Map<String, List<String>> roles = new HashMap<>();
    public final Map<String, List<String>> projects = new HashMap<>();
    public final Set<String> headquarters = new HashSet<>();
    public final Map<String, String> hashes = new HashMap<>();
    public final Map<String, Integer> failures = new HashMap<>();
    public final Set<String> temporary = new HashSet<>();
    public final Map<String, List<String>> leaders = new HashMap<>();
    public final List<History> history = new ArrayList<>();
    public final List<String> accesses = new ArrayList<>();
    public final UserMapper mapper;
    public final UserOptionsMapper options;

    public UserTestData(UserMapper mapper, UserOptionsMapper options) {
        this.mapper = mapper; this.options = options;
        when(options.selectRoles()).thenReturn(List.of(role("ADMIN", false), role("SALES_LDR", false),
            role("PM", true), role("PROJECT_READER", true), role("EXEC", false)));
        when(options.selectProjects()).thenReturn(List.of(projectOption("project-a"), projectOption("project-b")));
        when(mapper.selectAccount(anyString())).thenAnswer(call -> copy(accounts.get(call.getArgument(0))));
        when(mapper.selectRoles(anyString())).thenAnswer(call -> List.copyOf(roles.getOrDefault(call.getArgument(0), List.of())));
        when(mapper.selectRoleNames(anyString())).thenAnswer(call -> List.copyOf(roles.getOrDefault(call.getArgument(0), List.of())));
        when(mapper.selectParts(anyString())).thenAnswer(call -> List.copyOf(leaders.getOrDefault(call.getArgument(0), List.of())));
        when(mapper.selectLeaders(anyString())).thenAnswer(call -> List.copyOf(leaders.getOrDefault(call.getArgument(0), List.of())));
        when(mapper.selectHeadquarters()).thenReturn(List.of("original-hq"));
        when(mapper.hasHeadquarters(anyString())).thenAnswer(call -> headquarters.contains(call.getArgument(0)));
        when(mapper.selectProjects(anyString())).thenAnswer(call -> projects.getOrDefault(call.getArgument(0), List.of()).stream().map(UserTestData::project).toList());
        when(mapper.countActiveAdmins()).thenAnswer(call -> accounts.values().stream().filter(row -> "USE".equals(row.userStatCd)
            && roles.get(row.userId).contains("ADMIN")).count());
        when(mapper.insertUser(any(), anyString())).thenAnswer(call -> {
            Create input = call.getArgument(0);
            add(input.userId(), "USE", List.of());
            accounts.get(input.userId()).name = input.name();
            accounts.get(input.userId()).email = input.email();
            hashes.put(input.userId(), call.getArgument(1)); temporary.add(input.userId());
            return 1;
        });
        when(mapper.touch(anyString(), nullable(Instant.class))).thenAnswer(call -> touch(call.getArgument(0), call.getArgument(1)));
        when(mapper.changeState(anyString(), nullable(Instant.class), anyString(), anyBoolean())).thenAnswer(call -> {
            String id = call.getArgument(0);
            if (touch(id, call.getArgument(1)) == 0) return 0;
            accounts.get(id).userStatCd = call.getArgument(2);
            if (Boolean.TRUE.equals(call.getArgument(3))) failures.put(id, 0);
            return 1;
        });
        when(mapper.resetPassword(anyString(), nullable(Instant.class), anyString())).thenAnswer(call -> {
            String id = call.getArgument(0);
            if (touch(id, call.getArgument(1)) == 0) return 0;
            hashes.put(id, call.getArgument(2)); temporary.add(id); return 1;
        });
        doAnswer(call -> { roles.put(call.getArgument(0), new ArrayList<>()); return null; }).when(mapper).deleteRoles(anyString());
        doAnswer(call -> { roles.get(call.getArgument(0)).add(call.getArgument(1)); return null; }).when(mapper).insertRole(anyString(), anyString());
        doAnswer(call -> { projects.getOrDefault(call.getArgument(0), new ArrayList<>()).remove(call.getArgument(1)); return null; })
            .when(mapper).deleteProject(anyString(), anyString());
        doAnswer(call -> { headquarters.remove(call.getArgument(0)); return null; }).when(mapper).deleteHeadquarters(anyString());
        doAnswer(call -> {
            String id = call.getArgument(0);
            if ("PART".equals(call.getArgument(1))) headquarters.add(id);
            else projects.computeIfAbsent(id, key -> new ArrayList<>()).add(call.getArgument(2));
            return null;
        }).when(mapper).insertScope(anyString(), anyString(), anyString());
        doAnswer(call -> { history.add(call.getArgument(0)); return null; }).when(mapper).insertHistory(any());
        doAnswer(call -> { accesses.add(call.getArgument(0) + ":" + call.getArgument(1)); return null; })
            .when(mapper).insertAccess(anyString(), anyString(), anyString());
        when(mapper.selectPmOwners(anyString(), anyList())).thenAnswer(call -> {
            String target = call.getArgument(0); List<String> selected = call.getArgument(1);
            var result = new ArrayList<UserRows.Owner>();
            for (var entry : projects.entrySet()) if (!entry.getKey().equals(target) && roles.get(entry.getKey()).contains("PM")) {
                for (String id : entry.getValue()) if (selected.contains(id)) {
                    var owner = new UserRows.Owner(); owner.userId = entry.getKey(); owner.projectId = id; owner.projectName = id;
                    result.add(owner);
                }
            }
            return result;
        });
        when(mapper.selectUsers(any())).thenAnswer(call -> filtered(call.getArgument(0)).stream()
            .skip(((Filter) call.getArgument(0)).offset()).limit(((Filter) call.getArgument(0)).pageSize()).map(UserTestData::copy).toList());
        when(mapper.countUsers(any())).thenAnswer(call -> (long) filtered(call.getArgument(0)).size());
        when(mapper.selectAllUsers(any())).thenAnswer(call -> filtered(call.getArgument(0)).stream().map(UserTestData::copy).toList());
        when(mapper.updateProfile(anyString(), anyString(), anyString())).thenAnswer(call -> {
            var row = accounts.get((String) call.getArgument(0));
            row.name = call.getArgument(1); row.email = call.getArgument(2); return 1;
        });
        when(mapper.selectChanges(anyString())).thenAnswer(call -> history.stream()
            .filter(item -> item.key().equals(call.getArgument(0))).map(item -> {
                var row = new UserRows.Change(); row.changedAt = TOKEN; row.item = item.item();
                row.before = item.before(); row.after = item.after(); row.changedBy = "admin"; return row;
            }).toList());
        when(mapper.selectLogs(anyString(), any())).thenReturn(List.of());
        add("admin", "USE", List.of("ADMIN"));
    }
    private List<UserRows.Account> filtered(Filter filter) {
        return accounts.values().stream().filter(row -> filter.roleCd() == null || roles.get(row.userId).contains(filter.roleCd()))
            .filter(row -> filter.userStatCd() == null || row.userStatCd.equals(filter.userStatCd()))
            .sorted(Comparator.comparing((UserRows.Account row) -> row.name).thenComparing(row -> row.userId)).toList();
    }
    public UserRows.Account add(String id, String state, List<String> assignedRoles) {
        var row = new UserRows.Account(); row.userId = id; row.name = "시험 " + id;
        row.email = id + "@example.invalid"; row.userStatCd = state; row.modDttm = TOKEN;
        accounts.put(id, row); roles.put(id, new ArrayList<>(assignedRoles)); projects.put(id, new ArrayList<>());
        failures.put(id, state.equals("LOCK") ? 5 : 0);
        return row;
    }
    private int touch(String id, Instant token) {
        var row = accounts.get(id);
        if (row == null || !Objects.equals(row.modDttm, token)) return 0;
        row.modDttm = row.modDttm == null ? TOKEN : row.modDttm.plusNanos(1000); return 1;
    }
    private static UserRows.Account copy(UserRows.Account source) {
        if (source == null) return null;
        var row = new UserRows.Account(); row.userId = source.userId; row.name = source.name; row.email = source.email;
        row.userStatCd = source.userStatCd; row.modDttm = source.modDttm; row.lastAccessAt = source.lastAccessAt; return row;
    }
    public static UserRows.Project project(String id) {
        var row = new UserRows.Project(); row.projectId = id; row.projectName = id; return row;
    }
    private static UserOptionRows.Role role(String id, boolean eligible) {
        var row = new UserOptionRows.Role(); row.setRoleCd(id); row.setRoleName(id); row.setAssignedProjectScope(eligible); row.setAssignedPartScope(id.equals("SALES_LDR")); return row;
    }
    private static UserOptionRows.Project projectOption(String id) {
        var row = new UserOptionRows.Project(); row.setProjectId(id); row.setProjectName(id); return row;
    }
}
