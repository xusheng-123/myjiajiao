-- ============================================================================
-- 润物细无声 · Nurture — 参考数据 V2
-- 运营治理全局参数；初始运营管理员由 BootstrapAdminRunner 启动时按环境变量创建。
-- ============================================================================

INSERT INTO platform_configs (config_key, config_value, description) VALUES
    ('settlement.platform-fee-rate', '0.15', '平台课酬抽成比例（0~1）'),
    ('booking.trial-price-cents', '0', '试听课默认定价（分），0 表示免费'),
    ('booking.cancel-deadline-hours', '2', '开课前多少小时内不可自助取消'),
    ('matching.max-result-size', '20', '找老师单次推荐上限'),
    ('messaging.max-message-length', '2000', '站内消息正文长度上限')
ON CONFLICT (config_key) DO NOTHING;
