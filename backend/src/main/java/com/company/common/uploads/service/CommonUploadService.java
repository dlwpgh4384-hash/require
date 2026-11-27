package com.company.common.uploads.service;

import com.company.common.commonops.mapper.AccountAccessMapper;
import com.company.common.commonops.service.AccountScopeGuard;
import com.company.common.support.auth.RequestAccess;
import com.company.common.uploads.dto.UploadWorkbookContracts.TabularRow;
import com.company.common.uploads.mapper.UploadMapper;
import com.company.common.uploads.mapper.UploadRows;
import com.company.common.support.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static com.company.common.uploads.dto.UploadContracts.*;

/** SFR-012 공통 업로드: 양식(UploadForm)마다 열 규칙만 다르고 검사 기록·결과·반영 흐름은 같다. */
@Service
public class CommonUploadService {
    /** 양식 설명 시트에 붙이는 예시 줄 안내. */
    static final String EXAMPLE_NOTE = "데이터 첫 줄은 적는 법을 보여 주는 예시입니다. 지우고 자기 자료를 적어 올려 주세요.";
    private static final DateTimeFormatter DB_MONTH = DateTimeFormatter.ofPattern("uuuuMM");
    private final UploadMapper mapper;
    private final Map<String, UploadForm> forms;
    private final UploadFileStore files;
    private final UploadWorkbookService workbooks;
    private final TransactionTemplate transaction;
    /** 데이터 범위 조회. 범위가 「전체」일 때(보안 필터 밖 조각 시험 포함)는 부르지 않아 없어도 된다. */
    private final ObjectProvider<AccountAccessMapper> access;
    /** 업로드 완료 알림(명세 ⑩-6, 올린 사람만). 알림 부품이 없는 조각 시험 문맥에서는 만들지 않는다. */
    private final ObjectProvider<com.company.common.notifications.service.NotificationPublisher> notifications;

