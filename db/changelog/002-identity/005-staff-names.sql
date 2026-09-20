-- Split stored staff names while keeping existing employee codes and read-view outputs stable.
-- This data migration must read all businesses, despite FORCE RLS. Fail rather than
-- silently backfilling no rows or rebuilding an empty analytics cache.
DO $do$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_user AND (rolsuper OR rolbypassrls)) THEN
    RAISE EXCEPTION 'Staff-name migration requires an administrative migration role with BYPASSRLS or superuser';
  END IF;
END $do$;

ALTER TABLE identity.staff ADD COLUMN first_name TEXT, ADD COLUMN last_name TEXT;
-- Historical names have no reliable structural metadata. Use the first word and
-- the remaining words; a one-word name has an empty last_name for later correction.
UPDATE identity.staff
SET first_name = split_part(regexp_replace(btrim(full_name), '\s+', ' ', 'g'), ' ', 1),
    last_name = regexp_replace(btrim(full_name), '^\S+\s*', '');
ALTER TABLE identity.staff ALTER COLUMN first_name SET NOT NULL, ALTER COLUMN last_name SET NOT NULL;

CREATE OR REPLACE VIEW cashclose.v_cash_close_overview AS
SELECT cc.business_id,
       cc.cash_close_id,
       cc.cash_close_code,
       bu.business_code,
       br.branch_code, br.branch_name,
       st.shift_code,  st.shift_name,
       cc.business_date,
       btrim(u_creator.first_name || ' ' || u_creator.last_name) AS created_by_name,
       btrim(u_sub.first_name || ' ' || u_sub.last_name) AS submitted_by_name,
       cc.submitted_at,
       cc.pos_expected_cash, cc.expected_cash_source,
       k.counted_cash,
       k.cash_difference,
       k.explained_difference,
       k.pending_difference,
       k.unexplained_difference,
       k.expense_total,
       k.cash_out_total, k.cash_in_total,
       cc.withdrawal_amount,
       k.cash_remaining,
       k.tips_total, k.tips_in_drawer_total,
       cc.status,
       CASE
         WHEN cc.applied_diff_alert_abs IS NULL                             THEN NULL
         WHEN abs(k.unexplained_difference) > cc.applied_diff_alert_abs     THEN 'HIGH'
         WHEN abs(k.unexplained_difference) > cc.applied_diff_allowed_abs   THEN 'MEDIUM'
         ELSE 'LOW'
       END::shared.risk_level AS risk_level,
       cc.is_late,
       cc.calc_version,
       cc.applied_diff_allowed_abs, cc.applied_diff_alert_abs,
       ap.full_name AS approved_by_name,
       ap.acted_at  AS approved_at
FROM cashclose.cash_close cc
JOIN cashclose.v_close_calc k ON k.cash_close_id = cc.cash_close_id
JOIN identity.business   bu ON bu.business_id   = cc.business_id
JOIN identity.branch     br ON br.branch_id     = cc.branch_id
JOIN identity.shift_type st ON st.shift_type_id = cc.shift_type_id
LEFT JOIN identity.staff u_creator ON u_creator.staff_id = cc.created_by
LEFT JOIN identity.staff u_sub ON u_sub.staff_id = cc.submitted_by
LEFT JOIN LATERAL (
  SELECT btrim(u.first_name || ' ' || u.last_name) AS full_name, d.acted_at
  FROM cashclose.cash_close_decision d
  JOIN identity.staff u ON u.staff_id = d.acted_by
  WHERE d.cash_close_id = cc.cash_close_id AND d.new_status = 'APPROVED'
  ORDER BY d.acted_at DESC
  LIMIT 1
) ap ON true;

CREATE OR REPLACE VIEW cashclose.v_movement_decision_detail AS
SELECT d.business_id,
       m.cash_close_id,
       cc.cash_close_code,
       d.movement_id,
       d.decision_id,
       d.old_status, d.new_status,
       k.kind_code,
       d.signed_amount,
       abs(d.signed_amount) AS abs_amount,
       btrim(u.first_name || ' ' || u.last_name) AS decided_by_name,
       d.decided_at,
       d.note
