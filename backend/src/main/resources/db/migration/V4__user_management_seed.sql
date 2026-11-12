-- Executed explicitly by SchemaPreparation, never by application startup or an HTTP request.
-- sql/DML_BaseData.sql의 역할(4번), 첫 관리자의 역할(7번) 원문.
-- 공통코드와 첫 사용자 원문은 앞선 V3에서 표 전체가 비어 있을 때만 준비한다.
DO $users$
BEGIN
    PERFORM pg_advisory_xact_lock(734901, 1);
    LOCK TABLE TB_SY_ROLE IN EXCLUSIVE MODE;
    IF NOT EXISTS (SELECT 1 FROM TB_SY_ROLE) THEN
        INSERT INTO TB_SY_ROLE (ROLE_ID, ROLE_NM, DATA_SCOPE_CD, SNSTV_VIEW_YN, SYS_ROLE_YN, SORT_ORD, USE_YN, REG_USER_ID) VALUES
        ('ADMIN', '시스템 관리자', 'ALL', 'Y', 'Y', 1, 'Y', 'SYSTEM'),
        ('EXEC', '경영진', 'ALL', 'Y', 'Y', 2, 'Y', 'SYSTEM'),
        ('FIN', '재무 담당', 'ALL', 'Y', 'Y', 3, 'Y', 'SYSTEM'),
        ('SALES_LDR', '영업 파트장', 'PART', 'N', 'Y', 4, 'Y', 'SYSTEM'),
        ('PM', '일반 사용자(PM)', 'PROJ', 'N', 'Y', 5, 'Y', 'SYSTEM');
    END IF;

    LOCK TABLE TB_SY_USER_ROLE IN EXCLUSIVE MODE;
    IF NOT EXISTS (SELECT 1 FROM TB_SY_USER_ROLE) THEN
        INSERT INTO TB_SY_USER_ROLE (USER_ID, ROLE_ID, REG_USER_ID) VALUES
        ('admin', 'ADMIN', 'SYSTEM');
    END IF;

    -- sql/DDL_PostgreSQL.sql 6번의 본사 예약 파트 원문. 기존 파트가 있으면 보충하지 않는다.
    LOCK TABLE TB_BS_PART IN EXCLUSIVE MODE;
    IF NOT EXISTS (SELECT 1 FROM TB_BS_PART) THEN
        INSERT INTO TB_BS_PART (PART_ID, PART_NM, HQ_YN, USE_YN, REG_USER_ID) VALUES ('HQ', '본사', 'Y', 'Y', 'SYSTEM');
    END IF;
END;
$users$;
