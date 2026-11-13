-- Explicit --prepare-schema resource. Existing incompatible structures are reported, never repaired.
DO $code_definitions$
DECLARE
    definitions text[] := ARRAY[
$ddl$CREATE TABLE IF NOT EXISTS TB_SY_CODE_MAP (
 MAP_ID NUMERIC(10) DEFAULT nextval('SQ_TB_SY_CODE_MAP') NOT NULL,
 SRC_TEXT VARCHAR(100) NOT NULL,
 CODE_GRP_ID VARCHAR(30) NOT NULL,
 CODE VARCHAR(20) NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_SY_CODE_MAP PRIMARY KEY (MAP_ID),
 CONSTRAINT UK_TB_SY_CODE_MAP_01 UNIQUE (SRC_TEXT, CODE_GRP_ID)
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_SY_CALC_RULE (
 CALC_ID VARCHAR(10) NOT NULL,
 SFR_ID VARCHAR(10),
 CALC_NM VARCHAR(100) NOT NULL,
 FRML_CN TEXT NOT NULL,
 DTL_CN TEXT NOT NULL,
 USE_SCRN_CN VARCHAR(200),
 SORT_ORD NUMERIC(5) NOT NULL,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_SY_CALC_RULE PRIMARY KEY (CALC_ID)
)$ddl$
    ];
    ddl text;
    table_name text;
    relation regclass;
    line text;
    parts text[];
    expected_type text;
    expected_default text;
    expected_constraint text;
    actual_type text;
    actual_default text;
    actual_required boolean;
    actual_constraint text;
    expected_columns text[];
    actual_columns text[];
    differences text[] := ARRAY[]::text[];
BEGIN
    PERFORM pg_advisory_xact_lock(734901, 1);
    FOREACH ddl IN ARRAY definitions LOOP
        table_name := lower(substring(ddl FROM 'EXISTS ([A-Z_]+)'));
        relation := to_regclass(format('%I.%I', current_schema(), table_name));
        IF relation IS NULL THEN CONTINUE; END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_class WHERE oid=relation AND relkind='r') THEN
            differences := array_append(differences, table_name || ': expected ordinary table');
            CONTINUE;
        END IF;
        EXECUTE format('LOCK TABLE %s IN ACCESS EXCLUSIVE MODE', relation);
        expected_columns := ARRAY[]::text[];
        FOREACH line IN ARRAY string_to_array(ddl, E'\n') LOOP
            parts := regexp_match(trim(line), '^([A-Z_][A-Z0-9_]*) ((?:VARCHAR|CHAR|NUMERIC)\([0-9,]+\)|TIMESTAMPTZ|TEXT)(.*)$');
            IF parts IS NOT NULL THEN
                expected_columns := array_append(expected_columns, lower(parts[1]));
                expected_type := lower(parts[2]);
                expected_type := replace(expected_type, 'varchar', 'character varying');
                IF expected_type='timestamptz' THEN expected_type := 'timestamp with time zone'; END IF;
                IF expected_type ~ '^numeric\([0-9]+\)$' THEN
                    expected_type := replace(expected_type, ')', ',0)');
                END IF;
                expected_default := lower(rtrim(substring(parts[3] FROM 'DEFAULT (.+?)(?: NOT NULL|,$|$)'), ','));
                SELECT format_type(a.atttypid, a.atttypmod), a.attnotnull,
                       lower(pg_get_expr(d.adbin, d.adrelid))
                  INTO actual_type, actual_required, actual_default
                  FROM pg_attribute a
                  LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
                 WHERE a.attrelid=relation AND a.attname=lower(parts[1])
                   AND a.attnum>0 AND NOT a.attisdropped;
                actual_default := regexp_replace(actual_default, '::(bpchar|character varying|text|regclass)', '', 'g');
                IF actual_type IS DISTINCT FROM expected_type
                   OR actual_required IS DISTINCT FROM (position('NOT NULL' IN parts[3])>0)
                   OR actual_default IS DISTINCT FROM expected_default THEN
                    differences := array_append(differences, format(
                        '%s.%s expected type=%s required=%s default=%s actual type=%s required=%s default=%s',
                        table_name, lower(parts[1]), expected_type, position('NOT NULL' IN parts[3])>0,
                        expected_default, actual_type, actual_required, actual_default));
                END IF;
            END IF;
            parts := regexp_match(trim(line), '^CONSTRAINT ([A-Z_0-9]+) (.*?)(?:,)?$');
            IF parts IS NOT NULL THEN
                SELECT pg_get_constraintdef(oid) INTO actual_constraint
                  FROM pg_constraint
                 WHERE conrelid=relation AND conname=lower(parts[1])
                   AND contype IN ('p','u') AND convalidated;
                expected_constraint := translate(regexp_replace(lower(rtrim(parts[2], ',')), '[[:space:]]', '', 'g'), '()[]', '');
                actual_constraint := translate(regexp_replace(lower(actual_constraint), '[[:space:]]', '', 'g'), '()[]', '');
                IF actual_constraint IS DISTINCT FROM expected_constraint THEN
                    differences := array_append(differences, format('%s.%s expected=%s actual=%s',
                        table_name, parts[1], expected_constraint, actual_constraint));
                END IF;
            END IF;
        END LOOP;
        SELECT array_agg(attname::text ORDER BY attnum) INTO actual_columns
          FROM pg_attribute
         WHERE attrelid=relation AND attnum>0 AND NOT attisdropped;
        IF actual_columns IS DISTINCT FROM expected_columns THEN
            differences := array_append(differences, format('%s.columns expected=%s actual=%s',
                table_name, expected_columns, actual_columns));
        END IF;
    END LOOP;
    relation := to_regclass(format('%I.sq_tb_sy_code_map', current_schema()));
    IF relation IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM pg_sequence
         WHERE seqrelid=relation AND seqstart=1 AND seqincrement=1 AND NOT seqcycle
    ) THEN
        differences := array_append(differences, 'sq_tb_sy_code_map: expected non-cycling sequence START 1 INCREMENT 1; existing sequence is not reset');
    END IF;
    IF cardinality(differences)>0 THEN
        RAISE EXCEPTION USING MESSAGE=array_to_string(differences, E'\n'), ERRCODE='P0001';
    END IF;
    CREATE SEQUENCE IF NOT EXISTS SQ_TB_SY_CODE_MAP START WITH 1 INCREMENT BY 1;
    FOREACH ddl IN ARRAY definitions LOOP
        table_name := lower(substring(ddl FROM 'EXISTS ([A-Z_]+)'));
        IF to_regclass(format('%I.%I', current_schema(), table_name)) IS NULL THEN
            EXECUTE ddl;
            IF table_name='tb_sy_code_map' THEN
                CREATE INDEX IF NOT EXISTS IX_TB_SY_CODE_MAP_01 ON TB_SY_CODE_MAP (CODE_GRP_ID, CODE);
                COMMENT ON TABLE TB_SY_CODE_MAP IS '엑셀 표기와 코드 그룹별 원래 코드의 대응을 저장한다.';
                COMMENT ON COLUMN TB_SY_CODE_MAP.CODE_GRP_ID IS 'TB_SY_CODE_GRP.CODE_GRP_ID 참조 의도 (FK 미선언)';
                COMMENT ON COLUMN TB_SY_CODE_MAP.CODE IS 'TB_SY_CODE(CODE_GRP_ID,CODE) 참조 의도 (FK 미선언)';
            ELSE
                COMMENT ON TABLE TB_SY_CALC_RULE IS '계산 공통 원칙과 원문 계산식·예외 설명을 보존한다. 실행 가능한 수식 언어가 아니다.';
                COMMENT ON COLUMN TB_SY_CALC_RULE.CALC_ID IS '원문 계산규칙 식별자 (외부 참조 아님)';
                COMMENT ON COLUMN TB_SY_CALC_RULE.SFR_ID IS '요구사항 문서 식별자 참조 의도 (DB FK 대상 아님)';
            END IF;
            EXECUTE format('COMMENT ON COLUMN %I.reg_user_id IS %L', table_name,
                'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)');
            EXECUTE format('COMMENT ON COLUMN %I.mod_user_id IS %L', table_name,
                'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)');
        END IF;
    END LOOP;
END;
$code_definitions$;
