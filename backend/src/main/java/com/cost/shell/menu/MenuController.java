package com.cost.shell.menu;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class MenuController {
    private final MenuService service;

    public MenuController(MenuService service) {
        this.service = service;
    }

    @GetMapping("/api/common/menus")
    public Map<String, Object> listCommonMenus() {
        var result = service.list();
        return Map.of(
            "success", true,
            "meta", Map.of("screenStates", result.screenStates(), "parentMenuIds", result.parentMenuIds()),
            "data", Map.of("success", true, "meta", Map.of(), "data", result.items())
        );
    }
}
