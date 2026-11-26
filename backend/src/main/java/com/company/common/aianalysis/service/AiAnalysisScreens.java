package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.*;
import com.company.common.menus.mapper.MenuMapper;
import com.company.common.menus.mapper.MenuRow;
import com.company.common.menus.service.DevelopedScreenRegistry;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 메뉴에 등록된 화면(창이 보낸 화면 ID 검사)과 [○○ 열기] 단추 이름. */
@Component
public class AiAnalysisScreens {
    private final DevelopedScreenRegistry developed;
    private final MenuMapper menus;
    public AiAnalysisScreens(DevelopedScreenRegistry developed, MenuMapper menus) {
        this.developed = developed; this.menus = menus;
    }
    public boolean registered(String screenId) {
        if (screenId == null) return false;
        if (developed.contains(screenId)) return true;
        var rows = menus.selectCandidates();
        return rows != null && rows.stream().map(MenuRow::getScrnId).filter(Objects::nonNull).anyMatch(screenId::equals);
    }
    public String name(String screenId) {
        return developed.screens().stream().filter(s -> s.scrnId().equals(screenId)).map(s -> s.menuNm())
            .findFirst().orElse(screenId);
    }
    /** 다음 행동 단추: 「<화면 이름> 열기」, 답의 기간을 넘긴다. */
    public Action open(String screenId, Period period) { return new Action(name(screenId) + " 열기", screenId, period); }
}
