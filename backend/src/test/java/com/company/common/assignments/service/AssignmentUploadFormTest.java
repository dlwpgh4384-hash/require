package com.company.common.assignments.service;

import com.company.common.assignments.mapper.AssignmentMapper;
import com.company.common.assignments.mapper.AssignmentRows.UploadDeveloper;
import com.company.common.calculation.mapper.CalculationRuleMapper;
import com.company.common.codegroups.mapper.CodeTextRow;
import com.company.common.codegroups.service.CodeTextResolver;
import com.company.common.projects.mapper.ProjectMapper;
import com.company.common.support.service.CodeFailure;
import com.company.common.support.service.ExcelExportService;
import com.company.common.uploads.dto.UploadContracts.Inspection;
import com.company.common.uploads.service.UploadWorkbookService;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * 코드 표기는 진짜 CodeTextResolver로 읽고, 그 아래 공통코드는 포장된 처음 값(V3 seed)의 TB_SY_CODE 줄을 쓴다.
 * (예전에는 resolver를 가짜로 바꿔 「높음」→「높음 (80%)」을 못 읽는 오류가 가려졌다.) 실제 DB로 같은 검사는 AssignmentPersistenceTest.
 */
class AssignmentUploadFormTest {
    AssignmentMapper mapper;ProjectMapper projects;CodeTextResolver codes;CommonAssignmentService assignments;AssignmentUploadForm form;
    final UploadWorkbookService workbook=new UploadWorkbookService(new ExcelExportService());
    static final List<CodeTextRow> SEED=seedCodes();

