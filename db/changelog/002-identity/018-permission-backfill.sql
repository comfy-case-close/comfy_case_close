-- Keep the legacy model available through parity verification.
INSERT INTO identity.staff_branch_permission(staff_id,branch_id,business_id,permission_code)
SELECT r.staff_id,r.branch_id,r.business_id,p.permission_code
FROM identity.staff_branch_role r CROSS JOIN identity.permission p
WHERE r.revoked_at IS NULL AND p.scope='BRANCH' AND
(r.role::text='ADMIN' OR p.permission_code IN
 ('CLOSE_READ','CLOSE_OPEN','CLOSE_EDIT','CLOSE_SUBMIT','DENOMINATION_WRITE','MOVEMENT_ADD','WITHDRAWAL_RECORD')
 OR (r.role::text IN ('MANAGER','ACCOUNTANT') AND p.permission_code IN ('CLOSE_REVIEW','MOVEMENT_REVIEW','FINANCE_READ','REPORT_READ')));
INSERT INTO identity.staff_business_permission(staff_id,business_id,permission_code)
SELECT DISTINCT r.staff_id,r.business_id,p.permission_code
FROM identity.staff_branch_role r CROSS JOIN identity.permission p
WHERE r.revoked_at IS NULL AND r.role::text='ADMIN' AND p.scope='BUSINESS';

DO $verify$
BEGIN
 IF EXISTS (
 SELECT r.staff_id,r.branch_id,p.permission_code FROM identity.staff_branch_role r CROSS JOIN identity.permission p
 WHERE r.revoked_at IS NULL AND p.scope='BRANCH' AND
 (r.role::text='ADMIN' OR p.permission_code IN ('CLOSE_READ','CLOSE_OPEN','CLOSE_EDIT','CLOSE_SUBMIT','DENOMINATION_WRITE','MOVEMENT_ADD','WITHDRAWAL_RECORD')
 OR (r.role::text IN ('MANAGER','ACCOUNTANT') AND p.permission_code IN ('CLOSE_REVIEW','MOVEMENT_REVIEW','FINANCE_READ','REPORT_READ')))
 EXCEPT SELECT staff_id,branch_id,permission_code FROM identity.staff_branch_permission WHERE revoked_at IS NULL
 ) THEN RAISE EXCEPTION 'Permission backfill failed branch parity'; END IF;
 IF EXISTS (
 SELECT r.staff_id,p.permission_code FROM identity.staff_branch_role r CROSS JOIN identity.permission p
 WHERE r.revoked_at IS NULL AND r.role::text='ADMIN' AND p.scope='BUSINESS'
 EXCEPT SELECT staff_id,permission_code FROM identity.staff_business_permission WHERE revoked_at IS NULL
 ) THEN RAISE EXCEPTION 'Permission backfill failed business parity'; END IF;
END $verify$;

-- A rolling upgrade can have service instances running against the old data while
-- this executes. Bumping every tenant's revision makes them drop their cached
-- permission sets on the next check instead of serving pre-backfill authority for
-- up to the cache TTL. The per-row audit trigger already bumps it; this covers the
-- rows inserted before any instance happened to read.
UPDATE identity.permission_revision SET revision=nextval('identity.permission_revision_seq');
