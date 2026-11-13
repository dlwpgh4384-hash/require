package com.company.common.dto;

import java.util.List;

public final class CodeMapContracts {
    private CodeMapContracts() {}

    public record Item(String excelText, String groupId, String code) {}
    public record Result(List<Item> items) {}
    public record DeleteInput(List<Item> items) {}
    public record DeleteResult(List<Item> deleted) {}
}
