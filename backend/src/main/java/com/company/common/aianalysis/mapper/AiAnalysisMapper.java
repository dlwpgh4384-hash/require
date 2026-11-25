package com.company.common.aianalysis.mapper;

import com.company.common.aianalysis.dto.AiAnalysisContracts.HistoryItem;
import com.company.common.aianalysis.mapper.AiAnalysisRows.*;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AiAnalysisMapper {
    String nextId();
    int insert(Write row);
    List<HistoryItem> history(@Param("actor") String actor);
    String saved(@Param("actor") String actor, @Param("id") String id);
    int download(@Param("actor") String actor, @Param("id") String id);
    List<Identity> identities();
}
