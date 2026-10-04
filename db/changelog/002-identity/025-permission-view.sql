-- Viewing another person's effective permissions is explicitly branch-scoped.
INSERT INTO identity.permission(permission_code, scope, description)
VALUES ('PERMISSION_VIEW', 'BRANCH', 'View staff permissions in this branch');

-- Existing permission administrators retain visibility at their assigned branches.
INSERT INTO identity.position_permission(position_id, business_id, permission_code, scope)
SELECT position_id, business_id, 'PERMISSION_VIEW', 'BRANCH'
FROM identity.position_permission
WHERE permission_code = 'PERMISSION_GRANT' AND revoked_at IS NULL
  AND granted_at <= clock_timestamp()
ON CONFLICT (position_id, permission_code) WHERE revoked_at IS NULL DO NOTHING;
