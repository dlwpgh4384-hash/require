package com.company.common.airecommendations.service;

import com.company.common.airecommendations.service.AiRecommendationLogCodec.Metadata;
import com.company.common.airecommendations.service.AiRecommendationLogCodec.Score;
import com.company.common.airecommendations.service.AiRecommendationLogCodec.Snapshot;
import com.company.common.common.ManpowerBoardFixture;
import com.company.common.schema.SchemaPreparation;
import com.company.common.support.service.BusinessCalendar;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "NAVIGATION_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class AiRecommendationSnapshotPersistenceTest {
    private ManpowerBoardFixture fixture;
    private final AiRecommendationLogCodec codec = new AiRecommendationLogCodec(new ObjectMapper());

    @BeforeEach
    void independentSchema() throws Exception {
        fixture = new ManpowerBoardFixture();
    }

    @AfterEach
    void removeOwnedSchema() throws Exception {
        if (fixture != null) fixture.close();
    }

    private Snapshot snapshot() {
        var scores = new LinkedHashMap<String, Score>();
        List<String> dimensions = List.of("job", "skill", "customerIndustry", "grade", "performance", "cost", "availability", "workplace");
        List<Integer> awarded = List.of(25, 14, 15, 5, 4, 4, 10, 10);
        List<Integer> maxima = List.of(25, 20, 15, 10, 5, 5, 10, 10);
        for (int i = 0; i < dimensions.size(); i++) {
            scores.put(dimensions.get(i), new Score(BigDecimal.valueOf(awarded.get(i)), BigDecimal.valueOf(maxima.get(i))));
        }
        return new Snapshot(scores, new Metadata("b".repeat(64), 1, null));
    }

    private long insert(String reason) {
        String opportunity = fixture.forecastSources();
        String developer = fixture.person("추천 기록 시험");
        long id = fixture.db().queryForObject("SELECT nextval('SQ_TB_SL_RCMD_LOG')", Long.class);
        fixture.db().update("""
            INSERT INTO TB_SL_RCMD_LOG(RCMD_ID,RANK_ORD,RCMD_TYPE_CD,OPP_ID,NEED_SN,
                JOB_CD,BGN_YM,REQ_GRADE_CD,DEV_ID,TOT_SCORE,SCORE_DTL,RCMD_RSN,REG_USER_ID,REG_DTTM)
            VALUES(?,1,'AI',?,1,'BE','202612','HI',?,87,?,?,'admin',clock_timestamp())
            """, id, opportunity, developer, codec.encode(snapshot()), codec.reason(reason));
        return id;
    }

    @Test
    void storedSnapshotRestoresScoresAndSelectedGradeAfterOperatingValuesChange() {
        long id = insert("실제 근거");
        fixture.db().update("UPDATE TB_SY_OPER_STD SET STD_VAL=STD_VAL+1 WHERE STD_CD='AI_SCORE_JOB'");
        var saved = fixture.db().queryForMap("""
            SELECT r.OPP_ID,r.JOB_CD,r.BGN_YM,r.REQ_GRADE_CD,r.SCORE_DTL,r.TOT_SCORE,r.USER_ACT_CD
            FROM TB_SL_RCMD_LOG r WHERE r.RCMD_ID=? AND r.RANK_ORD=1
            """, id);
        assertThat(saved.get("opp_id")).isNotNull();
        assertThat(saved.get("job_cd")).isEqualTo("BE");
        assertThat(saved.get("bgn_ym")).isEqualTo("202612");
        assertThat(saved.get("req_grade_cd")).isEqualTo("HI");
        assertThat((BigDecimal) saved.get("tot_score")).isEqualByComparingTo("87");
        assertThat(codec.decode((String) saved.get("score_dtl"))).isEqualTo(snapshot());
    }

    @Test
    void reasonAndScoreSnapshotFitExistingColumnLimitsWithoutChangingSchema() {
        long id = insert("😀".repeat(301));
        var lengths = fixture.db().queryForMap("""
            SELECT char_length(r.RCMD_RSN) AS reason_length,char_length(r.SCORE_DTL) AS score_length,
                r.SCORE_DTL
            FROM TB_SL_RCMD_LOG r WHERE r.RCMD_ID=? AND r.RANK_ORD=1
            """, id);
        assertThat(lengths.get("reason_length")).isEqualTo(300);
        assertThat((Integer) lengths.get("score_length")).isLessThanOrEqualTo(500);
        assertThat(codec.decode((String) lengths.get("score_dtl"))).isEqualTo(snapshot());
    }

    @Test
    void logInsertionDoesNotMutateDeveloperOrTentativeBusinessSources() {
        String opportunity = fixture.forecastSources();
        String developer = fixture.person("원천 불변 시험");
        String before = fixture.businessSnapshot();
        long id = fixture.db().queryForObject("SELECT nextval('SQ_TB_SL_RCMD_LOG')", Long.class);
        fixture.db().update("""
            INSERT INTO TB_SL_RCMD_LOG(RCMD_ID,RANK_ORD,RCMD_TYPE_CD,OPP_ID,JOB_CD,
                BGN_YM,REQ_GRADE_CD,DEV_ID,TOT_SCORE,SCORE_DTL,REG_USER_ID,REG_DTTM)
            VALUES(?,1,'AI',?,'BE','202612','HI',?,87,?,'admin',clock_timestamp())
            """, id, opportunity, developer, codec.encode(snapshot()));
        assertThat(fixture.businessSnapshot()).isEqualTo(before);
    }

    @Test
    void databaseClockTimestampMaterializesAsInstant() {
        Instant before = fixture.db().queryForObject("SELECT clock_timestamp()", (rs, row) -> rs.getTimestamp(1).toInstant());
        long id = insert("시각 경계");
        Instant stored = fixture.db().queryForObject(
            "SELECT r.REG_DTTM FROM TB_SL_RCMD_LOG r WHERE r.RCMD_ID=? AND r.RANK_ORD=1",
            (rs, row) -> rs.getTimestamp(1).toInstant(), id
        );
        Instant after = fixture.db().queryForObject("SELECT clock_timestamp()", (rs, row) -> rs.getTimestamp(1).toInstant());
        assertThat(stored).isBetween(before, after);
    }

    @Test
    void repeatedExplicitPreparationKeepsAnExistingRule70RowUnchanged() throws Exception {
        fixture.db().update("""
            UPDATE TB_SY_CALC_RULE SET FRML_CN=?,DTL_CN=?,MOD_USER_ID='admin',MOD_DTTM=clock_timestamp()
            WHERE CALC_ID='식-70'
            """, "기존 운영자가 관리한 원문", "기존 상세 원문");
        var before = fixture.db().queryForMap("SELECT * FROM TB_SY_CALC_RULE WHERE CALC_ID='식-70'");
        new SchemaPreparation(fixture.factory()).prepare();
        new SchemaPreparation(fixture.factory()).prepare();
        assertThat(fixture.db().queryForMap("SELECT * FROM TB_SY_CALC_RULE WHERE CALC_ID='식-70'"))
            .isEqualTo(before);
        try (var session = fixture.factory().openSession()) {
            var definitions = new AiRecommendationDefinitions(fixture.metadata(session), new BusinessCalendar(ManpowerBoardFixture.CLOCK));
            assertThat(definitions.definitions()).anySatisfy(value -> {
                assertThat(value.calcId()).isEqualTo("식-70");
                assertThat(value.formula()).isEqualTo("기존 운영자가 관리한 원문");
                assertThat(value.details()).isEqualTo("기존 상세 원문");
            });
        }
    }
}