FROM cashclose.cash_movement_decision d
JOIN cashclose.cash_movement m ON m.movement_id = d.movement_id
JOIN cashclose.cash_close    cc ON cc.cash_close_id = m.cash_close_id
JOIN platform.movement_kind  k ON k.kind_sk = d.kind_sk
LEFT JOIN identity.staff  u ON u.staff_id = d.decided_by;

CREATE OR REPLACE VIEW analytics.alert_unacknowledged
WITH (security_barrier = true) AS
SELECT a.alert_id, a.business_id, br.branch_code,
       a.source_module, a.alert_type, a.severity, a.message,
       a.channel, a.status,
       btrim(u_rcpt.first_name || ' ' || u_rcpt.last_name) AS recipient_name,
       btrim(u_subj.first_name || ' ' || u_subj.last_name) AS subject_name,
       a.created_at,
       now() - a.created_at AS age
FROM notify.alert a
LEFT JOIN identity.branch   br     ON br.branch_id      = a.branch_id
LEFT JOIN identity.staff u_rcpt ON u_rcpt.staff_id   = a.recipient_user_id
LEFT JOIN identity.staff u_subj ON u_subj.staff_id   = a.subject_user_id
WHERE a.acknowledged_at IS NULL
  AND a.severity IN ('HIGH','CRITICAL')
  AND a.business_id = shared.current_business_id();

-- Rebuild only the derived staff-risk cache. Rebind the existing security-barrier
-- wrapper in place so its grants and consumers survive. No CASCADE is used.

CREATE MATERIALIZED VIEW analytics.mv_staff_risk_30d_next AS
SELECT cc.business_id,
       cc.submitted_by                                        AS staff_id,
       btrim(u.first_name || ' ' || u.last_name) AS full_name,
       count(*)                                               AS close_count,
       count(*) FILTER (WHERE abs(k.unexplained_difference)
                              > cc.applied_diff_allowed_abs)   AS over_threshold_count,
       sum(abs(k.unexplained_difference))                      AS total_unexplained,
       -- A gap that leans consistently one way is more suspicious than one that
       -- balances out: a careless counter errs in both directions, someone
       -- skimming errs in only one.
       sum(k.unexplained_difference)                           AS net_unexplained,
       max(cc.business_date)                                   AS last_close_date
FROM cashclose.cash_close cc
JOIN cashclose.v_close_calc k ON k.cash_close_id = cc.cash_close_id
JOIN identity.staff u ON u.staff_id = cc.submitted_by
WHERE cc.status = 'APPROVED'
  AND cc.business_date >= current_date - INTERVAL '30 days'
GROUP BY cc.business_id, cc.submitted_by, u.first_name, u.last_name;

CREATE UNIQUE INDEX uq_mv_staff_risk_30d_next
  ON analytics.mv_staff_risk_30d_next (business_id, staff_id);
ALTER MATERIALIZED VIEW analytics.mv_staff_risk_30d_next OWNER TO analytics_refresher;
ALTER MATERIALIZED VIEW analytics.mv_staff_risk_30d RENAME TO mv_staff_risk_30d_old;
ALTER MATERIALIZED VIEW analytics.mv_staff_risk_30d_next RENAME TO mv_staff_risk_30d;
CREATE OR REPLACE VIEW analytics.staff_risk_30d
WITH (security_barrier = true) AS
SELECT * FROM analytics.mv_staff_risk_30d
WHERE business_id = shared.current_business_id();
DROP MATERIALIZED VIEW analytics.mv_staff_risk_30d_old;
ALTER INDEX analytics.uq_mv_staff_risk_30d_next RENAME TO uq_mv_staff_risk_30d;

ALTER TABLE identity.staff DROP COLUMN full_name;
