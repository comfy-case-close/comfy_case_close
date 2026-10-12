-- Human-readable codes are reused only when the earlier close was voided.
-- The existing live branch/shift/day uniqueness remains in force.
ALTER TABLE cashclose.cash_close DROP CONSTRAINT cash_close_business_id_cash_close_code_key;
CREATE UNIQUE INDEX uq_close_code_live ON cashclose.cash_close(business_id,cash_close_code)
  WHERE status <> 'VOIDED';

-- Apply the same format to scheduled creation. Existing rows retain their code.
CREATE OR REPLACE FUNCTION cashclose.fn_create_daily_closes() RETURNS INTEGER
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $fn$
DECLARE v_created INTEGER;
BEGIN
  INSERT INTO cashclose.cash_close(cash_close_code,business_id,branch_id,shift_type_id,business_date,status)
  SELECT 'CC-' || to_char(day.business_date, 'YYYYMMDD') || '-' || upper(btrim(branch.branch_code))
         || '-' || btrim(split_part(upper(btrim(shift.shift_code)), '_', 1)),
         business.business_id, branch.branch_id, shift.shift_type_id, day.business_date, 'CREATED'::shared.close_status
  FROM identity.business business
  JOIN identity.branch branch ON branch.business_id=business.business_id AND branch.is_active
  JOIN identity.shift_type shift ON shift.business_id=business.business_id AND shift.is_active
  CROSS JOIN LATERAL (SELECT (CURRENT_TIMESTAMP AT TIME ZONE business.timezone)::date AS business_date) day
  WHERE business.is_active AND NOT EXISTS (
    SELECT 1 FROM cashclose.cash_close existing
    WHERE existing.branch_id=branch.branch_id AND existing.shift_type_id=shift.shift_type_id
      AND existing.business_date=day.business_date
  )
  ORDER BY business.business_id, branch.branch_id, shift.shift_type_id
  ON CONFLICT (branch_id,shift_type_id,business_date) WHERE status <> 'VOIDED' DO NOTHING;
  GET DIAGNOSTICS v_created = ROW_COUNT;
  RETURN v_created;
END $fn$;
