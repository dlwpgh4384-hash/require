-- Original AI recommendation history; no recommendation writes or business seeds.
CREATE SEQUENCE IF NOT EXISTS SQ_TB_SL_RCMD_LOG START WITH 1 INCREMENT BY 1;

DO $recommendation_log$
DECLARE
    ddl text := $ddl$CREATE TABLE IF NOT EXISTS TB_SL_RCMD_LOG (
 RCMD_ID NUMERIC(12) NOT NULL,
 RANK_ORD NUMERIC(3) NOT NULL,
 RCMD_TYPE_CD VARCHAR(10) NOT NULL,
 OPP_ID VARCHAR(20),
 NEED_SN NUMERIC(3),
 PROJ_ID VARCHAR(20),
 JOB_CD VARCHAR(10) NOT NULL,
 BGN_YM CHAR(6) NOT NULL,
 REQ_GRADE_CD VARCHAR(10) NOT NULL,
 DEV_ID VARCHAR(20) NOT NULL,
 TOT_SCORE NUMERIC(5,1) NOT NULL,
 SCORE_DTL VARCHAR(500) NOT NULL,
 RCMD_RSN VARCHAR(300),
 USER_ACT_CD VARCHAR(10),
 RCMD_EXCL_CD VARCHAR(10),
 ACT_USER_ID VARCHAR(30),
 ACT_DTTM TIMESTAMPTZ,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 CONSTRAINT PK_TB_SL_RCMD_LOG PRIMARY KEY (RCMD_ID, RANK_ORD)
)$ddl$;
    relation regclass;
    expected_columns text[];
    actual_columns text[];
    expected_constraints text[];
    actual_constraints text[];
BEGIN
    PERFORM pg_advisory_xact_lock(734901, 1);
    relation := to_regclass(format('%I.tb_sl_rcmd_log', current_schema()));
    IF relation IS NULL THEN
        EXECUTE ddl;
    ELSE
        IF NOT EXISTS (SELECT 1 FROM pg_class WHERE oid=relation AND relkind='r') THEN
            RAISE EXCEPTION USING MESSAGE='tb_sl_rcmd_log: expected ordinary table', ERRCODE='P0001';
        END IF;
        EXECUTE format('LOCK TABLE %s IN ACCESS EXCLUSIVE MODE', relation);
        EXECUTE regexp_replace(ddl, 'CREATE TABLE IF NOT EXISTS [A-Z_]+', 'CREATE TEMP TABLE recommendation_expected');
        SELECT array_agg(a.attname || ':' || format_type(a.atttypid,a.atttypmod) || ':' || a.attnotnull::text
            || ':' || coalesce(pg_get_expr(d.adbin,d.adrelid),'') ORDER BY a.attnum)
          INTO expected_columns
          FROM pg_attribute a
          LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
         WHERE a.attrelid='pg_temp.recommendation_expected'::regclass AND a.attnum>0 AND NOT a.attisdropped;
        SELECT array_agg(a.attname || ':' || format_type(a.atttypid,a.atttypmod) || ':' || a.attnotnull::text
            || ':' || coalesce(pg_get_expr(d.adbin,d.adrelid),'') ORDER BY a.attnum)
          INTO actual_columns
          FROM pg_attribute a
          LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
         WHERE a.attrelid=relation AND a.attnum>0 AND NOT a.attisdropped;
        SELECT array_agg(conname || ':' || pg_get_constraintdef(oid) ORDER BY conname)
          INTO expected_constraints FROM pg_constraint
         WHERE conrelid='pg_temp.recommendation_expected'::regclass AND contype IN ('p','c','u');
        SELECT array_agg(conname || ':' || pg_get_constraintdef(oid) ORDER BY conname)
          INTO actual_constraints FROM pg_constraint
         WHERE conrelid=relation AND contype IN ('p','c','u') AND convalidated;
        DROP TABLE pg_temp.recommendation_expected;
        IF actual_columns IS DISTINCT FROM expected_columns OR actual_constraints IS DISTINCT FROM expected_constraints THEN
            RAISE EXCEPTION USING MESSAGE='tb_sl_rcmd_log: original columns, defaults, nullability, PK or checks differ', ERRCODE='P0001';
        END IF;
    END IF;
END
$recommendation_log$;

DO $recommendation_references$
DECLARE
    spec record;
    definition text;
    expected text;
