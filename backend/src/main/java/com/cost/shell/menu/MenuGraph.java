package com.cost.shell.menu;

import java.util.*;
import java.util.function.BiConsumer;

/** Pure graph processing. The database is never changed to repair invalid relationships. */
public final class MenuGraph {
    private MenuGraph() {
    }

    public record ScreenState(boolean available, String pcOnlyYn, String menuId, String menuNm) {
    }

    public record Result(List<MenuItem> items, Map<String, ScreenState> screenStates, List<String> parentMenuIds) {
    }

    public static Result evaluate(List<MenuRow> rows, BiConsumer<String, String> warning) {
        var byId = new LinkedHashMap<String, MenuRow>();
        var parents = new LinkedHashSet<String>();
        for (var row : rows) {
            byId.put(row.menuId(), row);
            if (hasText(row.upperMenuId())) {
                parents.add(row.upperMenuId());
            }
        }
        var allowed = new HashSet<String>();
        for (var row : rows) {
            var visited = new HashSet<String>();
            MenuRow current = row;
            boolean active = true;
            boolean valid = true;
            while (current != null) {
                if (!visited.add(current.menuId())) {
                    valid = false;
                    break;
                }
                active &= "Y".equals(current.useYn());
                if (!hasText(current.upperMenuId())) {
                    break;
                }
                current = byId.get(current.upperMenuId());
                if (current == null) {
                    valid = false;
                }
            }
            if (!valid) {
                warning.accept("INVALID_HIERARCHY", row.menuId());
            }
            if (parents.contains(row.menuId()) && hasText(row.scrnId())) {
                warning.accept("MIXED_NODE", row.menuId());
            }
            if (valid && active) {
                allowed.add(row.menuId());
            }
        }
        var sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparingInt(MenuRow::sortOrd).thenComparing(MenuRow::menuId));
        var children = new HashMap<String, List<MenuRow>>();
        for (var row : sorted) {
            if (allowed.contains(row.menuId())) {
                String parent = hasText(row.upperMenuId()) ? row.upperMenuId() : "";
                children.computeIfAbsent(parent, key -> new ArrayList<>()).add(row);
            }
        }
        var ordered = new ArrayList<MenuRow>();
        var pending = new ArrayDeque<MenuRow>();
        pushReverse(pending, children.getOrDefault("", Collections.emptyList()));
        while (!pending.isEmpty()) {
            var row = pending.pop();
            ordered.add(row);
            pushReverse(pending, children.getOrDefault(row.menuId(), Collections.emptyList()));
        }
        var screens = new LinkedHashMap<String, ScreenState>();
        for (var row : sorted) {
            if (hasText(row.scrnId())) {
                screens.putIfAbsent(row.scrnId(), new ScreenState(false, row.pcOnlyYn(), null, null));
            }
        }
        for (var row : ordered) {
            if (hasText(row.scrnId()) && !parents.contains(row.menuId())) {
                var previous = screens.get(row.scrnId());
                if (!previous.available() || ("Y".equals(previous.pcOnlyYn()) && "N".equals(row.pcOnlyYn()))) {
                    screens.put(row.scrnId(), new ScreenState(true, row.pcOnlyYn(), row.menuId(), row.menuNm()));
                }
            }
        }
        return new Result(
            ordered.stream().map(MenuRow::publicItem).toList(),
            Collections.unmodifiableMap(screens),
            List.copyOf(parents)
        );
    }

    private static void pushReverse(Deque<MenuRow> target, List<MenuRow> rows) {
        for (int index = rows.size() - 1; index >= 0; index--) {
            target.push(rows.get(index));
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
