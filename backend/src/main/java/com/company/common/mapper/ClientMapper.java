package com.company.common.mapper;

import com.company.common.dto.ClientContracts.*;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ClientMapper {
    List<ClientRows.Client> selectClients(@Param("filter") Filter filter, @Param("today") LocalDate today);
    ClientRows.Client selectClient(@Param("clientId") String clientId, @Param("today") LocalDate today);
    ClientRows.Client lockClient(String clientId);
    void lockClientRelations();
    String selectBusinessOwner(String businessNumber);
    List<String> selectSameNameNumbers(@Param("name") String name, @Param("excludedId") String excludedId);
    boolean codeExists(@Param("groupId") String groupId, @Param("code") String code);
    int insertClient(Write value);
    int updateClient(Write value);
    int updateUseStatus(StateWrite value);
    boolean hasOngoingProject(String clientId);
    boolean hasAffiliatedPerson(String clientId);
    List<ClientRows.Assignment> selectAssignments(@Param("clientId") String clientId, @Param("today") LocalDate today);
    List<ClientRows.Change> selectChanges(String clientId);
    List<String> selectSelectableClientIds();
}
