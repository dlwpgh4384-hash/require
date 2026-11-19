package com.company.common.projectissues.mapper;

import com.company.common.projectissues.dto.ProjectIssueContracts.*;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectIssueMapper {
    List<Issue> selectIssues(Query query);
    Issue selectIssue(@Param("issueId") long issueId, @Param("today") LocalDate today);
    String issueProject(long issueId);
    ProjectState lockProject(String projectId);
    Long lockIssue(@Param("issueId") long issueId, @Param("projectId") String projectId);
    String codeName(@Param("group") String group, @Param("code") String code);
    int insertIssue(Write write);
    int updateIssue(Write write);
}
