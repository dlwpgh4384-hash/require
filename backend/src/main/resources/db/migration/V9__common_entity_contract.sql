-- Validate physical entities reused by common menus, permissions and code management.
-- UI-24 / SLICE-006 is a document section, not an entity or a missing table.
-- Existing structures are reported for approval, never silently altered.
DO $common_entity_contract$
DECLARE
    expected record;
    relation regclass;
    actual_key text[];
    differences text[] := ARRAY[]::text[];
BEGIN
    PERFORM pg_advisory_xact_lock(734901, 1);
    FOR expected IN
        SELECT * FROM (VALUES
            ('tb_sy_calc_rule', 'p', 'pk_tb_sy_calc_rule', ARRAY['calc_id']),
            ('tb_sy_chg_hist', 'p', 'pk_tb_sy_chg_hist', ARRAY['hist_id']),
            ('tb_sy_code', 'p', 'pk_tb_sy_code', ARRAY['code_grp_id','code']),
            ('tb_sy_code_grp', 'p', 'pk_tb_sy_code_grp', ARRAY['code_grp_id']),
            ('tb_sy_code_map', 'p', 'pk_tb_sy_code_map', ARRAY['map_id']),
            ('tb_sy_code_map', 'u', 'uk_tb_sy_code_map_01', ARRAY['src_text','code_grp_id']),
            ('tb_sy_menu', 'p', 'pk_tb_sy_menu', ARRAY['menu_id']),
            ('tb_sy_oper_std', 'p', 'pk_tb_sy_oper_std', ARRAY['std_cd','aply_bgn_ym']),
            ('tb_sy_role', 'p', 'pk_tb_sy_role', ARRAY['role_id']),
            ('tb_sy_role', 'u', 'uk_tb_sy_role_01', ARRAY['role_nm']),
            ('tb_sy_role_menu_auth', 'p', 'pk_tb_sy_role_menu_auth', ARRAY['role_id','menu_id'])
        ) AS keys(table_name, constraint_type, constraint_name, columns)
    LOOP
        relation := to_regclass(format('%I.%I', current_schema(), expected.table_name));
        IF relation IS NULL THEN
            differences := array_append(differences, expected.table_name || ': required physical table missing');
            CONTINUE;
        END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_class WHERE oid=relation AND relkind='r') THEN
            differences := array_append(differences, expected.table_name || ': expected ordinary table');
            CONTINUE;
        END IF;
        EXECUTE format('LOCK TABLE %s IN ACCESS SHARE MODE', relation);
        SELECT array_agg(a.attname::text ORDER BY key.ordinality) INTO actual_key
          FROM pg_constraint c
          CROSS JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS key(attnum, ordinality)
          JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=key.attnum
         WHERE c.conrelid=relation AND c.conname=expected.constraint_name
           AND c.contype::text=expected.constraint_type AND c.convalidated;
        IF actual_key IS DISTINCT FROM expected.columns THEN
            differences := array_append(differences, format('%s.%s expected columns=%s actual columns=%s',
                expected.table_name, expected.constraint_name, expected.columns, actual_key));
        END IF;
    END LOOP;
    IF cardinality(differences)>0 THEN
        RAISE EXCEPTION USING MESSAGE=array_to_string(differences, E'\n'), ERRCODE='P0001';
    END IF;
END;
$common_entity_contract$;

-- Original lookup indexes; no inferred foreign keys or SCRN_ID uniqueness.
CREATE INDEX IF NOT EXISTS IX_TB_SY_CODE_MAP_01 ON TB_SY_CODE_MAP (CODE_GRP_ID, CODE);
CREATE INDEX IF NOT EXISTS IX_TB_SY_ROLE_MENU_AUTH_01 ON TB_SY_ROLE_MENU_AUTH (MENU_ID);
