package com.company.common.projects.service;

import com.company.common.calculation.dto.CalculationRuleContracts.Definition;
import com.company.common.calculation.dto.CalculationRuleContracts.TextResolution;
import com.company.common.calculation.service.CalculationRuleService;
import com.company.common.codegroups.service.CodeTextResolver;
import com.company.common.projects.dto.ProjectContracts.*;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.service.*;
import com.company.common.uploads.dto.UploadWorkbookContracts.TabularRow;
import com.company.common.uploads.service.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectUploadTest {
    ProjectMapper mapper; CommonProjectService projects; CodeTextResolver resolver;
    ProjectUploadForm b9; ProjectDistributionUploadForm b6;
    com.company.common.developers.mapper.DeveloperMapper areas;
    @BeforeEach void setup() {
        mapper=mock(ProjectMapper.class);projects=mock(CommonProjectService.class);resolver=mock(CodeTextResolver.class);
        areas=mock(com.company.common.developers.mapper.DeveloperMapper.class);
        b9=new ProjectUploadForm(mapper,resolver,projects,new com.company.common.developers.service.UploadAreaResolver(areas)); b6=new ProjectDistributionUploadForm(mapper,projects,resolver,new CurrentBusinessActor());
        when(mapper.projectIds()).thenReturn(List.of("P-0001"));when(mapper.codeName(anyString(),anyString())).thenAnswer(c->c.getArgument(1));
        when(resolver.resolve(anyString(),anyString())).thenAnswer(c->new TextResolution(c.getArgument(0),c.getArgument(1),c.getArgument(1),null));
        when(projects.create(any(),anyString())).thenAnswer(c->{var p=new Project();p.projectId=c.getArgument(1);return p;});
        var p=new Project();p.projectId="P-0001";p.contractTypeCode="TK";p.canEdit=true;p.startDate=LocalDate.of(2026,11,1);p.endDate=LocalDate.of(2026,12,31);
        when(projects.getCommonProject("P-0001")).thenReturn(p);
    }
    TabularRow project(int n,String id) { return new TabularRow(n,List.of(id,"내부 이관","","IN","2026-11-01","2026-12-31","","","ING","","","","","","특징"),List.of()); }
    UploadForm bound(String stage) { return b6.withContext(new UploadForm.Context("P-0001",stage)); }
    @Test void validB9InspectionHasNoWritesAndAssignsAroundExistingAndFileExplicitIds() {
        var check=b9.inspect(List.of(project(2,""),project(3,"P-0002")),null);
        assertThat(check.errorCount()).isZero();assertThat(check.assigned()).containsEntry(2,"P-0003").containsEntry(3,"P-0002");
        verify(projects,never()).create(any(),anyString());verify(mapper,never()).insertProject(any());
        assertThat(b9.apply(check,null,17)).isEqualTo(check.assigned());verify(projects,times(2)).create(any(),anyString());
    }
    @Test void duplicateExplicitB9IdIsASeparateRowError() {
        var check=b9.inspect(List.of(project(2,"P-file"),project(3,"P-file")),null);assertThat(check.errorCount()).isEqualTo(1);
        assertThat(check.issues()).anyMatch(i->i.row()==3&&i.column().equals("프로젝트ID"));
    }
    // SFR-012-03: 점·빗금 날짜(2026.11.01, 2026/12/31)도 받는다(엑셀 점검에서 찾음, 2026-11-19).
    @Test void dottedAndSlashedB9DatesAreAccepted() {
        var row=project(2,"");var values=new ArrayList<>(row.values());values.set(4,"2026.11.01");values.set(5,"2026/12/31");
        var check=b9.inspect(List.of(new TabularRow(2,values,List.of())),null);
        assertThat(check.issues()).noneMatch(i->i.column().equals("시작일")||i.column().equals("종료일"));
    }
    @Test void reversedB9DatesAreRejectedBeforeWriting() {
        // 수식 칸은 엑셀이 저장해 둔 값으로 읽으므로(SFR-012-03) 날짜 오류만 본다.
        var row=project(2,"");var values=new ArrayList<>(row.values());values.set(5,"2026-10-01");
        var check=b9.inspect(List.of(new TabularRow(2,values,List.of())),null);assertThat(check.errorCount()).isEqualTo(1);
        assertThat(check.issues()).anyMatch(i->i.column().equals("종료일")&&i.reason().equals("종료일이 시작일보다 앞입니다."));
        verifyNoInteractions(projects);
    }
    @Test void blankB9StatusMeansInProgressAndAmountsMayHaveCommas() {
        var values=new ArrayList<>(project(2,"").values());values.set(8,"");values.set(9,"1,200,000");
        var check=b9.inspect(List.of(new TabularRow(2,values,List.of())),null);
        assertThat(check.errorCount()).isZero();
        var input=((ProjectUploadForm.Row)check.records().get(0)).input();
        assertThat(input.statusCode()).isEqualTo("ING");assertThat(input.estimateAmount()).isEqualByComparingTo("1200000");
    }
    @Test void ambiguousCustomerListsEveryBusinessNumber() {
        when(mapper.uploadCustomers()).thenReturn(List.of(new ProjectMapper.Customer("one","같은상호","111-11-11111"),new ProjectMapper.Customer("two","같은상호","222-22-22222")));
        var part=new com.company.common.projects.mapper.ProjectRows.Job();part.code="part";part.name="영업";when(mapper.uploadParts()).thenReturn(List.of(part));
        var values=new ArrayList<>(project(2,"").values());values.set(2,"같은상호");values.set(3,"TK");values.set(6,"part");values.set(10,"100");
        var check=b9.inspect(List.of(new TabularRow(2,values,List.of())),null);
        assertThat(check.issues()).anyMatch(i->i.column().equals("고객사")&&i.reason().contains("111-11-11111")&&i.reason().contains("222-22-22222"));
        values.set(2,"222-22-22222");assertThat(b9.inspect(List.of(new TabularRow(2,values,List.of())),null).errorCount()).isZero();
    }
    @Test void nameConversionUsesSharedReadAsMessage() {
        when(resolver.resolve("CTRT_TYPE","내부별칭")).thenReturn(new TextResolution("CTRT_TYPE","IN","내부","converted"));
        var values=new ArrayList<>(project(2,"").values());values.set(3,"내부별칭");
        assertThat(b9.inspect(List.of(new TabularRow(2,values,List.of())),null).issues()).anyMatch(i->i.reason().equals(CodeTextResolver.readAs("내부")));
    }
    @Test void b6ContextsAreImmutableAndCannotLeakTheirStage() {
        var first=bound("EST");var second=bound("CTRT");
        assertThat(first.headers()).containsExactly("구분","관리번호","이름","항목명","2026-11","2026-12","합계");
        var check=first.inspect(List.of(new TabularRow(2,List.of("ITEM","","","장비","10","","10"),List.of())),null);
        assertThat(check.errorCount()).isZero();first.apply(check,null,17);second.apply(check,null,18);
        verify(mapper).deleteDistribution("P-0001","EST");verify(mapper).deleteDistribution("P-0001","CTRT");
        verify(mapper,times(4)).insertDistribution(argThat(c->c.month().matches("2026(11|12)")&&c.amount().signum()>=0));
    }
    @Test void b6RejectsUnknownNumberAndNameMismatchAndLongItem() {
        when(mapper.uploadDeveloper("K-known")).thenReturn(new ProjectMapper.Developer("dev","등록이름"));
        var rows=List.of(new TabularRow(2,List.of("DEV","missing","이름","","0","0","0"),List.of()),new TabularRow(3,List.of("DEV","K-known","다른이름","","0","0","0"),List.of()),new TabularRow(4,List.of("ITEM","","","가".repeat(51),"0","0","0"),List.of()));
        assertThat(bound("EST").inspect(rows,null).errorCount()).isEqualTo(3);verify(mapper,never()).deleteDistribution(any(),any());
    }
    @Test void b6BlankMonthIsZeroAndTotalIsComparisonOnly() {
        var f=bound("EST");var check=f.inspect(List.of(new TabularRow(2,List.of("ITEM","","","장비","","10","999"),List.of())),null);
        assertThat(check.errorCount()).isZero();assertThat(check.warningCount()).isEqualTo(1);f.apply(check,null,17);
        verify(mapper).insertDistribution(argThat(c->c.month().equals("202611")&&c.amount().signum()==0));
        verify(mapper).insertDistribution(argThat(c->c.month().equals("202612")&&c.amount().compareTo(BigDecimal.TEN)==0));
    }
    @Test void duplicateDistributionRowRejectsTheWholeFileAtInspection() {
        var row=List.of("ITEM","","","장비","1","2","3");
        var check=bound("EST").inspect(List.of(new TabularRow(2,row,List.of()),new TabularRow(3,row,List.of())),null);
        assertThat(check.errorCount()).isEqualTo(1);verify(mapper,never()).deleteDistribution(any(),any());
    }
    @Test void workbookGuardRejectsMalformedAndRenamedPayloadAndReadsFormulaAsSavedResult() throws Exception {
        var reader=new UploadWorkbookService(new ExcelExportService());
        assertThatThrownBy(()->reader.readTabular("file.xlsx","not xlsx".getBytes(),b9.headers())).isInstanceOf(CodeFailure.class);
        byte[] bytes=reader.tabularFile(b9.headers(),List.of(project(2,"").values()),null);
        assertThat(reader.readTabular("file.xlsx",bytes,b9.headers())).hasSize(1);
        assertThatThrownBy(()->reader.readTabular("file.xls",bytes,b9.headers())).isInstanceOf(CodeFailure.class);
        try(var workbook=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes));var out=new java.io.ByteArrayOutputStream()) {
            // SFR-012-03: 수식 칸은 엑셀이 저장해 둔 결과값으로 읽는다(오류로 막지 않음).
            var cell = workbook.getSheetAt(0).getRow(1).getCell(10); cell.setCellFormula("1+1");
            workbook.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(cell); workbook.write(out);
            var rows = reader.readTabular("file.xlsx", out.toByteArray(), b9.headers());
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).formulaColumns()).isEmpty();
            assertThat(rows.get(0).values().get(10)).isEqualTo("2");
            assertThat(b9.inspect(rows, null).issues()).noneMatch(issue -> issue.reason().contains("수식"));
        }
    }
    // 근무지 지역(2026-11-25): 맨 뒤 세 칸, 개발자 거주지와 같은 규칙(못 찾으면 찾은 단계까지 저장하고 경고).
    static com.company.common.developers.mapper.DeveloperRows.Area area(String code,String name) {
        var a=new com.company.common.developers.mapper.DeveloperRows.Area();a.areaCode=code;a.areaName=name;return a;
    }
    TabularRow external(String sido,String sgg,String dong) {
        when(mapper.uploadCustomers()).thenReturn(List.of(new ProjectMapper.Customer("one","고객","111-11-11111")));
        var part=new com.company.common.projects.mapper.ProjectRows.Job();part.code="part";part.name="영업";when(mapper.uploadParts()).thenReturn(List.of(part));
        var values=new ArrayList<>(project(2,"").values());values.set(2,"고객");values.set(3,"TK");values.set(6,"part");values.set(10,"100");
        values.addAll(List.of(sido,sgg,dong));
        return new TabularRow(2,values,List.of());
    }
    @Test void b9WorkAreaStoresTheLastChosenLevel() {
        when(areas.selectAreasByName("SIDO",null,"서울특별시")).thenReturn(List.of(area("1100000000","서울")));
        when(areas.selectAreasByName("SGG","1100000000","중구")).thenReturn(List.of(area("1114000000","중구")));
        when(areas.selectAreasByName("EMD","1114000000","을지로동")).thenReturn(List.of(area("1114010000","을지로동")));
        var check=b9.inspect(List.of(external("서울특별시","중구","을지로동")),null);
        assertThat(check.issues()).isEmpty();
        assertThat(((ProjectUploadForm.Row)check.records().get(0)).input().workAreaCode()).isEqualTo("1114010000");
        check=b9.inspect(List.of(external("서울특별시","중구","")),null);
        assertThat(((ProjectUploadForm.Row)check.records().get(0)).input().workAreaCode()).isEqualTo("1114000000");
        check=b9.inspect(List.of(external("","","")),null);
        assertThat(check.issues()).isEmpty();
        assertThat(((ProjectUploadForm.Row)check.records().get(0)).input().workAreaCode()).isNull();
    }
    @Test void b9UnknownWorkAreaPartsAreWarningsThatKeepTheFoundLevel() {
        when(areas.selectAreasByName("SIDO",null,"서울특별시")).thenReturn(List.of(area("1100000000","서울")));
        var check=b9.inspect(List.of(external("서울특별시","없는구","")),null);
        assertThat(check.errorCount()).isZero();assertThat(check.warningCount()).isEqualTo(1);
        assertThat(check.issues()).singleElement().satisfies(i->{
            assertThat(i.column()).isEqualTo("근무지(시·군·구)");assertThat(i.severity()).isEqualTo("경고");
            assertThat(i.reason()).isEqualTo("시·군·구를 못 찾아 비웠습니다. 「서울」까지 저장합니다.");});
        assertThat(((ProjectUploadForm.Row)check.records().get(0)).input().workAreaCode()).isEqualTo("1100000000");
        check=b9.inspect(List.of(external("없는도","","")),null);
        assertThat(check.issues()).singleElement().satisfies(i->{
            assertThat(i.column()).isEqualTo("근무지(시·도)");assertThat(i.reason()).isEqualTo("근무지를 못 찾아 비웠습니다.");});
        assertThat(((ProjectUploadForm.Row)check.records().get(0)).input().workAreaCode()).isNull();
        check=b9.inspect(List.of(external("","중구","")),null);
        assertThat(check.issues()).singleElement().satisfies(i->{assertThat(i.inputValue()).isEqualTo("(빈칸)");assertThat(i.severity()).isEqualTo("경고");});
    }
    @Test void b9InternalProjectIgnoresWorkAreaAndShortRowsHaveNoArea() {
        var values=new ArrayList<>(project(2,"").values());values.addAll(List.of("없는도","",""));
        var check=b9.inspect(List.of(new TabularRow(2,values,List.of())),null);
        assertThat(check.issues()).isEmpty();verifyNoInteractions(areas);
        assertThat(b9.inspect(List.of(project(2,"")),null).errorCount()).isZero();
    }
    @Test void b9TemplateHasWorkAreaColumnsLastAndOldFilesWithoutThemStillRead() {
        assertThat(b9.headers().subList(15,18)).containsExactly("근무지 시·도","근무지 시·군·구","근무지 동");
        assertThat(b9.exampleRow()).hasSize(b9.headers().size());
        var reader=new UploadWorkbookService(new ExcelExportService());
        byte[] old=reader.tabularFile(b9.headers().subList(0,15),List.of(project(2,"").values()),null);
        assertThatThrownBy(()->reader.readTabular("old.xlsx",old,b9.headers())).isInstanceOf(CodeFailure.class);
        var rows=reader.readTabular("old.xlsx",old,b9.headers(),b9.optionalHeaders());
        assertThat(rows).singleElement().satisfies(r->{assertThat(r.values()).hasSize(18);assertThat(r.values().subList(15,18)).containsOnly("");});
    }
    @Test void progressIsComputedInCodeEvenWhenDefinitionTextDiffers() {
        var metadata=mock(CalculationRuleService.class);var rules=new ProjectProgressRules(metadata,new com.company.common.common.ProjectBoardSignalRules());var step=new Step();step.weightRate=BigDecimal.TEN;step.plannedStartDate=LocalDate.of(2026,11,1);step.plannedEndDate=LocalDate.of(2026,11,10);
        when(metadata.definition("식-73")).thenReturn(new Definition("식-73","Runtime.exec()",""));
        assertThat(rules.progress("TK",List.of(step),null,LocalDate.of(2026,11,5)).plannedProgressRate()).isEqualByComparingTo("5.0");
        verifyNoInteractions(metadata);
    }

}
