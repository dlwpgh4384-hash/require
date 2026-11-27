package com.company.common.uploads.service;

import com.company.common.commonops.mapper.AccountAccessMapper;
import com.company.common.commonops.service.AccountScopeGuard;
import com.company.common.support.auth.RequestAccess;
import com.company.common.support.service.ExcelExportService;
import com.company.common.uploads.dto.UploadContracts.*;
import com.company.common.uploads.dto.UploadWorkbookContracts.TabularRow;
import com.company.common.uploads.mapper.UploadMapper;
import com.company.common.uploads.mapper.UploadRows;
import com.company.common.users.service.UserScopePolicy;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 데이터 범위(명세 ⑧-2)를 업로드 줄에도: 범위가 「전체」가 아닌 사람이 올린 파일에서 대상이 범위 밖인 줄은
 * 「이 건은 지금 볼 수 있는 범위 밖입니다.」 줄 오류가 되고, 오류가 있으면 반영이 막혀 아무것도 쓰지 않는다.
 */
class UploadDataScopeTest {
    static final List<String> HEADERS = List.of("프로젝트ID", "값");
    record Line(int row, String projectId) {}
    /** 프로젝트 줄만 받는 시험 양식. 반영하면 쓴 줄을 written에 남긴다. */
    static class ProjectLines implements UploadForm {
        final List<String> written = new ArrayList<>();
        public String formTypeCode() { return "T1"; }
        public String targetName() { return "시험"; }
        public String screenId() { return "UI-12"; }
        public List<String> headers() { return HEADERS; }
        public String templateNote() { return ""; }
        public String templateFileName() { return "t.xlsx"; }
        public void lock() {}
        public Inspection inspect(List<TabularRow> rows, YearMonth month) {
            var records = rows.stream().map(r -> new Line(r.rowNumber(), r.values().get(0).trim())).toList();
            return new Inspection(records, List.of(), rows.size(), 0, 0, rows.size());
        }
        public List<ScopeTarget> scopeTargets(Inspection inspection) {
            return inspection.records().stream().map(Line.class::cast)
                .map(l -> new ScopeTarget(l.row(), 0, "PROJ", l.projectId())).toList();
        }
        public Map<Integer, String> apply(Inspection inspection, YearMonth month, long uploadId) {
            inspection.records().forEach(r -> written.add(((Line) r).projectId()));
            return Map.of();
        }
    }

    @TempDir Path temp;
    final UploadWorkbookService workbooks = new UploadWorkbookService(new ExcelExportService());
    final ProjectLines form = new ProjectLines();
    UploadMapper mapper;
    AccountAccessMapper access;
    CommonUploadService service;
    final List<Check> checks = new ArrayList<>();
    final List<Completion> completions = new ArrayList<>();
    final UploadRows.Upload stored = new UploadRows.Upload();

