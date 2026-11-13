package com.company.common.dto;

import java.time.Instant;
import java.util.List;

public final class CodeHistoryContracts {
    private CodeHistoryContracts() {}

    public record Item(Instant changedAt, String kind, String item,
        String before, String after, String processor) {}
    public record Result(List<Item> items) {}
}
