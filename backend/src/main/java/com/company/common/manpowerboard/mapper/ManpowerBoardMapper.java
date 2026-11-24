package com.company.common.manpowerboard.mapper;

import com.company.common.manpowerboard.mapper.ManpowerBoardRows.*;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ManpowerBoardMapper {
    // 직무·등급·직원구분 조건은 서버에서 나눈다(요약의 철수 예정·동시 투입은 직접 인력 전체로 센다, 시안 PEOPLE).
    List<Developer> developers();
    List<Assignment> assignments(Period period);
    List<Monthly> monthly(Period period);
    List<History> history(Period period);
    List<Status> statuses(Period period);
    List<Tentative> tentative(Period period);
    List<Code> codes();
    int insertAccess(@Param("userId") String userId, @Param("target") String target);
}
