package com.company.common.commonops;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = "application/json")
public class CommonUserOptionsController {
    private final CommonUserOptionsService service;

    public CommonUserOptionsController(CommonUserOptionsService service) {
        this.service = service;
    }

    @GetMapping("/api/common/user-options")
    public CommonUserOptionsResponse getCommonUserOptions() {
        return service.getCommonUserOptions();
    }
}
