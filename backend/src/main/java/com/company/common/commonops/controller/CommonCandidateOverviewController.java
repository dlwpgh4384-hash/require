package com.company.common.commonops.controller;

import com.company.common.commonops.dto.CandidateOverviewContracts.*;
import com.company.common.commonops.service.CandidateOverviewService;
import com.company.common.commonops.service.CandidateResumeExtractionService;
import com.company.common.support.service.CodeFailure;
import com.company.common.users.dto.UserContracts.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class CommonCandidateOverviewController {
    private final CandidateOverviewService overview;
    private final CandidateResumeExtractionService resumes;

    public CommonCandidateOverviewController(CandidateOverviewService overview, CandidateResumeExtractionService resumes) {
        this.overview = overview;
        this.resumes = resumes;
    }

    @GetMapping("/api/common/candidate-summary")
    public Response<Summary> getCommonCandidateSummary() {
        return Response.of(overview.getCommonCandidateSummary());
    }

    @GetMapping("/api/common/candidate-shortages")
    public Response<Shortages> getCommonCandidateShortages() {
        return Response.of(overview.getCommonCandidateShortages());
    }

    @PostMapping(value = "/api/common/candidate-resume-extractions", consumes = "multipart/form-data")
    public Response<ResumeExtraction> postCommonCandidateResumeExtraction(@RequestParam(value = "file", required = false) MultipartFile file) {
        if (file == null) throw CodeFailure.invalid("file", "파일을 선택해 주세요.");
        return Response.of(resumes.postCommonCandidateResumeExtraction(file));
    }
}
