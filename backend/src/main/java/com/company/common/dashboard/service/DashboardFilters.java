package com.company.common.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 기존 API의 선택 필터 기본값을 그대로 적용하는 내부 요청 어댑터. */
@Component
public class DashboardFilters {
    private final ObjectMapper json;
    public DashboardFilters(ObjectMapper json) { this.json = json; }
    public <T> T of(Class<T> type, Map<String, ?> values) { return json.convertValue(values, type); }
}
