package com.company.common.controller;

import com.company.common.dto.UserContracts.Response;
import com.company.common.service.CommonMenuSettingsService;
import com.company.common.service.MenuSettingsRules;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;
import static com.company.common.dto.MenuSettingsContracts.*;

@RestController
@RequestMapping(produces = "application/json")
public class CommonMenuSettingsController {
    private final CommonMenuSettingsService service;

    public CommonMenuSettingsController(CommonMenuSettingsService service) { this.service = service; }

    @GetMapping("/api/common/menu-settings")
    public Response<Result> getCommonMenuSettings(@RequestParam(required = false) String menuLvl) {
        Integer level = null;
        if (menuLvl != null) {
            try {
                level = Integer.valueOf(menuLvl);
            } catch (NumberFormatException failure) {
                throw MenuSettingsRules.invalid("menuLvl");
            }
        }
        return Response.of(service.getCommonMenuSettings(level));
    }

    @PutMapping(value = "/api/common/menu-settings", consumes = "application/json")
    public Response<Result> putCommonMenuSettings(@RequestBody JsonNode body) {
        return Response.of(service.putCommonMenuSettings(MenuSettingsRules.parse(body)));
    }
}
