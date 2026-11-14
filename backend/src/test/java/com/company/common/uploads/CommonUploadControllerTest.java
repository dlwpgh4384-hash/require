package com.company.common.uploads;

import com.company.common.controller.ApiExceptionHandler;
import com.company.common.controller.CodeExceptionHandler;
import com.company.common.dto.ClientContracts.Write;
import com.company.common.mapper.CalculationRuleMapper;
import com.company.common.mapper.ClientMapper;
import com.company.common.mapper.CodeTextRow;
import com.company.common.mapper.UserMapper;
import com.company.common.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static com.company.common.contracts.OpenApiResponseContract.response;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommonUploadController.class)
@ContextConfiguration(classes = {CommonUploadController.class, CommonUploadService.class, ClientUploadRules.class,
    UploadClientWriter.class, CodeTextResolver.class, CurrentBusinessActor.class, UploadWorkbookService.class,
    ExcelExportService.class, CodeExceptionHandler.class, ApiExceptionHandler.class})
class CommonUploadControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UploadWorkbookService workbooks;
    @MockBean UploadMapper mapper;
    @MockBean ClientMapper clients;
    @MockBean UserMapper management;
    @MockBean CalculationRuleMapper codes;
    @MockBean UploadFileStore files;
    @MockBean PaymentAccountCipher cipher;
    @MockBean PlatformTransactionManager transactions;
    final Map<Long, UploadRows.Upload> uploads = new HashMap<>();
    final Map<Long, List<UploadRows.Issue>> issues = new HashMap<>();
    final Map<Long, byte[]> originals = new HashMap<>();
    final List<Write> writes = new ArrayList<>();

    @BeforeEach void setup() throws Exception {
        String contract = new org.springframework.core.io.ClassPathResource("contracts/openapi.yaml")
            .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(contract).contains("operationId: postCommonUpload", "operationId: postCommonUploadApply",
            "operationId: getCommonUpload", "operationId: getCommonUploadErrors", "operationId: getCommonUploadTemplate");
        uploads.clear(); issues.clear(); originals.clear(); writes.clear();
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(clients.codeExists("FORM_TYPE", "B7")).thenReturn(true);
        normal("CLNT_TYPE", "CUST", "고객사"); normal("CLNT_TYPE", "VNDR", "협력사"); normal("CLNT_TYPE", "BOTH", "고객·협력");
        normal("INDUTY", "IT", "정보통신"); normal("BANK", "004", "은행");
        when(mapper.nextUploadId()).thenReturn(81L);
        doAnswer(call -> { originals.put(call.getArgument(0), ((byte[]) call.getArgument(1)).clone()); return null; })
            .when(files).retain(anyLong(), any());
        when(files.read(anyLong())).thenAnswer(call -> originals.get(call.getArgument(0)));
        when(mapper.insertUpload(any())).thenAnswer(call -> {
            UploadContracts.Check value = call.getArgument(0);
            var row = new UploadRows.Upload(); row.uploadId=value.uploadId(); row.fileName=value.fileName();
            row.formTypeCode=value.formTypeCode(); row.statusCode="CHECKED"; row.statusName="검사완료";
            counts(row,value.inspection()); uploads.put(row.uploadId,row); issues.put(row.uploadId,new ArrayList<>()); return 1;
        });
        when(mapper.selectUpload(anyLong())).thenAnswer(call -> uploads.get(call.getArgument(0)));
        when(mapper.lockUpload(anyLong())).thenAnswer(call -> uploads.get(call.getArgument(0)));
        when(mapper.selectIssues(anyLong())).thenAnswer(call -> issues.getOrDefault(call.getArgument(0),List.of()));
        when(mapper.insertIssue(any())).thenAnswer(call -> {
            UploadContracts.IssueWrite value=call.getArgument(0); var issue=new UploadRows.Issue();
            issue.row=value.issue().row(); issue.column=value.issue().column(); issue.inputValue=value.issue().inputValue();
            issue.reason=value.issue().reason(); issue.severity=value.issue().severity(); issues.get(value.uploadId()).add(issue); return 1;
        });
        when(mapper.deleteIssues(anyLong())).thenAnswer(call -> { issues.get(call.getArgument(0)).clear(); return 1; });
        when(mapper.completeUpload(any())).thenAnswer(call -> {
            UploadContracts.Completion value=call.getArgument(0); var row=uploads.get(value.uploadId()); counts(row,value.inspection());
            row.statusCode=value.applied()?"APPLIED":"CHECKED"; row.statusName=value.applied()?"반영완료":"검사완료";
            row.modifiedAt=Instant.parse("2030-01-01T00:00:00.123456Z"); return 1;
        });
        when(cipher.encrypt(anyString())).thenReturn("encrypted-account");
        when(clients.insertClient(any())).thenAnswer(call -> { writes.add(call.getArgument(0)); return 1; });
    }
    private void normal(String group,String code,String name) {
        var row=new CodeTextRow(); row.groupId=group; row.code=code; row.codeName=name;
        when(codes.selectNormalText(group,code)).thenReturn(List.of(row));
        when(codes.selectNormalText(group,name)).thenReturn(List.of(row));
    }
    private void counts(UploadRows.Upload row,UploadContracts.Inspection value) {
        row.successCount=value.successCount(); row.warningCount=value.warningCount(); row.errorCount=value.errorCount();
    }
    private List<String> customer(String number) {
        return List.of("고객사","검사 거래처",number,"대표","정보통신","담당","010","client@example.test","","","");
    }
    private byte[] workbook(List<List<String>> rows) { return workbooks.tabularFile(ClientUploadRules.HEADERS,rows,null); }
    private org.springframework.test.web.servlet.ResultActions check(byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/common/uploads").file(new MockMultipartFile("file","clients.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",bytes)).param("formTypeCode","B7"));
    }
    private org.springframework.test.web.servlet.ResultActions apply(String body) throws Exception {
        return mvc.perform(post("/api/common/uploads/81/apply").contentType("application/json").content(body));
    }
    @Test
    @DisplayName("auth happy side-effect: TB_SY_UPLD 검사 결과 저장, TB_BS_CLNT 쓰기 없음")
    void anonymousCheckReturnsCountsAndGetPersistsResultWithoutRegisteringClients() throws Exception {
        mvc.perform(multipart("/api/common/uploads").file(new MockMultipartFile("file", "clients.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            workbook(List.of(customer("123-45-67890"))))).param("formTypeCode", "B7"))
            .andExpect(status().isOk())
            .andExpect(response("POST", "/api/common/uploads"))
            .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.data.uploadId").value("81"))
            .andExpect(jsonPath("$.data.successCount").value(1)).andExpect(jsonPath("$.data.warningCount").value(0))
            .andExpect(header().doesNotExist("Location"));
        mvc.perform(get("/api/common/uploads/81")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.statusName").value("검사완료")).andExpect(jsonPath("$.data.issues").isEmpty());
        verify(mapper).insertUpload(argThat(value -> value.uploadId() == 81 && value.inspection().successCount() == 1));
        verify(clients,never()).insertClient(any()); verifyNoInteractions(management);
    }
    @Test
    @DisplayName("validation side-effect: 이메일 누락은 TB_SY_UPLD_ERR에 행·열 오류로 저장한다")
    void customerMissingEmailIsADataErrorWithPhysicalRowAndColumn() throws Exception {
        var row=new ArrayList<>(customer("123-45-67890")); row.set(7,"");
        mvc.perform(multipart("/api/common/uploads").file(new MockMultipartFile("file", "clients.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook(List.of(row))))
            .param("formTypeCode", "B7")).andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCount").value(1))
            .andExpect(jsonPath("$.data.issues[0].row").value(2)).andExpect(jsonPath("$.data.issues[0].column").value("이메일"))
            .andExpect(jsonPath("$.data.issues[0].reason").value("필수 입력입니다."));
        assertThat(writes).isEmpty();
        verify(mapper).insertIssue(argThat(value -> value.uploadId() == 81 && value.issue().row() == 2
            && value.issue().column().equals("이메일") && value.issue().severity().equals("오류")));
    }
    @Test
    @DisplayName("business: 등록된 사업자번호는 검사 오류이며 TB_BS_CLNT 수정은 없다")
    void registeredBusinessNumberIsNeverUpdated() throws Exception {
        when(clients.selectBusinessOwner("123-45-67890")).thenReturn("기존 거래처");
        mvc.perform(multipart("/api/common/uploads").file(new MockMultipartFile("file", "clients.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            workbook(List.of(customer("123-45-67890"))))).param("formTypeCode", "B7")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.errorCount").value(1));
        apply("{\"modifiedAt\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCount").value(1));
        verify(clients,never()).updateClient(any()); assertThat(writes).isEmpty();
    }
    @Test void mappedTextWarnsAndOriginalWarningSurvivesGet() throws Exception {
        var mapped=new CodeTextRow(); mapped.groupId="CLNT_TYPE"; mapped.code="CUST"; mapped.codeName="고객사";
        when(codes.selectMappedText("CLNT_TYPE","옛 고객")).thenReturn(mapped);
        var row=new ArrayList<>(customer("123-45-67890")); row.set(0,"옛 고객");
        check(workbook(List.of(row))).andExpect(status().isOk()).andExpect(jsonPath("$.data.successCount").value(0))
            .andExpect(jsonPath("$.data.warningCount").value(1)).andExpect(jsonPath("$.data.issues[0].inputValue").value("옛 고객"));
        mvc.perform(get("/api/common/uploads/81")).andExpect(jsonPath("$.data.issues[0].severity").value("경고"));
        verify(mapper).insertIssue(argThat(value -> "경고".equals(value.issue().severity())));
    }
    @Test
    @DisplayName("auth happy side-effect: 경고 전량 반영은 TB_BS_CLNT와 TB_SY_UPLD 결과를 함께 저장한다")
    void anonymousWarningOnlyApplyUsesRetainedFileAndRegistersAllWithAdminAndOpaqueIds() throws Exception {
        var mapped=new CodeTextRow(); mapped.groupId="INDUTY"; mapped.code="IT"; mapped.codeName="정보통신";
        when(codes.selectMappedText("INDUTY","옛 업종")).thenReturn(mapped);
        var row=new ArrayList<>(customer("123-45-67890")); row.set(4,"옛 업종");
        byte[] original=workbook(List.of(row,customer("987-65-43210")));
        check(original).andExpect(status().isOk());
        mvc.perform(post("/api/common/uploads/81/apply").contentType("application/json").content("{\"modifiedAt\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.warningCount").value(1))
            .andExpect(jsonPath("$.data.successCount").value(1)).andExpect(jsonPath("$.data.errorCount").value(0));
        assertThat(writes).hasSize(2).allSatisfy(value -> {
            assertThat(value.actor()).isEqualTo("admin"); assertThat(value.clientId()).matches("[0-9a-f]{20}");
            assertThat(value.input().industryCode()).isEqualTo("IT");
        });
        verify(files).read(81); assertThat(originals.get(81L)).isEqualTo(original);
        verify(management,times(2)).insertHistory(any());
        verify(mapper).completeUpload(argThat(value -> value.uploadId() == 81 && value.applied()
            && value.inspection().successCount() + value.inspection().warningCount() == 2));
        mvc.perform(get("/api/common/uploads/81")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.statusName").value("반영완료"))
            .andExpect(jsonPath("$.data.issues[0].severity").value("경고"));
    }
    @Test
    @DisplayName("validation: modifiedAt 누락 반영은 거래처 추가와 업로드 갱신 없이 거부한다")
    void missingModifiedAtIs400BeforeAnyWrite() throws Exception {
        mvc.perform(post("/api/common/uploads/81/apply").contentType("application/json").content("{}"))
            .andExpect(response("POST", "/api/common/uploads/{uploadId}/apply"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("modifiedAt"));
        assertThat(writes).isEmpty(); verify(mapper,never()).completeUpload(any());
    }
    @Test void missingFileIs400WithFileFieldAndCreatesNothing() throws Exception {
        mvc.perform(multipart("/api/common/uploads").param("formTypeCode", "B7"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("file"));
        verify(mapper, never()).insertUpload(any()); verifyNoInteractions(files);
    }
    @Test void staleNullableVersionReturns409WithoutNewIssuesOrClients() throws Exception {
        check(workbook(List.of(customer("123-45-67890")))).andExpect(status().isOk());
        apply("{\"modifiedAt\":\"2031-01-01T00:00:00Z\"}").andExpect(status().isConflict());
        assertThat(writes).isEmpty(); verify(mapper,never()).deleteIssues(anyLong());
    }
    @Test
    @DisplayName("business: 반영 직전 사업자번호 재검사 오류는 전체 거래처 추가를 막는다")
    void competingRegistrationDetectedOnRecheckBlocksEveryRow() throws Exception {
        check(workbook(List.of(customer("123-45-67890"),customer("987-65-43210")))).andExpect(status().isOk());
        when(clients.selectBusinessOwner("987-65-43210")).thenReturn("다른 요청");
        mvc.perform(post("/api/common/uploads/81/apply").contentType("application/json").content("{\"modifiedAt\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCount").value(1))
            .andExpect(jsonPath("$.data.successCount").value(1));
        assertThat(writes).isEmpty(); assertThat(uploads.get(81L).statusCode).isEqualTo("CHECKED");
        verify(clients,never()).updateClient(any());
    }
    @Test void appliedReplayPrecedesStaleVersionAndDoesNotInsertAgain() throws Exception {
        check(workbook(List.of(customer("123-45-67890")))).andExpect(status().isOk());
        apply("{\"modifiedAt\":null}").andExpect(status().isOk());
        apply("{\"modifiedAt\":null}").andExpect(status().isOk());
        assertThat(writes).hasSize(1); verify(files,times(1)).read(81);
    }
    @Test void mixedRowsBlockAllAndFormulaIsARowErrorNotEvaluated() throws Exception {
        try(var wb=new XSSFWorkbook(new ByteArrayInputStream(workbook(List.of(customer("123-45-67890"),customer("987-65-43210")))))) {
            wb.getSheetAt(0).getRow(2).getCell(1).setCellFormula("1+1");
            var output=new java.io.ByteArrayOutputStream(); wb.write(output);
            check(output.toByteArray()).andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCount").value(1))
                .andExpect(jsonPath("$.data.issues[0].reason").value("수식은 사용할 수 없습니다."));
            apply("{\"modifiedAt\":null}").andExpect(status().isOk()); assertThat(writes).isEmpty();
        }
    }
    @Test void duplicateRowsAreBothErrors() throws Exception {
        check(workbook(List.of(customer("123-45-67890"),customer("123-45-67890")))).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.errorCount").value(2)).andExpect(jsonPath("$.data.successCount").value(0));
    }
    @Test void unknownIndustryAndBankHaveSeparateColumnIssues() throws Exception {
        var row=new ArrayList<>(customer("123-45-67890")); row.set(4,"없는 업종"); row.set(8,"없는 은행");
        check(workbook(List.of(row))).andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCount").value(1))
            .andExpect(jsonPath("$.data.issues[0].column").value("업종")).andExpect(jsonPath("$.data.issues[1].column").value("은행"));
    }
    @Test void vendorPaymentFieldsAreRequiredAndSensitiveInputIsNotRecorded() throws Exception {
        var row=new ArrayList<>(customer("123-45-67890")); row.set(0,"협력사"); row.set(9,"secret-account");
        check(workbook(List.of(row))).andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCount").value(1))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-account"))));
    }
    @Test void malformedFileIs400AndDoesNotCreateARecord() throws Exception {
        check("not xlsx".getBytes(java.nio.charset.StandardCharsets.UTF_8)).andExpect(status().isBadRequest());
        verify(mapper,never()).insertUpload(any()); verifyNoInteractions(files);
    }
    @Test void bothCategoryRequiresEmailEvenWithCompletePaymentFields() throws Exception {
        var row = new ArrayList<>(customer("123-45-67890")); row.set(0, "고객·협력");
        row.set(7, ""); row.set(8, "은행"); row.set(9, "12345678"); row.set(10, "예금주");
        check(workbook(List.of(row))).andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCount").value(1))
            .andExpect(jsonPath("$.data.issues[0].column").value("이메일"));
        assertThat(writes).isEmpty();
    }
    @Test void invalidEmailIsRecordedAsAnEmailIssue() throws Exception {
        var row = new ArrayList<>(customer("123-45-67890")); row.set(7, "not-an-email");
        check(workbook(List.of(row))).andExpect(status().isOk()).andExpect(jsonPath("$.data.errorCount").value(1))
            .andExpect(jsonPath("$.data.issues[0].column").value("이메일"));
    }
    @Test void mappedBankProducesWarningAndStoresResolvedCodeWithEncryptedAccount() throws Exception {
        var bank = new CodeTextRow(); bank.groupId = "BANK"; bank.code = "004"; bank.codeName = "은행";
        when(codes.selectMappedText("BANK", "옛 은행")).thenReturn(bank);
        var row = new ArrayList<>(customer("123-45-67890")); row.set(0, "협력사");
        row.set(7, ""); row.set(8, "옛 은행"); row.set(9, "12345678"); row.set(10, "예금주");
        check(workbook(List.of(row))).andExpect(status().isOk()).andExpect(jsonPath("$.data.warningCount").value(1))
            .andExpect(jsonPath("$.data.issues[0].inputValue").value("옛 은행"));
        apply("{\"modifiedAt\":null}").andExpect(status().isOk());
        assertThat(writes).singleElement().satisfies(value -> {
            assertThat(value.input().bankCode()).isEqualTo("004");
            assertThat(value.encryptedAccount()).isEqualTo("encrypted-account");
        });
    }
    @Test void missingUploadErrorDownloadIs400AndSuccessFileHasOnlyHeader() throws Exception {
        mvc.perform(get("/api/common/uploads/82/errors")).andExpect(status().isBadRequest());
        check(workbook(List.of(customer("123-45-67890")))).andExpect(status().isOk());
        var response = mvc.perform(get("/api/common/uploads/81/errors")).andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).andReturn();
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(response.getResponse().getContentAsByteArray()))) {
            assertThat(wb.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(1);
        }
    }
    @Test void missingUploadIs400AndInvalidIdNeverReachesSql() throws Exception {
        mvc.perform(get("/api/common/uploads/82")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/common/uploads/not-a-number")).andExpect(status().isBadRequest());
        verify(mapper,never()).selectUpload(0);
    }
    @Test void templateHasExactElevenHeaderCellsAndSeparateInstructions() throws Exception {
        var response=mvc.perform(get("/api/common/uploads/templates/B7")).andExpect(status().isOk())
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString(".xlsx"))).andReturn();
        try(var wb=new XSSFWorkbook(new ByteArrayInputStream(response.getResponse().getContentAsByteArray()))) {
            assertThat(wb.getSheetAt(0).getRow(0).getLastCellNum()).isEqualTo((short)11);
            for(int i=0;i<11;i++) assertThat(wb.getSheetAt(0).getRow(0).getCell(i).getStringCellValue()).isEqualTo(ClientUploadRules.HEADERS.get(i));
            assertThat(wb.getNumberOfSheets()).isEqualTo(2);
        }
        mvc.perform(get("/api/common/uploads/templates/UNKNOWN")).andExpect(status().isBadRequest());
    }
    @Test void errorDownloadIncludesOnlyErrorsNotWarningsOrSuccess() throws Exception {
        var mapped=new CodeTextRow(); mapped.groupId="INDUTY"; mapped.code="IT"; mapped.codeName="정보통신";
        when(codes.selectMappedText("INDUTY","옛 업종")).thenReturn(mapped);
        var warning=new ArrayList<>(customer("123-45-67890")); warning.set(4,"옛 업종");
        var bad=new ArrayList<>(customer("987-65-43210")); bad.set(7,"");
        check(workbook(List.of(warning,bad))).andExpect(status().isOk());
        var response=mvc.perform(get("/api/common/uploads/81/errors")).andExpect(status().isOk()).andReturn();
        try(var wb=new XSSFWorkbook(new ByteArrayInputStream(response.getResponse().getContentAsByteArray()))) {
            assertThat(wb.getSheetAt(0).getLastRowNum()).isEqualTo(1);
            assertThat(wb.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("3");
            assertThat(wb.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()).isEqualTo("이메일");
        }
    }
    @Test void recordFailureCompensatesOriginalAndRollsBack() throws Exception {
        doThrow(new IllegalStateException("private sql")).when(mapper).insertUpload(any());
        check(workbook(List.of(customer("123-45-67890")))).andExpect(status().isInternalServerError())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private sql"))));
        verify(files).discard(81); verify(transactions).rollback(any()); assertThat(writes).isEmpty();
    }
    @Test
    @DisplayName("side-effect: TB_SY_CHG_HIST 실패는 TB_BS_CLNT 반영 transaction rollback, TB_SY_UPLD 완료 없음")
    void historyFailureAbortsWholeApplyTransaction() throws Exception {
        check(workbook(List.of(customer("123-45-67890")))).andExpect(status().isOk());
        doThrow(new IllegalStateException("history failure")).when(management).insertHistory(any());
        mvc.perform(post("/api/common/uploads/81/apply").contentType("application/json").content("{\"modifiedAt\":null}"))
            .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.success").value(false));
        verify(clients).insertClient(any());
        verify(transactions).rollback(any()); verify(mapper,never()).completeUpload(any());
    }
    @Test void primaryKeyCollisionRetriesOnlyAfterRollbackAndRestartsWholeFile() throws Exception {
        check(workbook(List.of(customer("123-45-67890")))).andExpect(status().isOk());
        when(clients.insertClient(any())).thenThrow(new org.springframework.dao.DuplicateKeyException("pk_tb_bs_clnt"))
            .thenReturn(1);
        apply("{\"modifiedAt\":null}").andExpect(status().isOk());
        var order=inOrder(transactions,mapper); order.verify(transactions).rollback(any());
        order.verify(mapper).lockUpload(81); verify(files,times(2)).read(81);
    }
}
