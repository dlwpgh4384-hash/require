CREATE SEQUENCE IF NOT EXISTS SQ_TB_SY_AI_QNA START WITH 1 INCREMENT BY 1;

DO $ai_question$
DECLARE
    ddl TEXT := $ddl$CREATE TABLE IF NOT EXISTS TB_SY_AI_QNA (
        QNA_ID NUMERIC(12) DEFAULT nextval('SQ_TB_SY_AI_QNA') NOT NULL,
        USER_ID VARCHAR(30) NOT NULL,
        QSTN_CN VARCHAR(1000) NOT NULL,
        ANS_CN TEXT,
        USE_DATA_DESC VARCHAR(500),
        BASE_YM CHAR(6),
        PRVS_YN CHAR(1) DEFAULT 'N' NOT NULL,
        ANS_STAT_CD VARCHAR(10) NOT NULL,
        QSTN_DTTM TIMESTAMPTZ NOT NULL,
        REG_USER_ID VARCHAR(30) NOT NULL,
        REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
        MOD_USER_ID VARCHAR(30),
        MOD_DTTM TIMESTAMPTZ,
        CONSTRAINT PK_TB_SY_AI_QNA PRIMARY KEY (QNA_ID),
        CONSTRAINT CK_TB_SY_AI_QNA_01 CHECK (PRVS_YN IN ('Y', 'N'))
    )$ddl$;
    relation REGCLASS;
    expected_columns TEXT[];
    actual_columns TEXT[];
    expected_constraints TEXT[];
    actual_constraints TEXT[];
BEGIN
    PERFORM pg_advisory_xact_lock(734901, 22);
    relation := to_regclass(format('%I.tb_sy_ai_qna', current_schema()));
    IF relation IS NULL THEN
        EXECUTE ddl;
    ELSE
        IF NOT EXISTS (SELECT 1 FROM pg_class WHERE oid = relation AND relkind = 'r') THEN
            RAISE EXCEPTION USING MESSAGE = 'tb_sy_ai_qna: expected ordinary table', ERRCODE = 'P0001';
        END IF;
        EXECUTE format('LOCK TABLE %s IN ACCESS EXCLUSIVE MODE', relation);
        EXECUTE regexp_replace(ddl, 'CREATE TABLE IF NOT EXISTS [A-Z_]+', 'CREATE TEMP TABLE ai_question_expected') || ' ON COMMIT DROP';
        SELECT array_agg(a.attname || ':' || format_type(a.atttypid, a.atttypmod) || ':' || a.attnotnull::TEXT
            || ':' || coalesce(pg_get_expr(d.adbin, d.adrelid), '') ORDER BY a.attnum)
        INTO expected_columns
        FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
        WHERE a.attrelid = 'pg_temp.ai_question_expected'::REGCLASS AND a.attnum > 0 AND NOT a.attisdropped;
        SELECT array_agg(a.attname || ':' || format_type(a.atttypid, a.atttypmod) || ':' || a.attnotnull::TEXT
            || ':' || coalesce(pg_get_expr(d.adbin, d.adrelid), '') ORDER BY a.attnum)
        INTO actual_columns
        FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
        WHERE a.attrelid = relation AND a.attnum > 0 AND NOT a.attisdropped;
        SELECT array_agg(conname || ':' || pg_get_constraintdef(oid) ORDER BY conname)
        INTO expected_constraints FROM pg_constraint
        WHERE conrelid = 'pg_temp.ai_question_expected'::REGCLASS AND contype IN ('p', 'c', 'u');
        SELECT array_agg(conname || ':' || pg_get_constraintdef(oid) ORDER BY conname)
        INTO actual_constraints FROM pg_constraint
        WHERE conrelid = relation AND contype IN ('p', 'c', 'u') AND convalidated;
        IF actual_columns IS DISTINCT FROM expected_columns OR actual_constraints IS DISTINCT FROM expected_constraints THEN
            RAISE EXCEPTION USING MESSAGE = 'tb_sy_ai_qna: original columns, defaults, nullability, PK or checks differ', ERRCODE = 'P0001';
        END IF;
    END IF;
END
$ai_question$;

CREATE INDEX IF NOT EXISTS IX_TB_SY_AI_QNA_01 ON TB_SY_AI_QNA (USER_ID);
COMMENT ON TABLE TB_SY_AI_QNA IS 'AI질문기록';
COMMENT ON COLUMN TB_SY_AI_QNA.QNA_ID IS '질의응답ID';
COMMENT ON COLUMN TB_SY_AI_QNA.USER_ID IS '사용자ID';
COMMENT ON COLUMN TB_SY_AI_QNA.QSTN_CN IS '질문내용';
COMMENT ON COLUMN TB_SY_AI_QNA.ANS_CN IS '답변내용: 결론·표·가정';
COMMENT ON COLUMN TB_SY_AI_QNA.USE_DATA_DESC IS '사용데이터설명: 데이터, 기간, 기준월';
COMMENT ON COLUMN TB_SY_AI_QNA.BASE_YM IS '기준연월';
COMMENT ON COLUMN TB_SY_AI_QNA.PRVS_YN IS '잠정여부: 마감 전 달 숫자면 Y';
COMMENT ON COLUMN TB_SY_AI_QNA.ANS_STAT_CD IS '답변상태코드: OK 답변(권한 밖 숫자를 빼고 답한 것 포함) / NO_ANS 답할 수 없음(데이터에 없음·원인 판단 불가·권한 밖 질문이라 답하지 않음, 화면 「이 내용을 볼 권한이 없습니다」 포함) / ERR 오류. 명세서 v1.9 「급여 없이 답하거나 답할 수 없음」(2026-10-30 C19)';
COMMENT ON COLUMN TB_SY_AI_QNA.QSTN_DTTM IS '질문일시';
COMMENT ON COLUMN TB_SY_AI_QNA.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_SY_AI_QNA.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SY_AI_QNA.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_SY_AI_QNA.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
