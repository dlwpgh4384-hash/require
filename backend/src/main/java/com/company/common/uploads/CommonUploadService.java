package com.company.common.uploads;

import com.company.common.service.CodeFailure;
import com.company.common.service.UploadFileStore;
import com.company.common.service.UploadWorkbookService;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static com.company.common.uploads.UploadContracts.*;

@Service
public class CommonUploadService {
    private final UploadMapper mapper;
    private final ClientUploadRules rules;
    private final UploadClientWriter writer;
    private final UploadFileStore files;
    private final UploadWorkbookService workbooks;
    private final TransactionTemplate transaction;

    public CommonUploadService(UploadMapper mapper, ClientUploadRules rules, UploadClientWriter writer,
        UploadFileStore files, UploadWorkbookService workbooks, PlatformTransactionManager transactions) {
        this.mapper = mapper;
        this.rules = rules;
        this.writer = writer;
        this.files = files;
        this.workbooks = workbooks;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public byte[] getCommonUploadTemplate(String formTypeCode) {
        rules.form(formTypeCode);
        return workbooks.tabularFile(ClientUploadRules.HEADERS, java.util.Collections.emptyList(),
            "B.7 신규 이관 전용. 구분·상호·사업자등록번호 필수. 고객사/고객·협력은 이메일, "
            + "협력사/고객·협력은 은행·계좌번호·예금주 필수. 코드·코드 이름 사용 가능, 대응 표기는 경고로 표시합니다. "
            + "최대 10MB·첫 시트·데이터 5,000행. 수식 칸은 계산된 결과값으로 읽습니다.");
    }
    public Result postCommonUpload(String formTypeCode, String fileName, byte[] bytes) {
        rules.form(formTypeCode);
        var rows = workbooks.readTabular(fileName, bytes, ClientUploadRules.HEADERS);
        long[] retained = {0};
        try {
            return transaction.execute(status -> {
                var inspection = rules.inspect(rows);
                long id = mapper.nextUploadId();
                files.retain(id, bytes);
                retained[0] = id;
                if (mapper.insertUpload(new Check(id, formTypeCode, fileName, bytes.length, inspection)) != 1)
                    throw new IllegalStateException("검사 결과를 기록하지 못했습니다.");
                issues(id, inspection.issues());
                return result(require(mapper.selectUpload(id)));
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
        return result(require(mapper.selectUpload(id(uploadId))));
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
        throw new IllegalStateException("거래처를 반영하지 못했습니다.");
    }
    private Result apply(long id, Apply input) {
        writer.lock();
        var upload = require(mapper.lockUpload(id));
        if ("APPLIED".equals(upload.statusCode)) return result(upload);
        if (!"CHECKED".equals(upload.statusCode)) throw CodeFailure.invalid("uploadId", "반영할 수 없는 상태입니다.");
        if (!Objects.equals(upload.modifiedAt, input.modifiedAt())) throw CodeFailure.conflict();
        var rows = workbooks.readTabular(upload.fileName, files.read(id), ClientUploadRules.HEADERS);
        var inspection = rules.inspect(rows);
        boolean applied = inspection.errorCount() == 0;
        if (applied) for (var client : inspection.clients()) writer.insert(client);
        mapper.deleteIssues(id);
        issues(id, inspection.issues());
        if (mapper.completeUpload(new Completion(id, inspection, applied)) != 1)
            throw new IllegalStateException("반영 결과를 기록하지 못했습니다.");
        return result(require(mapper.selectUpload(id)));
    }
    private void issues(long id, List<Issue> issues) {
        int sequence = 0;
        for (var issue : issues) {
            if (mapper.insertIssue(new IssueWrite(id, ++sequence, issue)) != 1)
                throw new IllegalStateException("행 결과를 기록하지 못했습니다.");
        }
    }
    private Result result(UploadRows.Upload row) {
        return new Result(Long.toString(row.uploadId), row.modifiedAt == null ? null : row.modifiedAt.toString(),
            row.statusName, row.successCount, row.warningCount, row.errorCount,
            mapper.selectIssues(row.uploadId).stream().map(issue -> new Issue(issue.row, issue.column,
                issue.inputValue, issue.reason, issue.severity)).toList());
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
        com.company.common.service.ClientRules.object(body, Set.of("modifiedAt"));
        return new Apply(com.company.common.service.ClientRules.token(body));
    }
    private boolean primaryKeyCollision(Throwable failure) {
        for (var cause = failure; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().toLowerCase(java.util.Locale.ROOT)
                .contains("pk_tb_bs_clnt")) return true;
        }
        return false;
    }
}
