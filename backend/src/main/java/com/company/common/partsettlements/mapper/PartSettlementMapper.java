package com.company.common.partsettlements.mapper;

import com.company.common.partsettlements.dto.PartSettlementContracts.*;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PartSettlementMapper {
    List<PartSettlementRows.Part> selectParts(@Param("month") String month);
    PartSettlementRows.Header selectHeader(Key key);
    List<PartSettlementRows.Input> selectInputs(@Param("month") String month);
    List<PartSettlementRows.History> selectHistory();
    List<PartSettlementRows.Detail> selectDetails(Key key);
    boolean isClosed(@Param("month") String month);
    String selectCodeName(@Param("group") String group, @Param("code") String code);
    void lockSources();
    int insertHeader(Mutation mutation);
    int updateCard(Mutation mutation);
    int confirm(Mutation mutation);
    int cancel(@Param("mutation") Mutation mutation, @Param("reason") String reason);
    int deleteDetails(Key key);
    int insertDetail(Snapshot snapshot);
    int insertHistory(Audit audit);
    int insertAccess(@Param("actor") String actor, @Param("target") String target);
}
