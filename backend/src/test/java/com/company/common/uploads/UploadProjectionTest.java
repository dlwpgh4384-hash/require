package com.company.common.uploads;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;

class UploadProjectionTest {
    @ParameterizedTest @ValueSource(booleans = {false,true})
    void explicitUploadAndIssueMappingsMaterializeEverySqlLabel(boolean nullable) throws Exception {
        var config=new Configuration(); config.setMapUnderscoreToCamelCase(false);
        config.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.FAILING);
        String resource="mapper/uploads/UploadMapper.xml";
        try(var stream=new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(stream,config,resource,config.getSqlFragments()).parse();
        }
        var labels=new LinkedHashMap<String,Object>();
        labels.put("upld_id",81L); labels.put("form_type_cd","B7"); labels.put("file_nm","clients.xlsx");
        labels.put("upld_stat_cd","CHECKED"); labels.put("status_name","검사완료"); labels.put("succ_cnt",3);
        labels.put("warn_cnt",2); labels.put("err_cnt",1); labels.put("mod_dttm",nullable?null:Instant.parse("2030-01-01T00:00:00.123456Z"));
        var row=new UploadRows.Upload(); map(config,"selectUpload",row,labels);
        assertThat(row.uploadId).isEqualTo(81); assertThat(row.formTypeCode).isEqualTo("B7");
        assertThat(row.fileName).isEqualTo("clients.xlsx"); assertThat(row.statusCode).isEqualTo("CHECKED");
        assertThat(row.statusName).isEqualTo("검사완료"); assertThat(row.successCount).isEqualTo(3);
        assertThat(row.warningCount).isEqualTo(2); assertThat(row.errorCount).isEqualTo(1);
        assertThat(row.modifiedAt).isEqualTo(labels.get("mod_dttm"));
        var issueLabels=new LinkedHashMap<String,Object>(); issueLabels.put("row_no",7); issueLabels.put("col_nm","업종");
        issueLabels.put("input_val","원문"); issueLabels.put("rsn_cn","변환 경고"); issueLabels.put("severity","경고");
        var issue=new UploadRows.Issue(); map(config,"selectIssues",issue,issueLabels);
        assertThat(issue.row).isEqualTo(7); assertThat(issue.column).isEqualTo("업종");
        assertThat(issue.inputValue).isEqualTo("원문"); assertThat(issue.reason).isEqualTo("변환 경고");
        assertThat(issue.severity).isEqualTo("경고");
        var inspection=new UploadContracts.Inspection(java.util.List.of(),java.util.List.of(),3,2,1,6);
        var bound=config.getMappedStatement(UploadMapper.class.getName()+".insertUpload")
            .getBoundSql(new UploadContracts.Check(81,"B7","clients.xlsx",1234,inspection));
        var meta=config.newMetaObject(bound.getParameterObject());
        bound.getParameterMappings().forEach(parameter -> assertThat(meta.hasGetter(parameter.getProperty())).isTrue());
    }
    private void map(Configuration config,String operation,Object row,Map<String,Object> labels) {
        var statement=config.getMappedStatement(UploadMapper.class.getName()+"."+operation);
        var mapping=statement.getResultMaps().get(0); var meta=config.newMetaObject(row);
        assertThat(mapping.getAutoMapping()).isFalse();
        assertThat(mapping.getResultMappings()).extracting(value -> value.getColumn()).containsExactlyElementsOf(labels.keySet());
        String sql=statement.getBoundSql(81L).getSql().replaceAll("\\s+"," ");
        for(var field:mapping.getResultMappings()) {
            assertThat(sql).contains(field.getColumn());
            assertThat(field.getJavaType()).isEqualTo(meta.getSetterType(field.getProperty()));
            assertThat(field.getTypeHandler()).isNotNull();
            meta.setValue(field.getProperty(),labels.get(field.getColumn()));
        }
    }
}
