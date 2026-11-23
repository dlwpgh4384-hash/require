-- Explicit --prepare-schema only; preserve existing targets, actuals, codes and calculation rules.
DO $business_plan_schema$
DECLARE
    relation REGCLASS;
    definition TEXT := $ddl$CREATE TABLE IF NOT EXISTS TB_FI_BIZ_PLAN (
    PLAN_YM CHAR(6) NOT NULL,
    PART_ID VARCHAR(20) NOT NULL,
    SALES_TGT_AMT NUMERIC(15) NOT NULL,
    OPRT_PRFT_TGT_AMT NUMERIC(15) NOT NULL,
    UPLD_ID NUMERIC(12),
    REG_USER_ID VARCHAR(30) NOT NULL,
    REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID VARCHAR(30),
    MOD_DTTM TIMESTAMPTZ,
    CONSTRAINT PK_TB_FI_BIZ_PLAN PRIMARY KEY (PLAN_YM, PART_ID)
)$ddl$;
    expected_columns TEXT[];
    actual_columns TEXT[];
    expected_constraints TEXT[];
    actual_constraints TEXT[];
BEGIN
    PERFORM pg_advisory_xact_lock(734901, 1);
    relation := to_regclass(format('%I.%I', current_schema(), 'tb_fi_biz_plan'));
    IF relation IS NULL THEN
        EXECUTE left(definition, length(definition) - 1)
            || ', CONSTRAINT FK_TB_FI_BIZ_PLAN_01 FOREIGN KEY (UPLD_ID) REFERENCES TB_SY_UPLD(UPLD_ID))';
        relation := to_regclass(format('%I.%I', current_schema(), 'tb_fi_biz_plan'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_class c WHERE c.oid = relation AND c.relkind = 'r') THEN
        RAISE EXCEPTION USING MESSAGE='tb_fi_biz_plan: expected ordinary table', ERRCODE='P0001';
    END IF;
    EXECUTE format('LOCK TABLE %s IN ACCESS EXCLUSIVE MODE', relation);
    EXECUTE replace(definition, 'CREATE TABLE IF NOT EXISTS TB_FI_BIZ_PLAN',
        'CREATE TEMP TABLE business_plan_expected');
    SELECT array_agg(a.attname || ':' || format_type(a.atttypid, a.atttypmod)
        || ':' || a.attnotnull::TEXT || ':' || coalesce(pg_get_expr(d.adbin, d.adrelid), '')
        ORDER BY a.attnum)
    INTO expected_columns
    FROM pg_attribute a
    LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
    WHERE a.attrelid = 'pg_temp.business_plan_expected'::REGCLASS
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
    WHERE c.conrelid = 'pg_temp.business_plan_expected'::REGCLASS AND c.contype IN ('p', 'c', 'u');
    SELECT array_agg(c.conname || ':' || pg_get_constraintdef(c.oid)
        || ':' || c.condeferrable::TEXT ORDER BY c.conname)
    INTO actual_constraints
    FROM pg_constraint c
    WHERE c.conrelid = relation AND c.contype IN ('p', 'c', 'u') AND c.convalidated;
    DROP TABLE pg_temp.business_plan_expected;
    IF actual_columns IS DISTINCT FROM expected_columns
        OR actual_constraints IS DISTINCT FROM expected_constraints THEN
        RAISE EXCEPTION USING MESSAGE='tb_fi_biz_plan: original columns, defaults, nullability or PK differ',
            ERRCODE='P0001';
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        WHERE c.conrelid = relation AND c.conname = 'fk_tb_fi_biz_plan_01'
            AND c.contype = 'f' AND c.convalidated AND NOT c.condeferrable
            AND c.confrelid = to_regclass(format('%I.%I', current_schema(), 'tb_sy_upld'))
            AND lower(pg_get_constraintdef(c.oid)) = 'foreign key (upld_id) references tb_sy_upld(upld_id)'
    ) OR (SELECT count(*) FROM pg_constraint c WHERE c.conrelid = relation AND c.contype = 'f') <> 1 THEN
        RAISE EXCEPTION USING MESSAGE='tb_fi_biz_plan.FK_TB_FI_BIZ_PLAN_01: expected original foreign key only',
            ERRCODE='P0001';
    END IF;
END
$business_plan_schema$;

DO $business_plan_indexes$
DECLARE
    spec RECORD;
BEGIN
    FOR spec IN SELECT * FROM (VALUES
        ('ix_tb_fi_biz_plan_01', 'part_id'),
        ('ix_tb_fi_biz_plan_02', 'upld_id')
    ) AS indexes(name, column_name) LOOP
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON TB_FI_BIZ_PLAN (%I)', spec.name, spec.column_name);
        IF NOT EXISTS (
            SELECT 1
            FROM pg_index i
            JOIN pg_class c ON c.oid = i.indexrelid
            JOIN pg_am am ON am.oid = c.relam
            WHERE i.indexrelid = to_regclass(format('%I.%I', current_schema(), spec.name))
                AND i.indrelid = to_regclass(format('%I.%I', current_schema(), 'tb_fi_biz_plan'))
                AND i.indisvalid AND i.indisready AND NOT i.indisunique
                AND i.indnkeyatts = 1 AND i.indnatts = 1
                AND i.indpred IS NULL AND i.indexprs IS NULL AND am.amname = 'btree'
                AND pg_get_indexdef(i.indexrelid, 1, true) = spec.column_name
        ) THEN
            RAISE EXCEPTION USING MESSAGE='tb_fi_biz_plan.' || spec.name || ': expected original index',
                ERRCODE='P0001';
        END IF;
    END LOOP;
END
$business_plan_indexes$;

COMMENT ON TABLE TB_FI_BIZ_PLAN IS '사업계획목표 - 부록 B.10. 전체 합계 월별 매출 및 영업이익 목표를 저장한다.';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.PLAN_YM IS '계획연월';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.PART_ID IS '영업파트ID: ''ALL'' 고정. 파트별 목표는 넣지 않음. TB_BS_PART.PART_ID 참조 의도 (FK 미선언, ALL은 전체 합계)';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.SALES_TGT_AMT IS '매출목표금액: 업로드 빈칸은 0';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.OPRT_PRFT_TGT_AMT IS '영업이익목표금액: 업로드 빈칸은 0';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.UPLD_ID IS '업로드ID: TB_SY_UPLD.UPLD_ID';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
COMMENT ON COLUMN TB_FI_BIZ_PLAN.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
