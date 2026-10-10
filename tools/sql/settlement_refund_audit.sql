-- Read only. Requires V11+. Amounts are cents. Never use cached report totals for correction.
WITH refunded AS (
    SELECT o.id, o.order_no, o.status, o.amount_cents,
           count(*) FILTER (WHERE r.status = 'EXECUTED') AS executed_cases,
           coalesce(sum(r.computed_amount_cents) FILTER (WHERE r.status = 'EXECUTED'), 0) AS refunded_cents
    FROM orders o JOIN refund_cases r ON r.order_id = o.id
    GROUP BY o.id
)
SELECT f.order_no, f.id AS order_id, e.id AS earning_id, e.status AS earning_status,
       e.net_cents, f.refunded_cents,
       CASE
         WHEN e.id IS NULL THEN 'C_MISSING_EARNING'
         WHEN f.executed_cases <> 1 OR f.refunded_cents <> f.amount_cents THEN 'C_REFUND_MISMATCH'
         WHEN v.id IS NULL AND e.status = 'PENDING' AND f.status = 'CLOSED' THEN 'A_LEGACY_UNREVERSED'
         WHEN v.id IS NULL THEN 'B_MANUAL_ACCOUNTING_REQUIRED'
         WHEN v.net_cents <> -e.net_cents OR v.gross_cents <> -e.gross_cents OR v.platform_fee_cents <> 0
           OR v.order_id <> e.order_id OR v.teacher_id <> e.teacher_id OR v.booking_id <> e.booking_id THEN 'D_REVERSAL_MISMATCH'
         ELSE 'OK_REVERSED'
       END AS category
FROM refunded f
LEFT JOIN settlement_entries e ON e.order_id = f.id AND e.entry_kind = 'EARNING'
LEFT JOIN settlement_entries v ON v.reverses_entry_id = e.id AND v.entry_kind = 'REVERSAL'
WHERE f.executed_cases > 0
ORDER BY category, f.order_no;

-- Refund accounting must reconcile with the immutable wallet ledger, not just the case status.
WITH expected AS (
    SELECT order_id, sum(computed_amount_cents) AS expected_cents
    FROM refund_cases WHERE status = 'EXECUTED' GROUP BY order_id
), actual AS (
    SELECT related_order_id AS order_id, sum(amount_cents) AS actual_cents, count(*) AS credits
    FROM wallet_transactions WHERE txn_type = 'REFUND' GROUP BY related_order_id
)
SELECT e.order_id, e.expected_cents, coalesce(a.actual_cents, 0) AS actual_cents, coalesce(a.credits, 0) AS credits
FROM expected e LEFT JOIN actual a USING (order_id)
WHERE e.expected_cents <> coalesce(a.actual_cents, 0) OR coalesce(a.credits, 0) <> 1;
