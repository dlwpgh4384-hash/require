package com.company.common.menus.service;

import com.company.common.menus.dto.MenuRegistrationContracts.Screen;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DevelopedScreenRegistry {
    public List<Screen> screens() {
        return List.of(new Screen("UI-23", "사용자"), new Screen("UI-24", "권한"),
            new Screen("UI-25", "메뉴"), new Screen("UI-27", "공통코드"),
            new Screen("UI-15", "거래처·영업 파트"));
    }

    public boolean contains(String scrnId) {
        return screens().stream().anyMatch(screen -> screen.scrnId().equals(scrnId));
    }
}
