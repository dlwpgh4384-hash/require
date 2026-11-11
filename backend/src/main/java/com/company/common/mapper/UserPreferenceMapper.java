package com.company.common.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserPreferenceMapper {
    UserPreferenceRow selectPreference(@Param("type") String type, @Param("key") String key);
    int insertPreference(@Param("type") String type, @Param("key") String key, @Param("value") String value);
    int updatePreference(@Param("type") String type, @Param("key") String key,
        @Param("value") String value, @Param("token") LocalDateTime token);
}
