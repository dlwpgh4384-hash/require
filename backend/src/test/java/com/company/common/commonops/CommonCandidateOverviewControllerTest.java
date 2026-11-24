package com.company.common.commonops;

import com.company.common.common.CandidateDefinitions;
import com.company.common.commonops.controller.CommonCandidateOverviewController;
import com.company.common.commonops.dto.CandidateOverviewContracts.Summary;
import com.company.common.commonops.mapper.CandidateOverviewMapper;
import com.company.common.commonops.mapper.CandidateOverviewMapper.*;
import com.company.common.commonops.service.*;
import com.company.common.support.controller.ApiExceptionHandler;
import com.company.common.support.controller.CodeExceptionHandler;
import com.company.common.support.service.BusinessCalendar;
import com.company.common.support.service.CodeFailure;
import com.company.common.withdrawals.mapper.WithdrawalMapper;
import com.company.common.withdrawals.service.WithdrawalCalculationRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.company.common.support.controller.OpenApiResponseContract.response;

@WebMvcTest(CommonCandidateOverviewController.class)
@ContextConfiguration(classes = {CommonCandidateOverviewController.class, CandidateOverviewService.class,
    CandidateResumeExtractionService.class, CandidateResumeDocumentReader.class, CodeExceptionHandler.class, ApiExceptionHandler.class})
class CommonCandidateOverviewControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockBean CandidateOverviewMapper mapper;
    @MockBean WithdrawalMapper forecast;
    @MockBean WithdrawalCalculationRules calculations;
    @MockBean CandidateDefinitions definitions;
    @MockBean BusinessCalendar calendar;
    @MockBean PlatformTransactionManager transactions;

    @BeforeEach void setup() {
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(calendar.today()).thenReturn(LocalDate.of(2026, 12, 31));
        when(calendar.toDatabaseMonth(any())).thenAnswer(call -> ((YearMonth)call.getArgument(0)).toString().replace("-", ""));
        when(mapper.dictionary("RSM_LABEL")).thenReturn(List.of(
            new Dictionary("RSM_LABEL", "a", "이름", "NAME"), new Dictionary("RSM_LABEL", "b", "희망금액", "WANT"),
            new Dictionary("RSM_LABEL", "c", "주소", "ADDR"), new Dictionary("RSM_LABEL", "d", "소속", "VNDR"),
            new Dictionary("RSM_LABEL", "e", "기술", "SKILL"), new Dictionary("RSM_LABEL", "f", "가능일", "FROM")));
        when(mapper.dictionary("JOB")).thenReturn(List.of(new Dictionary("JOB", "DEV", "개발", null)));
        when(mapper.dictionary("RSM_JOB_KW")).thenReturn(List.of(new Dictionary("RSM_JOB_KW", "java", "Java", "DEV")));
        when(mapper.dictionary("EMP_TYPE")).thenReturn(List.of(new Dictionary("EMP_TYPE", "FR", "프리랜서", null)));
        when(mapper.companies()).thenReturn(List.of(new Company("vendor", "등록업체")));
        when(mapper.areas()).thenReturn(List.of(new Area("1168000000", "서울", "서울특별시", "강남구")));
    }

    private org.springframework.test.web.servlet.ResultActions extract(String text) throws Exception {
        return mvc.perform(multipart("/api/common/candidate-resume-extractions").file(new MockMultipartFile(
            "file", "resume.txt", "text/plain", text.getBytes(StandardCharsets.UTF_8))))
            .andExpect(response("POST", "/api/common/candidate-resume-extractions"));
    }

    @Test void publicExtractionNormalizesMoneyWithoutWritingCandidateConsentOrUpload() throws Exception {
        var result = extract("이름: 김개발\n희망금액: 650만원\n01012345678\nUSER@EXAMPLE.COM\n주소: 서울특별시 강남구 역삼동")
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.reviewRequired").doesNotExist()).andReturn();
        var fields = json.readTree(result.getResponse().getContentAsByteArray()).path("data").path("fields");
        assertThat(fields.findValuesAsText("value")).contains("6500000", "010-1234-5678", "user@example.com", "1168000000", "서울 강남구");
        assertThat(fields.findValuesAsText("field")).doesNotContain("privacyAgreement", "privacyAgreementUserId", "gradeCode");
        assertThat(mockingDetails(mapper).getInvocations()).allMatch(call ->
            List.of("dictionary", "companies", "areas").contains(call.getMethod().getName()));
        verifyNoInteractions(forecast, definitions, calculations);
    }

    @Test void unknownCompanyIsOmittedAndInferredJobNeedsHumanReviewWithoutGrade() throws Exception {
        var result = extract("소속: 사전에 없는 업체\nJava 개발 경력\n경력: 7년")
            .andExpect(status().isOk()).andReturn();
        var body = json.readTree(result.getResponse().getContentAsByteArray()).path("data");
        var fields = body.path("fields");
        assertThat(fields.findValuesAsText("field")).contains("primaryJobCode").doesNotContain("companyId", "gradeCode");
        assertThat(fields.get(0).path("estimated").asBoolean()).isTrue();
        assertThat(fields.get(0).path("originalText").asText()).contains("Java");
        assertThat(body.path("careerYears").asInt()).isEqualTo(7);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"0212345678,02-1234-5678", "0311234567,031-123-4567", "01012345678,010-1234-5678"})
    void recognizedTelephonePrefixesArePreserved(String raw, String normalized) throws Exception {
        var result = extract(raw).andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(result.getResponse().getContentAsByteArray()).path("data").path("fields").findValuesAsText("value"))
            .contains(normalized);
    }

    @Test void activePhysicalDictionaryIsReadAgainForEveryExtraction() throws Exception {
        extract("요청금액:650만원").andExpect(status().isOk()).andExpect(jsonPath("$.data.fields").isEmpty());
        when(mapper.dictionary("RSM_LABEL")).thenReturn(List.of(new Dictionary("RSM_LABEL", "new", "요청금액", "WANT")));
        extract("요청금액:650만원").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.fields[0].value").value("6500000"));
        verify(mapper, times(2)).dictionary("RSM_LABEL");
    }

    @ParameterizedTest @ValueSource(strings = {"jpg", "png", "gif", "xlsx"})
    void imagesAndUnapprovedFormatsAreFileErrorsBeforeDatabaseRead(String extension) throws Exception {
        mvc.perform(multipart("/api/common/candidate-resume-extractions").file(new MockMultipartFile(
            "file", "resume." + extension, "application/octet-stream", new byte[]{1,2,3})))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("file"))
            .andExpect(jsonPath("$.errors[0].message").value(CandidateResumeDocumentReader.TYPE_MESSAGE));
        verifyNoInteractions(mapper, forecast);
    }

    @Test void oversizedFileIsFileErrorBeforeDatabaseRead() throws Exception {
        mvc.perform(multipart("/api/common/candidate-resume-extractions").file(new MockMultipartFile(
            "file", "resume.txt", "text/plain", new byte[CandidateResumeDocumentReader.MAX_BYTES + 1])))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("file"))
            .andExpect(jsonPath("$.errors[0].message").value("10MB 이하 파일만 올릴 수 있습니다."));
        verifyNoInteractions(mapper);
    }

    @Test void labelsNeedABoundaryAndLongerLabelsWin() throws Exception {
        when(mapper.dictionary("RSM_LABEL")).thenReturn(List.of(
            new Dictionary("RSM_LABEL", "a", "이름", "NAME"), new Dictionary("RSM_LABEL", "b", "기술", "SKILL"),
            new Dictionary("RSM_LABEL", "c", "기술스택", "SKILL"), new Dictionary("RSM_LABEL", "d", "소속", "VNDR"),
            new Dictionary("RSM_LABEL", "e", "소속업체", "VNDR")));
        var result = extract("이름표기: 틀린값\n기술 자격: 정보처리기사\n기술스택: Java, SQL\n소속업체: 등록업체\n이름: 홍길동")
            .andExpect(status().isOk()).andReturn();
        var fields = json.readTree(result.getResponse().getContentAsByteArray()).path("data").path("fields");
        var values = new java.util.HashMap<String, String>();
        fields.forEach(row -> values.put(row.path("field").asText(), row.path("value").asText()));
        assertThat(values).containsEntry("name", "홍길동").containsEntry("skills", "Java,SQL").containsEntry("companyId", "vendor");
    }

    @Test void tableCellsOnOneLineAndEmptyLabelCellsUseTheNextPiece() throws Exception {
        var result = extract("이름\t홍길동\t희망금액\t650만원\n주소\n서울특별시 강남구 역삼동\n소속:\n이름: 다른값")
            .andExpect(status().isOk()).andReturn();
        var fields = json.readTree(result.getResponse().getContentAsByteArray()).path("data").path("fields");
        var values = new java.util.HashMap<String, String>();
        fields.forEach(row -> values.put(row.path("field").asText(), row.path("value").asText()));
        assertThat(values).containsEntry("name", "홍길동").containsEntry("desiredMonthlyRate", "6500000")
            .containsEntry("homeAddress", "서울 강남구").doesNotContainKey("companyId");
    }

    @Test void latinJobKeywordsMatchWholeWordsOnly() throws Exception {
        when(mapper.dictionary("JOB")).thenReturn(List.of(new Dictionary("JOB", "PM", "PM", null), new Dictionary("JOB", "DEV", "개발", null)));
        when(mapper.dictionary("RSM_JOB_KW")).thenReturn(List.of(new Dictionary("RSM_JOB_KW", "pm", "PM", "PM"),
            new Dictionary("RSM_JOB_KW", "dev", "개발", "DEV")));
        var none = extract("web development, deploy template").andExpect(status().isOk()).andReturn();
        assertThat(json.readTree(none.getResponse().getContentAsByteArray()).path("data").path("fields").findValuesAsText("field"))
            .doesNotContain("primaryJobCode");
        var found = extract("개발자 경력, 최근 PM/PL 수행").andExpect(status().isOk()).andReturn();
        var fields = json.readTree(found.getResponse().getContentAsByteArray()).path("data").path("fields");
        assertThat(fields.findValuesAsText("value")).contains("PM", "DEV");
    }

    @ParameterizedTest @ValueSource(strings = {"", "text/plain; charset=UTF-8", "application/x-unknown"})
    void contentTypeParametersAndEmptyTypesRelyOnTheExtension(String type) throws Exception {
        mvc.perform(multipart("/api/common/candidate-resume-extractions").file(new MockMultipartFile(
            "file", "resume.txt", type, "희망금액: 650만원".getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.fields[0].value").value("6500000"));
    }

    @Test void imageContentTypeIsRejectedEvenWithTextExtension() throws Exception {
        mvc.perform(multipart("/api/common/candidate-resume-extractions").file(new MockMultipartFile(
            "file", "resume.txt", "image/png; name=scan", new byte[]{1,2,3})))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].message").value(CandidateResumeDocumentReader.TYPE_MESSAGE));
        verifyNoInteractions(mapper);
    }

    @Test void missingFileHasFileValidationError() throws Exception {
        mvc.perform(multipart("/api/common/candidate-resume-extractions")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("file"));
        verifyNoInteractions(mapper);
    }

    @Test void malformedFileCannotCreateAnyBusinessRow() throws Exception {
        mvc.perform(multipart("/api/common/candidate-resume-extractions").file(new MockMultipartFile(
            "file", "resume.pdf", "application/pdf", "not a PDF".getBytes(StandardCharsets.UTF_8))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("file"));
        verifyNoInteractions(mapper);
    }

    @Test void duplicateLabelsAndSkillsDoNotCreateDuplicateFields() throws Exception {
        var response = extract("이름: 첫이름\n이름: 다음이름\n기술: Java,Java,SQL")
            .andExpect(status().isOk()).andReturn();
        var fields = json.readTree(response.getResponse().getContentAsByteArray()).path("data").path("fields");
        assertThat(fields.findValuesAsText("field")).doesNotHaveDuplicates();
        assertThat(fields.findValuesAsText("value")).contains("첫이름", "Java,SQL").doesNotContain("다음이름");
    }

    @Test void summaryUsesKoreanNextMonthStartAndStrictThirtyDayBoundary() throws Exception {
        when(mapper.summary(any())).thenReturn(new Summary(10, 7, 2, 3, 1));
        mvc.perform(get("/api/common/candidate-summary")).andExpect(status().isOk())
            .andExpect(response("GET", "/api/common/candidate-summary"))
            .andExpect(jsonPath("$.data.totalCount").value(10))
            .andExpect(jsonPath("$.data.activeCount").value(7)).andExpect(jsonPath("$.data.interviewScheduledCount").value(2))
            .andExpect(jsonPath("$.data.availableByNextMonthCount").value(3)).andExpect(jsonPath("$.data.overdueContactCount").value(1));
        verify(mapper).summary(new Dates(LocalDate.of(2027,1,1), LocalDate.of(2026,12,1)));
        verifyNoInteractions(forecast);
    }

    @Test void calculationDefinitionErrorHasContract400() throws Exception {
        when(definitions.definitions()).thenThrow(CodeFailure.invalid("calcId", "계산 정의를 확인해 주세요."));
        mvc.perform(get("/api/common/candidate-shortages")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("calcId"));
        verifyNoInteractions(forecast);
    }

    private void forecastCalculations() {
        var metadata = mock(com.company.common.calculation.service.CalculationRuleService.class);
        when(metadata.definition(anyString())).thenAnswer(call -> new com.company.common.calculation.dto.CalculationRuleContracts.Definition(
            call.getArgument(0), "max(0,1-확정) 투입이 하나도 없는 사람은 1 소수 첫째 자리", "시험 정의"));
        when(calculations.settings()).thenAnswer(call -> new WithdrawalCalculationRules(metadata).settings());
        when(forecast.supply(any())).thenReturn(List.of(new com.company.common.withdrawals.mapper.WithdrawalRows.SupplyRow("person", java.math.BigDecimal.ZERO)));
    }

    @Test void shortageForecastWeightsOnlyOpportunityDemandAndCountsAvailableAtFirstDay() throws Exception {
        forecastCalculations();
        when(mapper.activeCandidates()).thenReturn(List.of(new CandidateCount("DEV", LocalDate.of(2027,1,1)),
            new CandidateCount("DEV", LocalDate.of(2027,1,2)), new CandidateCount("PM", LocalDate.of(2026,12,1))));
        when(forecast.opportunityDemand(any())).thenReturn(List.of(new com.company.common.withdrawals.mapper.WithdrawalRows.OpportunityDemandRow(
            new java.math.BigDecimal("4"), java.math.BigDecimal.ONE, new java.math.BigDecimal("50"))));
        mvc.perform(get("/api/common/candidate-shortages")).andExpect(status().isOk())
            .andExpect(response("GET", "/api/common/candidate-shortages"))
            .andExpect(jsonPath("$.data.items[0].jobCode").value("DEV"))
            .andExpect(jsonPath("$.data.items[0].shortageStartMonth").value("2027-01"))
            .andExpect(jsonPath("$.data.items[0].maxShortage").value(1.0))
            .andExpect(jsonPath("$.data.items[0].availableCandidateCount").value(1))
            .andExpect(jsonPath("$.data.items[0].totalCandidateCount").value(2));
        verify(forecast, times(6)).opportunityDemand(any());
        verify(forecast).opportunityDemand(new com.company.common.withdrawals.mapper.WithdrawalRows.MonthFilter(
            "DEV", "202706", LocalDate.of(2027,6,1), LocalDate.of(2027,6,30), LocalDate.of(2026,12,1)));
        verify(forecast, never()).projectDemand(any());
    }

    @Test void projectOnlyDemandProducesNoCandidateShortage() throws Exception {
        forecastCalculations();
        when(forecast.projectDemand(any())).thenReturn(List.of(new com.company.common.withdrawals.mapper.WithdrawalRows.ProjectDemandRow(
            new java.math.BigDecimal("999"), java.math.BigDecimal.ZERO, 1)));
        mvc.perform(get("/api/common/candidate-shortages")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items").isEmpty());
        verify(forecast, never()).projectDemand(any());
    }
}
