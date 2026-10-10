-- 保留正向收益，追加全额退款红冲；历史金额不回写。
ALTER TABLE settlement_entries ADD COLUMN entry_kind varchar(16) NOT NULL DEFAULT 'EARNING';
ALTER TABLE settlement_entries ADD COLUMN reverses_entry_id uuid REFERENCES settlement_entries(id);
ALTER TABLE settlement_entries ADD CONSTRAINT ck_settlement_entry_kind
    CHECK (entry_kind IN ('EARNING', 'REVERSAL'));
ALTER TABLE settlement_entries ADD CONSTRAINT ck_settlement_reversal_shape CHECK (
    (entry_kind = 'EARNING' AND reverses_entry_id IS NULL) OR
    (entry_kind = 'REVERSAL' AND reverses_entry_id IS NOT NULL
     AND gross_cents <= 0 AND net_cents <= 0 AND platform_fee_cents = 0)
);
ALTER TABLE settlement_entries DROP CONSTRAINT ux_settlement_booking;
CREATE UNIQUE INDEX ux_settlement_booking_earning ON settlement_entries(booking_id)
    WHERE entry_kind = 'EARNING';
CREATE UNIQUE INDEX ux_settlement_reversal ON settlement_entries(reverses_entry_id)
    WHERE entry_kind = 'REVERSAL';
