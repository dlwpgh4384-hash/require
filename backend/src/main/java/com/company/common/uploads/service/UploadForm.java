package com.company.common.uploads.service;

import com.company.common.uploads.dto.UploadContracts.Inspection;
import com.company.common.uploads.dto.UploadWorkbookContracts.TabularRow;
import java.time.YearMonth;
import java.util.List;

/**
 * SFR-012 공통 업로드에 붙는 양식 하나(B.7 거래처, B.8 개발자, B.1 급여 …).
 * 공통 서비스가 파일 읽기·검사 기록·반영 트랜잭션을 맡고, 양식은 열 규칙과 반영만 맡는다.
 */
public interface UploadForm {
    record Context(String projectId, String distributionStepCode) {}
    default UploadForm withContext(Context context) {
        if (context != null && (context.projectId() != null || context.distributionStepCode() != null))
            throw com.company.common.support.service.CodeFailure.invalid("projectId", "이 양식은 프로젝트 문맥을 받지 않습니다.");
        return this;
    }
    default Integer distributionMismatchCount() { return null; }
    String formTypeCode();
    /** 결과 창 제목 「엑셀 업로드 결과 · {대상}」과 업로드 기록의 화면 번호. */
    String targetName();
    String screenId();
    List<String> headers();
    /**
     * headers() 가운데 파일에 없어도 되는 머리 글자(나중에 더한 선택 칸). 없으면 그 칸은 빈칸으로 읽어
     * 칸을 더하기 전에 받은 양식도 그대로 올릴 수 있다. 양식 내려받기에는 늘 들어간다.
     */
    default java.util.Set<String> optionalHeaders() { return java.util.Set.of(); }
    String templateNote();
    String templateFileName();
    /** 한 달 양식(B.1 급여)이면 올릴 때 연월을 받는다(TB_SY_UPLD.TRGT_YM). */
    default boolean monthly() { return false; }
    /** 한 달 양식의 연월 검사(마감한 달 등). 맞지 않으면 CodeFailure. */
    default void checkMonth(YearMonth month) {}
    Inspection inspect(List<TabularRow> rows, YearMonth month);
    /** 반영 전에 같은 대상의 동시 쓰기를 막는다. */
    void lock();
    /** 오류가 없을 때만 불린다. 호출자의 전량 반영 트랜잭션에 참여한다. 반영으로 붙은 관리번호 등을 행번호별로 돌려준다. */
    java.util.Map<Integer, String> apply(Inspection inspection, YearMonth month, long uploadId);
    /** B.10 마감한 달 목표 처리 값: 마감한 달은 두기 / 전체 변경. */
    String KEEP_CLOSED = "keep";
    String REPLACE_CLOSED = "replace";
    /** 반영 요청의 선택 값(closedMonthPolicy)을 받는 양식만 재정의한다. 값을 주지 않으면 지금과 같다. */
    default java.util.Map<Integer, String> apply(Inspection inspection, YearMonth month, long uploadId,
            String closedMonthPolicy) {
        if (closedMonthPolicy != null)
            throw com.company.common.support.service.CodeFailure.invalid("closedMonthPolicy", "이 양식은 마감한 달 목표 선택을 받지 않습니다.");
        return apply(inspection, month, uploadId);
    }
    /** 양식 내려받기의 데이터 첫 줄 예시(사용자 요청 2026-11-23). 올리는 사람이 지우고 쓴다. 미리 채울 줄이 있는 양식은 쓰지 않는다. */
    default List<String> exampleRow() { return List.of(); }
    /** 양식 내려받기에 미리 채울 줄. 연 양식(B.10)만 연도를 받아 1~12월 줄을 채운다. */
    default List<List<String>> templateRows(Integer year) {
        if (year != null)
            throw com.company.common.support.service.CodeFailure.invalid("year", "이 양식은 연도를 받지 않습니다.");
        return List.of();
    }
}
