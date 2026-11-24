package com.company.common.support.controller;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    @Test
    void multipartLimitUsesTheSameValidationEnvelopeAsTheUploadController() {
        var response = new ApiExceptionHandler().uploadTooLarge(
            new MaxUploadSizeExceededException(10_000_000), new MockHttpServletRequest("POST", "/api/common/uploads")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.meta()).isEqualTo(Map.of());
        assertThat(body.message()).isEqualTo("10,000,000 bytes 이하의 엑셀 파일을 선택해 주세요.");
        assertThat(body.errors()).containsExactly(
            new ApiExceptionHandler.FieldError("file", body.message())
        );
    }

    @Test
    void resumeUploadLimitHasResumeMessageOnlyOnResumePath() {
        var response = new ApiExceptionHandler().uploadTooLarge(new MaxUploadSizeExceededException(10_000_000),
            new MockHttpServletRequest("POST", "/api/common/candidate-resume-extractions"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("10MB 이하 파일만 올릴 수 있습니다.");
        assertThat(response.getBody().errors()).containsExactly(
            new ApiExceptionHandler.FieldError("file", "10MB 이하 파일만 올릴 수 있습니다."));
    }
}
