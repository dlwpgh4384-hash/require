package com.company.common.dto;

import com.company.common.mapper.UserPreferenceRow;

import java.time.ZoneOffset;
import java.util.Map;

public record UserPreferenceResponse(boolean success, Map<String, Object> meta, Result data) {
    public record Result(String prefTypeCd, String prefKey, boolean found, String prefVal, String modDttm) {}

    public static UserPreferenceResponse from(String type, String key, UserPreferenceRow row) {
        return new UserPreferenceResponse(true, Map.of(), new Result(type, key, row != null,
            row == null ? null : row.getPrefVal(),
            row == null || row.getModDttm() == null ? null
                : row.getModDttm().atOffset(ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
    }
}
