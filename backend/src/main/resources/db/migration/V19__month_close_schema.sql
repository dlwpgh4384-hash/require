-- Explicit --prepare-schema only; existing month close and financial sources are unchanged.
DO $month_close_schema$
DECLARE
    relation REGCLASS;
    definition TEXT := $ddl$CREATE TABLE IF NOT EXISTS TB_CL_CLOSE_HIST (
    CLOSE_YM CHAR(6) NOT NULL,
    HIST_SN NUMERIC(5) NOT NULL,
    ACT_CD VARCHAR(10) NOT NULL,
    ACT_RSN VARCHAR(500),
    REMAIN_CHK_CN VARCHAR(500),
    REG_USER_ID VARCHAR(30) NOT NULL,
    REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_TB_CL_CLOSE_HIST PRIMARY KEY (CLOSE_YM, HIST_SN)
)$ddl$;
    expected_columns TEXT[];
    actual_columns TEXT[];
    expected_constraints TEXT[];
    actual_constraints TEXT[];
BEGIN
    PERFORM pg_advisory_xact_lock(734901, 1);
    relation := to_regclass(format('%I.%I', current_schema(), 'tb_cl_close_hist'));
    IF relation IS NULL THEN
        EXECUTE definition;
        relation := to_regclass(format('%I.%I', current_schema(), 'tb_cl_close_hist'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_class c WHERE c.oid = relation AND c.relkind = 'r') THEN
        RAISE EXCEPTION USING MESSAGE='tb_cl_close_hist: expected ordinary table', ERRCODE='P0001';
    END IF;
    EXECUTE format('LOCK TABLE %s IN ACCESS EXCLUSIVE MODE', relation);
    EXECUTE replace(definition, 'CREATE TABLE IF NOT EXISTS TB_CL_CLOSE_HIST',
        'CREATE TEMP TABLE month_close_expected');
    SELECT array_agg(a.attname || ':' || format_type(a.atttypid, a.atttypmod)
        || ':' || a.attnotnull::TEXT || ':' || coalesce(pg_get_expr(d.adbin, d.adrelid), '')
        ORDER BY a.attnum)
    INTO expected_columns
    FROM pg_attribute a
    LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
    WHERE a.attrelid = 'pg_temp.month_close_expected'::REGCLASS
        AND a.attnum > 0 AND NOT a.attisdropped;
    SELECT array_agg(a.attname || ':' || format_type(a.atttypid, a.atttypmod)
        || ':' || a.attnotnull::TEXT || ':' || coalesce(pg_get_expr(d.adbin, d.adrelid), '')
        ORDER BY a.attnum)
    INTO actual_columns
    FROM pg_attribute a
    LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
    WHERE a.attrelid = relation AND a.attnum > 0 AND NOT a.attisdropped;
    SELECT array_agg(c.conname || ':' || pg_get_constraintdef(c.oid)
        || ':' || c.condeferrable::TEXT ORDER BY c.conname)
    INTO expected_constraints
    FROM pg_constraint c
    WHERE c.conrelid = 'pg_temp.month_close_expected'::REGCLASS;
    SELECT array_agg(c.conname || ':' || pg_get_constraintdef(c.oid)
        || ':' || c.condeferrable::TEXT ORDER BY c.conname)
    INTO actual_constraints
    FROM pg_constraint c
    WHERE c.conrelid = relation AND c.convalidated;
    DROP TABLE pg_temp.month_close_expected;
    IF actual_columns IS DISTINCT FROM expected_columns
        OR actual_constraints IS DISTINCT FROM expected_constraints THEN
        RAISE EXCEPTION USING MESSAGE='tb_cl_close_hist: original columns, defaults, nullability or PK differ',
            ERRCODE='P0001';
    END IF;
END
$month_close_schema$;

COMMENT ON TABLE TB_CL_CLOSE_HIST IS '월 마감 및 해제 이력. 처리 사유와 마감 직전 확인 필요 항목의 표시명을 보존한다.';
COMMENT ON COLUMN TB_CL_CLOSE_HIST.CLOSE_YM IS 'TB_CL_CLOSE.CLOSE_YM 참조 의도 (FK 미선언)';
COMMENT ON COLUMN TB_CL_CLOSE_HIST.HIST_SN IS '마감연월별 처리 순번. 월 쓰기 잠금 안에서 애플리케이션이 부여한다.';
COMMENT ON COLUMN TB_CL_CLOSE_HIST.ACT_CD IS 'CLOSE:마감|OPEN:해제';
COMMENT ON COLUMN TB_CL_CLOSE_HIST.ACT_RSN IS '해제 사유';
COMMENT ON COLUMN TB_CL_CLOSE_HIST.REMAIN_CHK_CN IS '월 마감 처리 시 애플리케이션이 자동 정산 전 확인 필요 항목 표시명을 쉼표로 연결하여 기록하는 불변 이력';
COMMENT ON COLUMN TB_CL_CLOSE_HIST.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
COMMENT ON COLUMN TB_CL_CLOSE_HIST.REG_DTTM IS '등록일시';
