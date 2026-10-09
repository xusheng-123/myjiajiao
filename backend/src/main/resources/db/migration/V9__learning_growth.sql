-- V9: 学习过程与成长陪伴（学习成长中心 / 课后任务 / 学习档案 / 家长周报 / 课堂记录 / 课后答疑 / 教师收藏）

-- 课后任务（教师发布 → 学生提交 → 教师反馈；OVERDUE 为派生态：deadline 已过且仍 PENDING）
CREATE TABLE learning_tasks (
    id                   uuid PRIMARY KEY,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    booking_id           uuid        NOT NULL,
    teacher_id           uuid        NOT NULL,
    student_id           uuid        NOT NULL,
    title                varchar(120) NOT NULL,
    description          text,
    priority             varchar(12) NOT NULL DEFAULT 'NORMAL',
    deadline             timestamptz,
    status               varchar(16) NOT NULL DEFAULT 'PENDING',
    submission_content   text,
    submitted_at         timestamptz,
    feedback             text,
    feedback_at          timestamptz,
    CONSTRAINT ck_learning_tasks_status CHECK (status IN ('PENDING', 'SUBMITTED', 'COMPLETED')),
    CONSTRAINT ck_learning_tasks_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH'))
);
CREATE INDEX ix_ltask_student ON learning_tasks (student_id, status);
CREATE INDEX ix_ltask_teacher ON learning_tasks (teacher_id, created_at DESC);
CREATE INDEX ix_ltask_booking ON learning_tasks (booking_id);

-- 教师课堂记录（每节课一条，教师课后填写；学生/授权家长/后续教师可读）
CREATE TABLE lesson_records (
    id                uuid PRIMARY KEY,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    booking_id        uuid        NOT NULL,
    teacher_id        uuid        NOT NULL,
    student_id        uuid        NOT NULL,
    subject           varchar(60),
    knowledge_points  varchar(400),
    performance       varchar(400),
    problems          varchar(400),
    next_suggestion   varchar(400),
    note              text,
    CONSTRAINT ux_lesson_record_booking UNIQUE (booking_id)
);
CREATE INDEX ix_lrecord_student ON lesson_records (student_id, created_at DESC);
CREATE INDEX ix_lrecord_teacher ON lesson_records (teacher_id, created_at DESC);

-- 学习档案（学生维护的学习目标/阶段/目标科目；学习记录由课程与任务数据聚合）
CREATE TABLE learning_profiles (
    id               uuid PRIMARY KEY,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    student_id       uuid        NOT NULL,
    goal             varchar(200),
    stage            varchar(60),
    target_subjects  varchar(200),
    CONSTRAINT ux_learning_profile_student UNIQUE (student_id)
);

-- 课后答疑（学生 → 自己的教师；OPEN → ANSWERED → CLOSED）
CREATE TABLE questions (
    id            uuid PRIMARY KEY,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    student_id    uuid        NOT NULL,
    teacher_id    uuid        NOT NULL,
    subject       varchar(60) NOT NULL,
    title         varchar(120) NOT NULL,
    content       text        NOT NULL,
    status        varchar(12) NOT NULL DEFAULT 'OPEN',
    answer        text,
    answered_at   timestamptz,
    CONSTRAINT ck_questions_status CHECK (status IN ('OPEN', 'ANSWERED', 'CLOSED'))
);
CREATE INDEX ix_question_student ON questions (student_id, created_at DESC);
CREATE INDEX ix_question_teacher ON questions (teacher_id, status);

-- 教师收藏（学生收藏教师，用于对比与再次选择）
CREATE TABLE favorite_teachers (
    id           uuid PRIMARY KEY,
    created_at   timestamptz NOT NULL,
    student_id   uuid        NOT NULL,
    teacher_id   uuid        NOT NULL,
    CONSTRAINT ux_favorite UNIQUE (student_id, teacher_id)
);
CREATE INDEX ix_favorite_student ON favorite_teachers (student_id, created_at DESC);
