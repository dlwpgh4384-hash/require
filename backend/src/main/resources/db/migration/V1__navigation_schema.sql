-- Executed explicitly by SchemaPreparation, never by an HTTP request.
-- FK: TB_SY_USER_SCOPE.USER_ID -> TB_SY_USER.USER_ID; PART_ID -> TB_BS_PART.PART_ID; PROJ_ID -> TB_PJ_PROJ.PROJ_ID
-- FK: TB_SY_MENU.UPPER_MENU_ID -> TB_SY_MENU.MENU_ID
-- FK: TB_SY_USER_PREF.USER_ID -> TB_SY_USER.USER_ID
-- FK: TB_SY_NOTI.RCVR_USER_ID -> TB_SY_USER.USER_ID
-- FK: TB_BS_PART_STD.PART_ID -> TB_BS_PART.PART_ID; PART_LDR_USER_ID -> TB_SY_USER.USER_ID
-- FK: TB_PJ_PROJ.CUST_CLNT_ID -> TB_BS_CLNT.CLNT_ID; OPP_ID -> TB_SL_OPP.OPP_ID
-- FK: TB_BL_BILL.PROJ_ID -> TB_PJ_PROJ.PROJ_ID; CNCL_USER_ID -> TB_SY_USER.USER_ID
-- FK: TB_BL_DUN_HIST.BILL_ID -> TB_BL_BILL.BILL_ID
-- FK: TB_CL_CLOSE.CLOSE_USER_ID -> TB_SY_USER.USER_ID
DO $navigation$
DECLARE
    definitions text[] := ARRAY[
$ddl$CREATE TABLE IF NOT EXISTS TB_SY_USER_SCOPE (
 USER_ID VARCHAR(30) NOT NULL,
 SCOPE_SN NUMERIC(5) NOT NULL,
 SCOPE_TYPE_CD VARCHAR(10) NOT NULL,
 PART_ID VARCHAR(20),
 PROJ_ID VARCHAR(20),
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMP,
 CONSTRAINT PK_TB_SY_USER_SCOPE PRIMARY KEY (USER_ID, SCOPE_SN)
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_SY_MENU (
 MENU_ID VARCHAR(20) NOT NULL,
 UPPER_MENU_ID VARCHAR(20),
 MENU_NM VARCHAR(50) NOT NULL,
 SCRN_ID VARCHAR(10),
 MENU_LVL NUMERIC(1) NOT NULL,
 SORT_ORD NUMERIC(5) NOT NULL,
 USE_YN CHAR(1) DEFAULT 'Y' NOT NULL,
 PC_ONLY_YN CHAR(1) DEFAULT 'N' NOT NULL,
 ICON_NM VARCHAR(30),
 LOCK_YN CHAR(1) DEFAULT 'N' NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMP,
 CONSTRAINT PK_TB_SY_MENU PRIMARY KEY (MENU_ID),
 CONSTRAINT CK_TB_SY_MENU_01 CHECK (USE_YN IN ('Y', 'N')),
 CONSTRAINT CK_TB_SY_MENU_02 CHECK (PC_ONLY_YN IN ('Y', 'N')),
 CONSTRAINT CK_TB_SY_MENU_03 CHECK (LOCK_YN IN ('Y', 'N'))
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_SY_USER_PREF (
 USER_ID VARCHAR(30) NOT NULL,
 PREF_TYPE_CD VARCHAR(20) NOT NULL,
 PREF_KEY VARCHAR(30) NOT NULL,
 PREF_VAL VARCHAR(2000) NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMP,
 CONSTRAINT PK_TB_SY_USER_PREF PRIMARY KEY (USER_ID, PREF_TYPE_CD, PREF_KEY)
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_SY_NOTI (
 NOTI_ID NUMERIC(12) DEFAULT nextval('SQ_TB_SY_NOTI') NOT NULL,
 RCVR_USER_ID VARCHAR(30) NOT NULL,
 NOTI_TYPE_CD VARCHAR(20) NOT NULL,
 NOTI_CN VARCHAR(300) NOT NULL,
 LINK_SCRN_ID VARCHAR(10),
 LINK_KEY VARCHAR(50),
 SEND_MTHD_CD VARCHAR(10) NOT NULL,
 READ_YN CHAR(1) DEFAULT 'N' NOT NULL,
 READ_DTTM TIMESTAMP,
 NOTI_DTTM TIMESTAMP NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMP,
 CONSTRAINT PK_TB_SY_NOTI PRIMARY KEY (NOTI_ID),
 CONSTRAINT CK_TB_SY_NOTI_01 CHECK (READ_YN IN ('Y', 'N'))
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_BS_PART_STD (
 PART_ID VARCHAR(20) NOT NULL,
 APLY_BGN_YM CHAR(6) NOT NULL,
 APLY_END_YM CHAR(6),
 PART_LDR_USER_ID VARCHAR(30) NOT NULL,
 PRFT_DSTRB_RT NUMERIC(5,2) NOT NULL,
 SALES_EXPS_AMT NUMERIC(15) NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMP,
 CONSTRAINT PK_TB_BS_PART_STD PRIMARY KEY (PART_ID, APLY_BGN_YM)
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_PJ_PROJ (
 PROJ_ID VARCHAR(20) NOT NULL,
 PROJ_NM VARCHAR(100) NOT NULL,
 CUST_CLNT_ID VARCHAR(20),
 CTRT_TYPE_CD VARCHAR(10) NOT NULL,
 PROJ_STAT_CD VARCHAR(10) NOT NULL,
 LOSE_DT DATE,
 LOSE_RSN_CD VARCHAR(10),
 LOSE_MEMO VARCHAR(500),
 CTRT_BGN_DT DATE NOT NULL,
 CTRT_END_DT DATE NOT NULL,
 WORK_PLC_ADDR VARCHAR(200),
 EST_AMT NUMERIC(15),
 ORGNL_CTRT_AMT NUMERIC(15),
 CTRT_AMT NUMERIC(15) NOT NULL,
 BDGT_AMT NUMERIC(15),
 BDGT_MANUAL_YN CHAR(1) DEFAULT 'N' NOT NULL,
 TEAM_EXPS_AMT NUMERIC(15) NOT NULL,
 PROJ_EXPS_AMT NUMERIC(15) NOT NULL,
 FEATURE_CN VARCHAR(500),
 OPP_ID VARCHAR(20),
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMP,
 CONSTRAINT PK_TB_PJ_PROJ PRIMARY KEY (PROJ_ID),
 CONSTRAINT CK_TB_PJ_PROJ_01 CHECK (BDGT_MANUAL_YN IN ('Y', 'N'))
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_BL_BILL (
 BILL_ID VARCHAR(20) NOT NULL,
 PROJ_ID VARCHAR(20) NOT NULL,
 BILL_ORD NUMERIC(3) NOT NULL,
 BILL_YM CHAR(6) NOT NULL,
 BILL_DT DATE NOT NULL,
 SPLY_AMT NUMERIC(15) NOT NULL,
 VAT_RT NUMERIC(5,2) NOT NULL,
 VAT_AMT NUMERIC(15) NOT NULL,
 TOT_AMT NUMERIC(15) NOT NULL,
 RCPT_SUM_AMT NUMERIC(15) NOT NULL,
 UNPAID_AMT NUMERIC(15) NOT NULL,
 PAY_DUE_DT DATE NOT NULL,
 TAX_INVC_NO VARCHAR(30),
 CNCL_YN CHAR(1) DEFAULT 'N' NOT NULL,
 CNCL_RSN VARCHAR(500),
 CNCL_USER_ID VARCHAR(30),
 CNCL_DTTM TIMESTAMP,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMP,
 CONSTRAINT PK_TB_BL_BILL PRIMARY KEY (BILL_ID),
 CONSTRAINT CK_TB_BL_BILL_01 CHECK (CNCL_YN IN ('Y', 'N'))
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_BL_DUN_HIST (
 BILL_ID VARCHAR(20) NOT NULL,
 DUN_SN NUMERIC(5) NOT NULL,
 DUN_STEP NUMERIC(1) NOT NULL,
 SEND_DT DATE NOT NULL,
 RCVR_DESC VARCHAR(300) NOT NULL,
 SEND_MTHD_CD VARCHAR(10) NOT NULL,
 RESEND_YN CHAR(1) DEFAULT 'N' NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 CONSTRAINT PK_TB_BL_DUN_HIST PRIMARY KEY (BILL_ID, DUN_SN),
 CONSTRAINT CK_TB_BL_DUN_HIST_01 CHECK (RESEND_YN IN ('Y', 'N'))
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_CL_CLOSE (
 CLOSE_YM CHAR(6) NOT NULL,
 CLOSE_STAT_CD VARCHAR(10) NOT NULL,
 CLOSE_USER_ID VARCHAR(30),
 CLOSE_DTTM TIMESTAMP,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMP,
 CONSTRAINT PK_TB_CL_CLOSE PRIMARY KEY (CLOSE_YM)
)$ddl$
];
    ddl text;
    table_name text;
    line text;
    parts text[];
    relation regclass;
    expected_type text;
    expected_default text;
    actual_type text;
    actual_default text;
    actual_nullable boolean;
    actual_constraint text;
    expected_constraint text;
    expected_columns text[];
    actual_columns text[];
    expected_constraints text[];
    actual_constraints text[];
    indexed_columns text[];
    indexed_column text;
    index_number integer;
    differences text[] := ARRAY[]::text[];
BEGIN
    PERFORM pg_advisory_xact_lock(734901, 1);
    -- Inspect every existing target before the first structural or data write.
    FOREACH ddl IN ARRAY definitions LOOP
        table_name := lower(substring(ddl FROM 'EXISTS ([A-Z_]+)'));
        relation := to_regclass(format('%I.%I', current_schema(), table_name));
        IF relation IS NULL THEN CONTINUE; END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_class WHERE oid = relation AND relkind = 'r') THEN
            differences := array_append(differences, table_name || ': expected ordinary table');
            CONTINUE;
        END IF;
        EXECUTE format('LOCK TABLE %s IN ACCESS EXCLUSIVE MODE', relation);
        expected_columns := ARRAY[]::text[];
        expected_constraints := ARRAY[]::text[];
        FOREACH line IN ARRAY string_to_array(ddl, E'\n') LOOP
            parts := regexp_match(trim(line), '^([A-Z_]+) ((?:VARCHAR|CHAR|NUMERIC)\([0-9,]+\)|TIMESTAMP|DATE)(.*)$');
            IF parts IS NOT NULL THEN
                expected_columns := array_append(expected_columns, lower(parts[1]));
                expected_type := lower(parts[2]);
                expected_type := replace(replace(expected_type, 'varchar', 'character varying'), 'char(', 'character(');
                IF expected_type = 'timestamp' THEN expected_type := 'timestamp without time zone'; END IF;
                IF expected_type ~ '^numeric\([0-9]+\)$' THEN
                    expected_type := replace(expected_type, ')', ',0)');
                END IF;
                expected_default := substring(parts[3] FROM 'DEFAULT (.+?)(?: NOT NULL|,$|$)');
                expected_default := lower(rtrim(expected_default, ','));
                SELECT format_type(a.atttypid, a.atttypmod), NOT a.attnotnull,
                       lower(pg_get_expr(d.adbin, d.adrelid))
                  INTO actual_type, actual_nullable, actual_default
                  FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
                 WHERE a.attrelid=relation AND a.attname=lower(parts[1]) AND a.attnum>0 AND NOT a.attisdropped;
                actual_default := regexp_replace(actual_default, '::(bpchar|character varying|text|regclass)', '', 'g');
                IF actual_type IS DISTINCT FROM expected_type
                   OR actual_nullable IS DISTINCT FROM (position('NOT NULL' IN parts[3]) = 0)
                   OR actual_default IS DISTINCT FROM expected_default THEN
                    differences := array_append(differences, format('%s.%s expected type=%s nullable=%s default=%s actual type=%s nullable=%s default=%s',
                        table_name, lower(parts[1]), expected_type, position('NOT NULL' IN parts[3])=0,
                        expected_default, actual_type, actual_nullable, actual_default));
                END IF;
            END IF;
            parts := regexp_match(trim(line), '^CONSTRAINT ([A-Z_0-9]+) (.*?)(?:,)?$');
            IF parts IS NOT NULL THEN
                expected_constraints := array_append(expected_constraints, lower(parts[1]));
                SELECT pg_get_constraintdef(oid) INTO actual_constraint FROM pg_constraint
                 WHERE conrelid=relation AND conname=lower(parts[1]) AND contype IN ('p','c') AND convalidated;
                expected_constraint := lower(rtrim(parts[2], ','));
                actual_constraint := lower(actual_constraint);
                actual_constraint := regexp_replace(actual_constraint, '::(bpchar|text|character varying)', '', 'g');
                actual_constraint := translate(regexp_replace(actual_constraint, '[[:space:]]', '', 'g'), '()[]', '');
                actual_constraint := replace(actual_constraint, '=anyarray', 'in');
                expected_constraint := translate(regexp_replace(expected_constraint, '[[:space:]]', '', 'g'), '()[]', '');
                IF actual_constraint IS DISTINCT FROM expected_constraint THEN
                    differences := array_append(differences, format('%s.%s expected=%s actual=%s', table_name, parts[1], expected_constraint, actual_constraint));
                END IF;
            END IF;
        END LOOP;
        SELECT array_agg(name ORDER BY name) INTO expected_constraints FROM unnest(expected_constraints) AS name;
        SELECT array_agg(conname::text ORDER BY conname::text) INTO actual_constraints FROM pg_constraint
         WHERE conrelid=relation AND contype IN ('p','c','u');
        IF actual_constraints IS DISTINCT FROM expected_constraints THEN
            differences := array_append(differences, format('%s.constraints expected=%s actual=%s', table_name, expected_constraints, actual_constraints));
        END IF;
        SELECT array_agg(attname::text ORDER BY attnum) INTO actual_columns FROM pg_attribute
         WHERE attrelid=relation AND attnum>0 AND NOT attisdropped;
        IF actual_columns IS DISTINCT FROM expected_columns THEN
            differences := array_append(differences, format('%s.columns expected=%s actual=%s', table_name, expected_columns, actual_columns));
        END IF;
    END LOOP;
    IF to_regclass(format('%I.sq_tb_sy_noti', current_schema())) IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_sequence WHERE seqrelid=to_regclass(format('%I.sq_tb_sy_noti', current_schema()))
                       AND seqincrement=1 AND seqstart=1 AND NOT seqcycle) THEN
        differences := array_append(differences, 'sq_tb_sy_noti: expected non-cycling sequence START 1 INCREMENT 1; existing sequence is not reset');
    END IF;
    IF cardinality(differences)>0 THEN
        RAISE EXCEPTION USING MESSAGE = array_to_string(differences, E'\n'), ERRCODE='P0001';
    END IF;
    CREATE SEQUENCE IF NOT EXISTS SQ_TB_SY_NOTI START WITH 1 INCREMENT BY 1;
    FOREACH ddl IN ARRAY definitions LOOP
        table_name := lower(substring(ddl FROM 'EXISTS ([A-Z_]+)'));
        IF to_regclass(format('%I.%I', current_schema(), table_name)) IS NULL THEN
            EXECUTE ddl;
            indexed_columns := CASE table_name
                WHEN 'tb_sy_user_scope' THEN ARRAY['part_id','proj_id']
                WHEN 'tb_sy_menu' THEN ARRAY['upper_menu_id']
                WHEN 'tb_sy_noti' THEN ARRAY['rcvr_user_id','noti_dttm']
                WHEN 'tb_bs_part_std' THEN ARRAY['part_ldr_user_id']
                WHEN 'tb_pj_proj' THEN ARRAY['cust_clnt_id','ctrt_type_cd','proj_stat_cd','opp_id']
                WHEN 'tb_bl_bill' THEN ARRAY['proj_id','bill_ym','pay_due_dt','cncl_user_id']
                WHEN 'tb_cl_close' THEN ARRAY['close_user_id']
                ELSE ARRAY[]::text[] END;
            index_number := 0;
            FOREACH indexed_column IN ARRAY indexed_columns LOOP
                index_number := index_number + 1;
                EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I (%I)',
                    'ix_' || table_name || '_' || lpad(index_number::text,2,'0'), table_name, indexed_column);
            END LOOP;
            EXECUTE format('COMMENT ON TABLE %I IS %L', table_name,
                CASE WHEN table_name='tb_sy_menu' THEN '공통 화면의 메뉴 계층과 화면 연결을 제공한다.'
                     ELSE '현재 미사용 — 추후 업무 연동 예정. 원문 구조만 준비한다.' END);
            FOREACH line IN ARRAY string_to_array(ddl, E'\n') LOOP
                parts := regexp_match(trim(line), '^([A-Z_]+) (?:VARCHAR|CHAR|NUMERIC|TIMESTAMP|DATE)');
                IF parts IS NOT NULL AND lower(parts[1]) ~ '(_id|_by)$' THEN
                    EXECUTE format('COMMENT ON COLUMN %I.%I IS %L', table_name, lower(parts[1]),
                        CASE lower(parts[1])
                          WHEN 'upper_menu_id' THEN 'TB_SY_MENU.MENU_ID 참조 의도 (FK 미선언)'
                          WHEN 'proj_id' THEN 'TB_PJ_PROJ.PROJ_ID 식별 또는 참조 의도 (FK 미선언)'
                          WHEN 'bill_id' THEN 'TB_BL_BILL.BILL_ID 식별 또는 참조 의도 (FK 미선언)'
                          WHEN 'part_id' THEN 'TB_BS_PART.PART_ID 참조 의도 (FK 미선언)'
                          WHEN 'cust_clnt_id' THEN 'TB_BS_CLNT.CLNT_ID 참조 의도 (FK 미선언)'
                          WHEN 'opp_id' THEN 'TB_SL_OPP.OPP_ID 참조 의도 (FK 미선언)'
                          WHEN 'menu_id' THEN '메뉴의 자체 식별자 (외부 FK 참조 없음)'
                          WHEN 'scrn_id' THEN '화면 연결 식별자 (FK 미선언)'
                          WHEN 'link_scrn_id' THEN '연결 화면 식별자 (FK 미선언)'
                          WHEN 'noti_id' THEN '알림의 자체 식별자 (외부 FK 참조 없음)'
                          ELSE 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)' END);
                END IF;
            END LOOP;
        END IF;
    END LOOP;
END;
$navigation$;