    @BeforeEach void setup() {
        mapper = mock(UploadMapper.class);
        access = mock(AccountAccessMapper.class);
        when(access.projectOf(eq("PROJ"), anyString())).thenAnswer(i -> i.getArgument(1));
        when(access.projectInParts(eq("P-IN"), any())).thenReturn(true);
        when(access.projectInParts(eq("P-OUT"), any())).thenReturn(false);
        when(mapper.nextUploadId()).thenReturn(7L);
        when(mapper.insertUpload(any())).thenAnswer(i -> {
            Check check = i.getArgument(0); checks.add(check);
            stored.uploadId = check.uploadId(); stored.formTypeCode = check.formTypeCode(); stored.fileName = check.fileName();
            stored.statusCode = "CHECKED"; return 1;
        });
        when(mapper.insertIssue(any())).thenReturn(1);
        when(mapper.completeUpload(any())).thenAnswer(i -> { completions.add(i.getArgument(0)); return 1; });
        when(mapper.selectUpload(7L)).thenReturn(stored);
        when(mapper.lockUpload(7L)).thenReturn(stored);
        when(mapper.selectIssues(7L)).thenReturn(List.of());
        @SuppressWarnings("unchecked") ObjectProvider<AccountAccessMapper> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(access);
        service = new CommonUploadService(mapper, List.of(form), new UploadFileStore(temp.resolve("originals")),
            workbooks, mock(PlatformTransactionManager.class), provider,
            new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(com.company.common.notifications.service.NotificationPublisher.class));
    }
    @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); }

    /** 영업 파트장(담당 파트 PT1, 전체 없음)으로 요청한다. */
    private void partLeader() {
        var request = new MockHttpServletRequest();
        new RequestAccess("lead", Set.of("SALES_LEAD"), new UserScopePolicy.Scope(false, Set.of("PT1"), Set.of()), true,
            Set.of(), Set.of(), "127.0.0.1", true, Set.of()).store(request);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    private void upload(String... projects) {
        var rows = Arrays.stream(projects).map(p -> List.of(p, "1")).toList();
        service.postCommonUpload("T1", null, "t.xlsx", workbooks.tabularFile(HEADERS, rows, null));
    }
    private void apply() { service.postCommonUploadApply("7", new Apply(null)); }

    @Test
    @DisplayName("범위가 담당 파트인 사람: 범위 밖 줄 하나가 줄 오류가 되고 반영은 아무것도 쓰지 않는다")
    void scopedUploaderGetsOutOfScopeRowErrorAndApplyWritesNothing() {
        partLeader();
        upload("P-IN", "P-OUT");
        var inspection = checks.get(0).inspection();
        assertThat(inspection.successCount()).isEqualTo(1);
        assertThat(inspection.errorCount()).isEqualTo(1);
        assertThat(inspection.issues()).containsExactly(
            new Issue(3, "프로젝트ID", "P-OUT", AccountScopeGuard.OUT_OF_SCOPE, "오류"));
        assertThat(AccountScopeGuard.OUT_OF_SCOPE).isEqualTo("이 건은 지금 볼 수 있는 범위 밖입니다.");
        apply();
        assertThat(form.written).isEmpty();
        assertThat(completions.get(0).applied()).isFalse();
        assertThat(completions.get(0).inspection().errorCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("범위가 담당 파트인 사람: 범위 안 줄만 있으면 그대로 반영한다")
    void scopedUploaderWithInScopeRowsOnlyApplies() {
        partLeader();
        when(access.projectInParts(eq("P-IN2"), any())).thenReturn(true);
        upload("P-IN", "P-IN2");
        assertThat(checks.get(0).inspection().errorCount()).isZero();
        apply();
        assertThat(form.written).containsExactly("P-IN", "P-IN2");
        assertThat(completions.get(0).applied()).isTrue();
    }

    @Test
    @DisplayName("범위가 전체인 사람(관리자·재무)은 범위를 보지 않고 전부 반영한다")
    void allScopeUploaderIsUnaffected() {
        var request = new MockHttpServletRequest();
        new RequestAccess("admin", Set.of("ADMIN"), new UserScopePolicy.Scope(true, Set.of(), Set.of()), true,
            Set.of(), Set.of(), "127.0.0.1", false, Set.of()).store(request);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        upload("P-IN", "P-OUT");
        assertThat(checks.get(0).inspection().errorCount()).isZero();
        assertThat(checks.get(0).inspection().issues()).isEmpty();
        apply();
        assertThat(form.written).containsExactly("P-IN", "P-OUT");
        verifyNoInteractions(access);
    }

    @Test
    @DisplayName("개발자 등록 대상(새 개발자의 영업 파트)은 화면처럼 담당 파트 역할만 자기 파트로 거르고 PM은 거르지 않는다")
    void developerTargetsFollowDeveloperPoolScope() {
        var part = new UserScopePolicy.Scope(false, Set.of("PT1"), Set.of());
        assertThat(AccountScopeGuard.allows(access, "DEV_PART", "PT1", part, true)).isTrue();
        assertThat(AccountScopeGuard.allows(access, "DEV_PART", "PT2", part, true)).isFalse();
        assertThat(AccountScopeGuard.allows(access, "DEV_PART", "PT2", part, false)).isTrue();
        assertThat(AccountScopeGuard.allows(access, "DEV_PART", null, part, true)).isFalse();
        assertThat(AccountScopeGuard.allows(access, "PART", "PT1", part, true)).isTrue();
        assertThat(AccountScopeGuard.allows(access, "PART", "PT2", part, true)).isFalse();
    }

    private void as(boolean all, boolean sensitive) {
        var request = new MockHttpServletRequest();
        new RequestAccess("u", Set.of("R"), new UserScopePolicy.Scope(all, all ? Set.of() : Set.of("PT1"), Set.of()), sensitive,
            Set.of(), Set.of(), "127.0.0.1", !all, Set.of()).store(request);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("급여처럼 「전체 범위+민감」 양식: 범위가 좁거나 민감 체크가 없으면 검사·반영 모두 403, 전체+민감이면 반영한다")
    void allScopeSensitiveFormRejectsOthersOnCheckAndApply() {
        var payroll = new ProjectLines() {
            @Override public boolean allScopeSensitiveOnly() { return true; }
        };
        @SuppressWarnings("unchecked") ObjectProvider<AccountAccessMapper> provider = mock(ObjectProvider.class);
        var guarded = new CommonUploadService(mapper, List.of(payroll), new UploadFileStore(temp.resolve("payroll")),
            workbooks, mock(PlatformTransactionManager.class), provider,
            new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(com.company.common.notifications.service.NotificationPublisher.class));
        byte[] file = workbooks.tabularFile(HEADERS, List.<List<String>>of(List.of("P-OUT", "1")), null);
        stored.formTypeCode = "T1"; stored.statusCode = "CHECKED";
        for (boolean[] who : new boolean[][]{{false, true}, {true, false}, {false, false}}) {
            as(who[0], who[1]);
            assertThatThrownBy(() -> guarded.postCommonUpload("T1", null, "t.xlsx", file))
                .isInstanceOfSatisfying(com.company.common.support.service.CodeFailure.class, f -> assertThat(f.getStatus()).isEqualTo(403))
                .hasMessage("급여 업로드는 전체 범위와 민감 항목 권한이 있는 사람만 할 수 있습니다.");
            assertThatThrownBy(() -> guarded.postCommonUploadApply("7", new Apply(null)))
                .isInstanceOfSatisfying(com.company.common.support.service.CodeFailure.class, f -> assertThat(f.getStatus()).isEqualTo(403));
        }
        assertThat(checks).isEmpty();
        assertThat(payroll.written).isEmpty();
        as(true, true);
        guarded.postCommonUpload("T1", null, "t.xlsx", file);
        guarded.postCommonUploadApply("7", new Apply(null));
        assertThat(payroll.written).containsExactly("P-OUT");
        verifyNoInteractions(access);
    }

    @Test
    @DisplayName("SFR-012-08: 반영하는 동안 세션이 끝났으면 커밋하지 않고 반영 전체를 되돌리며 401, 세션이 살아 있으면 커밋한다")
    void applyRollsBackWholeWhenSessionEndedDuringApply() {
        var transactions = mock(PlatformTransactionManager.class);
        @SuppressWarnings("unchecked") ObjectProvider<AccountAccessMapper> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(access);
        var checked = new CommonUploadService(mapper, List.of(form), new UploadFileStore(temp.resolve("session")),
            workbooks, transactions, provider,
            new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(com.company.common.notifications.service.NotificationPublisher.class));
        var accounts = mock(com.company.common.commonops.service.AccountAccessService.class);
        var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        beans.addBean("accounts", accounts);
        checked.sessionCheck(beans.getBeanProvider(com.company.common.commonops.service.AccountAccessService.class));
        as(true, true);
        checked.postCommonUpload("T1", null, "t.xlsx", workbooks.tabularFile(HEADERS, List.<List<String>>of(List.of("P-IN", "1")), null));
        clearInvocations(transactions);
        when(accounts.authenticated(any())).thenThrow(com.company.common.commonops.service.AccountAccessFailure.unauthorized());
        assertThatThrownBy(() -> checked.postCommonUploadApply("7", new Apply(null)))
            .isInstanceOfSatisfying(com.company.common.support.service.CodeFailure.class, f -> {
                assertThat(f.getStatus()).isEqualTo(401);
                assertThat(f.getField()).isEqualTo("session");
            })
            .hasMessage("다시 로그인해 주세요.");
        verify(transactions).rollback(any());
        verify(transactions, never()).commit(any());
        reset(accounts);
        checked.postCommonUploadApply("7", new Apply(null));
        verify(accounts).authenticated(any());
        verify(transactions).commit(any());
    }
}
