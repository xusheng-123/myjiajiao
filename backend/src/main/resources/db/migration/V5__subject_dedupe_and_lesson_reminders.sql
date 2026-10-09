-- V5：数据一致性约束 + 课程临近提醒（D9 / D12）
--
-- D9：同导师+科目+学段的在售开课条目唯一（服务层幂等 + 部分唯一索引双重保障）。
--     先归并历史重复数据（仅保留最早一条在售），再加部分唯一索引。
UPDATE subject_offerings o SET active = false
WHERE o.active = true
  AND o.id <> (
      SELECT keep.id FROM subject_offerings keep
      WHERE keep.active = true
        AND keep.teacher_id = o.teacher_id
        AND keep.subject = o.subject
        AND keep.grade_band = o.grade_band
      ORDER BY keep.id
      LIMIT 1
  );

CREATE UNIQUE INDEX ux_subject_offerings_active_key
    ON subject_offerings (teacher_id, subject, grade_band) WHERE active = true;

-- D12：课程临近提醒去重登记——同一预约同一时段只提醒一次；
-- 提醒内容经站内通知（system_notices）触达，「已处理」复用通知已读状态。
CREATE TABLE lesson_reminders (
    id              uuid PRIMARY KEY,
    booking_id      uuid NOT NULL,
    slot_id         uuid NOT NULL,
    lesson_start_at timestamptz NOT NULL,
    reminded_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_lesson_reminders_booking_slot UNIQUE (booking_id, slot_id)
);
CREATE INDEX ix_lesson_reminders_start ON lesson_reminders (lesson_start_at);
