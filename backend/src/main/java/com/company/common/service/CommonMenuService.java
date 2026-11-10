package com.company.common.service;

import com.company.common.dto.MenuResponse;
import com.company.common.mapper.MenuMapper;
import org.springframework.stereotype.Service;

@Service
public class CommonMenuService {
    private final MenuMapper mapper;

    public CommonMenuService(MenuMapper mapper) { this.mapper = mapper; }

    public MenuResponse getCommonMenus() {
        return MenuRules.classify(mapper.selectCandidates());
    }
}
