package com.company.common.manpowerboard.mapper;

import com.company.common.manpowerboard.dto.ManpowerBoardContracts.Filter;
import com.company.common.manpowerboard.mapper.ManpowerBoardRows.*;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ManpowerBoardMapper {
    List<Developer> developers(Filter filter);
    List<Assignment> assignments(Filter filter);
    List<Monthly> monthly(Filter filter);
    List<History> history(Filter filter);
    List<Status> statuses(Filter filter);
    List<Tentative> tentative(Filter filter);
    List<Code> codes();
    int insertAccess(@Param("userId") String userId, @Param("target") String target);
}
