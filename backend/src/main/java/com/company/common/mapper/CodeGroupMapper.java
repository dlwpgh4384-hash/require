package com.company.common.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CodeGroupMapper {
    List<CodeRows.Group> selectGroups();
    List<CodeRows.Code> selectCodes(String groupId);
    CodeRows.Code selectCode(@Param("groupId") String groupId, @Param("code") String code);
    int nextGroupOrder();
    int nextCodeOrder(String groupId);
    int insertGroup(CodeRows.Group group);
    int insertCode(CodeRows.Code code);
    int updateCode(CodeRows.Code code);
    int deleteCode(@Param("groupId") String groupId, @Param("code") String code);
    boolean relationExists(String table);
    void lockRelation(String table);
    boolean referenceExists(@Param("ref") CodeReference ref, @Param("row") CodeRows.Code row);
    boolean historicalReferenceExists(@Param("ref") CodeReference ref, @Param("row") CodeRows.Code row);
    boolean additionalReferenceExists(CodeRows.Code row);
    boolean mappedReferenceExists(CodeRows.Code row);
}
