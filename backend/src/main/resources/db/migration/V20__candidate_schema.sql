-- Explicit --prepare-schema only; existing candidate structures and data are never repaired.
DO $candidate_schema$
DECLARE
    spec RECORD;
    relation REGCLASS;
    expected_relation REGCLASS;
    expected_columns TEXT[];
    actual_columns TEXT[];
    expected_constraints TEXT[];
    actual_constraints TEXT[];
BEGIN
    PERFORM pg_advisory_xact_lock(734901, 1);
    FOR spec IN SELECT * FROM (VALUES
        ('tb_hr_cand', $ddl$CREATE TABLE IF NOT EXISTS TB_HR_CAND (
    CAND_ID VARCHAR(20) NOT NULL,
    CAND_NM VARCHAR(50) NOT NULL,
    EMP_TYPE_CD VARCHAR(10) NOT NULL,
    BLNG_CLNT_ID VARCHAR(20),
    TELNO VARCHAR(20),
    EMAIL_ADDR VARCHAR(100),
    PRVC_AGRE_YN CHAR(1) DEFAULT 'N' NOT NULL,
    PRVC_AGRE_DT DATE,
    PRVC_AGRE_USER_ID VARCHAR(30),
    JOB_CD VARCHAR(10) NOT NULL,
    SUB_JOB_CD VARCHAR(10),
    GRADE_CD VARCHAR(10) NOT NULL,
    HOME_AREA_CD VARCHAR(10),
    HOME_ADDR VARCHAR(50),
    HOPE_UPRC NUMERIC(15) NOT NULL,
    INPUT_PSBL_DT DATE NOT NULL,
    CNTC_STAT_CD VARCHAR(10) NOT NULL,
    LAST_CNTC_DT DATE,
    CAND_SRC_CD VARCHAR(10) NOT NULL,
    MEMO_CN VARCHAR(500),
    REG_USER_ID VARCHAR(30) NOT NULL,
    REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID VARCHAR(30),
    MOD_DTTM TIMESTAMPTZ,
    CONSTRAINT PK_TB_HR_CAND PRIMARY KEY (CAND_ID),
    CONSTRAINT CK_TB_HR_CAND_01 CHECK (PRVC_AGRE_YN IN ('Y', 'N'))
)$ddl$, ', CONSTRAINT FK_TB_HR_CAND_01 FOREIGN KEY (BLNG_CLNT_ID) REFERENCES TB_BS_CLNT (CLNT_ID),
    CONSTRAINT FK_TB_HR_CAND_02 FOREIGN KEY (HOME_AREA_CD) REFERENCES TB_SY_ADM_AREA (AREA_CD)',
            '외부 인력 후보의 기본 정보와 최초 동의 및 최근 연락 상태를 저장한다.'),
        ('tb_hr_cand_skill', $ddl$CREATE TABLE IF NOT EXISTS TB_HR_CAND_SKILL (
    CAND_ID VARCHAR(20) NOT NULL,
    SKILL_NM VARCHAR(50) NOT NULL,
    SORT_ORD NUMERIC(3) NOT NULL,
    REG_USER_ID VARCHAR(30) NOT NULL,
    REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID VARCHAR(30),
    MOD_DTTM TIMESTAMPTZ,
    CONSTRAINT PK_TB_HR_CAND_SKILL PRIMARY KEY (CAND_ID, SKILL_NM)
)$ddl$, ', CONSTRAINT FK_TB_HR_CAND_SKILL_01 FOREIGN KEY (CAND_ID) REFERENCES TB_HR_CAND (CAND_ID)',
            '후보의 보유 기술을 입력 순서와 함께 저장한다.'),
        ('tb_hr_cand_cntc', $ddl$CREATE TABLE IF NOT EXISTS TB_HR_CAND_CNTC (
    CAND_ID VARCHAR(20) NOT NULL,
    CNTC_SN NUMERIC(5) NOT NULL,
    CNTC_STAT_CD VARCHAR(10) NOT NULL,
    CNTC_MEMO VARCHAR(200) NOT NULL,
    CNTC_DT DATE NOT NULL,
    REG_USER_ID VARCHAR(30) NOT NULL,
    REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    MOD_USER_ID VARCHAR(30),
    MOD_DTTM TIMESTAMPTZ,
    CONSTRAINT PK_TB_HR_CAND_CNTC PRIMARY KEY (CAND_ID, CNTC_SN)
)$ddl$, ', CONSTRAINT FK_TB_HR_CAND_CNTC_01 FOREIGN KEY (CAND_ID) REFERENCES TB_HR_CAND (CAND_ID)',
            '후보의 연락 기록과 시스템 등록 및 정보 수정 기록을 저장한다.')
    ) AS definitions(table_name, definition, foreign_keys, description) LOOP
        relation := to_regclass(format('%I.%I', current_schema(), spec.table_name));
        IF relation IS NULL THEN
            EXECUTE left(spec.definition, length(spec.definition) - 1) || spec.foreign_keys || ')';
            relation := to_regclass(format('%I.%I', current_schema(), spec.table_name));
            EXECUTE format('COMMENT ON TABLE %s IS %L', relation, spec.description);
            EXECUTE format('COMMENT ON COLUMN %s.REG_USER_ID IS %L', relation,
                'TB_SY_USER.USER_ID 참조 의도 (FK 미선언); 시스템 기록은 SYSTEM');
            EXECUTE format('COMMENT ON COLUMN %s.MOD_USER_ID IS %L', relation,
                'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)');
            IF spec.table_name = 'tb_hr_cand' THEN
                CREATE INDEX IF NOT EXISTS IX_TB_HR_CAND_01 ON TB_HR_CAND (JOB_CD);
                CREATE INDEX IF NOT EXISTS IX_TB_HR_CAND_02 ON TB_HR_CAND (BLNG_CLNT_ID);
                CREATE INDEX IF NOT EXISTS IX_TB_HR_CAND_03 ON TB_HR_CAND (HOME_AREA_CD);
                COMMENT ON COLUMN TB_HR_CAND.CAND_ID IS '후보ID: 애플리케이션에서 발급하는 업무 식별자';
                COMMENT ON COLUMN TB_HR_CAND.PRVC_AGRE_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언); 최초 동의 확인자';
                COMMENT ON COLUMN TB_HR_CAND.PRVC_AGRE_DT IS '최초 동의 확인일: 이후 철회 및 재동의에도 보존';
                COMMENT ON COLUMN TB_HR_CAND.CNTC_STAT_CD IS 'BEFORE:연락 전|DONE:연락함|ITVW:면접 예정|HOLD:보류|CTRT:계약 완료';
                COMMENT ON COLUMN TB_HR_CAND.CAND_SRC_CD IS 'SITE:구인 사이트|VNDR:협력사 추천|ACQ:지인 추천|PAST:과거 투입';
                COMMENT ON COLUMN TB_HR_CAND.HOME_ADDR IS '후보 등록 및 정보 수정 시 애플리케이션이 HOME_AREA_CD의 행정구역 이름으로 갱신';
                COMMENT ON COLUMN TB_HR_CAND.LAST_CNTC_DT IS '후보 등록 및 연락 기록 저장 시 애플리케이션이 한국 오늘로 갱신; 정보 수정 시 보존';
                COMMENT ON COLUMN TB_HR_CAND.MOD_DTTM IS '후보 수정 및 연락 저장 시 애플리케이션이 갱신하는 동시 수정 감지 시각';
            ELSIF spec.table_name = 'tb_hr_cand_skill' THEN
                COMMENT ON COLUMN TB_HR_CAND_SKILL.SORT_ORD IS '후보 등록 및 정보 수정 시 애플리케이션이 입력 순서로 갱신';
            ELSE
                COMMENT ON COLUMN TB_HR_CAND_CNTC.CNTC_STAT_CD IS 'BEFORE:연락 전|DONE:연락함|ITVW:면접 예정|HOLD:보류|CTRT:계약 완료';
                COMMENT ON COLUMN TB_HR_CAND_CNTC.CNTC_SN IS '후보 행 잠금 안에서 애플리케이션이 부여하는 후보별 연락 순번';
                COMMENT ON COLUMN TB_HR_CAND_CNTC.REG_DTTM IS '기록 시각; 최신순은 REG_DTTM 내림차순 후 CNTC_SN 내림차순';
            END IF;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_class c WHERE c.oid = relation AND c.relkind = 'r') THEN
            RAISE EXCEPTION USING MESSAGE=spec.table_name || ': expected ordinary table', ERRCODE='P0001';
        END IF;
        EXECUTE format('LOCK TABLE %s IN ACCESS EXCLUSIVE MODE', relation);
        EXECUTE regexp_replace(spec.definition, '^CREATE TABLE IF NOT EXISTS [A-Z_]+',
            'CREATE TEMP TABLE candidate_expected');
        expected_relation := to_regclass('pg_temp.candidate_expected');
        SELECT array_agg(a.attname || ':' || format_type(a.atttypid, a.atttypmod)
            || ':' || a.attnotnull::TEXT || ':' || coalesce(pg_get_expr(d.adbin, d.adrelid), '')
            ORDER BY a.attnum)
        INTO expected_columns
        FROM pg_attribute a
        LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
        WHERE a.attrelid = expected_relation
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
        WHERE c.conrelid = expected_relation AND c.contype IN ('p', 'c', 'u');
        SELECT array_agg(c.conname || ':' || pg_get_constraintdef(c.oid)
            || ':' || c.condeferrable::TEXT ORDER BY c.conname)
        INTO actual_constraints
        FROM pg_constraint c
        WHERE c.conrelid = relation AND c.contype IN ('p', 'c', 'u');
        DROP TABLE pg_temp.candidate_expected;
        IF actual_columns IS DISTINCT FROM expected_columns
            OR actual_constraints IS DISTINCT FROM expected_constraints THEN
            RAISE EXCEPTION USING MESSAGE=spec.table_name
                || ': original columns, defaults, nullability or PK/check/unique constraints differ', ERRCODE='P0001';
        END IF;
    END LOOP;
END
$candidate_schema$;
