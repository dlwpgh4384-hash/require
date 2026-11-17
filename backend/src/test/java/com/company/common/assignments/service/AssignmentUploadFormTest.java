package com.company.common.assignments.service;

import com.company.common.assignments.mapper.AssignmentMapper;
import com.company.common.codegroups.service.CodeTextResolver;
import com.company.common.calculation.dto.CalculationRuleContracts.TextResolution;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.ExcelExportService;
import com.company.common.uploads.dto.UploadContracts.Inspection;
import com.company.common.uploads.service.UploadWorkbookService;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.time.Instant;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AssignmentUploadFormTest {
    AssignmentMapper mapper;ProjectMapper projects;CodeTextResolver codes;CommonAssignmentService assignments;AssignmentUploadForm form;
    final UploadWorkbookService workbook=new UploadWorkbookService(new ExcelExportService());
    @BeforeEach void setup(){mapper=mock(AssignmentMapper.class);projects=mock(ProjectMapper.class);codes=mock(CodeTextResolver.class);assignments=mock(CommonAssignmentService.class);form=new AssignmentUploadForm(assignments,projects,mapper,codes);when(projects.uploadDeveloper(anyString())).thenAnswer(i->new ProjectMapper.Developer(i.getArgument(0),"시험 개발자"));when(codes.resolve(anyString(),anyString())).thenAnswer(i->new TextResolution(i.getArgument(0),i.getArgument(1),i.getArgument(1),null));when(assignments.inspectRegistration(any())).thenReturn(List.of());}
    List<String> row(){return List.of("P-TEST","D-TEST","DEV","2026-10-01","2026-12-31","","8000000","NEW","","");}
    byte[] bytes(List<List<String>> rows){return new ExcelExportService().write("투입 배정",form.headers(),rows,"admin",Instant.EPOCH);}
    Inspection inspect(String name,byte[] bytes){return form.inspect(workbook.readTabular(name,bytes,form.headers()),null);}
    void apply(String name,byte[] bytes){form.apply(inspect(name,bytes),null,1L);}
    @Test void exactTenHeadersAndBlankMmDefaultsToOneWithoutAnyWrites(){assertThat(form.headers()).containsExactly("프로젝트ID","관리번호","직무","투입일","철수일","월 MM","청구 단가","투입 구분","계약상 인력 관리번호","연장 가능성");var report=inspect("valid.xlsx",bytes(List.of(row())));assertThat(report.errorCount()).isZero();verify(assignments).inspectRegistration(argThat(b->b.path("monthlyMm").decimalValue().compareTo(java.math.BigDecimal.ONE)==0));verify(assignments,never()).create(any(),any());}
    @Test void malformedWorkbookIsRejected(){assertThatThrownBy(()->inspect("bad.xlsx",new byte[]{'P','K',3,4})).isInstanceOf(CodeFailure.class);verify(assignments,never()).create(any(),any());}
    @Test void renamedTextIsNotAcceptedAsExcel(){assertThatThrownBy(()->inspect("renamed.xlsx","text".getBytes(java.nio.charset.StandardCharsets.UTF_8))).isInstanceOf(CodeFailure.class);}
    @Test void duplicateRowsReportErrorAndApplyHasZeroWrites(){byte[] file=bytes(List.of(row(),row()));assertThat(inspect("duplicate.xlsx",file).issues()).anyMatch(e->e.reason().contains("중복 행"));assertThatThrownBy(()->apply("duplicate.xlsx",file)).isInstanceOf(CodeFailure.class);verify(assignments,never()).create(any(),any());}
    @Test void fieldValidationIsAttachedToOriginalHeaderAndRow(){when(assignments.inspectRegistration(any())).thenThrow(CodeFailure.invalid("endDate","계약 종료일을 넘습니다."));var report=inspect("invalid.xlsx",bytes(List.of(row())));assertThat(report.errorCount()).isEqualTo(1);assertThat(report.issues().get(0).column()).isEqualTo("철수일");assertThat(report.issues().get(0).row()).isEqualTo(4);verify(assignments,never()).create(any(),any());}
    @Test void validApplyInsertsNewRowsOnlyAndDoesNotSendTogetherOrCandidateIdentity(){var second=new ArrayList<>(row());second.set(1,"D-OTHER");var result=mock(com.company.common.assignments.dto.AssignmentContracts.Assignment.class);when(result.assignmentId()).thenReturn("A-0001");when(assignments.create(any(),any())).thenReturn(result);apply("valid.xlsx",bytes(List.of(row(),second)));verify(assignments,times(2)).create(argThat(b->!b.has("candidateId")&&!b.has("earlyWithdrawalAssignmentIds")&&!b.has("replacementMode")&&!b.has("assignmentId")),eq(""));}
    @Test void onlyB5PassesExtensionCell(){var row=new ArrayList<>(row());row.set(9,"높음");when(codes.resolve("EXT_PSBL","높음")).thenReturn(new TextResolution("EXT_PSBL","H","높음",null));var report=inspect("valid.xlsx",bytes(List.of(row)));assertThat(((AssignmentUploadForm.UploadRow)report.records().get(0)).extension()).isEqualTo("H");}
    @Test void downstreamFailurePropagatesToUploadTransactionRatherThanContinuing(){when(assignments.create(any(),any())).thenThrow(CodeFailure.invalid("projectId","원천이 변경되었습니다."));assertThatThrownBy(()->apply("valid.xlsx",bytes(List.of(row())))).isInstanceOf(CodeFailure.class);verify(assignments,times(1)).create(any(),any());}
    @Test void cachedFormulaResultIsReadWithoutExecutingFormulaAtImport()throws Exception{try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes(List.of(row()))))){var cell=book.getSheetAt(0).getRow(3).getCell(0);cell.setCellFormula("\"P-TEST\"");book.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(cell);var out=new ByteArrayOutputStream();book.write(out);assertThat(inspect("cached.xlsx",out.toByteArray()).errorCount()).isZero();}}
    @Test void externalRelationshipIsForbidden()throws Exception{try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes(List.of(row()))))){book.getPackagePart().addExternalRelationship("https://example.invalid/external.xlsx","http://schemas.openxmlformats.org/officeDocument/2006/relationships/externalLink");var out=new ByteArrayOutputStream();book.write(out);assertThatThrownBy(()->inspect("external.xlsx",out.toByteArray())).isInstanceOf(CodeFailure.class);}}
    @Test void usesTenMillionByteBoundNotOldTenMiBBound(){assertThatThrownBy(()->inspect("large.xlsx",new byte[10_000_001])).isInstanceOf(CodeFailure.class);}
}
