package com.company.common.projects.mapper;

import com.company.common.projects.dto.ProjectContracts.*;
import com.company.common.projects.mapper.ProjectRows.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class ProjectProjectionTest {
    private Configuration config() throws Exception {
        var c = new Configuration(); c.setMapUnderscoreToCamelCase(false);
        c.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource = "mapper/projects/ProjectMapper.xml";
        try (var in = new ClassPathResource(resource).getInputStream()) { new XMLMapperBuilder(in,c,resource,c.getSqlFragments()).parse(); }
        return c;
    }
    @Test void everyInterfaceMethodHasItsOwnMappedStatement() throws Exception {
        var c = config();
        for (var method : ProjectMapper.class.getDeclaredMethods()) assertThat(c.hasStatement(ProjectMapper.class.getName()+"."+method.getName())).as(method.getName()).isTrue();
    }
    @Test void fabricatedSelectedLabelsMaterializeEveryMutableProjectionWithoutDatabase() throws Exception {
        var c = config(); var params = new HashMap<String,Object>();
        params.put("id","P-0001");params.put("today",LocalDate.of(2026,11,15));params.put("month","202611");
        params.put("start",LocalDate.of(2026,1,1));params.put("end",LocalDate.of(2026,12,31));params.put("developerId","dev");
        var alias = Pattern.compile("(?i)\\bAS\\s+\"([a-zA-Z][a-zA-Z0-9]*)\"");
        for (String name : List.of("selectProjects","selectProject","histories","salesParts","manpower","expenses","assignments","impactAssignments","lockAssignments","steps","issues","healthSource","costs","distributionCells","distributionUploads","distributionJobs","mismatches")) {
            var statement = c.getMappedStatement(ProjectMapper.class.getName()+"."+name);
            var resultMap = statement.getResultMaps().get(0);
            var object = resultMap.getType().getDeclaredConstructor().newInstance(); var meta = c.newMetaObject(object);
            var labels = new LinkedHashSet<String>();
            var matcher = alias.matcher(statement.getBoundSql(params).getSql()); while(matcher.find()) labels.add(matcher.group(1));
            assertThat(labels).as(name).isNotEmpty();
            for (String label : labels) {
                assertThat(meta.hasSetter(label)).as(name+":"+label).isTrue();
                Class<?> type = meta.getSetterType(label);
                Object value = type == LocalDate.class ? LocalDate.of(2026,11,15) : type == BigDecimal.class ? new BigDecimal("123.45") :
                    type == int.class ? 17 : type == long.class ? 19L : type == boolean.class ? true :
                    type == java.time.Instant.class ? java.time.Instant.parse("2026-11-15T00:00:00Z") : "value:"+label;
                assertThat(c.getTypeHandlerRegistry().getTypeHandler(type)).as(label).isNotNull();
                meta.setValue(label,value); assertThat(meta.getValue(label)).isEqualTo(value);
            }
        }
    }
    @Test void healthReadsWrittenDateAndStrictlyOverdueNonclosedIssues() throws Exception {
        var c = config();
        String sql = c.getMappedStatement(ProjectMapper.class.getName()+".healthSource")
            .getBoundSql(Map.of("id", "P-0001", "today", LocalDate.of(2026,11,15))).getSql();
        assertThat(sql).contains("MAX(WRT_DT)", "ISSUE_STAT_CD!='CLOSE'", "IMPR_CD='HIGH'", "DDLN_DT <")
            .doesNotContain("MOD_DTTM", "DDLN_DT <=");
        assertThat(sql).contains("SUM(e.EXPS_AMT)", "AS \"additionalExpenseAmount\"")
            .doesNotContain("SUM(e.AMT)");
    }
    @Test void absentPlanAndExplicitZeroHaveDifferentDifferenceValues() throws Exception {
        String sql = config().getMappedStatement(ProjectMapper.class.getName()+".manpower")
            .getBoundSql(Map.of("id", "P-0001")).getSql();
        assertThat(sql).contains("CASE WHEN p.PLAN_MM IS NOT NULL THEN COALESCE(a.mm,0)-p.PLAN_MM END");
    }
    @Test void recordProjectionsDeclareConstructorOrderAndTypes() throws Exception {
        var c=config();
        for (String name:List.of("customer","developer")) {
            var mapping=c.getResultMap(ProjectMapper.class.getName()+"."+name);
            var args=mapping.getConstructorResultMappings();var components=mapping.getType().getRecordComponents();
            assertThat(args).hasSize(components.length);
            for(int i=0;i<components.length;i++) { assertThat(args.get(i).getColumn()).isEqualTo(components[i].getName());assertThat(args.get(i).getJavaType()).isEqualTo(components[i].getType()); }
        }
    }
    @Test void expenseDeletionBindsEveryOriginalPrimaryKeyAndNeverDeletesOtherMonths() throws Exception {
        var c=config();var sql=c.getMappedStatement(ProjectMapper.class.getName()+".deleteExpense").getBoundSql(new ExpenseKey("P-0001","202611",1));
        assertThat(sql.getSql()).contains("PROJ_ID=", "EXPS_YM=", "EXPS_SN=");
        assertThat(sql.getParameterMappings()).extracting(p->p.getProperty()).containsExactly("projectId","month","sequence");
    }
    @Test void listIsUnpagedAndDeterministicallySortedAndCostSnapshotsNeverWritten() throws Exception {
        var c=config();var params=Map.of("today",LocalDate.of(2026,11,15),"month","202611");
        String list=c.getMappedStatement(ProjectMapper.class.getName()+".selectProjects").getBoundSql(params).getSql();
        assertThat(list).contains("ORDER BY p.CTRT_BGN_DT DESC, p.PROJ_ID ASC").doesNotContain("OFFSET");
        var write=new Write(new Project(),"admin");
        assertThat(c.getMappedStatement(ProjectMapper.class.getName()+".updateProject").getBoundSql(write).getSql()).doesNotContain("TB_CO_MON_COST","TB_SL_OPTY");
    }
    @Test void everyWriteStampsClockTimestampNotTransactionTime() throws Exception {
        var c=config();
        for (String name : List.of("insertProject","insertHistory","insertPart","savePlan","insertExpense","assignmentHistory","insertDistribution","start","lose","updateProject","changeAssignment")) {
            String sql=c.getMappedStatement(ProjectMapper.class.getName()+"."+name).getSqlSource().getBoundSql(Map.of("kind","EARLY")).getSql();
            assertThat(sql).as(name).contains("clock_timestamp()").doesNotContainIgnoringCase("now()").doesNotContain("CURRENT_TIMESTAMP");
        }
        String start=c.getMappedStatement(ProjectMapper.class.getName()+".start").getSqlSource().getBoundSql(Map.of("id","P-1","actor","admin")).getSql();
        assertThat(start).contains("MOD_USER_ID=?", "MOD_DTTM=clock_timestamp()");
    }
}
