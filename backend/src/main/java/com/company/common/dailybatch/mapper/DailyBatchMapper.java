package com.company.common.dailybatch.mapper;

import com.company.common.notifications.service.NotificationPublisher.WithdrawalTarget;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 일 배치(PER-004)가 직접 쓰는 SQL. 기록 사용자는 'SYSTEM'(명세 ⑪-2). */
@Mapper
public interface DailyBatchMapper {
    /** ② 퇴사일이 지난(퇴사일 다음 날부터, DeveloperBusinessPolicy.retired와 같음) 개발자 사용 중지. */
    int stopRetiredDevelopers(@Param("day") LocalDate day);
    /** ③ 계약 종료일이 지난 진행(ING) 프로젝트를 종료(END)로 저장. */
    int endExpiredProjects(@Param("day") LocalDate day);
    /** ⑤ 투입 종료일이 정확히 그날인 유효 투입. */
    List<WithdrawalTarget> dueWithdrawals(@Param("endDate") LocalDate endDate);
    /** ⑤·⑥ 받는 사람 후보: 사용 중 사용자 모두(권한·범위는 NotificationPublisher가 거른다). */
    List<String> activeUsers();
    /** ⑧ 접근 기록 3년 지난 줄 삭제(DAR-003). */
    int purgeAccessLogs(@Param("cutoff") Instant cutoff);
    /** ⑧ 변경 기록: 프로젝트·투입·단가·원가는 영구, 그 밖 3년 지난 줄 삭제(DAR-003). */
    int purgeChangeHistory(@Param("cutoff") Instant cutoff);
}
