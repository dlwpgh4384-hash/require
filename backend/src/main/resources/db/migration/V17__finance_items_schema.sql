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

COMMENT ON TABLE TB_CO_MON_PL IS '월별 손익 원천과 마감 스냅샷을 보존한다. 재무 저장은 기존 미마감 행의 재무 파생값만 갱신하며 없는 손익 행을 만들지 않는다.';
COMMENT ON COLUMN TB_CO_MON_PL.PL_YM IS '손익연월';
COMMENT ON COLUMN TB_CO_MON_PL.SALES_AMT IS '실제 매출 원천에서 손익 계산·마감 시 손익 소비 서비스가 갱신';
COMMENT ON COLUMN TB_CO_MON_PL.ALLOC_COST_AMT IS '손익 계산·마감 시 원가 소비 서비스가 갱신. 이관 자료의 미제공 값은 NULL';
COMMENT ON COLUMN TB_CO_MON_PL.IDLE_COST_AMT IS '손익 계산·마감 시 원가 소비 서비스가 갱신. 이관 자료의 미제공 값은 NULL';
COMMENT ON COLUMN TB_CO_MON_PL.DRCT_EXPS_AMT IS '손익 계산·마감 시 원가 소비 서비스가 갱신. 이관 자료의 미제공 값은 NULL';
COMMENT ON COLUMN TB_CO_MON_PL.SALES_COST_AMT IS '식-28에 따라 손익 계산·마감 시 손익 소비 서비스가 갱신';
COMMENT ON COLUMN TB_CO_MON_PL.GROSS_PRFT_AMT IS '손익 계산·마감 시 손익 소비 서비스가 매출에서 매출원가를 빼서 갱신';
COMMENT ON COLUMN TB_CO_MON_PL.SGA_INPUT_AMT IS '재무 서비스가 미마감 월 재무 저장 시 TB_FI_ITEM 판관비 합계로 갱신';
COMMENT ON COLUMN TB_CO_MON_PL.INDRCT_LABOR_AMT IS '손익 계산·마감 시 원가 소비 서비스가 간접 인건비로 갱신';
COMMENT ON COLUMN TB_CO_MON_PL.RND_AMT IS '손익 계산·마감 시 원가 소비 서비스가 내부 프로젝트 연구개발비로 갱신';
COMMENT ON COLUMN TB_CO_MON_PL.SGA_TOT_AMT IS '재무 서비스가 미마감 월 재무 저장 시 판관비 입력·기존 간접 인건비·연구개발비의 식-29 합계로 갱신';
COMMENT ON COLUMN TB_CO_MON_PL.PREV_ADJ_AMT IS '재무 서비스가 미마감 월 재무 저장 시 TB_CO_PREV_ADJ 반영월의 식-59 합계로 갱신';
COMMENT ON COLUMN TB_CO_MON_PL.OPRT_PRFT_AMT IS '재무 서비스가 미마감 월 재무 저장 시 기존 매출·매출원가와 재무 파생값으로 갱신';
COMMENT ON COLUMN TB_CO_MON_PL.NOPR_INCM_AMT IS '재무 서비스가 미마감 월 재무 저장 시 TB_FI_ITEM 영업외수익 합계로 갱신';
COMMENT ON COLUMN TB_CO_MON_PL.NOPR_EXPS_AMT IS '재무 서비스가 미마감 월 재무 저장 시 TB_FI_ITEM 영업외비용 합계로 갱신';
COMMENT ON COLUMN TB_CO_MON_PL.CORP_TAX_AMT IS '재무 서비스가 연간 예상액 저장 시 미마감 월에만 식-31로 재분배하고 마감 시 고정';
COMMENT ON COLUMN TB_CO_MON_PL.NET_PRFT_AMT IS '재무 서비스가 미마감 월 재무 저장 시 영업이익·영업외수익·영업외비용·법인세로 갱신';
COMMENT ON COLUMN TB_CO_MON_PL.MIGR_YN IS '이관여부. 이관한 세부 구성 미제공 값은 NULL로 보존';
COMMENT ON COLUMN TB_CO_MON_PL.FIX_YN IS '마감고정여부';
COMMENT ON COLUMN TB_CO_MON_PL.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 등록자';
COMMENT ON COLUMN TB_CO_MON_PL.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_CO_MON_PL.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 수정자';
COMMENT ON COLUMN TB_CO_MON_PL.MOD_DTTM IS 'DB 저장 수정일시: 낙관적 동시 수정 비교';
COMMENT ON TABLE TB_CO_PREV_ADJ IS '이미 마감된 달의 정정 금액을 현재 미마감 달에 반영하는 전월 조정 원천이다. 대상 마감월 자료를 바꾸지 않는다.';
COMMENT ON COLUMN TB_CO_PREV_ADJ.ADJ_ID IS '전월 조정 자체 식별자';
COMMENT ON COLUMN TB_CO_PREV_ADJ.APLY_YM IS '반영연월';
COMMENT ON COLUMN TB_CO_PREV_ADJ.TRGT_YM IS '원래 마감된 대상연월';
COMMENT ON COLUMN TB_CO_PREV_ADJ.ADJ_ITEM_CD IS 'SALES:매출|COST:원가|SGA:판관비|ETC:기타';
COMMENT ON COLUMN TB_CO_PREV_ADJ.ADJ_AMT IS '영업이익에 더하는 정정 금액: 이익 증가 양수·감소 음수';
COMMENT ON COLUMN TB_CO_PREV_ADJ.ADJ_RSN IS '필수 조정사유';
COMMENT ON COLUMN TB_CO_PREV_ADJ.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 등록자';
COMMENT ON COLUMN TB_CO_PREV_ADJ.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_CO_PREV_ADJ.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 수정자';
COMMENT ON COLUMN TB_CO_PREV_ADJ.MOD_DTTM IS 'DB 저장 수정일시: 낙관적 동시 수정 비교';
COMMENT ON TABLE TB_FI_ITEM IS '월별 판관비·영업외수익·영업외비용의 재무 입력 원천이다. 업로드 출처와 자동채움 여부는 서버가 보존한다.';
COMMENT ON COLUMN TB_FI_ITEM.FIN_YM IS '재무연월';
COMMENT ON COLUMN TB_FI_ITEM.ITEM_SN IS '재무연월 안에서만 유일한 항목일련번호';
COMMENT ON COLUMN TB_FI_ITEM.FIN_CLS_CD IS 'SGA:판관비|NOPR_IN:영업외수익|NOPR_OUT:영업외비용';
COMMENT ON COLUMN TB_FI_ITEM.SGA_ITEM_CD IS 'TB_SY_CODE.CODE 참조 의도 (FK 미선언): SGA_ITEM 그룹, 직접 입력은 NULL';
COMMENT ON COLUMN TB_FI_ITEM.ITEM_NM IS '세부항목명';
COMMENT ON COLUMN TB_FI_ITEM.AMT IS '부가세 제외 원 단위 금액';
COMMENT ON COLUMN TB_FI_ITEM.VAT_AMT IS '부가세: 자금수지 지출에만 더하며 손익에서 제외';
COMMENT ON COLUMN TB_FI_ITEM.IO_DT IS '자금수지 입출금일자, 없으면 재무연월';
COMMENT ON COLUMN TB_FI_ITEM.RPT_YN IS '매달반복여부';
COMMENT ON COLUMN TB_FI_ITEM.AUTO_FILL_YN IS '재무 서비스가 반복 자동채움 저장 시 Y, 명시적 지난달 불러오기 저장 시 N으로 설정';
COMMENT ON COLUMN TB_FI_ITEM.RMRK IS '비고';
COMMENT ON COLUMN TB_FI_ITEM.UPLD_ID IS 'TB_SY_UPLD.UPLD_ID 참조: 기존 출처 불변, 같은 달 업로드 교체 대상';
COMMENT ON COLUMN TB_FI_ITEM.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 등록자';
COMMENT ON COLUMN TB_FI_ITEM.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_ITEM.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 수정자';
COMMENT ON COLUMN TB_FI_ITEM.MOD_DTTM IS 'DB 저장 수정일시: 낙관적 동시 수정 비교';
COMMENT ON TABLE TB_FI_CORP_TAX IS '과세연도별 연간 예상 법인세의 유일한 저장 원천이다. 연도 준비 명령은 없는 행만 생성하고 일반 조회·기동은 생성하지 않는다.';
COMMENT ON COLUMN TB_FI_CORP_TAX.TAX_YR IS '과세연도';
COMMENT ON COLUMN TB_FI_CORP_TAX.EST_TAX_AMT IS '연간 예상 법인세: 변경 시 미마감 월에만 재분배';
COMMENT ON COLUMN TB_FI_CORP_TAX.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 등록자';
COMMENT ON COLUMN TB_FI_CORP_TAX.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_CORP_TAX.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 수정자';
COMMENT ON COLUMN TB_FI_CORP_TAX.MOD_DTTM IS 'DB 저장 수정일시: 낙관적 동시 수정 비교';
COMMENT ON TABLE TB_FI_TAX_PAY IS '과세연도·납부유형별 예정 및 실제 법인세 납부 원천이다. 납부 확인 후 실제 금액·일자는 연간 예상액 변경과 무관하게 보존한다.';
COMMENT ON COLUMN TB_FI_TAX_PAY.TAX_YR IS 'TB_FI_CORP_TAX.TAX_YR 참조 의도 (FK 미선언): 과세연도';
COMMENT ON COLUMN TB_FI_TAX_PAY.PAY_TYPE_CD IS 'MID:중간예납|CNFM:확정신고';
COMMENT ON COLUMN TB_FI_TAX_PAY.PAY_DUE_DT IS '납부예정일자';
COMMENT ON COLUMN TB_FI_TAX_PAY.PAY_AMT IS '유일한 납부 금액 원천: 미확인 MID NULL은 0, CNFM NULL은 연간 예상액에서 MID 차감. 확인 후 실제 숫자 고정';
COMMENT ON COLUMN TB_FI_TAX_PAY.PAY_DT IS '납부 확인 때 저장한 실제 납부일: 납부 후 자금수지는 이 날짜의 달에 반영';
COMMENT ON COLUMN TB_FI_TAX_PAY.PAY_YN IS '납부 확인 여부: 날짜 경과로 자동 변경하지 않음';
COMMENT ON COLUMN TB_FI_TAX_PAY.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 등록자';
COMMENT ON COLUMN TB_FI_TAX_PAY.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_TAX_PAY.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 수정자';
COMMENT ON COLUMN TB_FI_TAX_PAY.MOD_DTTM IS 'DB 저장 수정일시: 낙관적 동시 수정 비교';
COMMENT ON TABLE TB_FI_LOAN IS '대여금 받음·갚음의 실제 입출금 원천이다. 자금수지에는 반영하지만 손익에는 포함하지 않는다.';
COMMENT ON COLUMN TB_FI_LOAN.LOAN_SN IS '대여금 자체 일련번호';
COMMENT ON COLUMN TB_FI_LOAN.LOAN_DT IS '입출금일자';
COMMENT ON COLUMN TB_FI_LOAN.IO_CD IS 'IN:받음|OUT:갚음';
COMMENT ON COLUMN TB_FI_LOAN.AMT IS '양수 금액: 자금수지에서 방향별 부호 적용';
COMMENT ON COLUMN TB_FI_LOAN.CNTPRT_NM IS '상대명';
COMMENT ON COLUMN TB_FI_LOAN.RMRK IS '비고';
COMMENT ON COLUMN TB_FI_LOAN.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 등록자';
COMMENT ON COLUMN TB_FI_LOAN.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_LOAN.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 수정자';
COMMENT ON COLUMN TB_FI_LOAN.MOD_DTTM IS 'DB 저장 수정일시: 낙관적 동시 수정 비교';
COMMENT ON TABLE TB_FI_CARD IS '회사 법인카드의 공통 원장이다. 삭제는 USE_YN=N으로 보존하고 카드별 월 금액은 별도 원천을 사용한다.';
COMMENT ON COLUMN TB_FI_CARD.CARD_ID IS '서버 발급 카드 자체 식별자';
COMMENT ON COLUMN TB_FI_CARD.CARD_NM IS '카드명';
COMMENT ON COLUMN TB_FI_CARD.CARD_NO_L4 IS '카드번호 끝 네 자리만 저장';
COMMENT ON COLUMN TB_FI_CARD.USE_YN IS '사용여부: N이어도 선택월 PAY_AMT가 양수이면 목록에 표시';
COMMENT ON COLUMN TB_FI_CARD.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 등록자';
COMMENT ON COLUMN TB_FI_CARD.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_CARD.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 수정자';
COMMENT ON COLUMN TB_FI_CARD.MOD_DTTM IS 'DB 저장 수정일시: 낙관적 동시 수정 비교';
COMMENT ON TABLE TB_FI_CARD_MON IS '결제월별 카드 합계와 실제 자금수지 반영액을 보존한다. 카드별 금액이 아닌 월 전체 한 행이며 파트 정산카드를 변경하지 않는다.';
COMMENT ON COLUMN TB_FI_CARD_MON.PAY_YM IS '결제연월';
COMMENT ON COLUMN TB_FI_CARD_MON.CARD_SUM_AMT IS '재무 서비스가 미마감 월 저장 시 TB_FI_CARD_PAY.PAY_AMT 합계로 갱신';
COMMENT ON COLUMN TB_FI_CARD_MON.CASH_AMT IS '재무 서비스가 미마감 월 저장 시 수기 입력 또는 자동 카드 합계로 갱신하는 자금 반영액';
COMMENT ON COLUMN TB_FI_CARD_MON.EDIT_YN IS '수기 합계 여부: 자동 합계 복원 시 N';
COMMENT ON COLUMN TB_FI_CARD_MON.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 등록자';
COMMENT ON COLUMN TB_FI_CARD_MON.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_CARD_MON.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 수정자';
COMMENT ON COLUMN TB_FI_CARD_MON.MOD_DTTM IS 'DB 저장 수정일시: 낙관적 동시 수정 비교';
COMMENT ON TABLE TB_FI_CARD_PAY IS '결제월·카드별 원 단위 결제액 원천이다. 카드 월 합계는 TB_FI_CARD_MON에 저장하며 마감월 금액은 변경하지 않는다.';
COMMENT ON COLUMN TB_FI_CARD_PAY.PAY_YM IS 'TB_FI_CARD_MON.PAY_YM 참조: 결제연월';
COMMENT ON COLUMN TB_FI_CARD_PAY.CARD_ID IS 'TB_FI_CARD.CARD_ID 참조';
COMMENT ON COLUMN TB_FI_CARD_PAY.PAY_AMT IS '카드별 선택월 결제액: 부가세 포함';
COMMENT ON COLUMN TB_FI_CARD_PAY.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 등록자';
COMMENT ON COLUMN TB_FI_CARD_PAY.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_CARD_PAY.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 수정자';
COMMENT ON COLUMN TB_FI_CARD_PAY.MOD_DTTM IS 'DB 저장 수정일시: 낙관적 동시 수정 비교';
COMMENT ON TABLE TB_FI_XFER IS '계좌이체 제경비 및 특별비의 실제 지출 원천이다. 자금수지에만 반영하고 손익과 파트 정산은 변경하지 않는다.';
COMMENT ON COLUMN TB_FI_XFER.XFER_SN IS '이체 자체 일련번호';
COMMENT ON COLUMN TB_FI_XFER.XFER_DT IS '이체일자';
COMMENT ON COLUMN TB_FI_XFER.XFER_CLS_CD IS 'EXP:계좌이체|SPCL:특별비';
COMMENT ON COLUMN TB_FI_XFER.ITEM_NM IS '지출 내용';
COMMENT ON COLUMN TB_FI_XFER.RCVR_NM IS '받는곳명';
COMMENT ON COLUMN TB_FI_XFER.AMT IS '부가세 포함 실제 지출액';
COMMENT ON COLUMN TB_FI_XFER.RMRK IS '비고';
COMMENT ON COLUMN TB_FI_XFER.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 등록자';
COMMENT ON COLUMN TB_FI_XFER.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_FI_XFER.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언): 수정자';
COMMENT ON COLUMN TB_FI_XFER.MOD_DTTM IS 'DB 저장 수정일시: 낙관적 동시 수정 비교';
