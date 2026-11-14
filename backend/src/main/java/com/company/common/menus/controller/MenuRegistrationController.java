package com.company.common.menus.controller;

import com.company.common.menus.dto.MenuRegistrationContracts.Item;
import com.company.common.menus.dto.MenuRegistrationContracts.Options;
import com.company.common.users.dto.UserContracts.Response;
import com.company.common.menus.service.MenuRegistrationRules;
import com.company.common.menus.service.MenuRegistrationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(produces = "application/json")
public class MenuRegistrationController {
    private final MenuRegistrationService service;

    public MenuRegistrationController(MenuRegistrationService service) {
        this.service = service;
    }

    @GetMapping("/api/common/menu-options")
    public Response<Options> getCommonMenuOptions(@RequestParam Integer menuLvl) {
        return Response.of(service.getCommonMenuOptions(menuLvl));
    }

    @PostMapping(value = "/api/common/menus", consumes = "application/json")
    public Response<Item> postCommonMenu(@RequestBody JsonNode body) {
        return Response.of(service.postCommonMenu(MenuRegistrationRules.create(body)));
    }
}
