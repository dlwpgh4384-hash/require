package com.company.common.airecommendations.mapper;

import com.company.common.airecommendations.mapper.AiRecommendationRows.*;
import com.company.common.personalcostprofits.mapper.PersonalCostProfitRows.Pay;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiRecommendationMapper {
    Target target(@Param("type") String type, @Param("id") String id);
    List<Person> candidates(Filter filter);
    Person person(@Param("id") String id);
    Evidence evidence(EvidenceFilter filter);
    Pay payroll(@Param("id") String id, @Param("month") String month);
    Price price(@Param("id") String id, @Param("month") String month);
    List<String> skills(@Param("id") String id);
    List<Area> areas();
    String nextId();
    String lockRequest(@Param("fingerprint") String fingerprint);
    List<Log> replay(@Param("fingerprint") String fingerprint);
    List<Log> execution(@Param("id") String id);
    Log candidate(@Param("id") String id, @Param("developerId") String developerId);
    Log lockCandidate(@Param("id") String id, @Param("developerId") String developerId);
    Assignment assignment(@Param("id") String id);
    int insert(LogWrite write);
    int action(Action action);
    int exclude(ExclusionWrite write);
}
