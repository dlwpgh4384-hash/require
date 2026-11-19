package com.company.common.common;

import com.company.common.support.service.CodeFailure;
import java.time.LocalDate;

public final class ProjectBoardLifecycle {
    private ProjectBoardLifecycle() {}

    public enum Command { CREATE_MEMO, UPDATE_MEMO, CREATE_ISSUE, UPDATE_ISSUE, UPDATE_DATES, COMPLETE_STAGE }

    public static String effectiveStatus(String storedStatus, LocalDate contractEnd, LocalDate today) {
        if ("LOSE".equals(storedStatus)) return "LOSE";
        return contractEnd != null && contractEnd.isBefore(today) ? "END" : storedStatus;
    }

    public static boolean visible(String effectiveStatus) {
        return !"END".equals(effectiveStatus) && !"LOSE".equals(effectiveStatus);
    }

    public static boolean shouldEnsure(String effectiveStatus, int existingStageCount) {
        return visible(effectiveStatus) && existingStageCount == 0;
    }

    public static void requireWritable(String effectiveStatus, Command command) {
        if (!visible(effectiveStatus) && command != Command.UPDATE_ISSUE)
            throw CodeFailure.invalid("projectId", "종료·실주 프로젝트에는 이 작업을 할 수 없습니다.");
    }
}
