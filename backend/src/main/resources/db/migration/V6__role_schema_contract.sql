-- UI-24 / SLICE-006 identifies a screen capability, not a physical table.
-- Physical entities: TB_SY_ROLE and TB_SY_ROLE_MENU_AUTH (sql/DDL_PostgreSQL.sql).
-- Preserve existing data and reject incompatible permission structures instead of repairing them.
DO $role_contract$
DECLARE
    expected record;
    actual_type text;
    actual_required boolean;
    actual_default text;
    actual_check text;
    actual_key text[];
    flag text;
    position integer := 0;
    differences text[] := ARRAY[]::text[];
BEGIN
    PERFORM pg_advisory_xact_lock(734901, 1);
    LOCK TABLE TB_SY_ROLE_MENU_AUTH IN ACCESS EXCLUSIVE MODE;
    FOR expected IN
        SELECT * FROM (VALUES
            ('role_id', 'character varying(20)', true, NULL::text),
            ('menu_id', 'character varying(20)', true, NULL::text),
            ('inq_yn', 'character(1)', true, '''N'''),
            ('reg_yn', 'character(1)', true, '''N'''),
            ('upd_yn', 'character(1)', true, '''N'''),
            ('del_yn', 'character(1)', true, '''N'''),
            ('upld_yn', 'character(1)', true, '''N'''),
            ('dnld_yn', 'character(1)', true, '''N'''),
            ('cnfm_yn', 'character(1)', true, '''N'''),
            ('reg_user_id', 'character varying(30)', true, NULL::text),
            ('reg_dttm', 'timestamp with time zone', true, 'CURRENT_TIMESTAMP'),
            ('mod_user_id', 'character varying(30)', false, NULL::text),
            ('mod_dttm', 'timestamp with time zone', false, NULL::text)
        ) AS columns(name, type, required, default_value)
    LOOP
        SELECT format_type(a.atttypid, a.atttypmod), a.attnotnull,
               regexp_replace(pg_get_expr(d.adbin, d.adrelid), '::(bpchar|character varying|text)', '', 'g')
          INTO actual_type, actual_required, actual_default
          FROM pg_attribute a LEFT JOIN pg_attrdef d ON d.adrelid=a.attrelid AND d.adnum=a.attnum
         WHERE a.attrelid='tb_sy_role_menu_auth'::regclass
           AND a.attname=expected.name AND a.attnum>0 AND NOT a.attisdropped;
        IF actual_type IS DISTINCT FROM expected.type
           OR actual_required IS DISTINCT FROM expected.required
           OR actual_default IS DISTINCT FROM expected.default_value THEN
            differences := array_append(differences,
                format('tb_sy_role_menu_auth.%s expected type=%s required=%s default=%s actual type=%s required=%s default=%s',
                    expected.name, expected.type, expected.required, expected.default_value,
                    actual_type, actual_required, actual_default));
        END IF;
    END LOOP;
    SELECT array_agg(a.attname::text ORDER BY key.ordinality) INTO actual_key
      FROM pg_constraint c
      CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS key(attnum, ordinality)
      JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=key.attnum
     WHERE c.conrelid='tb_sy_role_menu_auth'::regclass AND c.contype='p'
       AND c.conname='pk_tb_sy_role_menu_auth';
    IF actual_key IS DISTINCT FROM ARRAY['role_id','menu_id']::text[] THEN
        differences := array_append(differences, 'tb_sy_role_menu_auth.PK_TB_SY_ROLE_MENU_AUTH expected (role_id,menu_id)');
    END IF;
    FOREACH flag IN ARRAY ARRAY['inq_yn','reg_yn','upd_yn','del_yn','upld_yn','dnld_yn','cnfm_yn'] LOOP
        position := position + 1;
        SELECT pg_get_constraintdef(oid) INTO actual_check FROM pg_constraint
         WHERE conrelid='tb_sy_role_menu_auth'::regclass AND contype='c' AND convalidated
           AND conname='ck_tb_sy_role_menu_auth_' || lpad(position::text,2,'0');
        actual_check := regexp_replace(lower(actual_check), '::(bpchar|text|character varying)', '', 'g');
        actual_check := translate(regexp_replace(actual_check, '[[:space:]]', '', 'g'), '()[]', '');
        actual_check := replace(actual_check, '=anyarray', 'in');
        IF actual_check IS DISTINCT FROM 'check' || flag || 'in''y'',''n''' THEN
            differences := array_append(differences, format('tb_sy_role_menu_auth.%s expected CHECK Y,N', flag));
        END IF;
    END LOOP;
    IF cardinality(differences)>0 THEN
        RAISE EXCEPTION USING MESSAGE=array_to_string(differences, E'\n'), ERRCODE='P0001';
    END IF;
END;
$role_contract$;

-- Original DDL index; the composite PK already supports role-scoped permission reads.
CREATE INDEX IF NOT EXISTS IX_TB_SY_ROLE_MENU_AUTH_01 ON TB_SY_ROLE_MENU_AUTH (MENU_ID);
COMMENT ON TABLE TB_SY_ROLE IS '역할의 이름·데이터 조회 범위·민감 항목 보기·사용 여부를 저장한다.';
COMMENT ON TABLE TB_SY_ROLE_MENU_AUTH IS '역할과 메뉴의 복합키로 일곱 동작 권한을 저장한다. UI-24 조회·저장·복사의 영속 원천이다.';
