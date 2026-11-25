package com.company.common.aianalysis.service;

import com.company.common.aianalysis.dto.AiAnalysisContracts.Answer;
import com.company.common.support.service.CodeFailure;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AiAnalysisProgressTest {
    @Test void concurrentDuplicateSharesOnlyInFlightExecutionAndReplayGetsNewExecution() {
        var progress = new AiAnalysisProgress(); var first = progress.claim("admin", "request-1"); var duplicate = progress.claim("admin", "request-1");
        assertThat(first.owner()).isTrue(); assertThat(duplicate.owner()).isFalse(); assertThat(duplicate.execution()).isSameAs(first.execution());
        var answer = new Answer("1", "NO_ANS", "답변 불가", null, List.of(), "", "2026-11", false, List.of(), List.of());
        first.execution().answer.complete(answer); progress.finish("admin", "request-1", first.execution());
        assertThat(duplicate.execution().answer.join()).isSameAs(answer); assertThat(progress.read("admin", "request-1").completed()).isTrue();
        var replay = progress.claim("admin", "request-1"); assertThat(replay.owner()).isTrue(); assertThat(replay.execution()).isNotSameAs(first.execution());
    }
    @Test void ownerBoundaryDoesNotDistinguishUnknownFromOtherOwner() {
        var progress = new AiAnalysisProgress(); progress.claim("admin", "r");
        assertThatThrownBy(() -> progress.read("other", "r")).isInstanceOf(CodeFailure.class);
        assertThatThrownBy(() -> progress.read("other", "unknown")).isInstanceOf(CodeFailure.class);
    }
    @Test void realStageIsStableUntilWorkExplicitlyAdvancesIt() {
        var progress = new AiAnalysisProgress(); var execution = progress.claim("admin", "r").execution();
        assertThat(progress.read("admin", "r").stage()).isEqualTo("데이터 찾는 중"); execution.stage("계산 중");
        for (int i = 0; i < 20; i++) assertThat(progress.read("admin", "r").stage()).isEqualTo("계산 중");
        execution.stage("정리 중"); assertThat(progress.read("admin", "r").stage()).isEqualTo("정리 중");
    }
    @Test void completedMetadataIsBoundedAndDoesNotRetainAnswerResult() {
        var progress = new AiAnalysisProgress();
        for (int i = 0; i < 130; i++) { var execution = progress.claim("admin", "r-" + i).execution(); execution.answer.complete(null); progress.finish("admin", "r-" + i, execution); }
        assertThatThrownBy(() -> progress.read("admin", "r-0")).isInstanceOf(CodeFailure.class);
        assertThat(progress.read("admin", "r-129").completed()).isTrue();
    }
}
