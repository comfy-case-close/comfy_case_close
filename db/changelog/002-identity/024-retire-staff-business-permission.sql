-- An uncovered direct grant cannot be translated without either losing that
-- staff member's access or granting it to someone else. Resolve it explicitly.
DO $guard$
BEGIN
  IF EXISTS (
    SELECT 1 FROM identity.staff_business_permission g
    WHERE g.revoked_at IS NULL
      AND (g.granted_at > clock_timestamp() OR NOT EXISTS (
        SELECT 1 FROM identity.staff_branch_position a
        JOIN identity.position p
          ON p.position_id = a.position_id AND p.business_id = a.business_id AND p.is_active
        JOIN identity.position_permission pp
          ON pp.position_id = a.position_id AND pp.business_id = a.business_id
         AND pp.permission_code = g.permission_code AND pp.scope = 'BUSINESS'
         AND pp.revoked_at IS NULL AND pp.granted_at <= clock_timestamp()
        WHERE a.staff_id = g.staff_id AND a.business_id = g.business_id
          AND a.revoked_at IS NULL AND a.assigned_at <= clock_timestamp()
      ))
  ) THEN
    RAISE EXCEPTION 'Unmapped staff_business_permission grants; assign business permissions to positions before dropping direct grants';
  END IF;
END $guard$;

-- Keep the history inspectable after retiring the direct-grant table.
INSERT INTO platform.audit_log(business_id, actor_user_id, action, entity_type, entity_id, old_value, new_value)
SELECT business_id, NULL, 'BUSINESS_PERMISSION_HISTORY_ARCHIVED', 'staff_business_permission',
       grant_id::text, to_jsonb(g), NULL
FROM identity.staff_business_permission g;

DROP TABLE identity.staff_business_permission;
UPDATE identity.permission_revision SET revision = nextval('identity.permission_revision_seq');
