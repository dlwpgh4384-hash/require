package com.company.common.roles;

import com.company.common.dto.UserContracts.History;
import com.company.common.mapper.UserMapper;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.company.common.roles.RoleContracts.*;

final class RoleTestData {
    final Map<String, RoleRows.Role> roles = new LinkedHashMap<>();
    final Map<String, List<RoleRows.Menu>> menus = new LinkedHashMap<>();
    final List<History> histories = new ArrayList<>();
    final List<String> accesses = new ArrayList<>();
    final List<RoleRows.Scope> scopes = new ArrayList<>();
    final List<String> preservedPm = new ArrayList<>();
    final Map<String, Long> assignments = new LinkedHashMap<>();
    final RoleMapper mapper;

    RoleTestData(RoleMapper mapper, UserMapper management) {
        this.mapper = mapper;
        add("ADMIN", "시스템 관리자", "ALL", "Y");
        add("EXEC", "경영진", "ALL", "Y");
        add("FIN", "재무 담당", "ALL", "Y");
        add("SALES_LDR", "영업 파트장", "PART", "N");
        add("PM", "일반 사용자(PM)", "PROJ", "N");
        add("R-001", "합성 추가 역할", "PART", "N");
        when(mapper.selectRole(anyString())).thenAnswer(call -> snapshot(roles.get(call.getArgument(0))));
        when(mapper.selectRoles()).thenAnswer(call -> roles.values().stream().filter(row -> row.useYn.equals("Y")).map(RoleTestData::snapshot).toList());
        when(mapper.selectMenus(anyString())).thenAnswer(call -> new ArrayList<>(menus.getOrDefault(call.getArgument(0), List.of())));
        when(mapper.selectDataScopes()).thenReturn(List.of(code("ALL", "전체"), code("PART", "담당 파트"), code("PROJ", "담당 프로젝트")));
        when(mapper.nameExists(anyString())).thenAnswer(call -> roles.values().stream().anyMatch(row -> row.roleName.equals(call.getArgument(0))));
        when(mapper.selectIssuedNumber()).thenAnswer(call -> java.util.stream.Stream.concat(roles.keySet().stream(),
            histories.stream().filter(h -> h.table().equals("TB_SY_ROLE")).map(History::key))
            .filter(id -> id.matches("R-[0-9]+"))
            .map(id -> new BigInteger(id.substring(2))).max(BigInteger::compareTo).orElse(BigInteger.ZERO));
        when(mapper.selectNextSortOrder()).thenReturn(7);
        doAnswer(call -> {
            String id = call.getArgument(0); Copy input = call.getArgument(1); RoleRows.Role source = call.getArgument(2);
            var row = new RoleRows.Role(); row.roleId=id; row.roleName=input.roleName(); row.dataScopeCd=input.dataScopeCd();
            row.snstvViewYn=source.snstvViewYn; row.useYn="Y"; roles.put(id,row); menus.put(id,new ArrayList<>()); return null;
        }).when(mapper).insertRole(anyString(),any(),any(),anyInt());
        doAnswer(call -> { menus.put(call.getArgument(0),new ArrayList<>(menus.get(call.getArgument(1)))); return null; })
            .when(mapper).copyMenus(anyString(),anyString());
        doAnswer(call -> { var row=roles.get(call.getArgument(0)); Update input=call.getArgument(1);
            row.dataScopeCd=input.dataScopeCd(); row.snstvViewYn=input.snstvViewYn(); return null; })
            .when(mapper).updateRole(anyString(),any());
        doAnswer(call -> { String id=call.getArgument(0); MenuInput input=call.getArgument(1);
            var list=menus.get(id); for(int i=0;i<list.size();i++) if(list.get(i).menuId.equals(input.menuId())) {
                var old=list.get(i); list.set(i,menu(input,old.menuName,old.groupName));
            } return null; }).when(mapper).updateMenu(anyString(),any());
        when(mapper.countAssignments(anyString())).thenAnswer(call -> assignments.getOrDefault(call.getArgument(0),0L));
        doAnswer(call -> { roles.get(call.getArgument(0)).useYn="N"; return null; }).when(mapper).stopRole(anyString());
        when(mapper.selectRemovableScopes(anyString(),anyString())).thenAnswer(call -> List.copyOf(scopes));
        when(mapper.selectPreservedPmUsers(anyString())).thenAnswer(call -> List.copyOf(preservedPm));
        when(mapper.deleteScope(any())).thenAnswer(call -> scopes.remove(call.getArgument(0)) ? 1 : 0);
        doAnswer(call -> { histories.add(call.getArgument(0)); return null; }).when(mapper).insertHistory(any());
        doAnswer(call -> { accesses.add(call.getArgument(0)); return null; }).when(mapper).insertRoleAccess(anyString());
    }
    void add(String id, String name, String scope, String sensitive) {
        var row=new RoleRows.Role(); row.roleId=id; row.roleName=name; row.dataScopeCd=scope; row.snstvViewYn=sensitive; row.useYn="Y";
        roles.put(id,row);
        String flag=id.equals("ADMIN") || id.equals("FIN") ? "Y" : "N";
        menus.put(id,new ArrayList<>(List.of(menu(new MenuInput("M028","Y",flag,flag,flag,flag,flag,flag),"권한","시스템"),
            menu(new MenuInput("M024","N","N","N","N","N","N","N"),"월 마감","마감"))));
    }
    static RoleRows.Menu menu(MenuInput input, String name, String group) {
        var row=new RoleRows.Menu(); row.menuId=input.menuId(); row.menuName=name; row.groupName=group;
        row.readYn=input.readYn(); row.createYn=input.createYn(); row.updateYn=input.updateYn(); row.deleteYn=input.deleteYn();
        row.uploadYn=input.uploadYn(); row.downloadYn=input.downloadYn(); row.cnfmYn=input.cnfmYn(); return row;
    }
    static RoleRows.Role snapshot(RoleRows.Role old) {
        if(old==null) return null;
        var row=new RoleRows.Role(); row.roleId=old.roleId; row.roleName=old.roleName; row.dataScopeCd=old.dataScopeCd;
        row.snstvViewYn=old.snstvViewYn; row.useYn=old.useYn; return row;
    }
    static RoleRows.ScopeCode code(String id, String name) {
        var row=new RoleRows.ScopeCode(); row.code=id; row.name=name; return row;
    }
    void removable(String user, String type) {
        var row=new RoleRows.Scope(); row.userId=user; row.scopeSn=scopes.size()+1; row.scopeTypeCd=type;
        row.partId=type.equals("PART") ? "HQ" : null; row.projId=type.equals("PROJ") ? "owned-project" : null;
        scopes.add(row);
    }
}
