package com.company.common.commonops.dto;

import java.time.Instant;
import java.util.List;

public final class PartSettlementCloseContracts {
    private PartSettlementCloseContracts() {}

    public record Item(String partId, boolean confirmed, Instant modifiedAt) {}
    public record Check(String yearMonth, int totalCount, int unconfirmedCount, List<Item> items) {}
    public record Period(String month, Instant nextMonthStart) {}
}
