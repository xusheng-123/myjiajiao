-- ============================================================
-- E2E 历史数据清理（D9 后续 / 维护工具）
-- 用法：docker exec -i deploy-postgres-1 psql -U nurture -d nurture < deploy/sql/cleanup_e2e_data.sql
--
-- 范围（仅测试痕迹，不触碰真实业务数据）：
--   1) 未来 90 天之后才开始的档期/预约及其全部下游（E2E 随机远期抽样特征）
--   2) 测试专用账户（display_name 带 探针/限频测试/指纹测试/IT- 前缀）
--      及其名下档案、档期、预约与下游
-- 保留：核心测试账号 13900000001/2/3、13800000000 及其 90 天内的常规数据。
-- ============================================================

-- 0) 收集本轮要清理的预约与档期（远期 = start_at > now() + 90 天）
CREATE TEMP TABLE _e2e_slots AS
    SELECT id FROM availability_slots WHERE start_at > now() + interval '90 days';
CREATE TEMP TABLE _e2e_bookings AS
    SELECT b.id FROM bookings b
    JOIN availability_slots s ON s.id = b.slot_id
    WHERE s.start_at > now() + interval '90 days';

-- 1) 预约下游
DELETE FROM lesson_reminders      WHERE booking_id IN (SELECT id FROM _e2e_bookings);
DELETE FROM preparation_tasks     WHERE booking_id IN (SELECT id FROM _e2e_bookings);
DELETE FROM classroom_sessions    WHERE booking_id IN (SELECT id FROM _e2e_bookings);
DELETE FROM reviews               WHERE booking_id IN (SELECT id FROM _e2e_bookings);
DELETE FROM refund_cases          WHERE booking_id IN (SELECT id FROM _e2e_bookings);
DELETE FROM settlement_entries    WHERE booking_id IN (SELECT id FROM _e2e_bookings);

-- 订单及其支付尝试
DELETE FROM payment_attempts WHERE order_id IN (SELECT id FROM orders WHERE booking_id IN (SELECT id FROM _e2e_bookings));
DELETE FROM orders           WHERE booking_id IN (SELECT id FROM _e2e_bookings);

-- 钱包流水中与上述订单相关的记录（保留充值/退款等其余流水）
DELETE FROM wallet_transactions WHERE related_order_id IN (SELECT id FROM _e2e_bookings);

-- 2) 预约本体，然后档期
DELETE FROM bookings           WHERE id IN (SELECT id FROM _e2e_bookings);
DELETE FROM availability_slots WHERE id IN (SELECT id FROM _e2e_slots);

-- 3) 测试专用账户（探针/限频/指纹/IT 前缀）及其下游
CREATE TEMP TABLE _e2e_accounts AS
    SELECT id FROM accounts
    WHERE display_name LIKE '探针%' OR display_name LIKE '限频测试%'
       OR display_name LIKE '指纹测试%' OR display_name LIKE 'IT-%'
       OR (role = 'AGENT' AND display_name LIKE 'e2e-agent%');

CREATE TEMP TABLE _e2e_teacher_profiles AS
    SELECT id FROM teacher_profiles WHERE account_id IN (SELECT id FROM _e2e_accounts);
CREATE TEMP TABLE _e2e_student_profiles AS
    SELECT id FROM student_profiles WHERE account_id IN (SELECT id FROM _e2e_accounts);
CREATE TEMP TABLE _e2e_parent_profiles AS
    SELECT id FROM parent_profiles WHERE account_id IN (SELECT id FROM _e2e_accounts);

-- 名下档期与预约
CREATE TEMP TABLE _e2e_t_slots AS
    SELECT id FROM availability_slots WHERE teacher_id IN (SELECT id FROM _e2e_teacher_profiles);
CREATE TEMP TABLE _e2e_a_bookings AS
    SELECT id FROM bookings
    WHERE slot_id IN (SELECT id FROM _e2e_t_slots)
       OR student_id IN (SELECT id FROM _e2e_student_profiles)
       OR teacher_id IN (SELECT id FROM _e2e_teacher_profiles);

DELETE FROM lesson_reminders      WHERE booking_id IN (SELECT id FROM _e2e_a_bookings);
DELETE FROM preparation_tasks     WHERE booking_id IN (SELECT id FROM _e2e_a_bookings);
DELETE FROM classroom_sessions    WHERE booking_id IN (SELECT id FROM _e2e_a_bookings);
DELETE FROM reviews               WHERE booking_id IN (SELECT id FROM _e2e_a_bookings);
DELETE FROM refund_cases          WHERE booking_id IN (SELECT id FROM _e2e_a_bookings);
DELETE FROM settlement_entries    WHERE booking_id IN (SELECT id FROM _e2e_a_bookings);
DELETE FROM payment_attempts      WHERE order_id IN (SELECT id FROM orders WHERE booking_id IN (SELECT id FROM _e2e_a_bookings));
DELETE FROM orders                WHERE booking_id IN (SELECT id FROM _e2e_a_bookings);
DELETE FROM bookings              WHERE id IN (SELECT id FROM _e2e_a_bookings);
DELETE FROM availability_slots    WHERE id IN (SELECT id FROM _e2e_t_slots);

-- 钱包与档案
DELETE FROM wallet_transactions WHERE wallet_id IN (SELECT id FROM family_wallets WHERE parent_id IN (SELECT id FROM _e2e_parent_profiles));
DELETE FROM family_wallets      WHERE parent_id IN (SELECT id FROM _e2e_parent_profiles);
DELETE FROM guardian_bindings   WHERE parent_id IN (SELECT id FROM _e2e_parent_profiles) OR student_id IN (SELECT id FROM _e2e_student_profiles);
DELETE FROM subject_offerings   WHERE teacher_id IN (SELECT id FROM _e2e_teacher_profiles);
DELETE FROM verification_cases  WHERE teacher_id IN (SELECT id FROM _e2e_accounts);
DELETE FROM student_profiles    WHERE id IN (SELECT id FROM _e2e_student_profiles);
DELETE FROM teacher_profiles    WHERE id IN (SELECT id FROM _e2e_teacher_profiles);
DELETE FROM parent_profiles     WHERE id IN (SELECT id FROM _e2e_parent_profiles);

-- 账户本体（登录/令牌/通知）
DELETE FROM system_notices   WHERE recipient_id IN (SELECT id FROM _e2e_accounts);
DELETE FROM refresh_tokens   WHERE account_id IN (SELECT id FROM _e2e_accounts);
DELETE FROM api_tokens       WHERE owner_account_id IN (SELECT id FROM _e2e_accounts);
DELETE FROM accounts         WHERE id IN (SELECT id FROM _e2e_accounts);

-- 4) 汇总
SELECT
    (SELECT count(*) FROM availability_slots WHERE start_at > now() + interval '90 days') AS remaining_far_future_slots,
    (SELECT count(*) FROM accounts WHERE display_name LIKE '探针%' OR display_name LIKE 'IT-%') AS remaining_probe_accounts;