    public CommonUploadService(UploadMapper mapper, List<UploadForm> forms,
        UploadFileStore files, UploadWorkbookService workbooks, PlatformTransactionManager transactions,
        ObjectProvider<AccountAccessMapper> access,
        ObjectProvider<com.company.common.notifications.service.NotificationPublisher> notifications) {
        this.mapper = mapper;
        this.access = access;
        this.notifications = notifications;
        this.forms = forms.stream().collect(Collectors.toUnmodifiableMap(UploadForm::formTypeCode, Function.identity()));
        this.files = files;
        this.workbooks = workbooks;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    /** 반영 끝의 세션 다시 보기(SFR-012-08). 로그인 부품이 없는 조각 시험·직접 만든 서비스에서는 비어 있다. */
    private ObjectProvider<com.company.common.commonops.service.AccountAccessService> accounts;
    @org.springframework.beans.factory.annotation.Autowired
    void sessionCheck(ObjectProvider<com.company.common.commonops.service.AccountAccessService> accounts) {
        this.accounts = accounts;
    }
    static final String SESSION_ENDED = "다시 로그인해 주세요.";
    /**
     * SFR-012-08 전체 롤백: 반영하는 동안 세션이 끝났으면(로그아웃·다른 기기 비밀번호 변경·계정 중지·시간 지남)
     * 커밋 직전에 401을 던져 반영 전체를 되돌린다. 보안 필터 밖(배치·조각 시험)은 보지 않는다.
     */
    private void requireLiveSession() {
        if (accounts == null || RequestAccess.current() == null) return;
        var service = accounts.getIfAvailable();
        if (service == null || !(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
            instanceof org.springframework.web.context.request.ServletRequestAttributes servlet)) return;
        try {
            service.authenticated(servlet.getRequest());
        } catch (com.company.common.commonops.service.AccountAccessFailure ended) {
            throw new CodeFailure(401, "session", SESSION_ENDED);
        }
    }
    private UploadForm form(String formTypeCode) {
        UploadForm form = formTypeCode == null ? null : forms.get(formTypeCode);
        if (form == null) throw CodeFailure.invalid("formTypeCode", "업로드 양식을 확인해 주세요.");
        return form;
    }
    static final String PAYROLL_DENIED = "급여 업로드는 전체 범위와 민감 항목 권한이 있는 사람만 할 수 있습니다.";
    /**
     * B.1 급여: 범위 「전체」+민감 체크가 있는 사람만 검사·반영(2026-11-27 사용자 결정). 반영이 그 달 급여 전체를 바꿔
     * 범위가 좁은 사람이 올리면 다른 파트 급여가 지워지고, 급여는 민감 항목이다. 요청마다 다시 본다(반영 때도).
     */
    private static void requireAllScopeSensitive(UploadForm form) {
        if (form.allScopeSensitiveOnly() && !(RequestAccess.scopeAll() && RequestAccess.sensitiveVisible()))
            throw new CodeFailure(403, "formTypeCode", PAYROLL_DENIED);
    }
    public String templateFileName(String formTypeCode) { return form(formTypeCode).templateFileName(); }
    public byte[] getCommonUploadTemplate(String formTypeCode) { return getCommonUploadTemplate(formTypeCode, null); }
    /** year: 연 양식(B.10)만 줄을 미리 채운다. 다른 양식은 받지 않으면 지금과 같다. */
    public byte[] getCommonUploadTemplate(String formTypeCode, Integer year) {
        UploadForm form = form(formTypeCode);
        var rows = form.templateRows(year);
        if (rows.isEmpty() && !form.exampleRow().isEmpty())
            return workbooks.tabularFile(form.headers(), List.of(form.exampleRow()), form.templateNote() + " " + EXAMPLE_NOTE);
        return workbooks.tabularFile(form.headers(), rows, form.templateNote());
    }
    /** yearMonth: 한 달 양식(B.1 급여)만, YYYY-MM. */
    public static YearMonth month(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}")) throw CodeFailure.invalid("yearMonth", "연월을 확인해 주세요.");
        try { return YearMonth.parse(value); }
        catch (RuntimeException failure) { throw CodeFailure.invalid("yearMonth", "연월을 확인해 주세요."); }
    }
    private YearMonth month(UploadForm form, String value) {
        YearMonth month = month(value);
        if (form.monthly() && month == null) throw CodeFailure.invalid("yearMonth", "연월을 골라 주세요.");
        if (!form.monthly() && month != null) throw CodeFailure.invalid("yearMonth", "이 양식은 연월을 받지 않습니다.");
        if (month != null) form.checkMonth(month);
        return month;
    }
    public Result postCommonUpload(String formTypeCode, String yearMonth, String fileName, byte[] bytes) {
        return postCommonUpload(formTypeCode, yearMonth, fileName, bytes, null, null);
    }
    public Result postCommonUpload(String formTypeCode, String yearMonth, String fileName, byte[] bytes,
            String projectId, String distributionStepCode) {
        UploadForm form = projectId == null && distributionStepCode == null && !"B6".equals(formTypeCode)
            ? form(formTypeCode) : form(formTypeCode).withContext(new UploadForm.Context(projectId, distributionStepCode));
        requireAllScopeSensitive(form);
        YearMonth month = month(form, yearMonth);
        var rows = workbooks.readTabular(fileName, bytes, form.headers(), form.optionalHeaders());
        long[] retained = {0};
        try {
            return transaction.execute(status -> {
                var inspection = inspect(form, rows, month);
                long id = mapper.nextUploadId();
                files.retain(id, bytes);
                retained[0] = id;
                if (mapper.insertUpload(new Check(id, formTypeCode, form.screenId(),
                    month == null ? null : month.format(DB_MONTH), fileName, bytes.length, inspection)) != 1)
                    throw new IllegalStateException("검사 결과를 기록하지 못했습니다.");
                issues(id, inspection.issues());
                if ("B6".equals(formTypeCode)) mapper.storeProjectReceipt(
                    new UploadMapper.ProjectReceipt(id, projectId, distributionStepCode));
                return result(require(mapper.selectUpload(id)), inspection.assigned());
            });
        } catch (RuntimeException failure) {
            if (retained[0] != 0) {
                try { files.discard(retained[0]); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            }
            throw failure;
        }
    }
    @Transactional(readOnly = true)
    public Result getCommonUpload(String uploadId) {
        var row = require(mapper.selectUpload(id(uploadId)));
        Map<Integer, String> assigned = Map.of();
        // B.8 「붙을 번호」: 반영 전 검사 결과는 저장된 파일을 다시 읽어 지금 붙을 번호를 계산한다.
        // 오류가 있는 파일도 시안처럼 오류 없는 줄의 붙을 번호를 보여 준다(반영은 막힘).
        if ("CHECKED".equals(row.statusCode) && Set.of("B8", "B9").contains(row.formTypeCode)) {
            UploadForm form = contextual(row);
            assigned = inspect(form, workbooks.readTabular(row.fileName, files.read(row.uploadId), form.headers(), form.optionalHeaders()),
                targetMonth(row)).assigned();
        }
        return result(row, assigned);
    }
    public byte[] getCommonUploadErrors(String uploadId) {
        var result = getCommonUpload(uploadId);
        var rows = result.issues().stream().filter(issue -> "오류".equals(issue.severity()))
            .map(issue -> List.of(Integer.toString(issue.row()), issue.column(), issue.inputValue(), issue.reason()))
            .toList();
        return workbooks.tabularFile(List.of("행", "열", "입력값", "사유"), rows, null);
    }
    public Result postCommonUploadApply(String uploadId, Apply input) {
        long id = id(uploadId);
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return transaction.execute(status -> apply(id, input));
            } catch (DuplicateKeyException failure) {
                if (!primaryKeyCollision(failure) || attempt == 2) throw failure;
                // execute가 rollback을 마친 뒤 파일 전체를 새 트랜잭션에서 재검사한다.
            }
        }
        throw new IllegalStateException("업로드를 반영하지 못했습니다.");
    }
    private static YearMonth targetMonth(UploadRows.Upload row) {
        return row.targetMonth == null ? null : YearMonth.parse(row.targetMonth, DB_MONTH);
    }
    private Result apply(long id, Apply input) {
        var current = require(mapper.selectUpload(id));
        UploadForm form = contextual(current);
        requireAllScopeSensitive(form);
        form.lock();
        var upload = require(mapper.lockUpload(id));
        if ("APPLIED".equals(upload.statusCode)) return result(upload, Map.of());
        if (!"CHECKED".equals(upload.statusCode)) throw CodeFailure.invalid("uploadId", "반영할 수 없는 상태입니다.");
        if (!Objects.equals(upload.modifiedAt, input.modifiedAt())) throw CodeFailure.conflict();
        YearMonth month = targetMonth(upload);
        if (month != null) form.checkMonth(month);
        var rows = workbooks.readTabular(upload.fileName, files.read(id), form.headers(), form.optionalHeaders());
        var inspection = inspect(form, rows, month);
        boolean applied = inspection.errorCount() == 0;
        Map<Integer, String> assigned = applied ? form.apply(inspection, month, id, input.closedMonthPolicy()) : Map.of();
        mapper.deleteIssues(id);
        issues(id, inspection.issues());
        if (mapper.completeUpload(new Completion(id, inspection, applied)) != 1)
            throw new IllegalStateException("반영 결과를 기록하지 못했습니다.");
        if (applied) {
            // 반영과 같은 트랜잭션: 반영이 되돌려지면 알림도 남지 않는다.
            var publisher = notifications.getIfAvailable();
            if (publisher != null) publisher.uploadCompleted(Long.toString(id), form.screenId(), form.targetName());
        }
        requireLiveSession();
        return result(require(mapper.selectUpload(id)), applied ? assigned : inspection.assigned());
    }
    /**
     * 양식 검사 + 데이터 범위(명세 ⑧-2). 범위가 「전체」가 아니면 오류 없던 줄 가운데 대상이 지금 범위 밖인 줄을
     * 오류로 바꾼다. 오류가 하나라도 있으면 반영이 막히므로 범위 밖 자료는 쓰이지 않는다. 범위는 요청마다 다시 읽는다(반영 때도).
     */
    private Inspection inspect(UploadForm form, List<TabularRow> rows, YearMonth month) {
        var inspection = form.inspect(rows, month);
        var scope = RequestAccess.currentScope();
        if (scope.all()) return inspection;
        var targets = form.scopeTargets(inspection);
        if (targets.isEmpty()) return inspection;
        AccountAccessMapper scopes = access.getObject();
        boolean developerPartOnly = RequestAccess.developerPartOnly();
        Set<Integer> failed = inspection.issues().stream().filter(i -> "오류".equals(i.severity()))
            .map(Issue::row).collect(Collectors.toCollection(HashSet::new));
        Map<Integer, TabularRow> byNumber = rows.stream()
            .collect(Collectors.toMap(TabularRow::rowNumber, Function.identity(), (a, b) -> a));
        List<String> headers = form.headers();
        var issues = new ArrayList<>(inspection.issues());
        var assigned = new LinkedHashMap<>(inspection.assigned());
        int success = inspection.successCount(), warning = inspection.warningCount(), error = inspection.errorCount();
        for (var target : targets) {
            int row = target.row();
            if (failed.contains(row)
                || AccountScopeGuard.allows(scopes, target.kind(), target.id(), scope, developerPartOnly)) continue;
            failed.add(row);
            if (issues.stream().anyMatch(i -> i.row() == row)) warning--; else success--;
            error++;
            assigned.remove(row);
            var values = byNumber.get(row);
            String value = values == null || target.column() >= values.values().size() ? "" : values.values().get(target.column());
            issues.add(new Issue(row, headers.get(target.column()), value, AccountScopeGuard.OUT_OF_SCOPE, "오류"));
        }
        if (error == inspection.errorCount()) return inspection;
        return new Inspection(inspection.records(), List.copyOf(issues), success, warning, error,
            inspection.totalCount(), Map.copyOf(assigned));
    }
    /** 검사 기록 칸 길이(TB_SY_UPLD_ERR: 열 이름 50·입력값 200·사유 300)에 맞춰 자른다. 양식마다 자르지 않아도 500이 나지 않게 한 곳에서 지킨다. */
    private void issues(long id, List<Issue> issues) {
        int sequence = 0;
        for (var raw : issues) {
            var issue = new Issue(raw.row(), clip(raw.column(), 50), clip(raw.inputValue(), 200),
                clip(raw.reason(), 300), raw.severity());
            if (mapper.insertIssue(new IssueWrite(id, ++sequence, issue)) != 1)
                throw new IllegalStateException("행 결과를 기록하지 못했습니다.");
        }
    }
    private Result result(UploadRows.Upload row, Map<Integer, String> assigned) {
        var issues = mapper.selectIssues(row.uploadId).stream().map(issue -> new Issue(issue.row, issue.column,
            issue.inputValue, issue.reason, issue.severity)).toList();
        YearMonth month = targetMonth(row);
        List<PlannedDeveloper> planned = "B8".equals(row.formTypeCode) ? assigned.entrySet().stream()
            .sorted(Map.Entry.comparingByKey()).map(e -> new PlannedDeveloper(e.getKey(), e.getValue())).toList() : null;
        return new Result(Long.toString(row.uploadId), row.modifiedAt == null ? null : row.modifiedAt.toString(),
            row.statusName, row.successCount, row.warningCount, row.errorCount, issues,
            planned, month == null ? null : month.toString(),
            "B9".equals(row.formTypeCode) ? projectAssignments(assigned) : null,
            "B6".equals(row.formTypeCode) ? contextual(row).distributionMismatchCount() : null);
    }
    private UploadForm contextual(UploadRows.Upload row) {
        return "B6".equals(row.formTypeCode) ? form(row.formTypeCode).withContext(new UploadForm.Context(row.projectId, row.distributionStepCode)) : form(row.formTypeCode);
    }
    /** B.9 「새 프로젝트ID」: 개발자 풀(B.8)처럼 표에 저장하지 않고 이번 검사·반영 응답으로만 돌려준다. */
    private static List<PlannedProject> projectAssignments(Map<Integer, String> assigned) {
        return assigned.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(e -> new PlannedProject(e.getKey(), e.getValue())).toList();
    }
    private static String clip(String value, int limit) {
        if (value == null || value.codePointCount(0, value.length()) <= limit) return value;
        return value.substring(0, value.offsetByCodePoints(0, limit));
    }
    private UploadRows.Upload require(UploadRows.Upload row) {
        if (row == null) throw CodeFailure.invalid("uploadId", "업로드 결과를 찾을 수 없습니다.");
        return row;
    }
    private static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,11}"))
            throw CodeFailure.invalid("uploadId", "업로드 식별자를 확인해 주세요.");
        return Long.parseLong(value);
    }
    public static Apply parseApply(JsonNode body) {
        com.company.common.clients.service.ClientRules.object(body, Set.of("modifiedAt", "closedMonthPolicy"));
        var token = com.company.common.clients.service.ClientRules.token(body);
        var choice = body.get("closedMonthPolicy");
        if (choice == null || choice.isNull()) return new Apply(token);
        if (!choice.isTextual() || !Set.of(UploadForm.KEEP_CLOSED, UploadForm.REPLACE_CLOSED).contains(choice.textValue()))
            throw CodeFailure.invalid("closedMonthPolicy", "마감한 달 목표 처리 방법을 확인해 주세요.");
        return new Apply(token, choice.textValue());
    }
    private boolean primaryKeyCollision(Throwable failure) {
        for (var cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().toLowerCase(java.util.Locale.ROOT)
                .contains("pk_tb_bs_clnt")) return true;
        }
        return false;
    }
}
