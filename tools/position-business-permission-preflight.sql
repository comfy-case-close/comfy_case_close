-- Run after changeset 023 and before retrying a blocked 024. Set
-- app.business_id for the tenant when using a role subject to RLS.
-- Every row returned still relies on a direct grant and needs an explicit
-- position mapping or an intentional revocation before the old table is dropped.
SELECT g.business_id, s.employee_code, g.staff_id, g.permission_code
FROM identity.staff_business_permission g
JOIN identity.staff s ON s.staff_id = g.staff_id AND s.business_id = g.business_id
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
ORDER BY g.business_id, s.employee_code, g.permission_code;
