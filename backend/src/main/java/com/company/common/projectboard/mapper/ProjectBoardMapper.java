package com.company.common.projectboard.mapper;

import com.company.common.common.ProjectBoardSignalRules.Issue;
import com.company.common.projectboard.dto.CommonProjectBoardContracts.Filter;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectBoardMapper {
    List<ProjectBoardSource> sources(@Param("filter") Filter filter,
            @Param("today") LocalDate today, @Param("month") String month);
    List<Issue> issues(String projectId);
    class BoardIssue {
        public String projectId, issueId, importanceCode, statusCode;
        public LocalDate dueDate;
        public Issue value() { return new Issue(issueId, importanceCode, statusCode, dueDate); }
    }
    List<BoardIssue> boardIssues(@Param("ids") List<String> ids);
    boolean partExists(String partId);
    /** 엑셀 다운로드 접근 기록(DNLD, SFR-011-03·SER-006). */
    int insertAccess(@Param("userId") String userId, @Param("target") String target);
}
