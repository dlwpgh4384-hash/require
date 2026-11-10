-- DB contract repair: preserve the eight declared structures without adding business operations.
-- Existing tables, rows, comments and constraints are deliberately left untouched.
-- Logical references are comments, not physical FKs (the model explicitly forbids them).
-- Only model PK indexes are required; no extra unique keys or indexes are inferred.

DO $$
BEGIN
    IF to_regclass('tb_sy_user_scope') IS NULL THEN
        CREATE TABLE IF NOT EXISTS TB_SY_USER_SCOPE (
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
        );
        COMMENT ON TABLE TB_SY_USER_SCOPE IS '사용자별 담당 범위를 보존한다. 현재 미사용 — 후속 업무 연동 예정.';
        COMMENT ON COLUMN TB_SY_USER_SCOPE.USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_SY_USER_SCOPE.PART_ID IS 'TB_BS_PART.PART_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_SY_USER_SCOPE.PROJ_ID IS 'TB_PJ_PROJ.PROJ_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_SY_USER_SCOPE.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_SY_USER_SCOPE.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('tb_sy_user_pref') IS NULL THEN
        CREATE TABLE IF NOT EXISTS TB_SY_USER_PREF (
            USER_ID VARCHAR(30) NOT NULL,
            PREF_TYPE_CD VARCHAR(20) NOT NULL,
            PREF_KEY VARCHAR(30) NOT NULL,
            PREF_VAL VARCHAR(2000) NOT NULL,
            REG_USER_ID VARCHAR(30) NOT NULL,
            REG_DTTM TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
            MOD_USER_ID VARCHAR(30),
            MOD_DTTM TIMESTAMP,
            CONSTRAINT PK_TB_SY_USER_PREF PRIMARY KEY (USER_ID, PREF_TYPE_CD, PREF_KEY)
        );
        COMMENT ON TABLE TB_SY_USER_PREF IS '사용자별 화면 설정을 보존한다. 현재 미사용 — 공통 메뉴의 화면 상태 저장에는 사용하지 않는다.';
        COMMENT ON COLUMN TB_SY_USER_PREF.USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_SY_USER_PREF.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_SY_USER_PREF.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('tb_sy_noti') IS NULL THEN
        CREATE SEQUENCE IF NOT EXISTS SQ_TB_SY_NOTI START WITH 1 INCREMENT BY 1;
        CREATE TABLE IF NOT EXISTS TB_SY_NOTI (
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
        );
        COMMENT ON TABLE TB_SY_NOTI IS '수신자별 알림 내용과 읽음 여부를 보존한다. 현재 미사용 — 알림 발송 및 조회 연동 예정.';
        COMMENT ON COLUMN TB_SY_NOTI.NOTI_ID IS 'SQ_TB_SY_NOTI로 생성하는 알림 식별자; 외부 참조 아님';
        COMMENT ON COLUMN TB_SY_NOTI.RCVR_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_SY_NOTI.LINK_SCRN_ID IS '업무 화면 식별자 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_SY_NOTI.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_SY_NOTI.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('tb_bs_part_std') IS NULL THEN
        CREATE TABLE IF NOT EXISTS TB_BS_PART_STD (
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
        );
        COMMENT ON TABLE TB_BS_PART_STD IS '영업 파트의 적용 기간별 기준 이력을 보존한다. 현재 미사용 — 후속 업무 연동 예정.';
        COMMENT ON COLUMN TB_BS_PART_STD.PART_ID IS 'TB_BS_PART.PART_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_BS_PART_STD.PART_LDR_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_BS_PART_STD.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_BS_PART_STD.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('tb_pj_proj') IS NULL THEN
        CREATE TABLE IF NOT EXISTS TB_PJ_PROJ (
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
        );
        COMMENT ON TABLE TB_PJ_PROJ IS '프로젝트 계약과 예산 정보를 보존한다. 현재 미사용 — 프로젝트 업무 및 계산 연동 예정.';
        COMMENT ON COLUMN TB_PJ_PROJ.PROJ_ID IS '프로젝트 자체 식별자; 외부 참조 아님';
        COMMENT ON COLUMN TB_PJ_PROJ.CUST_CLNT_ID IS 'TB_BS_CLNT.CLNT_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_PJ_PROJ.OPP_ID IS 'TB_SL_OPP.OPP_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_PJ_PROJ.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_PJ_PROJ.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        -- Source vocabulary: sql/DDL_PostgreSQL.sql; descriptive only, no invented transitions.
        COMMENT ON COLUMN TB_PJ_PROJ.PROJ_STAT_CD IS 'ING:진행|PRE:수주예정|END:종료|LOSE:실주';
        COMMENT ON COLUMN TB_PJ_PROJ.ORGNL_CTRT_AMT IS '현재 자동 갱신 주체 없음; 프로젝트 업무 연동 시 원계약금액 갱신 정책 확정 예정';
        COMMENT ON COLUMN TB_PJ_PROJ.BDGT_AMT IS '현재 자동 갱신 주체 없음; 프로젝트 업무 연동 시 예산금액 갱신 정책 확정 예정';
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('tb_bl_bill') IS NULL THEN
        CREATE TABLE IF NOT EXISTS TB_BL_BILL (
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
        );
        COMMENT ON TABLE TB_BL_BILL IS '프로젝트별 청구 금액과 입금 누계 및 취소 정보를 보존한다. 현재 미사용 — 청구 업무 연동 예정.';
        COMMENT ON COLUMN TB_BL_BILL.BILL_ID IS '청구 자체 식별자; 외부 참조 아님';
        COMMENT ON COLUMN TB_BL_BILL.PROJ_ID IS 'TB_PJ_PROJ.PROJ_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_BL_BILL.CNCL_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_BL_BILL.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_BL_BILL.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_BL_BILL.VAT_AMT IS '현재 자동 갱신 주체 없음; 청구 업무 연동 시 부가세금액 갱신 정책 확정 예정';
        COMMENT ON COLUMN TB_BL_BILL.TOT_AMT IS '현재 자동 갱신 주체 없음; 청구 업무 연동 시 합계금액 갱신 정책 확정 예정';
        COMMENT ON COLUMN TB_BL_BILL.RCPT_SUM_AMT IS '현재 자동 갱신 주체 없음; 입금 업무 연동 시 입금누계 갱신 정책 확정 예정';
        COMMENT ON COLUMN TB_BL_BILL.UNPAID_AMT IS '현재 자동 갱신 주체 없음; 입금 업무 연동 시 미수금액 갱신 정책 확정 예정';
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('tb_bl_dun_hist') IS NULL THEN
        CREATE TABLE IF NOT EXISTS TB_BL_DUN_HIST (
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
        );
        COMMENT ON TABLE TB_BL_DUN_HIST IS '청구별 미수 알림 발송 이력을 보존한다. 현재 미사용 — 발송 및 재발송 업무 연동 예정.';
        COMMENT ON COLUMN TB_BL_DUN_HIST.BILL_ID IS 'TB_BL_BILL.BILL_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_BL_DUN_HIST.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('tb_cl_close') IS NULL THEN
        CREATE TABLE IF NOT EXISTS TB_CL_CLOSE (
            CLOSE_YM CHAR(6) NOT NULL,
            CLOSE_STAT_CD VARCHAR(10) NOT NULL,
            CLOSE_USER_ID VARCHAR(30),
            CLOSE_DTTM TIMESTAMP,
            REG_USER_ID VARCHAR(30) NOT NULL,
            REG_DTTM TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
            MOD_USER_ID VARCHAR(30),
            MOD_DTTM TIMESTAMP,
            CONSTRAINT PK_TB_CL_CLOSE PRIMARY KEY (CLOSE_YM)
        );
        COMMENT ON TABLE TB_CL_CLOSE IS '월별 마감 상태와 처리 정보를 보존한다. 현재 미사용 — 월마감 업무 연동 예정.';
        COMMENT ON COLUMN TB_CL_CLOSE.CLOSE_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_CL_CLOSE.REG_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_CL_CLOSE.MOD_USER_ID IS 'TB_SY_USER.USER_ID 참조 의도 (FK 미선언)';
        COMMENT ON COLUMN TB_CL_CLOSE.CLOSE_STAT_CD IS 'WORK:작업중|CLOSE:마감';
    END IF;
END $$;
