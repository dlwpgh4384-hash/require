package com.company.common.mapper;

import com.company.common.dto.RoleContracts;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class RoleProjectionTest {
    private Configuration configuration() throws Exception {
        var config=new Configuration();
        config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        try(var input=new ClassPathResource("mapper/RoleMapper.xml").getInputStream()) {
            new XMLMapperBuilder(input,config,"mapper/RoleMapper.xml",config.getSqlFragments()).parse();
        }
        return config;
    }
    private void materialize(Configuration config, String name, Object row, Map<String,Object> labels) {
        var map=config.getResultMap(RoleMapper.class.getName()+"."+name);
        assertThat(map.getAutoMapping()).isFalse();
        assertThat(map.getResultMappings()).extracting(mapping -> mapping.getColumn())
            .containsExactlyInAnyOrderElementsOf(labels.keySet());
        var meta=config.newMetaObject(row);
        for(var mapping:map.getResultMappings()) {
            assertThat(mapping.getJavaType()).isEqualTo(meta.getSetterType(mapping.getProperty()));
            assertThat(mapping.getTypeHandler()).isNotNull();
            meta.setValue(mapping.getProperty(),labels.get(mapping.getColumn()));
            assertThat(meta.getValue(mapping.getProperty())).isEqualTo(labels.get(mapping.getColumn()));
        }
    }
    @Test void roleProjectionPreservesExactIdentifiersScopeAndFlags() throws Exception {
        var config=configuration(); var row=new RoleRows.Role();
        materialize(config,"role",row,Map.of("role_id","R-123","role_nm","합성 역할",
            "data_scope_cd","PART","snstv_view_yn","Y","use_yn","N"));
        assertThat(row.value()).isEqualTo(new RoleContracts.Role("R-123","합성 역할","PART","Y","N",false));
        String query=config.getMappedStatement(RoleMapper.class.getName()+".selectRole").getBoundSql(Map.of("id","R-123")).getSql();
        assertThat(query).contains("r.role_id", "r.role_nm", "r.data_scope_cd", "r.snstv_view_yn", "r.use_yn");
    }
    @Test void everyActionMaterializesFromItsPhysicalColumn() throws Exception {
        var config=configuration(); var row=new RoleRows.Menu(); var labels=new LinkedHashMap<String,Object>();
        labels.put("menu_id","M011"); labels.put("menu_nm","합성 메뉴"); labels.put("scrn_id","UI-29"); labels.put("group_name","합성 묶음");
        for(String name:new String[]{"inq_yn","reg_yn","upd_yn","del_yn","upld_yn","dnld_yn","cnfm_yn"}) labels.put(name,"Y");
        materialize(config,"menu",row,labels);
        assertThat(row.value().input().flags()).containsOnly("Y");
        assertThat(row.value().menuName()).isEqualTo("합성 메뉴");
        assertThat(row.value().groupName()).isEqualTo("합성 묶음");
        assertThat(row.value().screenId()).isEqualTo("UI-29");
        String query=config.getMappedStatement(RoleMapper.class.getName()+".selectMenus").getBoundSql(Map.of("id","EXEC")).getSql();
        assertThat(query).contains("a.inq_yn", "a.reg_yn", "a.upd_yn", "a.del_yn", "a.upld_yn", "a.dnld_yn", "a.cnfm_yn");
    }
    @Test void scopeAndCodeProjectionsAreExplicitAndNonDefault() throws Exception {
        var config=configuration(); var scope=new RoleRows.Scope();
        materialize(config,"scope",scope,Map.of("user_id","owned","scope_sn",7,"scope_type_cd","PROJ","part_id","HQ","proj_id","project-a"));
        assertThat(scope.userId).isEqualTo("owned"); assertThat(scope.scopeSn).isEqualTo(7);
        assertThat(scope.scopeTypeCd).isEqualTo("PROJ"); assertThat(scope.partId).isEqualTo("HQ"); assertThat(scope.projId).isEqualTo("project-a");
        var code=new RoleRows.ScopeCode(); materialize(config,"dataScope",code,Map.of("code","PART","code_nm","담당 파트"));
        assertThat(code.value()).isEqualTo(new RoleContracts.DataScope("PART","담당 파트"));
    }
    @Test void issuanceUsesRetainedRoleHistoryAndWritesHaveFixedActorAndDatabaseTime() throws Exception {
        var config=configuration();
        String number=config.getMappedStatement(RoleMapper.class.getName()+".selectIssuedNumber").getBoundSql(null).getSql();
        assertThat(number).contains("TB_SY_ROLE", "TB_SY_CHG_HIST", "trgt_tbl_nm='TB_SY_ROLE'").doesNotContain("use_yn", "nextval");
        String access=config.getMappedStatement(RoleMapper.class.getName()+".insertRoleAccess").getBoundSql(Map.of("id","ADMIN")).getSql();
        assertThat(access).contains("'admin'", "'UI-24'", "'AUTH_CHG'", "clock_timestamp()").doesNotContain("UI-23");
    }
}