BEGIN
    FOR spec IN SELECT * FROM (VALUES
        ('FK_TB_SL_RCMD_LOG_01','OPP_ID','TB_SL_OPP','OPP_ID'),
        ('FK_TB_SL_RCMD_LOG_02','PROJ_ID','TB_PJ_PROJ','PROJ_ID'),
        ('FK_TB_SL_RCMD_LOG_03','DEV_ID','TB_HR_DEV','DEV_ID')
    ) AS refs(name,column_name,target,target_column) LOOP
        expected := format('FOREIGN KEY (%s) REFERENCES %s(%s)',
            lower(spec.column_name),lower(spec.target),lower(spec.target_column));
        SELECT pg_get_constraintdef(oid) INTO definition FROM pg_constraint
         WHERE conrelid='tb_sl_rcmd_log'::regclass AND conname=lower(spec.name);
        IF definition IS NULL THEN
            EXECUTE format('ALTER TABLE TB_SL_RCMD_LOG ADD CONSTRAINT %I %s',lower(spec.name),expected);
        ELSIF lower(definition) IS DISTINCT FROM lower(expected) OR NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conrelid='tb_sl_rcmd_log'::regclass AND conname=lower(spec.name)
                AND convalidated AND NOT condeferrable
        ) THEN
            RAISE EXCEPTION USING MESSAGE='TB_SL_RCMD_LOG.' || spec.name || ': expected original foreign key', ERRCODE='P0001';
        END IF;
    END LOOP;
END
$recommendation_references$;

CREATE INDEX IF NOT EXISTS IX_TB_SL_RCMD_LOG_01 ON TB_SL_RCMD_LOG (OPP_ID);
CREATE INDEX IF NOT EXISTS IX_TB_SL_RCMD_LOG_02 ON TB_SL_RCMD_LOG (PROJ_ID);
CREATE INDEX IF NOT EXISTS IX_TB_SL_RCMD_LOG_03 ON TB_SL_RCMD_LOG (DEV_ID);

COMMENT ON TABLE TB_SL_RCMD_LOG IS 'AI推薦の候補別順位・点数と既存の採用・除外結果を保存するAI추천기록. 현재 추천 화면은 새 기록을 쓰거나 로그를 공개하지 않으며 공통코드 참조 검사에서 읽는다.';
COMMENT ON COLUMN TB_SL_RCMD_LOG.RCMD_ID IS '추천ID: 추천 1회, SQ_TB_SL_RCMD_LOG로 발급하는 원문 식별자 (FK 미선언)';
COMMENT ON COLUMN TB_SL_RCMD_LOG.RANK_ORD IS '순위: 1~5';
COMMENT ON COLUMN TB_SL_RCMD_LOG.RCMD_TYPE_CD IS 'AI:AI 인력 추천|NEXT:다음 자리';
COMMENT ON COLUMN TB_SL_RCMD_LOG.OPP_ID IS 'TB_SL_OPP.OPP_ID 참조';
COMMENT ON COLUMN TB_SL_RCMD_LOG.NEED_SN IS 'TB_SL_OPP_NEED.NEED_SN 참조 의도, OPP_ID와 복합 식별 (FK 미선언)';
COMMENT ON COLUMN TB_SL_RCMD_LOG.PROJ_ID IS 'TB_PJ_PROJ.PROJ_ID 참조';
COMMENT ON COLUMN TB_SL_RCMD_LOG.JOB_CD IS '직무코드: 공통코드 JOB';
COMMENT ON COLUMN TB_SL_RCMD_LOG.BGN_YM IS '시작연월';
COMMENT ON COLUMN TB_SL_RCMD_LOG.REQ_GRADE_CD IS '요청등급코드: 추천 당시 비교 기준 등급';
COMMENT ON COLUMN TB_SL_RCMD_LOG.DEV_ID IS 'TB_HR_DEV.DEV_ID 참조: 후보 개발자';
COMMENT ON COLUMN TB_SL_RCMD_LOG.TOT_SCORE IS '총점 스냅샷: 추천 생성 주체가 추천 시 기록, 기존 조회에서는 갱신하지 않음';
COMMENT ON COLUMN TB_SL_RCMD_LOG.SCORE_DTL IS '항목별 점수 JSON 스냅샷: 추천 생성 주체가 추천 시 기록, 기존 조회에서는 갱신하지 않음';
COMMENT ON COLUMN TB_SL_RCMD_LOG.RCMD_RSN IS '추천이유';
COMMENT ON COLUMN TB_SL_RCMD_LOG.USER_ACT_CD IS 'ASGN:배정|TENT:가배정|EXCL:제외';
COMMENT ON COLUMN TB_SL_RCMD_LOG.RCMD_EXCL_CD IS '추천제외사유코드: 공통코드 RCMD_EXCL';
COMMENT ON COLUMN TB_SL_RCMD_LOG.ACT_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 작업 사용자';
COMMENT ON COLUMN TB_SL_RCMD_LOG.ACT_DTTM IS '사용자 작업일시';
COMMENT ON COLUMN TB_SL_RCMD_LOG.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 등록자';
COMMENT ON COLUMN TB_SL_RCMD_LOG.REG_DTTM IS '등록일시';
