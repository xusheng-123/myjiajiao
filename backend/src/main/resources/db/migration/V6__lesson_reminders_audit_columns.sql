-- V6：补齐 lesson_reminders 的审计时间戳列（AggregateRoot @CreatedDate/@LastModifiedDate 校验需要）
ALTER TABLE lesson_reminders
    ADD COLUMN created_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();
