package com.company.common.dto;

import java.util.Map;

public record CommonUserOptionsResponse(
    boolean success, Map<String, Object> meta, CommonUserOptionsResult data) {}
