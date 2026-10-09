-- V7：核心交易链路数据库完整性约束（均经数据预检确认无违规数据）
-- ① 一课一单：每预约仅一个订单（并发下单兜底，服务层 if-exists 之外的最后防线）
CREATE UNIQUE INDEX ux_orders_booking ON orders (booking_id);
-- ② 同订单同渠道仅一笔进行中支付尝试
CREATE UNIQUE INDEX ux_payment_attempts_active ON payment_attempts (order_id, channel) WHERE status = 'INITIATED';
-- ③ 一订单一退款案件
CREATE UNIQUE INDEX ux_refund_cases_order ON refund_cases (order_id);
-- ④ 家庭钱包余额不允许为负（并发扣款数据库兜底）
ALTER TABLE family_wallets ADD CONSTRAINT ck_wallet_balance_nonneg CHECK (balance_cents >= 0);
