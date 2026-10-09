-- V8: 支付渠道枚举扩展 —— 新增 OFFLINE（运营线下收款确认，Release P0-3 模式 B）
-- 资金不经平台网关：运营核验到账后凭操作员权限落账，渠道标识 OFFLINE。
ALTER TABLE payment_attempts
    DROP CONSTRAINT payment_attempts_channel_check;
ALTER TABLE payment_attempts
    ADD CONSTRAINT payment_attempts_channel_check
    CHECK (channel IN ('MOCK', 'OFFLINE', 'WECHAT', 'ALIPAY'));
