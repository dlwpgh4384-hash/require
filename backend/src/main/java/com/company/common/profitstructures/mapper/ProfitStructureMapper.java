package com.company.common.profitstructures.mapper;

import com.company.common.profitstructures.mapper.ProfitStructureRows.*;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.*;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProfitStructureMapper {
    List<Snapshot> snapshots(Query query);
    List<ProjectMonth> projects(Query query);
    List<StoredCost> costs(Query query);
    List<Amount> amounts(Query query);
    BigDecimal annualTax(@Param("year") String year);
    List<Person> people(Query query);
    List<Pay> payroll(Query query);
    List<Assignment> assignments(Query query);
    List<Leave> leaves(Query query);
    List<Code> codes();
    List<PayrollClass> payrollClasses(Query query);
    int insertAccess(@Param("userId") String userId, @Param("target") String target);
}
