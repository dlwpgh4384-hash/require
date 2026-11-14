package com.company.common.users.dto;

import java.util.Map;

public record CommonUserOptionsResponse(
    boolean success, Map<String, Object> meta, CommonUserOptionsResult data) {}
