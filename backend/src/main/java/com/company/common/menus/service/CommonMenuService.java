package com.company.common.menus.service;

import com.company.common.menus.dto.MenuResponse;
import com.company.common.menus.mapper.MenuMapper;
import org.springframework.stereotype.Service;

@Service
public class CommonMenuService {
    private final MenuMapper mapper;

    public CommonMenuService(MenuMapper mapper) { this.mapper = mapper; }

    public MenuResponse getCommonMenus() {
        // 조회 권한 있는 메뉴만, 대메뉴는 아래 메뉴가 하나라도 남을 때만(명세 ⑦-4).
        var all = MenuRules.classify(mapper.selectCandidates());
        // 보안 필터 밖(조각 시험 등)에는 거를 권한 정보가 없어 그대로 준다.
        return com.company.common.support.auth.RequestAccess.current() == null ? all
            : MenuRules.readable(all, com.company.common.support.auth.RequestAccess::menuReadable);
    }
}
