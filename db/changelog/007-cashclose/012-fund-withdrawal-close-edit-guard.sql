CREATE OR REPLACE FUNCTION cashclose.fn_fund_decision_guard() RETURNS trigger LANGUAGE plpgsql AS $fn$
DECLARE w cashclose.fund_withdrawal%ROWTYPE; c cashclose.cash_close%ROWTYPE; prior_time TIMESTAMPTZ;
        before_data JSONB; after_data JSONB; amount_value NUMERIC; person_value UUID; time_value TIMESTAMPTZ;
BEGIN
  SELECT * INTO w FROM cashclose.fund_withdrawal
    WHERE fund_withdrawal_id=NEW.fund_withdrawal_id AND business_id=NEW.business_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Withdrawal not found'; END IF;
  -- Same lock order as Java and cash-close correction/approval.
  IF w.cash_close_id IS NOT NULL THEN
    SELECT * INTO c FROM cashclose.cash_close WHERE cash_close_id=w.cash_close_id FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Close not found'; END IF;
    IF c.status NOT IN ('SUBMITTED','PENDING_REVIEW') THEN
      RAISE EXCEPTION 'Close is frozen';
    END IF;
  END IF;
  SELECT * INTO w FROM cashclose.fund_withdrawal WHERE fund_withdrawal_id=NEW.fund_withdrawal_id FOR UPDATE;
  IF NEW.acted_by IS DISTINCT FROM shared.current_user_id() THEN
    RAISE EXCEPTION 'Withdrawal decision must identify the authenticated actor';
  END IF;
  IF NEW.old_status IS DISTINCT FROM w.status THEN
    RAISE EXCEPTION 'Withdrawal has changed since this decision was prepared';
  END IF;
  before_data := cashclose.fn_fund_snapshot(w);
  IF NEW.action IN ('CONFIRM','REJECT') THEN
    IF NEW.acted_by IS DISTINCT FROM w.withdrawn_by THEN
      RAISE EXCEPTION 'Only the named withdrawing person may decide';
    END IF;
    after_data := before_data; -- exact values this person acknowledged
  ELSIF NEW.action='EDIT' THEN
    IF NEW.changes IS NULL OR NOT (NEW.changes ?& ARRAY['before','after'])
        OR NOT ((NEW.changes->'before') ?& ARRAY['amount','withdrawnBy','withdrawnAt','note'])
        OR NOT ((NEW.changes->'after') ?& ARRAY['amount','withdrawnBy','withdrawnAt','note']) THEN
      RAISE EXCEPTION 'An edit requires complete before/after withdrawal values';
    END IF;
    IF ROW((NEW.changes->'before'->>'amount')::NUMERIC,(NEW.changes->'before'->>'withdrawnBy')::UUID,
           (NEW.changes->'before'->>'withdrawnAt')::TIMESTAMPTZ,NEW.changes->'before'->>'note')
       IS DISTINCT FROM ROW(w.amount,w.withdrawn_by,w.withdrawn_at,w.note) THEN
      RAISE EXCEPTION 'Withdrawal edit before values do not match the current record';
    END IF;
    amount_value := (NEW.changes->'after'->>'amount')::NUMERIC;
    person_value := (NEW.changes->'after'->>'withdrawnBy')::UUID;
    time_value := (NEW.changes->'after'->>'withdrawnAt')::TIMESTAMPTZ;
    IF amount_value IS NULL OR amount_value<0 OR amount_value>999999999999.99 OR amount_value<>round(amount_value,2)
        OR person_value IS NULL OR time_value IS NULL OR time_value>clock_timestamp() THEN
      RAISE EXCEPTION 'Invalid corrected withdrawal values';
    END IF;
    after_data := jsonb_build_object('amount',amount_value,'withdrawnBy',person_value,
                    'withdrawnAt',time_value,'note',NEW.changes->'after'->>'note');
  ELSE RAISE EXCEPTION 'Unknown withdrawal decision action';
  END IF;
  NEW.changes := jsonb_build_object('before',before_data,'after',after_data);
  -- Row locking plus monotonic decision times gives a deterministic history order.
  SELECT max(acted_at) INTO prior_time FROM cashclose.fund_withdrawal_decision
    WHERE fund_withdrawal_id=w.fund_withdrawal_id;
  NEW.acted_at := greatest(clock_timestamp(),prior_time+interval '1 microsecond');
  RETURN NEW;
END $fn$;
