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
    /** 받은 modifiedAt(input.modifiedAt)이 지금 값과 같을 때만 고친다. 0건이면 그 사이 다른 사람이 고친 것(409). */
    int updateIssue(Write write);
    /** SER-006 접근 기록: 이슈 모아 보기 엑셀 다운로드(DNLD, 화면 UI-31). */
    int insertAccess(@Param("userId") String userId, @Param("target") String target);
}
