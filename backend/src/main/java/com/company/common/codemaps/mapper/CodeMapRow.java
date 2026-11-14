package com.company.common.codemaps.mapper;

import com.company.common.codemaps.dto.CodeMapContracts.Item;

public class CodeMapRow {
    public Long mapId;
    public String excelText;
    public String groupId;
    public String code;

    public Item value() { return new Item(excelText, groupId, code); }
}