    /** V3 처음 값의 TB_SY_CODE 줄: (그룹, 코드, 이름, 순서, 사용, 삭제, ...) 중 사용·삭제 안 함만. */
    static List<CodeTextRow> seedCodes() {
        try {
            String seed=new ClassPathResource("db/migration/V3__common_function_seed.sql").getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            var rows=new ArrayList<CodeTextRow>();
            var m=Pattern.compile("\\('([A-Z][A-Z0-9_]*)', '([^']*)', '([^']*)', [0-9]+, '(Y|N)', '(Y|N)'").matcher(seed);
            while(m.find()) if("Y".equals(m.group(4)) && "N".equals(m.group(5))) {
                var row=new CodeTextRow();row.groupId=m.group(1);row.code=m.group(2);row.codeName=m.group(3);rows.add(row);
            }
            return rows;
        } catch(IOException e) { throw new UncheckedIOException(e); }
    }
    /** CalculationRuleMapper.xml의 세 조회를 처음 값 위에서 그대로 한다(엑셀 표기 대응표는 처음 값이 비어 있음). */
    static CalculationRuleMapper seedMapper() {
        var rules=mock(CalculationRuleMapper.class);
        when(rules.selectNormalText(anyString(),anyString())).thenAnswer(i -> SEED.stream()
            .filter(r -> r.groupId.equals(i.getArgument(0)) && (r.code.equals(i.getArgument(1)) || r.codeName.equals(i.getArgument(1))))
            .sorted(Comparator.comparing((CodeTextRow r) -> r.code)).toList());
        when(rules.selectLabelText(anyString(),anyString())).thenAnswer(i -> SEED.stream()
            .filter(r -> r.groupId.equals(i.getArgument(0)) && r.codeName.replaceAll("\\s*\\([^()]*\\)\\s*$","").equals(i.getArgument(1)))
            .sorted(Comparator.comparing((CodeTextRow r) -> r.code)).toList());
        when(rules.selectMappedText(anyString(),anyString())).thenReturn(null);
        return rules;
    }
    @BeforeEach void setup(){
        mapper=mock(AssignmentMapper.class);projects=mock(ProjectMapper.class);codes=new CodeTextResolver(seedMapper());assignments=mock(CommonAssignmentService.class);
        form=new AssignmentUploadForm(assignments,projects,mapper,codes);
        when(mapper.uploadDeveloper(anyString())).thenAnswer(i->new UploadDeveloper(i.getArgument(0),"시험 개발자","Y","DRCT"));
        when(assignments.inspectRegistration(any())).thenReturn(List.of());
    }
    List<String> row(){return List.of("P-TEST","D-TEST","BE","2026-10-01","2026-12-31","","8000000","신규","","");}
    List<String> row(int column,String value){var r=new ArrayList<>(row());r.set(column,value);return r;}
    byte[] bytes(List<List<String>> rows){return new ExcelExportService().write("투입 배정",form.headers(),rows,"admin",Instant.EPOCH);}
    Inspection inspect(String name,byte[] bytes){return form.inspect(workbook.readTabular(name,bytes,form.headers()),null);}
    void apply(String name,byte[] bytes){form.apply(inspect(name,bytes),null,1L);}
    String extension(Inspection report){return ((AssignmentUploadForm.UploadRow)report.records().get(0)).extension();}
    @Test void seedHasTheExtensionCodesThisTestReliesOn(){assertThat(SEED).filteredOn(r->r.groupId.equals("EXT_PSBL")).extracting(r->r.codeName).contains("높음 (80%)","보통 (50%)","낮음 (0%)");}
    @Test void exactTenHeadersAndBlankMmDefaultsToOneWithoutAnyWrites(){assertThat(form.headers()).containsExactly("프로젝트ID","관리번호","직무","투입일","철수일","월 MM","청구 단가","투입 구분","계약상 인력 관리번호","연장 가능성");var report=inspect("valid.xlsx",bytes(List.of(row())));assertThat(report.errorCount()).isZero();verify(assignments).inspectRegistration(argThat(b->b.path("monthlyMm").decimalValue().compareTo(java.math.BigDecimal.ONE)==0 && "BE".equals(b.path("jobCode").asText()) && "NEW".equals(b.path("inputTypeCode").asText())));verify(assignments,never()).create(any(),any());}
    @Test void malformedWorkbookIsRejected(){assertThatThrownBy(()->inspect("bad.xlsx",new byte[]{'P','K',3,4})).isInstanceOf(CodeFailure.class);verify(assignments,never()).create(any(),any());}
    @Test void renamedTextIsNotAcceptedAsExcel(){assertThatThrownBy(()->inspect("renamed.xlsx","text".getBytes(java.nio.charset.StandardCharsets.UTF_8))).isInstanceOf(CodeFailure.class);}
    /** 명세 B.5에 「같은 내용의 중복 행」 오류는 없다: 엑셀 한 줄 = 배정 한 건. */
    @Test void identicalSecondRowIsOneDuplicateErrorAndOnlyTheFirstIsKept(){var report=inspect("twice.xlsx",bytes(List.of(row(),row())));assertThat(report.errorCount()).isEqualTo(1);assertThat(report.records()).hasSize(1);assertThat(report.issues()).singleElement().satisfies(i->{assertThat(i.reason()).isEqualTo("같은 내용의 중복 행입니다.");assertThat(i.row()).isEqualTo(5);});}
    @Test void rowsThatDifferOnlyBySurroundingSpacesAreStillDuplicates(){var spaced=row(2," BE ");var report=inspect("spaced.xlsx",bytes(List.of(row(),spaced)));assertThat(report.issues()).anyMatch(i->i.reason().equals("같은 내용의 중복 행입니다."));}
    @Test void fieldValidationIsAttachedToOriginalHeaderAndRow(){when(assignments.inspectRegistration(any())).thenThrow(CodeFailure.invalid("endDate","계약 종료일을 넘습니다."));var report=inspect("invalid.xlsx",bytes(List.of(row())));assertThat(report.errorCount()).isEqualTo(1);assertThat(report.issues().get(0).column()).isEqualTo("철수일");assertThat(report.issues().get(0).row()).isEqualTo(4);verify(assignments,never()).create(any(),any());}
    @Test void validApplyInsertsNewRowsOnlyAndDoesNotSendTogetherOrCandidateIdentity(){var second=row(1,"D-OTHER");var result=mock(com.company.common.assignments.dto.AssignmentContracts.Assignment.class);when(result.assignmentId()).thenReturn("A-0001");when(assignments.create(any(),any())).thenReturn(result);apply("valid.xlsx",bytes(List.of(row(),second)));verify(assignments,times(2)).create(argThat(b->!b.has("candidateId")&&!b.has("earlyWithdrawalAssignmentIds")&&!b.has("replacementMode")&&!b.has("assignmentId")),eq(""));}
    /** 사람이 적는 「높음·보통·낮음」이 코드 이름 「높음 (80%)」 등으로 읽힌다(경고 없이). */
    @Test void specExtensionTextsResolveToSeedCodesWithoutWarnings(){
        for(var entry:Map.of("높음","H","보통","M","낮음","L").entrySet()) {
            var report=inspect("valid.xlsx",bytes(List.of(row(9,entry.getKey()))));
            assertThat(report.errorCount()).as(entry.getKey()).isZero();
            assertThat(report.issues()).as(entry.getKey()).isEmpty();
            assertThat(extension(report)).as(entry.getKey()).isEqualTo(entry.getValue());
        }
    }
    @Test void fullCodeNameAndCodeAreAlsoAccepted(){assertThat(extension(inspect("valid.xlsx",bytes(List.of(row(9,"보통 (50%)")))))).isEqualTo("M");assertThat(extension(inspect("valid.xlsx",bytes(List.of(row(9,"L")))))).isEqualTo("L");}
    @Test void blankAndUndecidedMeanNoExtensionValue(){assertThat(extension(inspect("valid.xlsx",bytes(List.of(row(9,"")))))).isEmpty();assertThat(extension(inspect("valid.xlsx",bytes(List.of(row(9,"미정")))))).isEmpty();}
    @Test void unknownExtensionTextIsOneErrorOnThatColumn(){var report=inspect("bad.xlsx",bytes(List.of(row(9,"아마도"))));assertThat(report.errorCount()).isEqualTo(1);assertThat(report.issues()).singleElement().satisfies(i->{assertThat(i.column()).isEqualTo("연장 가능성");assertThat(i.reason()).contains("아마도");});}
    @Test void unknownJobNameIsRejectedByTheRealResolver(){var report=inspect("bad.xlsx",bytes(List.of(row(2,"없는 직무"))));assertThat(report.errorCount()).isEqualTo(1);assertThat(report.issues().get(0).column()).isEqualTo("직무");}
    @Test void unregisteredDisabledAndIndirectDevelopersAreUploadErrorsOnManagementNumber(){
        when(mapper.uploadDeveloper("K-NONE")).thenReturn(null);
        when(mapper.uploadDeveloper("K-STOP")).thenReturn(new UploadDeveloper("stop","중지","N","DRCT"));
        when(mapper.uploadDeveloper("K-INDR")).thenReturn(new UploadDeveloper("indirect","관리","Y","INDRCT"));
        var report=inspect("people.xlsx",bytes(List.of(row(1,"K-NONE"),row(1,"K-STOP"),row(1,"K-INDR"))));
        assertThat(report.errorCount()).isEqualTo(3);
        assertThat(report.issues()).allMatch(i->i.column().equals("관리번호"));
        assertThat(report.issues()).extracting(i->i.reason()).containsExactlyInAnyOrder(
            "등록되지 않은 관리번호입니다. 개발자 풀에 먼저 등록해 주세요.",
            "사용 중지된 개발자의 관리번호입니다. 개발자 풀에서 확인해 주세요.",
            "간접 인력은 투입 배정 대상이 아닙니다.");
        verify(assignments,never()).inspectRegistration(any());
    }
    @Test void downstreamFailurePropagatesToUploadTransactionRatherThanContinuing(){when(assignments.create(any(),any())).thenThrow(CodeFailure.invalid("projectId","원천이 변경되었습니다."));assertThatThrownBy(()->apply("valid.xlsx",bytes(List.of(row())))).isInstanceOf(CodeFailure.class);verify(assignments,times(1)).create(any(),any());}
    @Test void cachedFormulaResultIsReadWithoutExecutingFormulaAtImport()throws Exception{try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes(List.of(row()))))){var cell=book.getSheetAt(0).getRow(3).getCell(0);cell.setCellFormula("\"P-TEST\"");book.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(cell);var out=new ByteArrayOutputStream();book.write(out);assertThat(inspect("cached.xlsx",out.toByteArray()).errorCount()).isZero();}}
    @Test void externalRelationshipIsForbidden()throws Exception{try(var book=new XSSFWorkbook(new ByteArrayInputStream(bytes(List.of(row()))))){book.getPackagePart().addExternalRelationship("https://example.invalid/external.xlsx","http://schemas.openxmlformats.org/officeDocument/2006/relationships/externalLink");var out=new ByteArrayOutputStream();book.write(out);assertThatThrownBy(()->inspect("external.xlsx",out.toByteArray())).isInstanceOf(CodeFailure.class);}}
    @Test void usesTenMillionByteBoundNotOldTenMiBBound(){assertThatThrownBy(()->inspect("large.xlsx",new byte[10_000_001])).isInstanceOf(CodeFailure.class);}
}
