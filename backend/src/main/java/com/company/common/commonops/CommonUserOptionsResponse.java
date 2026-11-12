package com.company.common.commonops;

import java.util.Map;

public record CommonUserOptionsResponse(
    boolean success, Map<String, Object> meta, CommonUserOptionsResult data) {}
