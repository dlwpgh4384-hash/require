-- Explicit schema preparation only. V10 owns TB_CO_PART_SETL; no business rows or code definitions are changed.
DO $part_settlement$
DECLARE
    spec RECORD;
    relation REGCLASS;
    ddl TEXT;
    reference TEXT;
    expected_columns TEXT[];
    actual_columns TEXT[];
    expected_constraints TEXT[];
    actual_constraints TEXT[];
BEGIN
    PERFORM pg_advisory_xact_lock(734901, 1);
    FOR spec IN SELECT * FROM (VALUES
        ('tb_co_part_setl_dtl', $ddl$CREATE TABLE IF NOT EXISTS TB_CO_PART_SETL_DTL (
 PART_ID VARCHAR(20) NOT NULL,
 SETL_YM CHAR(6) NOT NULL,
 DEV_ID VARCHAR(20) NOT NULL,
 EMP_TYPE_CD VARCHAR(10) NOT NULL,
 PROJ_NM_LIST VARCHAR(1000) NOT NULL,
 MM NUMERIC(6,2) NOT NULL,
 UPRC_AMT NUMERIC(15) NOT NULL,
 PAY_AMT NUMERIC(15) NOT NULL,
 SALES_EXPS_AMT NUMERIC(15) NOT NULL,
 RETIRE_AMT NUMERIC(15) NOT NULL,
 COST_AMT NUMERIC(15) NOT NULL,
 DIFF_AMT NUMERIC(15) NOT NULL,
 PRFT_AMT NUMERIC(15) NOT NULL,
 RMRK VARCHAR(100),
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_CO_PART_SETL_DTL PRIMARY KEY (PART_ID, SETL_YM, DEV_ID)
)$ddl$, ARRAY[
            'CONSTRAINT FK_TB_CO_PART_SETL_DTL_01 FOREIGN KEY (PART_ID, SETL_YM) REFERENCES TB_CO_PART_SETL(PART_ID, SETL_YM)',
            'CONSTRAINT FK_TB_CO_PART_SETL_DTL_02 FOREIGN KEY (DEV_ID) REFERENCES TB_HR_DEV(DEV_ID)'
        ]),
        ('tb_cl_close_chk', $ddl$CREATE TABLE IF NOT EXISTS TB_CL_CLOSE_CHK (
 CLOSE_YM CHAR(6) NOT NULL,
 CHK_ITEM_CD VARCHAR(10) NOT NULL,
 CHK_STAT_CD VARCHAR(10) NOT NULL,
 CHK_CNT NUMERIC(7),
 CHK_CN VARCHAR(300),
 LINK_SCRN_ID VARCHAR(10),
 CHK_DTTM TIMESTAMPTZ NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_CL_CLOSE_CHK PRIMARY KEY (CLOSE_YM, CHK_ITEM_CD)
)$ddl$, ARRAY[
            'CONSTRAINT FK_TB_CL_CLOSE_CHK_01 FOREIGN KEY (CLOSE_YM) REFERENCES TB_CL_CLOSE(CLOSE_YM)'
        ])
    ) AS definitions(table_name, definition, references_sql) LOOP
        relation := to_regclass(format('%I.%I', current_schema(), spec.table_name));
        IF relation IS NULL THEN
            -- Foreign keys are part of the new table, never patched onto an existing relation.
            ddl := left(spec.definition, length(spec.definition) - 1)
                || ', ' || array_to_string(spec.references_sql, ', ') || ')';
            EXECUTE ddl;
            IF spec.table_name = 'tb_co_part_setl_dtl' THEN
                CREATE INDEX IF NOT EXISTS IX_TB_CO_PART_SETL_DTL_01 ON TB_CO_PART_SETL_DTL (SETL_YM);
                CREATE INDEX IF NOT EXISTS IX_TB_CO_PART_SETL_DTL_02 ON TB_CO_PART_SETL_DTL (DEV_ID);
            END IF;
            relation := to_regclass(format('%I.%I', current_schema(), spec.table_name));
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_class c WHERE c.oid = relation AND c.relkind = 'r') THEN
            RAISE EXCEPTION USING MESSAGE=spec.table_name || ': expected ordinary table', ERRCODE='P0001';
        END IF;
        EXECUTE format('LOCK TABLE %s IN ACCESS EXCLUSIVE MODE', relation);
        EXECUTE regexp_replace(spec.definition, 'CREATE TABLE IF NOT EXISTS [A-Z_]+',
            'CREATE TEMP TABLE part_settlement_expected');
        SELECT array_agg(a.attname || ':' || format_type(a.atttypid, a.atttypmod)
            || ':' || a.attnotnull::TEXT || ':' || coalesce(pg_get_expr(d.adbin, d.adrelid), '')
            ORDER BY a.attnum)
        INTO expected_columns
        FROM pg_attribute a
        LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
        WHERE a.attrelid = 'pg_temp.part_settlement_expected'::REGCLASS
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
        WHERE c.conrelid = 'pg_temp.part_settlement_expected'::REGCLASS AND c.contype IN ('p', 'c', 'u');
        SELECT array_agg(c.conname || ':' || pg_get_constraintdef(c.oid)
            || ':' || c.condeferrable::TEXT ORDER BY c.conname)
        INTO actual_constraints
        FROM pg_constraint c
        WHERE c.conrelid = relation AND c.contype IN ('p', 'c', 'u') AND c.convalidated;
        DROP TABLE pg_temp.part_settlement_expected;
        IF actual_columns IS DISTINCT FROM expected_columns
            OR actual_constraints IS DISTINCT FROM expected_constraints THEN
            RAISE EXCEPTION USING MESSAGE=spec.table_name
                || ': original columns, defaults, nullability, PK or checks differ', ERRCODE='P0001';
        END IF;
        FOREACH reference IN ARRAY spec.references_sql LOOP
            IF NOT EXISTS (
                SELECT 1
                FROM pg_constraint c
                WHERE c.conrelid = relation
                    AND c.conname = lower(split_part(reference, ' ', 2))
                    AND c.contype = 'f' AND c.convalidated AND NOT c.condeferrable
                    AND lower(pg_get_constraintdef(c.oid)) = lower(substring(reference FROM 'FOREIGN KEY.*'))
            ) THEN
                RAISE EXCEPTION USING MESSAGE=spec.table_name || '.' || split_part(reference, ' ', 2)
                    || ': expected original foreign key', ERRCODE='P0001';
            END IF;
        END LOOP;
        IF (SELECT count(*) FROM pg_constraint c WHERE c.conrelid = relation AND c.contype = 'f')
            <> cardinality(spec.references_sql) THEN
            RAISE EXCEPTION USING MESSAGE=spec.table_name || ': foreign key inventory differs', ERRCODE='P0001';
        END IF;
    END LOOP;
