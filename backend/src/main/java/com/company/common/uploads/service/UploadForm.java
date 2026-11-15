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
    String formTypeCode();
    /** 결과 창 제목 「엑셀 업로드 결과 · {대상}」과 업로드 기록의 화면 번호. */
    String targetName();
    String screenId();
    List<String> headers();
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
}
