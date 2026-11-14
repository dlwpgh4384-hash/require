package com.company.common.dto;

import java.util.List;

public final class UploadWorkbookContracts {
    private UploadWorkbookContracts() {}

    public record InputRow(int rowNumber, List<String> values) {
        public InputRow { values = List.copyOf(values); }
    }

    public record TabularRow(int rowNumber, List<String> values, List<Integer> formulaColumns) {
        public TabularRow {
            values = List.copyOf(values);
            formulaColumns = List.copyOf(formulaColumns);
        }
    }
}
