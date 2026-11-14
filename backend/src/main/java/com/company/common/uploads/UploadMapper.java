package com.company.common.uploads;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UploadMapper {
    long nextUploadId();
    UploadRows.Upload selectUpload(long uploadId);
    UploadRows.Upload lockUpload(long uploadId);
    List<UploadRows.Issue> selectIssues(long uploadId);
    int insertUpload(UploadContracts.Check value);
    int completeUpload(UploadContracts.Completion value);
    int deleteIssues(long uploadId);
    int insertIssue(UploadContracts.IssueWrite value);
}
