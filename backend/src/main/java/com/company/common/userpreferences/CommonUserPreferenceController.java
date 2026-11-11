package com.company.common.userpreferences;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(produces = "application/json")
public class CommonUserPreferenceController {
    private final CommonUserPreferenceService service;

    public CommonUserPreferenceController(CommonUserPreferenceService service) { this.service = service; }

    @GetMapping("/api/common/user-preferences/{prefTypeCd}/{prefKey}")
    public UserPreferenceResponse getCommonUserPreference(
        @PathVariable("prefTypeCd") String type, @PathVariable("prefKey") String key) {
        return service.getCommonUserPreference(type, key);
    }

    @PutMapping(value = "/api/common/user-preferences/{prefTypeCd}/{prefKey}", consumes = "application/json")
    public UserPreferenceResponse putCommonUserPreference(
        @PathVariable("prefTypeCd") String type, @PathVariable("prefKey") String key,
        @RequestBody UserPreferenceInput input) {
        return service.putCommonUserPreference(type, key, input);
    }
}
