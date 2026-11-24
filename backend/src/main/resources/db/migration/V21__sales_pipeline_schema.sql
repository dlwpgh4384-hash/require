-- Explicit --prepare-schema only; existing sales structures and data are never repaired.
DO $sales_pipeline_schema$
DECLARE
    definition TEXT := $ddl$CREATE TABLE IF NOT EXISTS TB_SL_APRV_HIST (
    OPP_ID VARCHAR(20) NOT NULL,
    HIST_SN NUMERIC(5) NOT NULL,
    APRV_ACT_CD VARCHAR(10) NOT NULL,
    PRFT_RT NUMERIC(7,2) NOT NULL,
    ACT_RSN VARCHAR(300),
    REG_USER_ID VARCHAR(30) NOT NULL,
    REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT PK_TB_SL_APRV_HIST PRIMARY KEY (OPP_ID, HIST_SN)
)$ddl$;
    relation REGCLASS;
    expected_relation REGCLASS;
    expected_columns TEXT[];
    actual_columns TEXT[];
    expected_constraints TEXT[];
    actual_constraints TEXT[];
BEGIN
    PERFORM pg_advisory_xact_lock(734901, 1);
    relation := to_regclass(format('%I.%I', current_schema(), 'tb_sl_aprv_hist'));
    IF relation IS NULL THEN
        EXECUTE left(definition, length(definition) - 1)
            || ', CONSTRAINT FK_TB_SL_APRV_HIST_01 FOREIGN KEY (OPP_ID) REFERENCES TB_SL_OPP (OPP_ID))';
        relation := to_regclass(format('%I.%I', current_schema(), 'tb_sl_aprv_hist'));
        COMMENT ON TABLE TB_SL_APRV_HIST IS '영업 기회의 견적 승인 요청·승인·반려·요구 해제 이력을 저장한다.';
        COMMENT ON COLUMN TB_SL_APRV_HIST.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_SL_APRV_HIST.APRV_ACT_CD IS 'REQ:요청|OK:승인|REJ:반려|AUTO_REL:요구 해제';
        COMMENT ON COLUMN TB_SL_APRV_HIST.HIST_SN IS '영업 기회 행 잠금 안에서 애플리케이션이 부여하는 기회별 승인 이력 순번';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_class c WHERE c.oid = relation AND c.relkind = 'r') THEN
        RAISE EXCEPTION USING MESSAGE='tb_sl_aprv_hist: expected ordinary table', ERRCODE='P0001';
    END IF;
    EXECUTE format('LOCK TABLE %s IN ACCESS EXCLUSIVE MODE', relation);
    EXECUTE regexp_replace(definition, '^CREATE TABLE IF NOT EXISTS [A-Z_]+',
        'CREATE TEMP TABLE sales_pipeline_expected');
    expected_relation := to_regclass('pg_temp.sales_pipeline_expected');
    SELECT array_agg(a.attname || ':' || format_type(a.atttypid, a.atttypmod)
        || ':' || a.attnotnull::TEXT || ':' || coalesce(pg_get_expr(d.adbin, d.adrelid), '')
        ORDER BY a.attnum)
    INTO expected_columns
    FROM pg_attribute a
    LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
    WHERE a.attrelid = expected_relation AND a.attnum > 0 AND NOT a.attisdropped;
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
    WHERE c.conrelid = expected_relation AND c.contype IN ('p', 'c', 'u');
    SELECT array_agg(c.conname || ':' || pg_get_constraintdef(c.oid)
        || ':' || c.condeferrable::TEXT ORDER BY c.conname)
    INTO actual_constraints
    FROM pg_constraint c
    WHERE c.conrelid = relation AND c.contype IN ('p', 'c', 'u');
    DROP TABLE pg_temp.sales_pipeline_expected;
    IF actual_columns IS DISTINCT FROM expected_columns
        OR actual_constraints IS DISTINCT FROM expected_constraints THEN
        RAISE EXCEPTION USING MESSAGE='tb_sl_aprv_hist: original columns, defaults, nullability or PK/check/unique constraints differ',
            ERRCODE='P0001';
    END IF;
END
$sales_pipeline_schema$;
