-- Original receipt structure, explicitly prepared; no business data added.
-- 2026-11-19: DDL의 외래 키 FK_TB_BL_RCPT_01(BILL_ID → TB_BL_BILL), FK_TB_BL_RCPT_02(CNCL_USER_ID → TB_SY_USER)를
-- V10~V14와 같은 방식(없으면 만들고, 다르면 고치지 않고 멈춤)으로 더한다.
DO $billing_receipt$
DECLARE
    ddl TEXT := $ddl$CREATE TABLE IF NOT EXISTS TB_BL_RCPT (
 RCPT_ID NUMERIC(12) DEFAULT nextval('SQ_TB_BL_RCPT') NOT NULL,
 BILL_ID VARCHAR(20) NOT NULL,
 RCPT_DT DATE NOT NULL,
 RCPT_AMT NUMERIC(15) NOT NULL,
 CNCL_YN CHAR(1) DEFAULT 'N' NOT NULL,
 CNCL_RSN VARCHAR(500),
 CNCL_USER_ID VARCHAR(30),
 CNCL_DTTM TIMESTAMPTZ,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_BL_RCPT PRIMARY KEY (RCPT_ID),
 CONSTRAINT CK_TB_BL_RCPT_01 CHECK (CNCL_YN IN ('Y', 'N'))
)$ddl$;
    relation REGCLASS;
    sequence_relation REGCLASS;
    expected_columns TEXT[];
    actual_columns TEXT[];
    expected_constraints TEXT[];
    actual_constraints TEXT[];
BEGIN
    PERFORM pg_advisory_xact_lock(734901, 1);
    sequence_relation := to_regclass(format('%I.sq_tb_bl_rcpt', current_schema()));
    IF sequence_relation IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM pg_sequence s
        WHERE s.seqrelid = sequence_relation
            AND s.seqstart = 1 AND s.seqincrement = 1
            AND s.seqmin = 1 AND s.seqmax = 9223372036854775807
            AND s.seqcache = 1 AND NOT s.seqcycle
    ) THEN
        RAISE EXCEPTION USING MESSAGE='sq_tb_bl_rcpt: expected original noncycling sequence', ERRCODE='P0001';
    END IF;
    CREATE SEQUENCE IF NOT EXISTS SQ_TB_BL_RCPT START WITH 1 INCREMENT BY 1;

    relation := to_regclass(format('%I.tb_bl_rcpt', current_schema()));
    IF relation IS NULL THEN
        EXECUTE ddl;
    ELSE
        IF NOT EXISTS (SELECT 1 FROM pg_class WHERE oid = relation AND relkind = 'r') THEN
            RAISE EXCEPTION USING MESSAGE='tb_bl_rcpt: expected ordinary table', ERRCODE='P0001';
        END IF;
        EXECUTE format('LOCK TABLE %s IN ACCESS EXCLUSIVE MODE', relation);
        EXECUTE regexp_replace(ddl, 'CREATE TABLE IF NOT EXISTS [A-Z_]+', 'CREATE TEMP TABLE billing_receipt_expected');
        SELECT array_agg(a.attname || ':' || format_type(a.atttypid, a.atttypmod) || ':' || a.attnotnull::TEXT
            || ':' || coalesce(pg_get_expr(d.adbin, d.adrelid), '') ORDER BY a.attnum)
        INTO expected_columns
        FROM pg_attribute a
        LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
        WHERE a.attrelid = 'pg_temp.billing_receipt_expected'::REGCLASS AND a.attnum > 0 AND NOT a.attisdropped;
        SELECT array_agg(a.attname || ':' || format_type(a.atttypid, a.atttypmod) || ':' || a.attnotnull::TEXT
            || ':' || coalesce(pg_get_expr(d.adbin, d.adrelid), '') ORDER BY a.attnum)
        INTO actual_columns
        FROM pg_attribute a
        LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
        WHERE a.attrelid = relation AND a.attnum > 0 AND NOT a.attisdropped;
        SELECT array_agg(conname || ':' || pg_get_constraintdef(oid) || ':' || condeferrable::TEXT ORDER BY conname)
        INTO expected_constraints
        FROM pg_constraint
        WHERE conrelid = 'pg_temp.billing_receipt_expected'::REGCLASS AND contype IN ('p', 'c', 'u');
        SELECT array_agg(conname || ':' || pg_get_constraintdef(oid) || ':' || condeferrable::TEXT ORDER BY conname)
        INTO actual_constraints
        FROM pg_constraint
        WHERE conrelid = relation AND contype IN ('p', 'c', 'u') AND convalidated;
        DROP TABLE pg_temp.billing_receipt_expected;
        IF actual_columns IS DISTINCT FROM expected_columns OR actual_constraints IS DISTINCT FROM expected_constraints THEN
            RAISE EXCEPTION USING MESSAGE='tb_bl_rcpt: original columns, defaults, nullability, PK or checks differ', ERRCODE='P0001';
        END IF;
    END IF;
