-- ============================================================================
-- 润物细无声 · Nurture — V3
-- 对照「学伴 StudyPlus PRD」差距补齐：
--   1) matching 域新增学生需求发布（找老师双入口之一）
--   2) classroom 域新增考勤状态（迟到/缺席恢复路径，PRD 10.1）
-- ============================================================================

-- 学生需求发布（「告诉平台我的需求」路径；与筛选检索并存，不强迫先走问卷）
CREATE TABLE student_demands (
    id                uuid PRIMARY KEY,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    student_id        uuid        NOT NULL,
    subject           varchar(32) NOT NULL,
    grade_band        varchar(32),
    note              varchar(1024),
    budget_max_cents  bigint,
    preferred_time    varchar(128),
    status            varchar(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'MATCHED', 'CLOSED'))
);
CREATE INDEX ix_demand_student ON student_demands (student_id, created_at);
CREATE INDEX ix_demand_open ON student_demands (status, created_at);

-- 课堂考勤：由导师在结课时标记，为退款/结算争议提供事实依据
ALTER TABLE classroom_sessions
    ADD COLUMN attendance_status varchar(16) NOT NULL DEFAULT 'UNKNOWN'
    CHECK (attendance_status IN ('UNKNOWN', 'PRESENT', 'LATE', 'ABSENT'));
