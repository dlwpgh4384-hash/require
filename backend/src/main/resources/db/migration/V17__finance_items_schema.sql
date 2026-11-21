-- Explicit --prepare-schema only. Original finance sources; no business rows or formula/code seeds.
DO $finance_schema$
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
        ('sq_tb_co_prev_adj'), ('sq_tb_fi_loan'), ('sq_tb_fi_xfer')
    ) AS sequences(name) LOOP
        EXECUTE format('CREATE SEQUENCE IF NOT EXISTS %I START WITH 1 INCREMENT BY 1', spec.name);
        IF NOT EXISTS (
            SELECT 1
            FROM pg_sequence s
            WHERE s.seqrelid = to_regclass(format('%I.%I', current_schema(), spec.name))
                AND s.seqtypid = 'bigint'::REGTYPE AND s.seqstart = 1 AND s.seqincrement = 1
                AND s.seqmin = 1 AND s.seqmax = 9223372036854775807
                AND s.seqcache = 1 AND NOT s.seqcycle
        ) THEN
            RAISE EXCEPTION USING MESSAGE=spec.name || ': expected original sequence', ERRCODE='P0001';
        END IF;
    END LOOP;
    FOR spec IN SELECT * FROM (VALUES
        ('tb_co_mon_pl', $ddl$CREATE TABLE IF NOT EXISTS TB_CO_MON_PL (
 PL_YM CHAR(6) NOT NULL,
 SALES_AMT NUMERIC(15) NOT NULL,
 ALLOC_COST_AMT NUMERIC(15),
 IDLE_COST_AMT NUMERIC(15),
 DRCT_EXPS_AMT NUMERIC(15),
 SALES_COST_AMT NUMERIC(15) NOT NULL,
 GROSS_PRFT_AMT NUMERIC(15),
 SGA_INPUT_AMT NUMERIC(15),
 INDRCT_LABOR_AMT NUMERIC(15),
 RND_AMT NUMERIC(15),
 SGA_TOT_AMT NUMERIC(15) NOT NULL,
 PREV_ADJ_AMT NUMERIC(15),
 OPRT_PRFT_AMT NUMERIC(15) NOT NULL,
 NOPR_INCM_AMT NUMERIC(15),
 NOPR_EXPS_AMT NUMERIC(15),
 CORP_TAX_AMT NUMERIC(15),
 NET_PRFT_AMT NUMERIC(15),
 MIGR_YN CHAR(1) DEFAULT 'N' NOT NULL,
 FIX_YN CHAR(1) DEFAULT 'N' NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_CO_MON_PL PRIMARY KEY (PL_YM),
 CONSTRAINT CK_TB_CO_MON_PL_01 CHECK (MIGR_YN IN ('Y', 'N')),
 CONSTRAINT CK_TB_CO_MON_PL_02 CHECK (FIX_YN IN ('Y', 'N'))
)$ddl$, ARRAY[]::TEXT[]),
        ('tb_co_prev_adj', $ddl$CREATE TABLE IF NOT EXISTS TB_CO_PREV_ADJ (
 ADJ_ID NUMERIC(10) DEFAULT nextval('SQ_TB_CO_PREV_ADJ') NOT NULL,
 APLY_YM CHAR(6) NOT NULL,
 TRGT_YM CHAR(6) NOT NULL,
 ADJ_ITEM_CD VARCHAR(10) NOT NULL,
 ADJ_AMT NUMERIC(15) NOT NULL,
 ADJ_RSN VARCHAR(300) NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_CO_PREV_ADJ PRIMARY KEY (ADJ_ID)
)$ddl$, ARRAY[]::TEXT[]),
        ('tb_fi_item', $ddl$CREATE TABLE IF NOT EXISTS TB_FI_ITEM (
 FIN_YM CHAR(6) NOT NULL,
 ITEM_SN NUMERIC(5) NOT NULL,
 FIN_CLS_CD VARCHAR(10) NOT NULL,
 SGA_ITEM_CD VARCHAR(10),
 ITEM_NM VARCHAR(50) NOT NULL,
 AMT NUMERIC(15) NOT NULL,
 VAT_AMT NUMERIC(15) NOT NULL,
 IO_DT DATE,
 RPT_YN CHAR(1) DEFAULT 'N' NOT NULL,
 AUTO_FILL_YN CHAR(1) DEFAULT 'N' NOT NULL,
 RMRK VARCHAR(500),
 UPLD_ID NUMERIC(12),
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_FI_ITEM PRIMARY KEY (FIN_YM, ITEM_SN),
 CONSTRAINT CK_TB_FI_ITEM_01 CHECK (RPT_YN IN ('Y', 'N')),
 CONSTRAINT CK_TB_FI_ITEM_02 CHECK (AUTO_FILL_YN IN ('Y', 'N'))
)$ddl$, ARRAY[
            'CONSTRAINT FK_TB_FI_ITEM_01 FOREIGN KEY (UPLD_ID) REFERENCES TB_SY_UPLD(UPLD_ID)'
        ]),
        ('tb_fi_corp_tax', $ddl$CREATE TABLE IF NOT EXISTS TB_FI_CORP_TAX (
 TAX_YR CHAR(4) NOT NULL,
 EST_TAX_AMT NUMERIC(15) NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_FI_CORP_TAX PRIMARY KEY (TAX_YR)
)$ddl$, ARRAY[]::TEXT[]),
        ('tb_fi_tax_pay', $ddl$CREATE TABLE IF NOT EXISTS TB_FI_TAX_PAY (
 TAX_YR CHAR(4) NOT NULL,
 PAY_TYPE_CD VARCHAR(10) NOT NULL,
 PAY_DUE_DT DATE NOT NULL,
 PAY_AMT NUMERIC(15),
 PAY_DT DATE,
 PAY_YN CHAR(1) DEFAULT 'N' NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_FI_TAX_PAY PRIMARY KEY (TAX_YR, PAY_TYPE_CD),
 CONSTRAINT CK_TB_FI_TAX_PAY_01 CHECK (PAY_YN IN ('Y', 'N'))
)$ddl$, ARRAY[]::TEXT[]),
        ('tb_fi_loan', $ddl$CREATE TABLE IF NOT EXISTS TB_FI_LOAN (
 LOAN_SN NUMERIC(10) DEFAULT nextval('SQ_TB_FI_LOAN') NOT NULL,
 LOAN_DT DATE NOT NULL,
 IO_CD VARCHAR(10) NOT NULL,
 AMT NUMERIC(15) NOT NULL,
 CNTPRT_NM VARCHAR(100),
 RMRK VARCHAR(200),
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_FI_LOAN PRIMARY KEY (LOAN_SN)
)$ddl$, ARRAY[]::TEXT[]),
        ('tb_fi_card', $ddl$CREATE TABLE IF NOT EXISTS TB_FI_CARD (
 CARD_ID VARCHAR(20) NOT NULL,
 CARD_NM VARCHAR(100) NOT NULL,
 CARD_NO_L4 CHAR(4) NOT NULL,
 USE_YN CHAR(1) DEFAULT 'Y' NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_FI_CARD PRIMARY KEY (CARD_ID),
 CONSTRAINT CK_TB_FI_CARD_01 CHECK (USE_YN IN ('Y', 'N'))
)$ddl$, ARRAY[]::TEXT[]),
        ('tb_fi_card_mon', $ddl$CREATE TABLE IF NOT EXISTS TB_FI_CARD_MON (
 PAY_YM CHAR(6) NOT NULL,
 CARD_SUM_AMT NUMERIC(15) NOT NULL,
 CASH_AMT NUMERIC(15) NOT NULL,
 EDIT_YN CHAR(1) DEFAULT 'N' NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_FI_CARD_MON PRIMARY KEY (PAY_YM),
 CONSTRAINT CK_TB_FI_CARD_MON_01 CHECK (EDIT_YN IN ('Y', 'N'))
)$ddl$, ARRAY[]::TEXT[]),
        ('tb_fi_card_pay', $ddl$CREATE TABLE IF NOT EXISTS TB_FI_CARD_PAY (
 PAY_YM CHAR(6) NOT NULL,
 CARD_ID VARCHAR(20) NOT NULL,
 PAY_AMT NUMERIC(15) NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_FI_CARD_PAY PRIMARY KEY (PAY_YM, CARD_ID)
)$ddl$, ARRAY[
            'CONSTRAINT FK_TB_FI_CARD_PAY_01 FOREIGN KEY (PAY_YM) REFERENCES TB_FI_CARD_MON(PAY_YM)',
            'CONSTRAINT FK_TB_FI_CARD_PAY_02 FOREIGN KEY (CARD_ID) REFERENCES TB_FI_CARD(CARD_ID)'
        ]),
        ('tb_fi_xfer', $ddl$CREATE TABLE IF NOT EXISTS TB_FI_XFER (
 XFER_SN NUMERIC(10) DEFAULT nextval('SQ_TB_FI_XFER') NOT NULL,
 XFER_DT DATE NOT NULL,
 XFER_CLS_CD VARCHAR(10) NOT NULL,
 ITEM_NM VARCHAR(100) NOT NULL,
 RCVR_NM VARCHAR(100),
 AMT NUMERIC(15) NOT NULL,
 RMRK VARCHAR(200),
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_FI_XFER PRIMARY KEY (XFER_SN)
)$ddl$, ARRAY[]::TEXT[])
    ) AS definitions(table_name, definition, references_sql) LOOP
        relation := to_regclass(format('%I.%I', current_schema(), spec.table_name));
        IF relation IS NULL THEN
            ddl := spec.definition;
            IF cardinality(spec.references_sql) > 0 THEN
                ddl := left(ddl, length(ddl) - 1)
                    || ', ' || array_to_string(spec.references_sql, ', ') || ')';
            END IF;
            EXECUTE ddl;
            relation := to_regclass(format('%I.%I', current_schema(), spec.table_name));
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_class c WHERE c.oid = relation AND c.relkind = 'r') THEN
            RAISE EXCEPTION USING MESSAGE=spec.table_name || ': expected ordinary table', ERRCODE='P0001';
        END IF;
        EXECUTE format('LOCK TABLE %s IN ACCESS EXCLUSIVE MODE', relation);
        EXECUTE regexp_replace(spec.definition, 'CREATE TABLE IF NOT EXISTS [A-Z_]+',
            'CREATE TEMP TABLE finance_expected');
        SELECT array_agg(a.attname || ':' || format_type(a.atttypid, a.atttypmod)
            || ':' || a.attnotnull::TEXT || ':' || coalesce(pg_get_expr(d.adbin, d.adrelid), '')
            ORDER BY a.attnum)
        INTO expected_columns
        FROM pg_attribute a
        LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
        WHERE a.attrelid = 'pg_temp.finance_expected'::REGCLASS
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
        WHERE c.conrelid = 'pg_temp.finance_expected'::REGCLASS AND c.contype IN ('p', 'c', 'u');
        SELECT array_agg(c.conname || ':' || pg_get_constraintdef(c.oid)
            || ':' || c.condeferrable::TEXT ORDER BY c.conname)
        INTO actual_constraints
        FROM pg_constraint c
        WHERE c.conrelid = relation AND c.contype IN ('p', 'c', 'u') AND c.convalidated;
        DROP TABLE pg_temp.finance_expected;
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
$finance_schema$;

