package com.company.common.service;

import com.company.common.mapper.UserMapper;

import com.company.common.mapper.UserOptionsMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.company.common.dto.UserContracts.*;
import static com.company.common.service.UserTestData.TOKEN;

class CommonUserServiceTest {
    private final UserMapper mapper = mock(UserMapper.class);
    private final UserOptionsMapper options = mock(UserOptionsMapper.class);
    private final UserTestData data = new UserTestData(mapper, options);
    private final CommonUserService service = new CommonUserService(mapper, options, new TemporaryUserPassword());
    private Create create(String id, String role, boolean hq, List<String> projects) {
        return new Create(id, "합성 " + id, id + "@example.invalid", List.of(role), hq, projects);
    }
    @Test void pmTransferRemovesOnlyPreviousPmScopeAndInvalidatesPreviousVersion() {
        data.add("oldpm", "USE", List.of("PM")); data.projects.get("oldpm").add("project-a");
        data.add("reader", "USE", List.of("PROJECT_READER")); data.projects.get("reader").add("project-a");
        var result = service.putCommonUsers(create("newpm", "PM", false, List.of("project-a")));
        assertThat(result.transferredProjects()).extracting(Project::projectId).containsExactly("project-a");
        assertThat(data.projects.get("oldpm")).isEmpty();
        assertThat(data.accounts.get("oldpm").modDttm).isEqualTo(TOKEN.plusNanos(1000));
        assertThat(data.projects.get("reader")).containsExactly("project-a");
        assertThat(data.projects.get("newpm")).containsExactly("project-a");
        var order = inOrder(mapper);
        order.verify(mapper).lockManagement();
        order.verify(mapper).insertUser(any(), anyString());
        order.verify(mapper).deleteProject("oldpm", "project-a");
    }
    @Test void nonPmEligibleUsersCanShareAProject() {
        service.putCommonUsers(create("viewone", "PROJECT_READER", false, List.of("project-a")));
        service.putCommonUsers(create("viewtwo", "PROJECT_READER", false, List.of("project-a")));
        assertThat(data.projects.get("viewone")).containsExactly("project-a");
        assertThat(data.projects.get("viewtwo")).containsExactly("project-a");
        verify(mapper, never()).selectPmOwners(anyString(), anyList());
    }
    @Test void headquartersNeedsPartScopeRole() {
        assertThatThrownBy(() -> service.putCommonUsers(create("finance", "EXEC", true, List.of())))
            .isInstanceOfSatisfying(UserFailure.class, error -> {
                assertThat(error.getStatus()).isEqualTo(400);
                assertThat(error.getMessage()).contains("담당 파트 역할");
            });
        verify(mapper, never()).insertUser(any(), anyString());
    }
    @Test void pmRoleAloneMayHoldProjects() {
        service.putCommonUsers(create("pmonly", "PM", false, List.of("project-b")));
        assertThat(data.projects.get("pmonly")).containsExactly("project-b");
    }
    @Test void registrationAndEditLeaveOneHistoryLinePerChangedItem() {
        service.putCommonUsers(create("member", "EXEC", false, List.of()));
        assertThat(data.history).extracting(History::item).containsExactly("등록");
        data.history.clear();
        service.putCommonUser("member", new Update("새 이름", "member@example.invalid", List.of("EXEC", "PM"), false,
            List.of(), data.accounts.get("member").modDttm, List.of()));
        assertThat(data.history).extracting(History::item).containsExactly("이름", "역할");
        assertThat(data.accounts.get("member").name).isEqualTo("새 이름");
        assertThat(service.getCommonUserChangeHistory("member")).extracting(ChangeHistory::item).containsExactly("이름", "역할");
    }
    @Test void downloadReturnsAllMatchingRowsAndLeavesAccessLog() {
        for (int i = 0; i < 25; i++) data.add("bulk" + String.format("%02d", i), "USE", List.of("EXEC"));
        var filter = com.company.common.dto.UserRequests.filter(1, 20, null, null, null, true, null, null);
        assertThat(service.getCommonUsersForDownload(filter, "admin")).hasSize(26);
        assertThat(data.accesses).anyMatch(item -> item.contains("DNLD"));
    }
    @Test void multipleHeadquartersLeadersAreAllowed() {
        service.putCommonUsers(create("leaderone", "SALES_LDR", true, List.of()));
        service.putCommonUsers(create("leadertwo", "SALES_LDR", true, List.of()));
        assertThat(data.headquarters).contains("leaderone", "leadertwo");
    }
    @Test void ambiguousHeadquartersOnlyBlocksTrueAssignments() {
        when(mapper.selectHeadquarters()).thenReturn(List.of("part-one", "part-two"));
        assertThatThrownBy(() -> service.putCommonUsers(create("leaderone", "SALES_LDR", true, List.of())))
            .isInstanceOf(UserFailure.class).hasMessageContaining("본사");
        assertThat(data.accounts).doesNotContainKey("leaderone");
        service.putCommonUsers(create("leadertwo", "SALES_LDR", false, List.of()));
        assertThat(data.accounts).containsKey("leadertwo");
    }
    @Test void absentHeadquartersDoesNotPreventUnassignedRegistration() {
        when(mapper.selectHeadquarters()).thenReturn(List.of());
        service.putCommonUsers(create("reader", "PROJECT_READER", false, List.of()));
        assertThatThrownBy(() -> service.putCommonUsers(create("leader", "SALES_LDR", true, List.of())))
            .isInstanceOf(UserFailure.class);
    }
    @Test void ineligibleProjectAssignmentIsRejectedBeforeInsert() {
        assertThatThrownBy(() -> service.putCommonUsers(create("reader", "EXEC", false, List.of("project-a"))))
            .isInstanceOf(UserFailure.class);
        verify(mapper, never()).insertUser(any(), anyString());
    }
    @Test void confirmedPmStopDeletesProjectsAndResumeDoesNotRestoreThem() {
        data.add("firstpm", "USE", List.of("PM")); data.projects.get("firstpm").add("project-a");
        data.add("secondpm", "LOCK", List.of("PM")); data.projects.get("secondpm").add("project-b");
        var impact = service.getCommonUserChangeImpact(List.of("secondpm", "firstpm"), "STOP", null);
        assertThat(impact.projectCount()).isEqualTo(2);
        assertThat(data.history).isEmpty();
        var result = service.putCommonUserStop(new Stop(List.of(new Version("firstpm", TOKEN), new Version("secondpm", TOKEN)),
            impact.projects().stream().map(Project::projectId).toList()));
        assertThat(result.processedUserIds()).containsExactly("firstpm", "secondpm");
        assertThat(data.projects.get("firstpm")).isEmpty(); assertThat(data.projects.get("secondpm")).isEmpty();
        assertThat(data.history).filteredOn(item -> item.type().equals("STOP")).hasSize(2);
        assertThat(data.history).filteredOn(item -> item.item().equals("담당 프로젝트")).hasSize(2);
        service.putCommonUserResume("firstpm", TOKEN.plusNanos(1000));
        assertThat(data.projects.get("firstpm")).isEmpty();
        assertThat(data.accesses).isEmpty();
    }
    @Test void missingConfirmationLeavesPmAndProjectsButProcessesOtherUser() {
        data.add("firstpm", "USE", List.of("PM")); data.projects.get("firstpm").add("project-a");
        data.add("normal", "USE", List.of("EXEC"));
        var result = service.putCommonUserStop(new Stop(List.of(new Version("firstpm", TOKEN), new Version("normal", TOKEN)), List.of()));
        assertThat(result.processedUserIds()).containsExactly("normal");
        assertThat(result.unprocessed()).extracting(Rejected::userId).containsExactly("firstpm");
        assertThat(data.projects.get("firstpm")).containsExactly("project-a");
        assertThat(data.accounts.get("firstpm").userStatCd).isEqualTo("USE");
    }
    @Test void allStaleIs409AndNoMutation() {
        data.add("first", "USE", List.of("EXEC")); data.add("second", "LOCK", List.of("EXEC"));
        assertThatThrownBy(() -> service.putCommonUserStop(new Stop(List.of(new Version("first", null), new Version("second", null)), List.of())))
            .isInstanceOfSatisfying(UserFailure.class, error -> assertThat(error.getStatus()).isEqualTo(409));
        assertThat(data.history).isEmpty(); verify(mapper, never()).changeState(anyString(), any(), anyString(), anyBoolean());
    }
    @Test void staleAndAlreadyStoppedReturnsIndividualReasonsWithout409() {
        data.add("first", "USE", List.of("EXEC")); data.add("second", "STOP", List.of("EXEC"));
        var result = service.putCommonUserStop(new Stop(List.of(new Version("first", null), new Version("second", TOKEN)), List.of()));
        assertThat(result.processedUserIds()).isEmpty(); assertThat(result.unprocessedCount()).isEqualTo(2);
    }
    @Test void lockedAdminIsNotCountedAsAvailableReplacement() {
        data.add("locked", "LOCK", List.of("ADMIN"));
        var result = service.putCommonUserStop(new Stop(List.of(new Version("admin", TOKEN)), List.of()));
        assertThat(result.processedUserIds()).isEmpty(); assertThat(result.unprocessed().get(0).reason()).contains("관리자가 0명");
    }
    @Test void bulkOrderPreservesOneUseAdminRegardlessOfInputOrder() {
        data.add("secondadmin", "USE", List.of("ADMIN"));
        var result = service.putCommonUserStop(new Stop(List.of(new Version("secondadmin", TOKEN), new Version("admin", TOKEN)), List.of()));
        assertThat(result.processedUserIds()).containsExactly("admin");
        assertThat(result.unprocessed()).extracting(Rejected::userId).containsExactly("secondadmin");
    }
    @Test void noOpUpdateDoesNotDuplicateAuthorityAccessLog() {
        data.add("reader", "USE", List.of("PROJECT_READER"));
        service.putCommonUser("reader", new Update("시험 reader", "reader@example.invalid", List.of("PROJECT_READER"), false, List.of(), TOKEN, List.of()));
        assertThat(data.accesses).isEmpty(); assertThat(data.history).isEmpty();
    }
    @Test void confirmationMustExactlyMatchCurrentRemovalNotAnotherAccountsProjects() {
        data.add("reader", "USE", List.of("PROJECT_READER")); data.projects.get("reader").add("project-a");
        assertThatThrownBy(() -> service.putCommonUser("reader", new Update("reader", "reader@example.com", List.of("EXEC"), false, List.of(), TOKEN, List.of("project-b"))))
            .isInstanceOfSatisfying(UserFailure.class, error -> assertThat(error.getField()).isEqualTo("confirmedProjectIds"));
        assertThat(data.projects.get("reader")).containsExactly("project-a");
        verify(mapper, never()).touch(anyString(), any());
    }
    @Test void roleChangeImpactRequiresExplicitSingleUserAndRoleArray() {
        assertThatThrownBy(() -> service.getCommonUserChangeImpact(List.of("admin"), "ROLE_CHANGE", null)).isInstanceOf(UserFailure.class);
        assertThatThrownBy(() -> service.getCommonUserChangeImpact(List.of("admin", "other"), "ROLE_CHANGE", List.of())).isInstanceOf(UserFailure.class);
    }
    @Test void stopDuplicateIdentitiesRejectedBeforeLock() {
        assertThatThrownBy(() -> service.putCommonUserStop(new Stop(List.of(new Version("admin", TOKEN), new Version("admin", TOKEN)), List.of())))
            .isInstanceOf(UserFailure.class);
        verify(mapper, never()).lockManagement();
    }
    @Test void conditionalWriteRaceReturnsConflictBeforeScopeMutation() {
        data.add("reader", "USE", List.of("PROJECT_READER"));
        when(mapper.touch("reader", TOKEN)).thenReturn(0);
        assertThatThrownBy(() -> service.putCommonUser("reader", new Update("reader", "reader@example.com", List.of("PROJECT_READER"), false, List.of("project-a"), TOKEN, List.of())))
            .isInstanceOfSatisfying(UserFailure.class, error -> assertThat(error.getStatus()).isEqualTo(409));
        verify(mapper, never()).insertScope(anyString(), anyString(), anyString());
    }
}
