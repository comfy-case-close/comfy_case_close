-- Run only against a disposable database with the current changelog applied.
-- All fixtures and created closes are rolled back.
BEGIN;
DO $test$
DECLARE
  comfy UUID := '3ad0c256-5018-4e7a-9d36-fed3ff12da58';
  other UUID := gen_random_uuid();
  no_shift UUID := gen_random_uuid();
  dcn UUID := gen_random_uuid();
  created INTEGER;
BEGIN
  INSERT INTO identity.business(business_id,business_code,business_name) VALUES
    (comfy,'TEST_COMFY','Comfy'),(other,'TEST_OTHER','Other'),(no_shift,'TEST_NO_SHIFT','No shifts');
  INSERT INTO identity.branch(branch_id,business_id,branch_code,branch_name) VALUES (dcn,comfy,'DCN','DCN');
  INSERT INTO identity.branch(business_id,branch_code,branch_name) VALUES
    (comfy,'TX','TX'),(comfy,'TD','TD'),(comfy,'BBT','BBT'),
    (other,'MAIN','Main'),(other,'TX','TX'),(other,'DCN','DCN'),(no_shift,'MAIN','Main');
  INSERT INTO identity.branch(business_id,branch_code,branch_name,is_active) VALUES (other,'OFF','Off',false);
  INSERT INTO identity.shift_type(business_id,shift_code,shift_name,submit_deadline) VALUES
    (comfy,'MORNING_CLOSE','Morning','12:00'),(comfy,'EVENING_CLOSE','Evening','23:59'),
    (other,'AM','Morning','12:00');
  INSERT INTO identity.shift_type(business_id,shift_code,shift_name,submit_deadline,is_active)
    VALUES (comfy,'OFF','Off','23:59',false);

  -- Existing closes on excluded branches must remain untouched.
  INSERT INTO cashclose.cash_close(cash_close_code,business_id,branch_id,shift_type_id,business_date,status)
    SELECT 'EXISTING-DCN',comfy,b.branch_id,s.shift_type_id,CURRENT_DATE-1,'CREATED'
    FROM identity.branch b JOIN identity.shift_type s ON s.business_id=b.business_id
    WHERE b.business_id=comfy AND b.branch_code='DCN' AND s.shift_code='MORNING_CLOSE';

  created := cashclose.fn_create_daily_closes();
  IF created <> 9 THEN RAISE EXCEPTION 'Expected 9 new closes, got %',created; END IF;
  IF (SELECT count(*) FROM cashclose.cash_close WHERE business_id=comfy AND branch_id<>dcn) <> 6
    THEN RAISE EXCEPTION 'TX, TD and BBT must receive both active shifts'; END IF;
  IF EXISTS (SELECT 1 FROM cashclose.cash_close WHERE business_id=comfy AND branch_id=dcn AND cash_close_code<>'EXISTING-DCN')
    THEN RAISE EXCEPTION 'DCN received a new close'; END IF;
  IF NOT EXISTS (SELECT 1 FROM cashclose.cash_close WHERE cash_close_code='EXISTING-DCN')
    THEN RAISE EXCEPTION 'Existing close was removed'; END IF;
  IF (SELECT count(*) FROM cashclose.cash_close WHERE business_id=other) <> 3
    THEN RAISE EXCEPTION 'Other business must retain all active branches'; END IF;
  IF EXISTS (SELECT 1 FROM cashclose.cash_close WHERE business_id=no_shift)
    THEN RAISE EXCEPTION 'Business without shifts must receive no closes'; END IF;
  IF cashclose.fn_create_daily_closes() <> 0 THEN RAISE EXCEPTION 'Creation must be idempotent'; END IF;

  UPDATE identity.branch SET is_active=false WHERE branch_id=dcn;
  UPDATE identity.business SET is_active=false WHERE business_id=other;
  IF cashclose.fn_create_daily_closes() <> 0 THEN RAISE EXCEPTION 'Inactive branches/businesses must be skipped'; END IF;
END $test$;
ROLLBACK;
