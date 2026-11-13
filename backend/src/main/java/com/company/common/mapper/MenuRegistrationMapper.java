package com.company.common.mapper;

import com.company.common.dto.MenuRegistrationContracts.Item;
import java.math.BigInteger;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MenuRegistrationMapper {
    List<MenuRegistrationRow> selectParents();
    MenuRegistrationRow selectMenu(@Param("id") String id);
    boolean screenExists(@Param("scrnId") String scrnId);
    BigInteger selectIssuedNumber(@Param("prefix") String prefix);
    int selectNextSortOrder(@Param("parentId") String parentId);
    void insertMenu(Item item);
    void insertPermissions(@Param("id") String id);
}