END
$part_settlement$;

DO $part_settlement_indexes$
DECLARE
    spec RECORD;
BEGIN
    FOR spec IN SELECT * FROM (VALUES
        ('ix_tb_co_part_setl_dtl_01', 'setl_ym'),
        ('ix_tb_co_part_setl_dtl_02', 'dev_id')
    ) AS indexes(name, column_name) LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM pg_index i
            JOIN pg_class c ON c.oid = i.indexrelid
            JOIN pg_am am ON am.oid = c.relam
            WHERE i.indexrelid = to_regclass(format('%I.%I', current_schema(), spec.name))
                AND i.indrelid = 'tb_co_part_setl_dtl'::REGCLASS
                AND i.indisvalid AND i.indisready AND NOT i.indisunique
                AND i.indnkeyatts = 1 AND i.indnatts = 1
                AND i.indpred IS NULL AND i.indexprs IS NULL AND am.amname = 'btree'
                AND pg_get_indexdef(i.indexrelid, 1, true) = spec.column_name
        ) THEN
            RAISE EXCEPTION USING MESSAGE='tb_co_part_setl_dtl.' || spec.name
                || ': expected original index', ERRCODE='P0001';
        END IF;
    END LOOP;
END
$part_settlement_indexes$;

COMMENT ON TABLE TB_CO_PART_SETL_DTL IS '영업 파트의 인력별 월 정산 확정 스냅샷이다. 취소 후에도 상세를 보존하고 재확정 시 해당 파트월 상세만 교체한다.';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.PART_ID IS 'TB_CO_PART_SETL.PART_ID 참조: 확정 시 월말 또는 퇴사 시점 소속';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.SETL_YM IS 'TB_CO_PART_SETL.SETL_YM 참조: 정산연월';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.DEV_ID IS 'TB_HR_DEV.DEV_ID 참조';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.EMP_TYPE_CD IS '확정 시 TB_HR_MON_PAY.EMP_TYPE_CD, 해당월 급여가 없으면 TB_HR_DEV.EMP_TYPE_CD를 복사';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.PROJ_NM_LIST IS '확정 시 프로젝트별 MM 내림차순·동률 이름순으로 중복 없이 이름을 쉼표와 공백으로 이어 저장. 이후 프로젝트명 변경으로 갱신하지 않음';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.MM IS '직접·자동 확정 시 애플리케이션이 저장하는 내부 제외·무상지원 포함 월 MM';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.UPRC_AMT IS '직접·자동 확정 시 애플리케이션이 저장하는 월 단가금액';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.PAY_AMT IS '직접·자동 확정 시 애플리케이션이 저장하는 급여금액: 미업로드 시작월만 일할';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.SALES_EXPS_AMT IS '직접·자동 확정 시 애플리케이션이 저장하는 해당월 파트 기준 관리비: 외주는 0';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.RETIRE_AMT IS '직접·자동 확정 시 애플리케이션이 저장하는 정규직 급여/12의 원 단위 절삭액';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.COST_AMT IS '직접·자동 확정 시 애플리케이션이 저장하는 급여+관리비+퇴직금';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.DIFF_AMT IS '직접·자동 확정 시 애플리케이션이 저장하는 이익: 월 단가-비용 계, 적자 음수';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.PRFT_AMT IS '직접·자동 확정 시 애플리케이션이 저장하는 인력별 이익금, 적자 음수';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.RMRK IS '확정 시 해당 비고와 경고를 가운데점( · )으로 이어 저장. 경고는 차단하지 않으나 100자 초과는 저장 거부';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 등록자';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 수정자';
COMMENT ON COLUMN TB_CO_PART_SETL_DTL.MOD_DTTM IS '수정일시';
COMMENT ON TABLE TB_CL_CLOSE_CHK IS '월 마감 점검의 항목별 결과를 보존하는 기존 구조다. 파트 정산 대상은 이 기록 유무와 무관하게 원천에서 조회하며 현재 파트 정산 GET은 이 표에 쓰지 않는다.';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.CLOSE_YM IS 'TB_CL_CLOSE.CLOSE_YM 참조';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.CHK_ITEM_CD IS 'PAY:급여 업로드|MM_OVER:MM 1.0 초과자|BILL:세금계산서 번호 미입력|FI:재무 항목|SETL:영업 파트 정산|BILL_MISS:청구 누락 점검';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.CHK_STAT_CD IS 'OK:정상|WARN:확인 필요';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.CHK_CNT IS '마감 점검 실행 시 점검 소유 서비스가 갱신하는 점검건수';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.CHK_CN IS '마감 점검 실행 시 점검 소유 서비스가 갱신하는 한 줄 설명';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.LINK_SCRN_ID IS 'TB_SY_MENU.SCRN_ID 참조 의도 (FK 미선언): 확인하기 연결화면';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.CHK_DTTM IS '점검 실행일시';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 등록자';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 수정자';
COMMENT ON COLUMN TB_CL_CLOSE_CHK.MOD_DTTM IS '수정일시';
