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
    boolean partExists(String partId);
}