END
$billing_receipt$;

DO $billing_receipt_references$
DECLARE
    spec record;
    definition text;
    expected text;
BEGIN
    FOR spec IN SELECT * FROM (VALUES
        ('FK_TB_BL_RCPT_01','BILL_ID','TB_BL_BILL','BILL_ID'),
        ('FK_TB_BL_RCPT_02','CNCL_USER_ID','TB_SY_USER','USER_ID')
    ) AS refs(name,column_name,target,target_column) LOOP
        expected := format('FOREIGN KEY (%s) REFERENCES %s(%s)',
            lower(spec.column_name),lower(spec.target),lower(spec.target_column));
        SELECT pg_get_constraintdef(oid) INTO definition FROM pg_constraint
         WHERE conrelid='tb_bl_rcpt'::regclass AND conname=lower(spec.name);
        IF definition IS NULL THEN
            EXECUTE format('ALTER TABLE TB_BL_RCPT ADD CONSTRAINT %I %s',lower(spec.name),expected);
        ELSIF lower(definition) IS DISTINCT FROM lower(expected) OR NOT EXISTS (
            SELECT 1 FROM pg_constraint WHERE conrelid='tb_bl_rcpt'::regclass AND conname=lower(spec.name)
                AND convalidated AND NOT condeferrable
        ) THEN
            RAISE EXCEPTION USING MESSAGE='TB_BL_RCPT.' || spec.name || ': expected original foreign key', ERRCODE='P0001';
        END IF;
    END LOOP;
END
$billing_receipt_references$;

CREATE INDEX IF NOT EXISTS IX_TB_BL_RCPT_01 ON TB_BL_RCPT (BILL_ID);
CREATE INDEX IF NOT EXISTS IX_TB_BL_RCPT_02 ON TB_BL_RCPT (CNCL_USER_ID);

DO $billing_receipt_indexes$
DECLARE
    spec RECORD;
BEGIN
    FOR spec IN SELECT * FROM (VALUES
        ('ix_tb_bl_rcpt_01', 'bill_id'),
        ('ix_tb_bl_rcpt_02', 'cncl_user_id')
    ) AS indexes(name, column_name) LOOP
        IF NOT EXISTS (
            SELECT 1 FROM pg_index i
            JOIN pg_class c ON c.oid = i.indexrelid
            JOIN pg_am am ON am.oid = c.relam
            WHERE i.indexrelid = to_regclass(format('%I.%I', current_schema(), spec.name))
                AND i.indrelid = 'tb_bl_rcpt'::REGCLASS
                AND i.indisvalid AND i.indisready AND NOT i.indisunique
                AND i.indnkeyatts = 1 AND i.indnatts = 1
                AND i.indpred IS NULL AND i.indexprs IS NULL AND am.amname = 'btree'
                AND pg_get_indexdef(i.indexrelid, 1, true) = spec.column_name
        ) THEN
            RAISE EXCEPTION USING MESSAGE='tb_bl_rcpt.' || spec.name || ': expected original index', ERRCODE='P0001';
        END IF;
    END LOOP;
END
$billing_receipt_indexes$;

COMMENT ON TABLE TB_BL_RCPT IS '청구별 실제 입금과 취소 사유·처리자·시각을 보존한다. 취소 입금은 정상 입금 합계에서 제외하며 원행을 삭제하지 않는다.';
COMMENT ON COLUMN TB_BL_RCPT.RCPT_ID IS 'SQ_TB_BL_RCPT로 발급하는 원문 입금 식별자 (FK 미선언)';
COMMENT ON COLUMN TB_BL_RCPT.BILL_ID IS 'TB_BL_BILL.BILL_ID 참조 (FK_TB_BL_RCPT_01)';
COMMENT ON COLUMN TB_BL_RCPT.RCPT_DT IS '입금일자: 자금수지 실적 기준';
COMMENT ON COLUMN TB_BL_RCPT.RCPT_AMT IS '입금금액: 0 초과, 미수금 이하';
COMMENT ON COLUMN TB_BL_RCPT.CNCL_YN IS '취소 입금은 정상 입금 합계에서 제외하며 입금월 마감 뒤 취소할 수 없음';
COMMENT ON COLUMN TB_BL_RCPT.CNCL_RSN IS '취소사유: 취소할 때 필수';
COMMENT ON COLUMN TB_BL_RCPT.CNCL_USER_ID IS 'TB_SY_USER.USER_ID 참조 (FK_TB_BL_RCPT_02): 취소 처리자';
COMMENT ON COLUMN TB_BL_RCPT.CNCL_DTTM IS '서버가 기록한 취소일시';
COMMENT ON COLUMN TB_BL_RCPT.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 등록자';
COMMENT ON COLUMN TB_BL_RCPT.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_BL_RCPT.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 수정자';
COMMENT ON COLUMN TB_BL_RCPT.MOD_DTTM IS '수정일시: 동시 수정 감지';
