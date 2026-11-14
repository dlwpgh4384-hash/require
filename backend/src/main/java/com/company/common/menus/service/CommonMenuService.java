package com.company.common.menus.service;

import com.company.common.menus.dto.MenuResponse;
import com.company.common.menus.mapper.MenuMapper;
import org.springframework.stereotype.Service;

@Service
public class CommonMenuService {
    private final MenuMapper mapper;

    public CommonMenuService(MenuMapper mapper) { this.mapper = mapper; }

    public MenuResponse getCommonMenus() {
        return MenuRules.classify(mapper.selectCandidates());
    }
}