DO $finance_indexes$
DECLARE
    spec RECORD;
BEGIN
    FOR spec IN SELECT * FROM (VALUES
        ('ix_tb_co_prev_adj_01', 'tb_co_prev_adj', 'aply_ym'),
        ('ix_tb_fi_item_01', 'tb_fi_item', 'upld_id'),
        ('ix_tb_fi_loan_01', 'tb_fi_loan', 'loan_dt'),
        ('ix_tb_fi_card_pay_01', 'tb_fi_card_pay', 'card_id'),
        ('ix_tb_fi_xfer_01', 'tb_fi_xfer', 'xfer_dt')
    ) AS indexes(name, table_name, column_name) LOOP
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (%I)',
            spec.name, spec.table_name, spec.column_name);
        IF NOT EXISTS (
            SELECT 1
            FROM pg_index i
            JOIN pg_class c ON c.oid = i.indexrelid
            JOIN pg_am am ON am.oid = c.relam
            WHERE i.indexrelid = to_regclass(format('%I.%I', current_schema(), spec.name))
                AND i.indrelid = to_regclass(format('%I.%I', current_schema(), spec.table_name))
                AND i.indisvalid AND i.indisready AND NOT i.indisunique
                AND i.indnkeyatts = 1 AND i.indnatts = 1
                AND i.indpred IS NULL AND i.indexprs IS NULL AND am.amname = 'btree'
                AND pg_get_indexdef(i.indexrelid, 1, true) = spec.column_name
        ) THEN
            RAISE EXCEPTION USING MESSAGE=spec.table_name || '.' || spec.name
                || ': expected original index', ERRCODE='P0001';
        END IF;
    END LOOP;
