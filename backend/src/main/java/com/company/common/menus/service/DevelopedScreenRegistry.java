package com.company.common.menus.service;

import com.company.common.menus.dto.MenuRegistrationContracts.Screen;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DevelopedScreenRegistry {
    public List<Screen> screens() {
        return List.of(new Screen("UI-23", "사용자"), new Screen("UI-24", "권한"),
            new Screen("UI-25", "메뉴"), new Screen("UI-27", "공통코드"),
            new Screen("UI-15", "거래처·영업 파트"), new Screen("UI-02", "개발자 풀"),
            new Screen("UI-01", "프로젝트"), new Screen("UI-12", "투입 배정"),
            new Screen("UI-05", "철수 리스트"),
            new Screen("UI-11", "인원 계획 대비 투입"), new Screen("UI-31", "프로젝트 현황판"),
            new Screen("UI-13", "청구·입금"), new Screen("UI-03", "개인 원가·수익표"),
            new Screen("UI-04", "프로젝트 원가 분석"), new Screen("UI-19", "영업 파트 이익금"),
            new Screen("UI-32", "단가 재협상 대상"), new Screen("UI-10", "재무 항목"),
            new Screen("UI-07", "전체 수익 구조"), new Screen("UI-08", "영업이익표"),
            new Screen("UI-09", "순이익표"), new Screen("UI-18", "사업계획 대비 실적"),
            new Screen("UI-21", "자금수지표"));
    }

    public boolean contains(String scrnId) {
        return screens().stream().anyMatch(screen -> screen.scrnId().equals(scrnId));
    }
}
