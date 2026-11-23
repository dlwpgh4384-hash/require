package com.company.common.candidates.mapper;

import com.company.common.candidates.dto.CandidateContracts.*;
import com.company.common.candidates.mapper.CandidateRows.*;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CandidateMapper {
    List<CandidateRow> selectCandidates(Query query);
    CandidateRow selectCandidate(String id);
    CandidateRow lockCandidate(String id);
    int insertCandidate(Write write);
    int updateCandidate(Write write);
    void deleteSkills(String id);
    void insertSkill(@Param("id") String id, @Param("name") String name, @Param("sort") int sort);
    List<String> selectSkills(String id);
    void insertContact(ContactWrite write);
    int updateContact(ContactWrite write);
    List<ContactRow> selectContacts(String id);
    boolean selectableCompany(String id);
    Area selectArea(String code);
    List<Seat> selectSeats(String jobCode);
    BigDecimal allowance(String code);
}