END
$finance_indexes$;

COMMENT ON TABLE TB_CO_MON_PL IS '월별손익 - 마감 스냅샷 · 2025(시작한 달 전) 이관';
COMMENT ON COLUMN TB_CO_MON_PL.PL_YM IS '손익연월';
COMMENT ON COLUMN TB_CO_MON_PL.SALES_AMT IS '매출액';
COMMENT ON COLUMN TB_CO_MON_PL.ALLOC_COST_AMT IS '배분원가: 내부 제외. NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.IDLE_COST_AMT IS '대기원가: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.DRCT_EXPS_AMT IS '직접경비: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.SALES_COST_AMT IS '매출원가: 식-28';
COMMENT ON COLUMN TB_CO_MON_PL.GROSS_PRFT_AMT IS '매출총이익: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.SGA_INPUT_AMT IS '판관비입력: UI-10. NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.INDRCT_LABOR_AMT IS '간접인건비: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.RND_AMT IS '연구개발비: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.SGA_TOT_AMT IS '판관비합계: 식-29';
COMMENT ON COLUMN TB_CO_MON_PL.PREV_ADJ_AMT IS '전월조정: 식-59. NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.OPRT_PRFT_AMT IS '영업이익: 식-30';
COMMENT ON COLUMN TB_CO_MON_PL.NOPR_INCM_AMT IS '영업외수익: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.NOPR_EXPS_AMT IS '영업외비용: NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.CORP_TAX_AMT IS '법인세월할: 식-31, 마감 시 고정. NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.NET_PRFT_AMT IS '당기순이익: 식-32. NULL은 2025(시작한 달 전) 이관(MIGR_YN=Y) 달에 옛 엑셀에 없던 값만, 화면 「–」';
COMMENT ON COLUMN TB_CO_MON_PL.MIGR_YN IS '이관여부: 2025년(시작한 달 전) B.11 = Y(세부 구성 일부만). B.11 열(2026-10-29 사용자 결정, 테이블 검증 2장 E11): 필수 = 연월·매출·매출원가·판관비 합계·영업이익, 있으면 넣음 = 배분원가·대기원가·직접경비·매출총이익·판관비 입력·간접인건비·연구개발비·전월조정·영업외수익·영업외비용·법인세·당기순이익. Y인 달은 엑셀 값을 그대로 저장하고 다시 계산하지 않음. 빈칸은 0이 아니라 NULL(화면 「–」). N인 달은 모든 금액 칸에 값이 있음';
COMMENT ON COLUMN TB_CO_MON_PL.FIX_YN IS '마감고정여부';
COMMENT ON COLUMN TB_CO_MON_PL.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_CO_MON_PL.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_CO_MON_PL.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_CO_MON_PL.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_CO_PREV_ADJ IS '전월조정';
COMMENT ON COLUMN TB_CO_PREV_ADJ.ADJ_ID IS '조정ID';
COMMENT ON COLUMN TB_CO_PREV_ADJ.APLY_YM IS '반영연월: 조정을 넣는 이번 달';
COMMENT ON COLUMN TB_CO_PREV_ADJ.TRGT_YM IS '대상연월: 원래 마감된 달';
COMMENT ON COLUMN TB_CO_PREV_ADJ.ADJ_ITEM_CD IS '조정항목코드: SALES 매출 / COST 원가 / SGA 판관비 / ETC 기타 (UI-10 전월 조정 표, 35번 1)';
COMMENT ON COLUMN TB_CO_PREV_ADJ.ADJ_AMT IS '조정금액: 영업이익에 더하는 값(이익 늘면 +, 줄면 −, Q-식-55 결정 2026-10-21)';
COMMENT ON COLUMN TB_CO_PREV_ADJ.ADJ_RSN IS '조정사유: UI-10 전월 조정 표의 사유(필수, SFR-110-12)';
COMMENT ON COLUMN TB_CO_PREV_ADJ.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_CO_PREV_ADJ.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_CO_PREV_ADJ.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_CO_PREV_ADJ.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_ITEM IS '재무항목';
COMMENT ON COLUMN TB_FI_ITEM.FIN_YM IS '재무연월';
COMMENT ON COLUMN TB_FI_ITEM.ITEM_SN IS '항목일련번호';
COMMENT ON COLUMN TB_FI_ITEM.FIN_CLS_CD IS '재무구분코드: SGA 판관비 / NOPR_IN 영업외수익 / NOPR_OUT 영업외비용 (시스템)';
COMMENT ON COLUMN TB_FI_ITEM.SGA_ITEM_CD IS '판관비항목코드: 공통코드 SGA_ITEM. 직접 쓴 항목은 비움(Q-10-1)';
COMMENT ON COLUMN TB_FI_ITEM.ITEM_NM IS '세부항목명: 필수';
COMMENT ON COLUMN TB_FI_ITEM.AMT IS '금액: 부가세 제외, 필수';
COMMENT ON COLUMN TB_FI_ITEM.VAT_AMT IS '부가세금액: 화면·엑셀(B.2)에서 선택 입력, 비우면 0으로 저장. 자금수지 지출에만 더함(금액 + 부가세)(2026-10-31 C13)';
COMMENT ON COLUMN TB_FI_ITEM.IO_DT IS '입출금일자: 판관비·영업외비용은 자금수지 제경비 달(없으면 그 달), 영업외수익은 자금수지 잡수익 예정일(2026-10-28 지급 관리 삭제)';
COMMENT ON COLUMN TB_FI_ITEM.RPT_YN IS '매달반복여부';
COMMENT ON COLUMN TB_FI_ITEM.AUTO_FILL_YN IS '자동채움여부: “자동” 표시';
COMMENT ON COLUMN TB_FI_ITEM.RMRK IS '비고';
COMMENT ON COLUMN TB_FI_ITEM.UPLD_ID IS '업로드ID: 엑셀(B.2)로 올린 줄이면 그 업로드 ID, 직접 입력·자동 채움 줄은 NULL. 같은 달 재업로드 때 이 값이 있는 줄만 지우고 새로 넣는다(2026-10-29 사용자 결정, 테이블 검증 2장 E9)';
COMMENT ON COLUMN TB_FI_ITEM.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_ITEM.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_ITEM.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_ITEM.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_CORP_TAX IS '연간법인세';
COMMENT ON COLUMN TB_FI_CORP_TAX.TAX_YR IS '과세연도';
COMMENT ON COLUMN TB_FI_CORP_TAX.EST_TAX_AMT IS '연간예상법인세: 바꾸면 마감 안 된 달부터 재분배. 새 해 줄은 그 해 재무 항목을 처음 열 때 작년 값으로 만들어짐(없으면 0), 재무가 고침(2026-10-29)';
COMMENT ON COLUMN TB_FI_CORP_TAX.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_CORP_TAX.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_CORP_TAX.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_CORP_TAX.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_TAX_PAY IS '법인세납부';
COMMENT ON COLUMN TB_FI_TAX_PAY.TAX_YR IS '과세연도';
COMMENT ON COLUMN TB_FI_TAX_PAY.PAY_TYPE_CD IS '납부유형코드: CNFM 확정 신고(다음 해 3월) / MID 중간예납(8월)';
COMMENT ON COLUMN TB_FI_TAX_PAY.PAY_DUE_DT IS '납부예정일자';
COMMENT ON COLUMN TB_FI_TAX_PAY.PAY_AMT IS '납부금액: 금액 원본. MID: 재무가 세무사 안내 금액을 입력(비어 있으면 0으로 봄). CNFM: NULL이면 자동 = TB_FI_CORP_TAX.EST_TAX_AMT − 같은 과세연도 MID의 PAY_AMT(식-50)로 보여 주고, 사람이 고치면 그 값을 저장(납부 확인 전에 자동 값과 같게 넣으면 NULL로 둠). 납부 확인 때 실제 낸 금액을 숫자로 저장(자동 값과 같아도 NULL로 두지 않음)하고, 그 뒤로는 연간 예상액을 고쳐도 바뀌지 않으며 PAY_YN=Y 줄은 고칠 수 없음(2026-10-30 B2)';
COMMENT ON COLUMN TB_FI_TAX_PAY.PAY_DT IS '납부일자: 납부 확인 때 넣는 실제 낸 날(기본 PAY_DUE_DT). 자금수지 「법인세 납부」는 PAY_YN=Y면 이 날의 달, N이면 PAY_DUE_DT의 달에 잡힘(2026-10-29)';
COMMENT ON COLUMN TB_FI_TAX_PAY.PAY_YN IS '납부여부: N 예정 / Y 납부 확인. 날짜가 지나도 자동으로 Y가 되지 않고 [납부 확인]으로만 Y(2026-10-29). 화면 상태: Y → 납부, N이고 PAY_DUE_DT < 오늘 → 확인 필요(대시보드 경고), 그 밖 → 예정';
COMMENT ON COLUMN TB_FI_TAX_PAY.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_TAX_PAY.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_TAX_PAY.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_TAX_PAY.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_LOAN IS '대여금입출금';
COMMENT ON COLUMN TB_FI_LOAN.LOAN_SN IS '대여금일련번호';
COMMENT ON COLUMN TB_FI_LOAN.LOAN_DT IS '입출금일자: 이 날짜의 달 자금수지에 잡힘';
COMMENT ON COLUMN TB_FI_LOAN.IO_CD IS '입출금구분코드: IN 받음 / OUT 갚음';
COMMENT ON COLUMN TB_FI_LOAN.AMT IS '금액: 양수로 저장, 갚음은 자금수지에서 −';
COMMENT ON COLUMN TB_FI_LOAN.CNTPRT_NM IS '상대명: 대표이사·은행 등';
COMMENT ON COLUMN TB_FI_LOAN.RMRK IS '비고';
COMMENT ON COLUMN TB_FI_LOAN.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_LOAN.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_LOAN.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_LOAN.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_CARD IS '법인카드';
COMMENT ON COLUMN TB_FI_CARD.CARD_ID IS '카드ID: K-01 형식';
COMMENT ON COLUMN TB_FI_CARD.CARD_NM IS '카드명: 예: 영업 1팀 카드';
COMMENT ON COLUMN TB_FI_CARD.CARD_NO_L4 IS '카드번호끝4자리: 전체 카드번호는 저장하지 않음';
COMMENT ON COLUMN TB_FI_CARD.USE_YN IS '사용여부: 화면 [삭제]는 N으로 바꿈(물리 삭제 안 함, D-4). N이어도 결제 금액이 있는 달에는 목록에 보임. [되살리기]는 Y';
COMMENT ON COLUMN TB_FI_CARD.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_CARD.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_CARD.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_CARD.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_CARD_MON IS '법인카드월합계';
COMMENT ON COLUMN TB_FI_CARD_MON.PAY_YM IS '결제연월: 통장에서 카드 대금이 나간 달';
COMMENT ON COLUMN TB_FI_CARD_MON.CARD_SUM_AMT IS '카드별합계금액: TB_FI_CARD_PAY 합계(저장 때 계산)';
COMMENT ON COLUMN TB_FI_CARD_MON.CASH_AMT IS '자금수지반영금액: 기본은 카드별 합계. 카드사 청구서와 다르면 재무가 직접 고침';
COMMENT ON COLUMN TB_FI_CARD_MON.EDIT_YN IS '직접수정여부: Y면 화면에 카드별 합계와의 차액 표시, [자동 합계로]를 누르면 N';
COMMENT ON COLUMN TB_FI_CARD_MON.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_CARD_MON.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_CARD_MON.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_CARD_MON.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_CARD_PAY IS '법인카드월결제';
COMMENT ON COLUMN TB_FI_CARD_PAY.PAY_YM IS '결제연월: → TB_FI_CARD_MON';
COMMENT ON COLUMN TB_FI_CARD_PAY.CARD_ID IS '카드ID: → TB_FI_CARD';
COMMENT ON COLUMN TB_FI_CARD_PAY.PAY_AMT IS '결제금액: 그 달 카드 대금(부가세 포함). 빈칸은 0';
COMMENT ON COLUMN TB_FI_CARD_PAY.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_CARD_PAY.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_CARD_PAY.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_CARD_PAY.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_FI_XFER IS '계좌이체지출';
COMMENT ON COLUMN TB_FI_XFER.XFER_SN IS '이체일련번호';
COMMENT ON COLUMN TB_FI_XFER.XFER_DT IS '이체일자: 이 날짜의 달 자금수지에 잡힘';
COMMENT ON COLUMN TB_FI_XFER.XFER_CLS_CD IS '이체구분코드: 공통코드 XFER_CLS: EXP 계좌이체(자금수지 제경비) / SPCL 특별비(자금수지 미지급 인건비). 화면 기본 선택은 EXP';
COMMENT ON COLUMN TB_FI_XFER.ITEM_NM IS '내용: 예: 장비 임대, 퇴직금(홍길동), 퇴직연금 11월 납입';
COMMENT ON COLUMN TB_FI_XFER.RCVR_NM IS '받는곳명: 업체 이름(특별비는 받는 사람)';
COMMENT ON COLUMN TB_FI_XFER.AMT IS '금액: 실제 나간 금액(부가세 포함)';
COMMENT ON COLUMN TB_FI_XFER.RMRK IS '비고';
COMMENT ON COLUMN TB_FI_XFER.REG_USER_ID IS '등록자ID: 등록한 사용자(TB_SY_USER.USER_ID)';
COMMENT ON COLUMN TB_FI_XFER.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_XFER.MOD_USER_ID IS '수정자ID';
COMMENT ON COLUMN TB_FI_XFER.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
