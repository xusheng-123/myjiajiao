-- Read-only; compatible with historical V10 and current V12. Amounts are cents.
BEGIN READ ONLY;
SELECT a.role AS payer_role, o.status, count(*) AS orders, sum(o.amount_cents) AS amount_cents
FROM orders o JOIN accounts a ON a.id=o.payer_parent_id
GROUP BY a.role,o.status ORDER BY a.role,o.status;

SELECT a.role AS wallet_owner_role, count(*) AS wallets, sum(w.balance_cents) AS balance_cents
FROM family_wallets w JOIN parent_profiles p ON p.id=w.parent_id JOIN accounts a ON a.id=p.account_id
GROUP BY a.role ORDER BY a.role;

SELECT count(*) AS wallet_ledger_mismatch_count
FROM family_wallets w LEFT JOIN (
  SELECT wallet_id,sum(amount_cents) AS total FROM wallet_transactions GROUP BY wallet_id
) t ON t.wallet_id=w.id WHERE w.balance_cents<>coalesce(t.total,0);

SELECT count(*) AS refund_wallet_owner_mismatch_count
FROM wallet_transactions t JOIN orders o ON o.id=t.related_order_id
LEFT JOIN family_wallets w ON w.id=t.wallet_id LEFT JOIN parent_profiles p ON p.id=w.parent_id
WHERE t.txn_type='REFUND' AND (p.account_id IS NULL OR p.account_id<>o.payer_parent_id);
COMMIT;
