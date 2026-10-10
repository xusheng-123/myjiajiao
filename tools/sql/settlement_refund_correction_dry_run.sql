-- TEST DATABASE ONLY; always rolled back. Not an authorization to alter historical business records.
\set ON_ERROR_STOP on
BEGIN;
DO $$ BEGIN
    IF current_database() <> 'nurture_test' AND current_database() NOT LIKE 'nurture_gap_acceptance_%' THEN
        RAISE EXCEPTION 'Correction rehearsal is restricted to an isolated test database';
    END IF;
END $$;

-- Lock original entries. All original amounts and statuses remain unchanged.
SELECT e.id FROM settlement_entries e JOIN orders o ON o.id = e.order_id
WHERE e.entry_kind = 'EARNING' AND e.status = 'PENDING' AND o.status = 'CLOSED'
  AND EXISTS (SELECT 1 FROM refund_cases r WHERE r.order_id = o.id AND r.status = 'EXECUTED')
FOR UPDATE OF e, o;

WITH eligible AS (
    SELECT e.* FROM settlement_entries e JOIN orders o ON o.id = e.order_id
    WHERE e.entry_kind = 'EARNING' AND e.status = 'PENDING' AND o.status = 'CLOSED'
      AND (SELECT count(*) FROM refund_cases r WHERE r.order_id = o.id AND r.status = 'EXECUTED') = 1
      AND (SELECT sum(computed_amount_cents) FROM refund_cases r WHERE r.order_id = o.id AND r.status = 'EXECUTED') = o.amount_cents
      AND (SELECT count(*) FROM wallet_transactions w WHERE w.related_order_id = o.id AND w.txn_type = 'REFUND') = 1
      AND (SELECT sum(amount_cents) FROM wallet_transactions w WHERE w.related_order_id = o.id AND w.txn_type = 'REFUND') = o.amount_cents
      AND NOT EXISTS (SELECT 1 FROM settlement_entries v WHERE v.reverses_entry_id = e.id)
), corrected AS (
    INSERT INTO settlement_entries (id, created_at, updated_at, teacher_id, booking_id, order_id,
        gross_cents, platform_fee_cents, net_cents, status, entry_kind, reverses_entry_id)
    SELECT gen_random_uuid(), now(), now(), teacher_id, booking_id, order_id,
        -gross_cents, 0, -net_cents, 'PENDING', 'REVERSAL', id FROM eligible
    ON CONFLICT (reverses_entry_id) WHERE entry_kind = 'REVERSAL' DO NOTHING
    RETURNING *
)
INSERT INTO audit_logs (id, created_at, updated_at, caller_role, occurred_at, action_type, payload_hash, execution_status, detail)
SELECT gen_random_uuid(), now(), now(), 'SYSTEM', now(), 'settlement:historical-test-correction',
    encode(sha256(reverses_entry_id::text::bytea), 'hex'), 'SUCCESS',
    'TEST rehearsal only; order=' || order_id || '; original=' || reverses_entry_id || '; netCents=' || net_cents
FROM corrected;

SELECT count(*) AS reversals_inside_rehearsal FROM settlement_entries WHERE entry_kind = 'REVERSAL';
ROLLBACK;
