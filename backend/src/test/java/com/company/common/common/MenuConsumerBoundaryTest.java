package com.company.common.service;

import com.company.common.controller.ApiExceptionHandler;
import com.company.common.controller.CommonMenuController;
import com.company.common.controller.CommonRoleController;
import com.company.common.controller.MenuExceptionHandler;
import com.company.common.controller.RoleExceptionHandler;
import com.company.common.mapper.MenuMapper;
import com.company.common.mapper.RoleMapper;
import com.company.common.mapper.RoleRows;
import com.company.common.mapper.UserMapper;
import com.company.common.service.CommonMenuService;
import com.company.common.service.CommonRoleService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static com.company.common.service.MenuFixtures.row;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({CommonMenuController.class, CommonRoleController.class})
@ContextConfiguration(classes = {CommonMenuController.class, CommonMenuService.class,
    CommonRoleController.class, CommonRoleService.class, MenuExceptionHandler.class,
    RoleExceptionHandler.class, ApiExceptionHandler.class})
class MenuConsumerBoundaryTest {
    @Autowired MockMvc mvc;
    @MockBean MenuMapper menus;
    @MockBean RoleMapper roles;
    @MockBean UserMapper management;

    @Test
    void noRightsPermissionRowDoesNotHideTheAnonymousSidebarOrRoleDetail() throws Exception {
        var menu = row("owned-menu", null, "UI-25", 1);
        when(menus.selectCandidates()).thenReturn(List.of(menu));
        var role = new RoleRows.Role();
        role.roleId = "ADMIN";
        role.roleName = "시스템 관리자";
        role.dataScopeCd = "ALL";
        role.snstvViewYn = "Y";
        role.useYn = "Y";
        var permission = new RoleRows.Menu();
        permission.menuId = menu.getMenuId();
        permission.menuName = menu.getMenuNm();
        permission.screenId = menu.getScrnId();
        permission.groupName = "합성 묶음";
        permission.readYn = "N";
        permission.createYn = "N";
        permission.updateYn = "N";
        permission.deleteYn = "N";
        permission.uploadYn = "N";
        permission.downloadYn = "N";
        permission.cnfmYn = "N";
        when(roles.selectRole("ADMIN")).thenReturn(role);
        when(roles.selectMenus("ADMIN")).thenReturn(List.of(permission));
        when(roles.selectDataScopes()).thenReturn(List.of());

        mvc.perform(get("/api/common/roles/ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.menuAuths[0].menuId").value(menu.getMenuId()))
            .andExpect(jsonPath("$.data.menuAuths[0].readYn").value("N"))
            .andExpect(jsonPath("$.data.menuAuths[0].createYn").value("N"))
            .andExpect(jsonPath("$.data.menuAuths[0].updateYn").value("N"))
            .andExpect(jsonPath("$.data.menuAuths[0].deleteYn").value("N"))
            .andExpect(jsonPath("$.data.menuAuths[0].uploadYn").value("N"))
            .andExpect(jsonPath("$.data.menuAuths[0].downloadYn").value("N"))
            .andExpect(jsonPath("$.data.menuAuths[0].cnfmYn").value("N"));
        mvc.perform(get("/api/common/menus"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].menuId").value(menu.getMenuId()))
            .andExpect(jsonPath("$.meta.screenAccess[0].available").value(true));

        menu.setMenuNm("변경된 메뉴 이름");
        permission.menuName = menu.getMenuNm();
        mvc.perform(get("/api/common/menus"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].menuNm").value("변경된 메뉴 이름"));
        mvc.perform(get("/api/common/roles/ADMIN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.menuAuths[0].menuName").value("변경된 메뉴 이름"))
            .andExpect(jsonPath("$.data.menuAuths[0].readYn").value("N"));
        verify(menus, times(2)).selectCandidates();
        verify(roles, times(2)).selectRole("ADMIN");
        verify(roles, times(2)).selectMenus("ADMIN");
        verify(roles, times(2)).selectDataScopes();
        verifyNoMoreInteractions(menus, roles);
        verifyNoInteractions(management);
    }

    @Test
    void sidebarDoesNotConsultRolePermissionsEvenWhenRoleStorageIsUnavailable() throws Exception {
        when(menus.selectCandidates()).thenReturn(List.of(row("owned", null, "UI-25", 1)));
        when(roles.selectRole(anyString())).thenThrow(new DataAccessResourceFailureException("unavailable"));
        mvc.perform(get("/api/common/menus"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].menuId").value("owned"));
        verify(menus).selectCandidates();
        verifyNoMoreInteractions(menus);
        verifyNoInteractions(roles, management);
    }

    @Test
    void sidebarReadFailureKeepsItsOriginalEnvelopeWithOtherAdvicePresent() throws Exception {
        when(menus.selectCandidates()).thenThrow(new DataAccessResourceFailureException("private SQL"));
        mvc.perform(get("/api/common/menus"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().json("{\"success\":false,\"meta\":{}}", true));
        verifyNoInteractions(roles, management);
    }
}
