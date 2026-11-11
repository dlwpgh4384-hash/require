package com.company.common.service;

import com.company.common.dto.MenuItem;
import com.company.common.dto.MenuResponse;
import com.company.common.dto.MenuResponse.ScreenAccess;
import com.company.common.mapper.MenuRow;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MenuRules {
    private static final Logger log = LoggerFactory.getLogger(MenuRules.class);
    private static final Comparator<MenuRow> ORDER = Comparator.comparingInt(MenuRow::getSortOrd)
        .thenComparing(MenuRow::getMenuId);

    private MenuRules() {}

    public static MenuResponse classify(List<MenuRow> candidates) {
        List<MenuRow> rows = candidates.stream().sorted(ORDER).toList();
        Map<String, MenuRow> byId = new HashMap<>();
        Map<String, MenuRow> representatives = new LinkedHashMap<>();
        Set<String> parents = new HashSet<>();
        Set<String> suppressed = new HashSet<>();
        for (MenuRow row : rows) {
            byId.put(row.getMenuId(), row);
            if (nonempty(row.getUpperMenuId())) parents.add(row.getUpperMenuId());
            if (nonempty(row.getScrnId())) {
                MenuRow first = representatives.putIfAbsent(row.getScrnId(), row);
                if (first != null) {
                    suppressed.add(row.getMenuId());
                    log.warn("Menu duplicate scrnId={} representative={} suppressed={}",
                        row.getScrnId(), first.getMenuId(), row.getMenuId());
                }
            }
        }
        Set<String> warned = new HashSet<>();
        Map<String, Boolean> eligible = new HashMap<>();
        for (MenuRow row : rows) {
            if (nonempty(row.getScrnId()) && parents.contains(row.getMenuId())) {
                log.warn("Menu mixed group menuId={} scrnId={}", row.getMenuId(), row.getScrnId());
            }
            boolean available = true;
            Set<String> visited = new HashSet<>();
            MenuRow cursor = row;
            while (cursor != null) {
                String id = cursor.getMenuId();
                if (!visited.add(id)) {
                    warn(warned, "cycle", id);
                    available = false;
                    break;
                }
                if (!"Y".equals(cursor.getUseYn()) || suppressed.contains(id)) available = false;
                String parent = cursor.getUpperMenuId();
                if (!nonempty(parent)) break;
                if (parent.equals(id)) {
                    warn(warned, "self-reference", id);
                    available = false;
                    break;
                }
                if (!byId.containsKey(parent)) {
                    warn(warned, "missing-parent", id);
                    available = false;
                    break;
                }
                cursor = byId.get(parent);
            }
            eligible.put(row.getMenuId(), available);
        }
        List<MenuItem> data = rows.stream().filter(row -> eligible.get(row.getMenuId()))
            .map(MenuItem::from).toList();
        List<ScreenAccess> access = new ArrayList<>();
        representatives.forEach((screen, row) -> access.add(new ScreenAccess(screen,
            eligible.get(row.getMenuId()) && !parents.contains(row.getMenuId()), row.getPcOnlyYn())));
        return new MenuResponse(true, Map.of("screenAccess", List.copyOf(access)), data);
    }

    private static boolean nonempty(String value) { return value != null && !value.isEmpty(); }

    private static void warn(Set<String> warned, String reason, String id) {
        if (warned.add(reason + ":" + id)) log.warn("Menu hierarchy reason={} menuId={}", reason, id);
    }
}
