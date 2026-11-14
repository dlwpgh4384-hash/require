package com.company.common.codehistory.mapper;

import com.company.common.codehistory.dto.CodeHistoryContracts.Item;
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
