package com.company.common.projectcostanalyses.mapper;

import com.company.common.projectcostanalyses.dto.ProjectCostAnalysisContracts.Period;
import com.company.common.projectcostanalyses.mapper.ProjectCostAnalysisRows.*;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectCostAnalysisMapper {
    List<Project> selectProjects();
    List<History> selectHistory();
    List<Snapshot> selectSnapshots(Period period);
    List<Input> selectInputs(Period period);
    List<Expense> selectExpenses(Period period);
    List<Expense> selectDistributions(Period period);
    List<Salary> selectSalaries();
    List<Progress> selectProgress(Period period);
    List<Person> selectPeople();
    List<Pay> selectPayroll(Period period);
    List<Leave> selectLeaves(Period period);
    List<Code> selectCodes();
    int insertAccess(@Param("userId") String userId, @Param("target") String target);
}
