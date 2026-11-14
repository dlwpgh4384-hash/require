-- Explicit --prepare-schema only. Original sql/DDL_PostgreSQL.sql definitions.
-- Existing relations are validated, never repaired; no business rows are seeded.
DO $client_part_upload$
DECLARE
    definitions text[] := ARRAY[
$ddl$CREATE TABLE IF NOT EXISTS TB_BS_CLNT (
 CLNT_ID VARCHAR(20) NOT NULL,
 CLNT_TYPE_CD VARCHAR(10) NOT NULL,
 CLNT_NM VARCHAR(50) NOT NULL,
 BIZ_REG_NO VARCHAR(12) NOT NULL,
 CEO_NM VARCHAR(20),
 INDUTY_CD VARCHAR(10),
 CHRG_NM VARCHAR(20),
 TELNO VARCHAR(20),
 TAX_EMAIL_ADDR VARCHAR(100),
 BANK_CD VARCHAR(10),
 ACCT_NO_ENC VARCHAR(200),
 DPSTR_NM VARCHAR(30),
 USE_YN CHAR(1) DEFAULT 'Y' NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_BS_CLNT PRIMARY KEY (CLNT_ID),
 CONSTRAINT UK_TB_BS_CLNT_01 UNIQUE (BIZ_REG_NO),
 CONSTRAINT CK_TB_BS_CLNT_01 CHECK (USE_YN IN ('Y', 'N'))
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_SY_UPLD (
 UPLD_ID NUMERIC(12) DEFAULT nextval('SQ_TB_SY_UPLD') NOT NULL,
 FORM_TYPE_CD VARCHAR(10) NOT NULL,
 SCRN_ID VARCHAR(10) NOT NULL,
 FILE_NM VARCHAR(200) NOT NULL,
 FILE_SIZE NUMERIC(12) NOT NULL,
 TRGT_YM CHAR(6),
 TRGT_PROJ_ID VARCHAR(20),
 TRGT_DSTRB_STEP_CD VARCHAR(10),
 TOT_ROW_CNT NUMERIC(7) NOT NULL,
 SUCC_CNT NUMERIC(7) NOT NULL,
 WARN_CNT NUMERIC(7) NOT NULL,
 ERR_CNT NUMERIC(7) NOT NULL,
 UPLD_STAT_CD VARCHAR(10) NOT NULL,
 APLY_DTTM TIMESTAMPTZ,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_SY_UPLD PRIMARY KEY (UPLD_ID)
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_SY_UPLD_ERR (
 UPLD_ID NUMERIC(12) NOT NULL,
 RSLT_SN NUMERIC(7) NOT NULL,
 RSLT_TYPE_CD VARCHAR(10) NOT NULL,
 ROW_NO NUMERIC(7) NOT NULL,
 COL_NM VARCHAR(50),
 INPUT_VAL VARCHAR(200),
 RSN_CN VARCHAR(300) NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_SY_UPLD_ERR PRIMARY KEY (UPLD_ID, RSLT_SN)
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_SY_ADM_AREA (
 AREA_CD VARCHAR(10) NOT NULL,
 AREA_LVL_CD VARCHAR(10) NOT NULL,
 UPPER_AREA_CD VARCHAR(10),
 SIDO_NM VARCHAR(20) NOT NULL,
 SIDO_FULL_NM VARCHAR(30) NOT NULL,
 SGG_NM VARCHAR(30),
 EMD_NM VARCHAR(30),
 USE_YN CHAR(1) DEFAULT 'Y' NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_SY_ADM_AREA PRIMARY KEY (AREA_CD),
 CONSTRAINT CK_TB_SY_ADM_AREA_01 CHECK (USE_YN IN ('Y', 'N'))
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_HR_DEV (
 DEV_ID VARCHAR(20) NOT NULL,
 MNG_NO VARCHAR(20) NOT NULL,
 DEV_NM VARCHAR(50) NOT NULL,
 EMP_TYPE_CD VARCHAR(10) NOT NULL,
 BLNG_CLNT_ID VARCHAR(20),
 GRADE_CD VARCHAR(10),
 JOB_CD VARCHAR(10) NOT NULL,
 SUB_JOB_CD VARCHAR(10),
 JBPS_CD VARCHAR(10),
 DRCT_CLS_CD VARCHAR(10) NOT NULL,
 PART_ID VARCHAR(20) NOT NULL,
 JNCMP_DT DATE,
 RETIRE_DT DATE,
 WORK_STAT_CD VARCHAR(10) NOT NULL,
 HOME_AREA_CD VARCHAR(10),
 HOME_ADDR VARCHAR(50),
 TELNO VARCHAR(20),
 USE_YN CHAR(1) DEFAULT 'Y' NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_HR_DEV PRIMARY KEY (DEV_ID),
 CONSTRAINT UK_TB_HR_DEV_01 UNIQUE (MNG_NO),
 CONSTRAINT CK_TB_HR_DEV_01 CHECK (USE_YN IN ('Y', 'N'))
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_PJ_ASGN (
 ASGN_ID VARCHAR(20) NOT NULL,
 PROJ_ID VARCHAR(20) NOT NULL,
 DEV_ID VARCHAR(20) NOT NULL,
 JOB_CD VARCHAR(10) NOT NULL,
 INPUT_BGN_DT DATE NOT NULL,
 INPUT_END_DT DATE NOT NULL,
 CTRT_MM NUMERIC(4,2) NOT NULL,
 BILL_UPRC NUMERIC(15),
 INPUT_TYPE_CD VARCHAR(10) NOT NULL,
 CTRT_DEV_ID VARCHAR(20),
 EXT_PSBL_CD VARCHAR(10),
 EXT_CNT NUMERIC(3) NOT NULL,
 EARLY_WTHDR_YN CHAR(1) DEFAULT 'N' NOT NULL,
 ASGN_STAT_CD VARCHAR(10) NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_PJ_ASGN PRIMARY KEY (ASGN_ID),
 CONSTRAINT CK_TB_PJ_ASGN_01 CHECK (EARLY_WTHDR_YN IN ('Y', 'N'))
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_PJ_PROJ_PART (
 PROJ_ID VARCHAR(20) NOT NULL,
 APLY_BGN_YM CHAR(6) NOT NULL,
 PART_ID VARCHAR(20),
 CHG_RSN VARCHAR(500),
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_PJ_PROJ_PART PRIMARY KEY (PROJ_ID, APLY_BGN_YM)
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_CO_PART_SETL (
 PART_ID VARCHAR(20) NOT NULL,
 SETL_YM CHAR(6) NOT NULL,
 CORP_CARD_AMT NUMERIC(15) NOT NULL,
 PART_PRFT_AMT NUMERIC(15),
 SETL_STAT_CD VARCHAR(10) NOT NULL,
 CNFM_USER_ID VARCHAR(30),
 CNFM_DTTM TIMESTAMPTZ,
 CNCL_RSN VARCHAR(500),
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_CO_PART_SETL PRIMARY KEY (PART_ID, SETL_YM)
)$ddl$
    ];
    foreign_keys text[] := ARRAY[
        'ALTER TABLE TB_SY_UPLD_ERR ADD CONSTRAINT FK_TB_SY_UPLD_ERR_01 FOREIGN KEY (UPLD_ID) REFERENCES TB_SY_UPLD (UPLD_ID)',
        'ALTER TABLE TB_SY_ADM_AREA ADD CONSTRAINT FK_TB_SY_ADM_AREA_01 FOREIGN KEY (UPPER_AREA_CD) REFERENCES TB_SY_ADM_AREA (AREA_CD)',
        'ALTER TABLE TB_HR_DEV ADD CONSTRAINT FK_TB_HR_DEV_01 FOREIGN KEY (BLNG_CLNT_ID) REFERENCES TB_BS_CLNT (CLNT_ID)',
        'ALTER TABLE TB_HR_DEV ADD CONSTRAINT FK_TB_HR_DEV_02 FOREIGN KEY (PART_ID) REFERENCES TB_BS_PART (PART_ID)',
        'ALTER TABLE TB_HR_DEV ADD CONSTRAINT FK_TB_HR_DEV_03 FOREIGN KEY (HOME_AREA_CD) REFERENCES TB_SY_ADM_AREA (AREA_CD)',
        'ALTER TABLE TB_PJ_PROJ_PART ADD CONSTRAINT FK_TB_PJ_PROJ_PART_01 FOREIGN KEY (PROJ_ID) REFERENCES TB_PJ_PROJ (PROJ_ID)',
        'ALTER TABLE TB_PJ_PROJ_PART ADD CONSTRAINT FK_TB_PJ_PROJ_PART_02 FOREIGN KEY (PART_ID) REFERENCES TB_BS_PART (PART_ID)',
        'ALTER TABLE TB_PJ_ASGN ADD CONSTRAINT FK_TB_PJ_ASGN_01 FOREIGN KEY (PROJ_ID) REFERENCES TB_PJ_PROJ (PROJ_ID)',
        'ALTER TABLE TB_PJ_ASGN ADD CONSTRAINT FK_TB_PJ_ASGN_02 FOREIGN KEY (DEV_ID) REFERENCES TB_HR_DEV (DEV_ID)',
        'ALTER TABLE TB_PJ_ASGN ADD CONSTRAINT FK_TB_PJ_ASGN_03 FOREIGN KEY (CTRT_DEV_ID) REFERENCES TB_HR_DEV (DEV_ID)',
        'ALTER TABLE TB_CO_PART_SETL ADD CONSTRAINT FK_TB_CO_PART_SETL_01 FOREIGN KEY (PART_ID) REFERENCES TB_BS_PART (PART_ID)',
        'ALTER TABLE TB_CO_PART_SETL ADD CONSTRAINT FK_TB_CO_PART_SETL_02 FOREIGN KEY (CNFM_USER_ID) REFERENCES TB_SY_USER (USER_ID)'
    ];
    indexes text[] := ARRAY[
        'CREATE INDEX IF NOT EXISTS IX_TB_SY_ADM_AREA_01 ON TB_SY_ADM_AREA (AREA_LVL_CD)',
        'CREATE INDEX IF NOT EXISTS IX_TB_SY_ADM_AREA_02 ON TB_SY_ADM_AREA (SIDO_NM)',
        'CREATE INDEX IF NOT EXISTS IX_TB_SY_ADM_AREA_03 ON TB_SY_ADM_AREA (UPPER_AREA_CD)',
        'CREATE INDEX IF NOT EXISTS IX_TB_BS_CLNT_01 ON TB_BS_CLNT (CLNT_TYPE_CD)',
        'CREATE INDEX IF NOT EXISTS IX_TB_HR_DEV_01 ON TB_HR_DEV (EMP_TYPE_CD)',
        'CREATE INDEX IF NOT EXISTS IX_TB_HR_DEV_02 ON TB_HR_DEV (JOB_CD)',
        'CREATE INDEX IF NOT EXISTS IX_TB_HR_DEV_03 ON TB_HR_DEV (WORK_STAT_CD)',
        'CREATE INDEX IF NOT EXISTS IX_TB_HR_DEV_04 ON TB_HR_DEV (BLNG_CLNT_ID)',
        'CREATE INDEX IF NOT EXISTS IX_TB_HR_DEV_05 ON TB_HR_DEV (PART_ID)',
        'CREATE INDEX IF NOT EXISTS IX_TB_HR_DEV_06 ON TB_HR_DEV (HOME_AREA_CD)',
        'CREATE INDEX IF NOT EXISTS IX_TB_PJ_PROJ_PART_01 ON TB_PJ_PROJ_PART (PART_ID)',
        'CREATE INDEX IF NOT EXISTS IX_TB_PJ_ASGN_01 ON TB_PJ_ASGN (PROJ_ID)',
        'CREATE INDEX IF NOT EXISTS IX_TB_PJ_ASGN_02 ON TB_PJ_ASGN (DEV_ID, INPUT_BGN_DT, INPUT_END_DT)',
        'CREATE INDEX IF NOT EXISTS IX_TB_PJ_ASGN_03 ON TB_PJ_ASGN (CTRT_DEV_ID)',
        'CREATE INDEX IF NOT EXISTS IX_TB_CO_PART_SETL_01 ON TB_CO_PART_SETL (CNFM_USER_ID)'
    ];
    ddl text;
    line text;
    table_name text;
    relation regclass;
    parts text[];
    expected_type text;
    actual_type text;
    expected_default text;
    actual_default text;
    actual_required boolean;
    expected_constraint text;
    actual_constraint text;
    expected_columns text[];
    actual_columns text[];
    new_tables text[] := ARRAY[]::text[];
    differences text[] := ARRAY[]::text[];
BEGIN
    PERFORM pg_advisory_xact_lock(734901, 1);
    FOREACH ddl IN ARRAY definitions LOOP
        table_name := lower(substring(ddl FROM 'EXISTS ([A-Z_]+)'));
        relation := to_regclass(format('%I.%I', current_schema(), table_name));
        IF relation IS NULL THEN
            new_tables := array_append(new_tables, table_name);
            CONTINUE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_class WHERE oid=relation AND relkind='r') THEN
            differences := array_append(differences, table_name || ': expected ordinary table');
            CONTINUE;
        END IF;
        EXECUTE format('LOCK TABLE %s IN ACCESS EXCLUSIVE MODE', relation);
        expected_columns := ARRAY[]::text[];
        FOREACH line IN ARRAY string_to_array(ddl, E'\n') LOOP
            parts := regexp_match(trim(line), '^([A-Z_][A-Z0-9_]*) ((?:VARCHAR|CHAR|NUMERIC)\([0-9,]+\)|TIMESTAMPTZ|DATE)(.*)$');
            IF parts IS NOT NULL THEN
                expected_columns := array_append(expected_columns, lower(parts[1]));
                expected_type := replace(replace(lower(parts[2]), 'varchar', 'character varying'), 'char(', 'character(');
                IF expected_type='timestamptz' THEN expected_type := 'timestamp with time zone'; END IF;
                IF expected_type ~ '^numeric\([0-9]+\)$' THEN expected_type := replace(expected_type, ')', ',0)'); END IF;
                expected_default := lower(rtrim(substring(parts[3] FROM 'DEFAULT (.+?)(?: NOT NULL|,$|$)'), ','));
                SELECT format_type(a.atttypid, a.atttypmod), a.attnotnull, lower(pg_get_expr(d.adbin,d.adrelid))
                  INTO actual_type, actual_required, actual_default
                  FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
                 WHERE a.attrelid=relation AND a.attname=lower(parts[1]) AND a.attnum>0 AND NOT a.attisdropped;
                actual_default := regexp_replace(actual_default, '::(bpchar|character varying|text|regclass)', '', 'g');
                IF actual_type IS DISTINCT FROM expected_type
                   OR actual_required IS DISTINCT FROM (position('NOT NULL' IN parts[3])>0)
                   OR actual_default IS DISTINCT FROM expected_default THEN
                    differences := array_append(differences, format('%s.%s expected type=%s required=%s default=%s actual type=%s required=%s default=%s',
                        table_name, lower(parts[1]), expected_type, position('NOT NULL' IN parts[3])>0,
                        expected_default, actual_type, actual_required, actual_default));
                END IF;
            END IF;
            parts := regexp_match(trim(line), '^CONSTRAINT ([A-Z_0-9]+) (.*?)(?:,)?$');
            IF parts IS NOT NULL THEN
                SELECT pg_get_constraintdef(oid) INTO actual_constraint FROM pg_constraint
                 WHERE conrelid=relation AND conname=lower(parts[1]) AND contype IN ('p','u','c') AND convalidated;
                actual_constraint := regexp_replace(lower(actual_constraint), '::(bpchar|text|character varying)', '', 'g');
                actual_constraint := replace(translate(regexp_replace(actual_constraint, '[[:space:]]', '', 'g'), '()[]', ''), '=anyarray', 'in');
                expected_constraint := translate(regexp_replace(lower(rtrim(parts[2], ',')), '[[:space:]]', '', 'g'), '()[]', '');
                IF actual_constraint IS DISTINCT FROM expected_constraint THEN
                    differences := array_append(differences, format('%s.%s expected=%s actual=%s', table_name, parts[1], expected_constraint, actual_constraint));
                END IF;
            END IF;
        END LOOP;
        SELECT array_agg(attname::text ORDER BY attnum) INTO actual_columns FROM pg_attribute
         WHERE attrelid=relation AND attnum>0 AND NOT attisdropped;
        IF actual_columns IS DISTINCT FROM expected_columns THEN
            differences := array_append(differences, format('%s.columns expected=%s actual=%s', table_name, expected_columns, actual_columns));
        END IF;
    END LOOP;
    FOREACH ddl IN ARRAY foreign_keys LOOP
        parts := regexp_match(ddl, '^ALTER TABLE ([A-Z_]+) ADD CONSTRAINT ([A-Z_0-9]+) (.+)$');
        relation := to_regclass(format('%I.%I', current_schema(), lower(parts[1])));
        IF relation IS NULL THEN CONTINUE; END IF;
        SELECT pg_get_constraintdef(oid) INTO actual_constraint FROM pg_constraint
         WHERE conrelid=relation AND conname=lower(parts[2]) AND contype='f' AND convalidated
           AND NOT condeferrable;
        expected_constraint := regexp_replace(lower(parts[3]), '[[:space:]]', '', 'g');
        actual_constraint := regexp_replace(lower(actual_constraint), '[[:space:]]', '', 'g');
        IF actual_constraint IS DISTINCT FROM expected_constraint THEN
            differences := array_append(differences, format('%s.%s expected=%s actual=%s', parts[1], parts[2], expected_constraint, actual_constraint));
        END IF;
    END LOOP;
    FOREACH ddl IN ARRAY indexes LOOP
        parts := regexp_match(ddl, 'EXISTS ([A-Z_0-9]+) ON ([A-Z_]+) \((.+)\)$');
        relation := to_regclass(format('%I.%I', current_schema(), lower(parts[2])));
        IF relation IS NULL THEN CONTINUE; END IF;
        IF NOT EXISTS (
            SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid JOIN pg_am am ON am.oid=c.relam
             WHERE i.indrelid=relation AND c.relname=lower(parts[1]) AND i.indisvalid AND i.indisready
               AND NOT i.indisunique AND i.indpred IS NULL AND i.indexprs IS NULL AND am.amname='btree'
               AND i.indnatts=i.indnkeyatts
               AND (SELECT array_agg(pg_get_indexdef(i.indexrelid,n,true) ORDER BY n)
                      FROM generate_series(1,i.indnkeyatts) n)
                   = string_to_array(lower(replace(parts[3], ' ', '')), ',')
        ) THEN
            differences := array_append(differences, parts[1] || ': expected original btree index on ' || parts[3]);
        END IF;
    END LOOP;
    relation := to_regclass(format('%I.sq_tb_sy_upld', current_schema()));
    IF relation IS NOT NULL AND NOT EXISTS (SELECT 1 FROM pg_sequence WHERE seqrelid=relation AND seqstart=1 AND seqincrement=1 AND NOT seqcycle) THEN
        differences := array_append(differences, 'sq_tb_sy_upld: expected non-cycling sequence START 1 INCREMENT 1; existing sequence is not reset');
    END IF;
    IF cardinality(differences)>0 THEN
        RAISE EXCEPTION USING MESSAGE=array_to_string(differences, E'\n'), ERRCODE='P0001';
    END IF;
    CREATE SEQUENCE IF NOT EXISTS SQ_TB_SY_UPLD START WITH 1 INCREMENT BY 1;
    FOREACH ddl IN ARRAY definitions LOOP
        table_name := lower(substring(ddl FROM 'EXISTS ([A-Z_]+)'));
        IF table_name=ANY(new_tables) THEN EXECUTE ddl; END IF;
    END LOOP;
    FOREACH ddl IN ARRAY foreign_keys LOOP
        table_name := lower(substring(ddl FROM '^ALTER TABLE ([A-Z_]+)'));
        IF table_name=ANY(new_tables) THEN EXECUTE ddl; END IF;
    END LOOP;
    FOREACH ddl IN ARRAY indexes LOOP
        table_name := lower(substring(ddl FROM ' ON ([A-Z_]+)'));
        IF table_name=ANY(new_tables) THEN EXECUTE ddl; END IF;
    END LOOP;
    FOREACH table_name IN ARRAY new_tables LOOP
        EXECUTE format('COMMENT ON TABLE %I IS %L', table_name, CASE table_name
            WHEN 'tb_bs_clnt' THEN '고객사·협력사 거래처와 사용 상태 및 암호화 지급 계좌를 관리한다.'
            WHEN 'tb_sy_upld' THEN '공통 엑셀 업로드의 검사 건수와 반영 상태를 보존한다.'
            WHEN 'tb_sy_upld_err' THEN '엑셀 업로드의 행별 경고와 오류 사유를 보존한다.'
            WHEN 'tb_sy_adm_area' THEN '행정구역 계층과 표준 지역명을 제공한다. 현재 지역 CRUD는 미사용 — 추후 연동 예정.'
            WHEN 'tb_hr_dev' THEN '개발자의 소속 거래처·영업 파트와 근무 상태를 제공한다. 현재 개발자 CRUD는 미사용 — 추후 연동 예정.'
            WHEN 'tb_pj_asgn' THEN '프로젝트별 개발자 투입 기간과 투입 상태를 제공한다. 현재 투입 CRUD는 미사용 — 추후 연동 예정.'
            WHEN 'tb_pj_proj_part' THEN '프로젝트에 적용되는 영업 파트의 월별 변경 이력을 보존한다.'
            WHEN 'tb_co_part_setl' THEN '영업 파트의 월별 정산 금액과 확정 상태를 보존한다.' END);
        EXECUTE format('COMMENT ON COLUMN %I.reg_user_id IS %L', table_name, 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)');
        EXECUTE format('COMMENT ON COLUMN %I.mod_user_id IS %L', table_name, 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)');
        CASE table_name
            WHEN 'tb_bs_clnt' THEN
                COMMENT ON COLUMN TB_BS_CLNT.CLNT_ID IS '거래처의 자체 식별자 (외부 FK 참조 없음)';
                COMMENT ON COLUMN TB_BS_CLNT.ACCT_NO_ENC IS 'AES-256-GCM IV·암호문·인증 태그의 Base64. 전체값은 전용 계좌 조회에서만 복호화한다.';
            WHEN 'tb_sy_upld' THEN
                COMMENT ON COLUMN TB_SY_UPLD.UPLD_ID IS '업로드의 자체 식별자 (외부 FK 참조 없음)';
                COMMENT ON COLUMN TB_SY_UPLD.SCRN_ID IS 'TB_SY_MENU.SCRN_ID 참조 의도 (FK 미선언)';
                COMMENT ON COLUMN TB_SY_UPLD.TRGT_PROJ_ID IS 'TB_PJ_PROJ.PROJ_ID 참조 의도 (FK 미선언)';
                COMMENT ON COLUMN TB_SY_UPLD.UPLD_STAT_CD IS 'CHECKED:검사완료|APPLIED:반영|CANCEL:취소';
                COMMENT ON COLUMN TB_SY_UPLD.TOT_ROW_CNT IS 'CommonUploadService 검사·재검사 시 전체 행수 갱신';
                COMMENT ON COLUMN TB_SY_UPLD.SUCC_CNT IS 'CommonUploadService 검사·반영 시 성공 건수 갱신';
                COMMENT ON COLUMN TB_SY_UPLD.WARN_CNT IS 'CommonUploadService 검사·재검사 시 경고 건수 갱신';
                COMMENT ON COLUMN TB_SY_UPLD.ERR_CNT IS 'CommonUploadService 검사·재검사 시 오류 건수 갱신';
            WHEN 'tb_sy_upld_err' THEN
                COMMENT ON COLUMN TB_SY_UPLD_ERR.RSLT_TYPE_CD IS 'WARN:경고|ERR:오류';
            WHEN 'tb_hr_dev' THEN
                COMMENT ON COLUMN TB_HR_DEV.DEV_ID IS '개발자의 자체 식별자 (외부 FK 참조 없음)';
                COMMENT ON COLUMN TB_HR_DEV.WORK_STAT_CD IS 'INPUT:투입중|WTHDR:철수예정|IDLE:대기|LEAVE:휴직|RETIRE:퇴사|WORK:재직';
                COMMENT ON COLUMN TB_HR_DEV.RETIRE_DT IS 'TB_HR_DEV_STAT 퇴사 등록·취소 시 애플리케이션에서 갱신';
                COMMENT ON COLUMN TB_HR_DEV.HOME_ADDR IS '개발자 등록·수정 시 HOME_AREA_CD의 행정구역 명칭으로 애플리케이션에서 갱신';
            WHEN 'tb_pj_asgn' THEN
                COMMENT ON COLUMN TB_PJ_ASGN.ASGN_ID IS '투입의 자체 식별자 (외부 FK 참조 없음)';
                COMMENT ON COLUMN TB_PJ_ASGN.ASGN_STAT_CD IS 'ACT:유효|CNCL:배정취소';
                COMMENT ON COLUMN TB_PJ_ASGN.CTRT_MM IS '투입 변경 저장 시와 매달 1일 배치에서 현재 월 MM으로 갱신';
                COMMENT ON COLUMN TB_PJ_ASGN.BILL_UPRC IS '투입 변경 저장 시와 매달 1일 배치에서 현재 월 청구단가로 갱신';
                COMMENT ON COLUMN TB_PJ_ASGN.EXT_CNT IS '투입 변경 이력 저장 시 배치·트리거에서 정정을 제외한 연장 횟수 갱신';
            WHEN 'tb_co_part_setl' THEN
                COMMENT ON COLUMN TB_CO_PART_SETL.SETL_STAT_CD IS 'WORK:작업중|CNFM:확정|AUTO:마감 시 자동 확정';
                COMMENT ON COLUMN TB_CO_PART_SETL.PART_PRFT_AMT IS '파트 정산 확정 시 애플리케이션에서 금액 고정';
            ELSE NULL;
        END CASE;
    END LOOP;
END;
$client_part_upload$;
