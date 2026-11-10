package com.company.common.dto;

import java.util.List;
import java.util.Map;

public record MenuResponse(boolean success, Map<String, Object> meta, List<MenuItem> data) {
    public record ScreenAccess(String scrnId, boolean available, String pcOnlyYn) {}
}
