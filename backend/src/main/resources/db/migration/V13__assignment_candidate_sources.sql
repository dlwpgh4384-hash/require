-- Original sales sources for assignment candidates; no business seed or assignment schema change.
CREATE SEQUENCE IF NOT EXISTS SQ_TB_SL_TENT_ASGN START WITH 1 INCREMENT BY 1;

DO $assignment_sources$
DECLARE
    definitions text[] := ARRAY[
$ddl$CREATE TABLE IF NOT EXISTS TB_SL_OPP (
 OPP_ID VARCHAR(20) NOT NULL,
 OPP_NM VARCHAR(100) NOT NULL,
 CUST_CLNT_ID VARCHAR(20) NOT NULL,
 PART_ID VARCHAR(20) NOT NULL,
 SALES_STEP_CD VARCHAR(10) NOT NULL,
 WIN_PRBL_RT NUMERIC(5,2) NOT NULL,
 PRBL_MANUAL_YN CHAR(1) DEFAULT 'N' NOT NULL,
 EXPCT_AMT NUMERIC(15) NOT NULL,
 BGN_PLAN_YM CHAR(6) NOT NULL,
 CTRT_TYPE_CD VARCHAR(10),
 EXPCT_PRD_MCNT NUMERIC(3),
 NEXT_TODO_CN VARCHAR(200),
 NEXT_TODO_DDLN_DT DATE,
 WORK_PLC_ADDR VARCHAR(200),
 RMRK VARCHAR(500),
 OPP_STAT_CD VARCHAR(10) NOT NULL,
 EXPCT_PRFT_RT NUMERIC(7,2),
 APRV_STAT_CD VARCHAR(10) NOT NULL,
 APRV_PRFT_RT NUMERIC(7,2),
 RSLT_DT DATE,
 LOSE_RSN_CD VARCHAR(10),
 LOSE_MEMO VARCHAR(500),
 PROJ_ID VARCHAR(20),
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_SL_OPP PRIMARY KEY (OPP_ID),
 CONSTRAINT CK_TB_SL_OPP_01 CHECK (PRBL_MANUAL_YN IN ('Y', 'N'))
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_SL_OPP_NEED (
 OPP_ID VARCHAR(20) NOT NULL,
 NEED_SN NUMERIC(3) NOT NULL,
 JOB_CD VARCHAR(10) NOT NULL,
 GRADE_CD VARCHAR(10),
 NEED_CNT NUMERIC(3) NOT NULL,
 BGN_YM CHAR(6) NOT NULL,
 PRD_MCNT NUMERIC(3) NOT NULL,
 MON_MM NUMERIC(4,2) NOT NULL,
 EXPCT_UPRC NUMERIC(15),
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_SL_OPP_NEED PRIMARY KEY (OPP_ID, NEED_SN)
)$ddl$,
$ddl$CREATE TABLE IF NOT EXISTS TB_SL_TENT_ASGN (
 TENT_ID NUMERIC(10) DEFAULT nextval('SQ_TB_SL_TENT_ASGN') NOT NULL,
 OPP_ID VARCHAR(20) NOT NULL,
 NEED_SN NUMERIC(3) NOT NULL,
 DEV_ID VARCHAR(20) NOT NULL,
 BGN_YM CHAR(6) NOT NULL,
 TENT_STAT_CD VARCHAR(10) NOT NULL,
 SRC_SCRN_ID VARCHAR(10) NOT NULL,
 PROC_DTTM TIMESTAMPTZ,
 REG_USER_ID VARCHAR(30) NOT NULL,
 REG_DTTM TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
 MOD_USER_ID VARCHAR(30),
 MOD_DTTM TIMESTAMPTZ,
 CONSTRAINT PK_TB_SL_TENT_ASGN PRIMARY KEY (TENT_ID)
)$ddl$];
    ddl text;
    table_name text;
    relation regclass;
    expected_columns text[];
    actual_columns text[];
    expected_constraints text[];
    actual_constraints text[];
BEGIN
    PERFORM pg_advisory_xact_lock(734901, 1);
    FOREACH ddl IN ARRAY definitions LOOP
        table_name := lower(substring(ddl FROM 'EXISTS ([A-Z_]+)'));
        relation := to_regclass(format('%I.%I', current_schema(), table_name));
        IF relation IS NOT NULL THEN
            IF NOT EXISTS (SELECT 1 FROM pg_class WHERE oid=relation AND relkind='r') THEN
                RAISE EXCEPTION USING MESSAGE=table_name || ': expected ordinary table', ERRCODE='P0001';
            END IF;
            EXECUTE format('LOCK TABLE %s IN ACCESS EXCLUSIVE MODE', relation);
            EXECUTE regexp_replace(ddl, 'CREATE TABLE IF NOT EXISTS [A-Z_]+', 'CREATE TEMP TABLE assignment_expected');
            SELECT array_agg(a.attname || ':' || format_type(a.atttypid,a.atttypmod) || ':' || a.attnotnull::text
                || ':' || coalesce(pg_get_expr(d.adbin,d.adrelid),'') ORDER BY a.attnum)
              INTO expected_columns
              FROM pg_attribute a
              LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
             WHERE a.attrelid='pg_temp.assignment_expected'::regclass AND a.attnum>0 AND NOT a.attisdropped;
            SELECT array_agg(a.attname || ':' || format_type(a.atttypid,a.atttypmod) || ':' || a.attnotnull::text
                || ':' || coalesce(pg_get_expr(d.adbin,d.adrelid),'') ORDER BY a.attnum)
              INTO actual_columns
              FROM pg_attribute a
              LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
             WHERE a.attrelid=relation AND a.attnum>0 AND NOT a.attisdropped;
            SELECT array_agg(conname || ':' || pg_get_constraintdef(oid) ORDER BY conname)
              INTO expected_constraints
              FROM pg_constraint
             WHERE conrelid='pg_temp.assignment_expected'::regclass AND contype IN ('p','c','u');
            SELECT array_agg(conname || ':' || pg_get_constraintdef(oid) ORDER BY conname)
              INTO actual_constraints
              FROM pg_constraint
             WHERE conrelid=relation AND contype IN ('p','c','u') AND convalidated;
            DROP TABLE pg_temp.assignment_expected;
            IF actual_columns IS DISTINCT FROM expected_columns OR actual_constraints IS DISTINCT FROM expected_constraints THEN
                RAISE EXCEPTION USING MESSAGE=table_name || ': original columns, defaults, nullability, PK or checks differ', ERRCODE='P0001';
            END IF;
        ELSE
            EXECUTE ddl;
        END IF;
    END LOOP;
END
$assignment_sources$;

DO $assignment_references$
DECLARE
    spec record;
    definition text;
    expected text;
BEGIN
    FOR spec IN SELECT * FROM (VALUES
        ('TB_SL_OPP','FK_TB_SL_OPP_01','CUST_CLNT_ID','TB_BS_CLNT','CLNT_ID'),
        ('TB_SL_OPP','FK_TB_SL_OPP_02','PART_ID','TB_BS_PART','PART_ID'),
        ('TB_SL_OPP','FK_TB_SL_OPP_03','PROJ_ID','TB_PJ_PROJ','PROJ_ID'),
        ('TB_SL_OPP_NEED','FK_TB_SL_OPP_NEED_01','OPP_ID','TB_SL_OPP','OPP_ID'),
        ('TB_SL_TENT_ASGN','FK_TB_SL_TENT_ASGN_01','OPP_ID, NEED_SN','TB_SL_OPP_NEED','OPP_ID, NEED_SN'),
        ('TB_SL_TENT_ASGN','FK_TB_SL_TENT_ASGN_02','DEV_ID','TB_HR_DEV','DEV_ID'),
        ('TB_PJ_PROJ','FK_TB_PJ_PROJ_02','OPP_ID','TB_SL_OPP','OPP_ID')
    ) AS refs(table_name,name,column_name,target,target_column) LOOP
        expected := format('FOREIGN KEY (%s) REFERENCES %s(%s)',
            lower(spec.column_name),lower(spec.target),lower(spec.target_column));
        SELECT pg_get_constraintdef(oid) INTO definition
          FROM pg_constraint
         WHERE conrelid=to_regclass(lower(spec.table_name)) AND conname=lower(spec.name);
        IF definition IS NULL THEN
            EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I %s',lower(spec.table_name),lower(spec.name),expected);
        ELSIF lower(definition) IS DISTINCT FROM lower(expected) OR NOT EXISTS (
            SELECT 1 FROM pg_constraint
             WHERE conrelid=to_regclass(lower(spec.table_name)) AND conname=lower(spec.name)
               AND convalidated AND NOT condeferrable
        ) THEN
            RAISE EXCEPTION USING MESSAGE=spec.table_name || '.' || spec.name || ': expected original foreign key', ERRCODE='P0001';
        END IF;
    END LOOP;
END
$assignment_references$;

CREATE INDEX IF NOT EXISTS IX_TB_SL_OPP_01 ON TB_SL_OPP (PART_ID);
CREATE INDEX IF NOT EXISTS IX_TB_SL_OPP_02 ON TB_SL_OPP (OPP_STAT_CD);
CREATE INDEX IF NOT EXISTS IX_TB_SL_OPP_03 ON TB_SL_OPP (CUST_CLNT_ID);
CREATE INDEX IF NOT EXISTS IX_TB_SL_OPP_04 ON TB_SL_OPP (PROJ_ID);
CREATE INDEX IF NOT EXISTS IX_TB_SL_OPP_NEED_01 ON TB_SL_OPP_NEED (JOB_CD);
CREATE INDEX IF NOT EXISTS IX_TB_SL_TENT_ASGN_01 ON TB_SL_TENT_ASGN (OPP_ID, NEED_SN);
CREATE INDEX IF NOT EXISTS IX_TB_SL_TENT_ASGN_02 ON TB_SL_TENT_ASGN (DEV_ID);

COMMENT ON TABLE TB_SL_OPP IS '영업기회';
COMMENT ON COLUMN TB_SL_OPP.OPP_ID IS '영업기회ID';
COMMENT ON COLUMN TB_SL_OPP.OPP_NM IS '기회명';
COMMENT ON COLUMN TB_SL_OPP.CUST_CLNT_ID IS '고객사거래처ID: 사용 중인 고객사';
COMMENT ON COLUMN TB_SL_OPP.PART_ID IS '영업파트ID';
COMMENT ON COLUMN TB_SL_OPP.SALES_STEP_CD IS '영업단계코드: FIND 발굴 / PROP 제안 / NEGO 협상 / PRE 계약 직전 (시스템)';
COMMENT ON COLUMN TB_SL_OPP.WIN_PRBL_RT IS '수주확률: 0~100, 단계 기본값 10/30/60/90';
COMMENT ON COLUMN TB_SL_OPP.PRBL_MANUAL_YN IS '확률수기여부: Y면 단계 이동해도 유지';
COMMENT ON COLUMN TB_SL_OPP.EXPCT_AMT IS '예상금액: 부가세 제외, 0 초과';
COMMENT ON COLUMN TB_SL_OPP.BGN_PLAN_YM IS '시작예정연월';
COMMENT ON COLUMN TB_SL_OPP.CTRT_TYPE_CD IS '계약유형코드: 공통코드 CTRT_TYPE 중 TK 턴키 / MO 월단위 / DP 파견(IN 내부 제외). 수주 처리 창 기본값, AI 인력 추천 파견 자리 판정(식-69)';
COMMENT ON COLUMN TB_SL_OPP.EXPCT_PRD_MCNT IS '예상기간개월수: 1 이상. 비우면 6개월로 봄. 매출 예측(식-92), 수주 처리 창 종료일 기본값';
COMMENT ON COLUMN TB_SL_OPP.NEXT_TODO_CN IS '다음할일';
COMMENT ON COLUMN TB_SL_OPP.NEXT_TODO_DDLN_DT IS '다음할일기한: 지나면 빨강';
COMMENT ON COLUMN TB_SL_OPP.WORK_PLC_ADDR IS '근무지주소: 주소 전체(프로젝트 근무지와 같이 직접 입력). AI 인력 추천 근무지 점수(식-70, 맨 앞 시·도 비교는 프로젝트 근무지와 같음, 02 SFR-021), 수주 처리 때 TB_PJ_PROJ.WORK_PLC_ADDR로 넘김(2026-10-29 C12)';
COMMENT ON COLUMN TB_SL_OPP.RMRK IS '비고: AI 인력 추천 대상이 이 기회면 특징으로 씀(식-70)';
COMMENT ON COLUMN TB_SL_OPP.OPP_STAT_CD IS 'ING:진행|WIN:수주|LOSE:실주|STOP:사용 안 함';
COMMENT ON COLUMN TB_SL_OPP.EXPCT_PRFT_RT IS '예상이익률: 저장 시 계산(식-84)';
COMMENT ON COLUMN TB_SL_OPP.APRV_STAT_CD IS 'NONE:승인 불필요|WAIT:승인 대기|OK:승인|REJ:반려';
COMMENT ON COLUMN TB_SL_OPP.APRV_PRFT_RT IS '승인시이익률: 재저장 비교(±0.5%p)';
COMMENT ON COLUMN TB_SL_OPP.RSLT_DT IS '결과일자: 수주·실주일, 사용 안 함(STOP) 처리일. [다시 사용]으로 ING가 되면 NULL(2026-10-30 C6)';
COMMENT ON COLUMN TB_SL_OPP.LOSE_RSN_CD IS '실주사유코드: PRICE / TECH / SCHD / CNCL / ETC';
COMMENT ON COLUMN TB_SL_OPP.LOSE_MEMO IS '실주메모';
COMMENT ON COLUMN TB_SL_OPP.PROJ_ID IS '프로젝트ID: 수주 시 생성된 프로젝트';
COMMENT ON COLUMN TB_SL_OPP.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
COMMENT ON COLUMN TB_SL_OPP.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SL_OPP.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
COMMENT ON COLUMN TB_SL_OPP.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SL_OPP_NEED IS '영업기회필요인력';
COMMENT ON COLUMN TB_SL_OPP_NEED.OPP_ID IS '영업기회ID';
COMMENT ON COLUMN TB_SL_OPP_NEED.NEED_SN IS '필요일련번호: 1줄 이상';
COMMENT ON COLUMN TB_SL_OPP_NEED.JOB_CD IS '직무코드';
COMMENT ON COLUMN TB_SL_OPP_NEED.GRADE_CD IS '등급코드';
COMMENT ON COLUMN TB_SL_OPP_NEED.NEED_CNT IS '필요인원: 1 이상 정수';
COMMENT ON COLUMN TB_SL_OPP_NEED.BGN_YM IS '시작연월';
COMMENT ON COLUMN TB_SL_OPP_NEED.PRD_MCNT IS '기간개월수: 1 이상';
COMMENT ON COLUMN TB_SL_OPP_NEED.MON_MM IS '월MM: 0 초과 1.0 이하';
COMMENT ON COLUMN TB_SL_OPP_NEED.EXPCT_UPRC IS '예상단가: 비우면 직무 기준 단가(Q-식-80)';
COMMENT ON COLUMN TB_SL_OPP_NEED.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
COMMENT ON COLUMN TB_SL_OPP_NEED.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SL_OPP_NEED.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
COMMENT ON COLUMN TB_SL_OPP_NEED.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
COMMENT ON TABLE TB_SL_TENT_ASGN IS '가배정';
COMMENT ON COLUMN TB_SL_TENT_ASGN.TENT_ID IS '가배정ID';
COMMENT ON COLUMN TB_SL_TENT_ASGN.OPP_ID IS '영업기회ID';
COMMENT ON COLUMN TB_SL_TENT_ASGN.NEED_SN IS '필요일련번호: 가배정 자리(그 기회의 필요 인력 줄). UI-30은 조건 「가배정 자리」(같은 직무 줄 자동, 없으면 첫 줄), UI-05는 누른 추천 카드의 줄, UI-29 수정 화면은 그 표의 줄(2026-10-29)';
COMMENT ON COLUMN TB_SL_TENT_ASGN.DEV_ID IS '개발자ID';
COMMENT ON COLUMN TB_SL_TENT_ASGN.BGN_YM IS '시작연월: max(철수 다음 달, 자리 시작월)';
COMMENT ON COLUMN TB_SL_TENT_ASGN.TENT_STAT_CD IS 'ACT:가배정|REL:해제|CNVT:배정 후보로 넘김|DONE:배정 확정|DROP:후보에서 뺌';
COMMENT ON COLUMN TB_SL_TENT_ASGN.SRC_SCRN_ID IS 'TB_SY_MENU.SCRN_ID 참조 의도 (FK 미선언). UI-05 / UI-29 / UI-30';
COMMENT ON COLUMN TB_SL_TENT_ASGN.PROC_DTTM IS '처리일시: ACT에서 상태가 바뀐 때: 해제(실주·삭제·바꾸기)·배정 후보로 넘김(수주 처리)·확정·빼기. 수정일시(MOD_DTTM)는 시작월만 고쳐도 바뀌어서 따로 둠(2026-10-28, 예전 REL_DTTM 해제일시)';
COMMENT ON COLUMN TB_SL_TENT_ASGN.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
COMMENT ON COLUMN TB_SL_TENT_ASGN.REG_DTTM IS '등록일시';
COMMENT ON COLUMN TB_SL_TENT_ASGN.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
COMMENT ON COLUMN TB_SL_TENT_ASGN.MOD_DTTM IS '수정일시: 동시 수정 감지(낙관적 잠금)';
