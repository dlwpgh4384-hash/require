package com.company.common.menus.controller;

import com.company.common.menus.dto.MenuResponse;
import com.company.common.menus.service.CommonMenuService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CommonMenuController {
    private final CommonMenuService service;

    public CommonMenuController(CommonMenuService service) { this.service = service; }

    @GetMapping(value = "/api/common/menus", produces = "application/json")
    public MenuResponse getCommonMenus() { return service.getCommonMenus(); }
}
