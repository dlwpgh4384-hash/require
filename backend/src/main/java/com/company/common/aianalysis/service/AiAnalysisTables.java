package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisTables {
    private final ObjectMapper json;
    public AiAnalysisTables(ObjectMapper json) { this.json = json; }
    public Table table(List<?> source, String... fields) {
        var columns = new ArrayList<Column>();
        for (int i = 0; i < fields.length; i += 2) columns.add(new Column(fields[i], fields[i + 1]));
        var rows = new ArrayList<List<Object>>();
        for (Object item : source) {
            var node = json.valueToTree(item);
            var row = new ArrayList<Object>();
            for (var column : columns) {
                var value = node.get(column.key());
                row.add(value == null || value.isNull() ? null : value.isNumber() ? value.decimalValue() : value.asText());
            }
            rows.add(Collections.unmodifiableList(row));
        }
        return new Table(List.copyOf(columns), List.copyOf(rows));
    }
    public <T> T filter(Map<String, ?> values, Class<T> type) { return json.convertValue(values, type); }
}
