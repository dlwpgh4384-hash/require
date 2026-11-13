package com.company.common.mapper;

import com.company.common.dto.CodeHistoryContracts.Item;
import java.time.Instant;

public class CodeHistoryRow {
    public Instant changedAt;
    public String kind;
    public String item;
    public String before;
    public String after;
    public String processor;

    public Item value() {
        return new Item(changedAt, kind, item, before, after, processor);
    }
}
