DO $test$
DECLARE counts INT[]; n INT;
BEGIN
 SELECT array_agg(c ORDER BY staff_id) INTO counts FROM
 (SELECT staff_id,count(*)::int c FROM identity.staff_branch_permission
  WHERE business_id='00000000-0000-4000-8000-000000000001' GROUP BY staff_id) x;
 IF counts IS DISTINCT FROM ARRAY[7,11,13,11] THEN RAISE EXCEPTION 'Unexpected role backfill: %',counts; END IF;
 SELECT count(*) INTO n FROM identity.staff_business_permission WHERE business_id='00000000-0000-4000-8000-000000000001';
 IF n<>6 THEN RAISE EXCEPTION 'Expected six business grants for former ADMIN, got %',n; END IF;
 IF EXISTS(SELECT 1 FROM identity.staff_business_permission WHERE business_id='00000000-0000-4000-8000-000000000001' AND staff_id<>'00000000-0000-4000-8000-000000000103') THEN
  RAISE EXCEPTION 'Business grants leaked to a non-administrator'; END IF;
 SELECT count(*) INTO n FROM identity.position_permission WHERE position_id='00000000-0000-4000-8000-000000000003';
 IF n<>7 THEN RAISE EXCEPTION 'Position starter bundle was not seeded'; END IF;
 SELECT count(*) INTO n FROM identity.staff_branch_position WHERE business_id='00000000-0000-4000-8000-000000000001' AND assigned_at IS NOT NULL AND assignment_id IS NOT NULL;
 IF n<>4 THEN RAISE EXCEPTION 'Position assignments were lost'; END IF;
 IF to_regclass('identity.staff_branch_role') IS NOT NULL OR to_regclass('identity.app_role') IS NOT NULL OR to_regtype('shared.user_role') IS NOT NULL THEN
  RAISE EXCEPTION 'Legacy authorization objects survived retirement'; END IF;
 SELECT count(*) INTO n FROM platform.audit_log WHERE business_id='00000000-0000-4000-8000-000000000001' AND action='ROLE_HISTORY_ARCHIVED';
 IF n<>4 THEN RAISE EXCEPTION 'Legacy history was not archived'; END IF;
END $test$;
