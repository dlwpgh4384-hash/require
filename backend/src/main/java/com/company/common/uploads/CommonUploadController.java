package com.company.common.uploads;

import com.company.common.dto.UserContracts.Response;
import com.company.common.service.CodeFailure;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class CommonUploadController {
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private final CommonUploadService service;

    public CommonUploadController(CommonUploadService service) { this.service = service; }

    @GetMapping("/api/common/uploads/templates/{formTypeCode}")
    public ResponseEntity<byte[]> getCommonUploadTemplate(@PathVariable String formTypeCode) {
        return download(service.getCommonUploadTemplate(formTypeCode), "client-template.xlsx");
    }
    @PostMapping(value = "/api/common/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<UploadContracts.Result> postCommonUpload(@RequestParam String formTypeCode,
        @RequestPart MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 10_000_000)
            throw CodeFailure.invalid("file", "10,000,000 bytes 이하의 엑셀 파일을 선택해 주세요.");
        try {
            return Response.of(service.postCommonUpload(formTypeCode, file.getOriginalFilename(), file.getBytes()));
        } catch (IOException failure) {
            throw CodeFailure.invalid("file", "파일을 읽지 못했습니다.");
        }
    }
    @GetMapping("/api/common/uploads/{uploadId}")
    public Response<UploadContracts.Result> getCommonUpload(@PathVariable String uploadId) {
        return Response.of(service.getCommonUpload(uploadId));
    }
    @GetMapping("/api/common/uploads/{uploadId}/errors")
    public ResponseEntity<byte[]> getCommonUploadErrors(@PathVariable String uploadId) {
        return download(service.getCommonUploadErrors(uploadId), "upload-errors.xlsx");
    }
    @PostMapping(value = "/api/common/uploads/{uploadId}/apply", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Response<UploadContracts.Result> postCommonUploadApply(@PathVariable String uploadId,
        @RequestBody JsonNode body) {
        return Response.of(service.postCommonUploadApply(uploadId, CommonUploadService.parseApply(body)));
    }
    private ResponseEntity<byte[]> download(byte[] bytes, String name) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
            .body(bytes);
    }
    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    public ResponseEntity<com.company.common.controller.ApiExceptionHandler.Error> missingFile() {
        String message = "엑셀 파일을 선택해 주세요.";
        return ResponseEntity.badRequest().body(new com.company.common.controller.ApiExceptionHandler.Error(
            false, java.util.Map.of(), message,
            java.util.List.of(new com.company.common.controller.ApiExceptionHandler.FieldError("file", message))));
    }
}
