package com.company.common.uploads.dto;

import com.company.common.clients.dto.ClientContracts.Input;
import java.time.Instant;
import java.util.List;

public final class UploadContracts {
    private UploadContracts() {}

    public record Issue(int row, String column, String inputValue, String reason, String severity) {}
    public record Result(String uploadId, String modifiedAt, String statusName,
        int successCount, int warningCount, int errorCount, List<Issue> issues) {}
    public record Apply(Instant modifiedAt) {}
    public record Inspection(List<Input> clients, List<Issue> issues, int successCount,
        int warningCount, int errorCount, int totalCount) {}
    public record Check(long uploadId, String formTypeCode, String fileName, int fileSize,
        Inspection inspection) {}
    public record Completion(long uploadId, Inspection inspection, boolean applied) {}
    public record IssueWrite(long uploadId, int sequence, Issue issue) {}
}
