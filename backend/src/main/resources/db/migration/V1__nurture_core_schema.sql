-- ============================================================================
-- 润物细无声 · Nurture — 核心Schema V1
-- PostgreSQL 是系统业务关系、交易订单、并发排课与操作审计的唯一核心事实源。
-- 结构唯一事实源 = 本迁移文件；JPA 侧 ddl-auto=validate 仅做校验。
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- 原生模糊检索（规范禁止 Elasticsearch）

-- ----------------------------------------------------------------------------
-- identity 身份认证域
-- ----------------------------------------------------------------------------
CREATE TABLE accounts (
    id              uuid PRIMARY KEY,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    role            varchar(16)  NOT NULL CHECK (role IN ('STUDENT', 'PARENT', 'TEACHER', 'ADMIN', 'AGENT')),
    account_no      varchar(24)  NOT NULL,
    phone           varchar(16)  NOT NULL,
    display_name    varchar(64)  NOT NULL,
    password_hash   varchar(100),
    status          varchar(16)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED', 'CLOSED')),
    CONSTRAINT ux_accounts_phone UNIQUE (phone),
    CONSTRAINT ux_accounts_account_no UNIQUE (account_no)
);

CREATE TABLE api_tokens (
    id                 uuid PRIMARY KEY,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    owner_account_id   uuid        NOT NULL,
    name               varchar(64) NOT NULL,
    token_hash         varchar(64) NOT NULL,          -- SHA-256(raw + salt)，明文永不落库
    salt               varchar(32) NOT NULL,
    scopes             varchar(512) NOT NULL,         -- 逗号分隔的最小权限 Scope 声明
    expires_at         timestamptz NOT NULL,          -- 绝对有效期
    revoked_at         timestamptz,                   -- 即时吊销
    last_used_at       timestamptz,
    CONSTRAINT ux_api_tokens_hash UNIQUE (token_hash)
);

CREATE TABLE refresh_tokens (
    id          uuid PRIMARY KEY,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    account_id  uuid        NOT NULL,
    token_hash  varchar(64) NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    CONSTRAINT ux_refresh_tokens_hash UNIQUE (token_hash)
);

-- ----------------------------------------------------------------------------
-- student 学生领域
-- ----------------------------------------------------------------------------
CREATE TABLE student_profiles (
    id                uuid PRIMARY KEY,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    account_id        uuid        NOT NULL,
    student_no        varchar(24) NOT NULL,
    grade_level       varchar(32),
    learning_style    varchar(256),
    focus_preference  varchar(256),
    CONSTRAINT ux_student_account UNIQUE (account_id)
);

CREATE TABLE student_preferred_subjects (
    student_id uuid NOT NULL REFERENCES student_profiles (id) ON DELETE CASCADE,
    subject    varchar(32) NOT NULL
);

CREATE TABLE guardian_bindings (
    id          uuid PRIMARY KEY,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    parent_id   uuid        NOT NULL,
    student_id  uuid        NOT NULL,
    status      varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
    relation    varchar(16),
    CONSTRAINT ux_guardian_pair UNIQUE (parent_id, student_id)
);
CREATE INDEX ix_guardian_student ON guardian_bindings (student_id);

-- ----------------------------------------------------------------------------
-- parent 家长领域（与学生实体物理分离）
-- ----------------------------------------------------------------------------
CREATE TABLE parent_profiles (
    id           uuid PRIMARY KEY,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    account_id   uuid        NOT NULL,
    parent_no    varchar(24) NOT NULL,
    contact_name varchar(64),
    CONSTRAINT ux_parent_account UNIQUE (account_id)
);

CREATE TABLE family_wallets (
    id             uuid PRIMARY KEY,
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    parent_id      uuid        NOT NULL,
    balance_cents  bigint      NOT NULL DEFAULT 0,
    currency       varchar(8)  NOT NULL DEFAULT 'CNY',
    version        bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ux_wallet_parent UNIQUE (parent_id)
);

CREATE TABLE wallet_transactions (
    id                   uuid PRIMARY KEY,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    wallet_id            uuid        NOT NULL,
    txn_type             varchar(16) NOT NULL CHECK (txn_type IN ('TOPUP', 'PAYMENT', 'REFUND', 'ADJUSTMENT')),
    amount_cents         bigint      NOT NULL,
    balance_after_cents  bigint      NOT NULL,
    related_order_id     uuid,
    memo                 varchar(256)
);
CREATE INDEX ix_wallet_txn_wallet ON wallet_transactions (wallet_id, created_at);
-- 同一订单可产生支付与退款两笔流水，故此处只建普通索引；幂等由支付域渠道引用唯一约束保障
CREATE INDEX ix_wallet_txn_related ON wallet_transactions (related_order_id) WHERE related_order_id IS NOT NULL;

-- ----------------------------------------------------------------------------
-- teacher 教师领域
-- ----------------------------------------------------------------------------
CREATE TABLE teacher_profiles (
    id                 uuid PRIMARY KEY,
    created_at         timestamptz NOT NULL,
    updated_at         timestamptz NOT NULL,
    account_id         uuid        NOT NULL,
    teacher_no         varchar(24) NOT NULL,
    school             varchar(128),
    highest_degree     varchar(32),
    years_of_teaching  integer,
    intro              varchar(1024),
    CONSTRAINT ux_teacher_account UNIQUE (account_id)
);
-- GIN 倒排全文检索（原生能力，禁止外部搜索引擎）
CREATE INDEX ix_teacher_intro_fts ON teacher_profiles USING gin (to_tsvector('simple', coalesce(intro, '')));
CREATE INDEX ix_teacher_school_trgm ON teacher_profiles USING gin (school gin_trgm_ops);

CREATE TABLE teacher_style_tags (
    teacher_id uuid NOT NULL REFERENCES teacher_profiles (id) ON DELETE CASCADE,
    style_tag  varchar(32) NOT NULL
);

CREATE TABLE subject_offerings (
    id                    uuid PRIMARY KEY,
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    teacher_id            uuid        NOT NULL,
    subject               varchar(32) NOT NULL,
    grade_band            varchar(32) NOT NULL,
    price_per_hour_cents  bigint      NOT NULL CHECK (price_per_hour_cents > 0),
    active                boolean     NOT NULL DEFAULT true
);
CREATE INDEX ix_offering_teacher ON subject_offerings (teacher_id);
CREATE INDEX ix_offering_subject ON subject_offerings (subject, grade_band);
CREATE INDEX ix_offering_subject_trgm ON subject_offerings USING gin (subject gin_trgm_ops);

-- ----------------------------------------------------------------------------
-- verification 资质合规域
-- ----------------------------------------------------------------------------
CREATE TABLE verification_cases (
    id               uuid PRIMARY KEY,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    teacher_id       uuid        NOT NULL,
    id_document_key  varchar(256),
    degree_cert_key  varchar(256),
    status           varchar(16) NOT NULL DEFAULT 'SUBMITTED'
                     CHECK (status IN ('SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'REJECTED')),
    reviewer_id      uuid,
    decision_note    varchar(512),
    submitted_at     timestamptz NOT NULL DEFAULT now(),
    decided_at       timestamptz
);
CREATE INDEX ix_verification_teacher ON verification_cases (teacher_id);
CREATE INDEX ix_verification_status ON verification_cases (status);

-- ----------------------------------------------------------------------------
-- scheduling 档期排定域
-- ----------------------------------------------------------------------------
CREATE TABLE availability_slots (
    id          uuid PRIMARY KEY,
    created_at  timestamptz NOT NULL,
    updated_at  timestamptz NOT NULL,
    teacher_id  uuid        NOT NULL,
    start_at    timestamptz NOT NULL,
    end_at      timestamptz NOT NULL CHECK (end_at > start_at),
    status      varchar(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'HELD', 'BOOKED', 'BLOCKED')),
    version     bigint      NOT NULL DEFAULT 0,
    CONSTRAINT ux_slot_teacher_time UNIQUE (teacher_id, start_at)
);
CREATE INDEX ix_slot_teacher_time_status ON availability_slots (teacher_id, start_at);

-- ----------------------------------------------------------------------------
-- booking 预约管理域
-- ----------------------------------------------------------------------------
CREATE TABLE bookings (
    id            uuid PRIMARY KEY,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    student_id    uuid        NOT NULL,
    teacher_id    uuid        NOT NULL,
    slot_id       uuid        NOT NULL,
    subject       varchar(32) NOT NULL,
    booking_type  varchar(16) NOT NULL CHECK (booking_type IN ('TRIAL', 'FORMAL')),
    status        varchar(16) NOT NULL DEFAULT 'REQUESTED'
                  CHECK (status IN ('REQUESTED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'CLOSED')),
    price_cents   bigint      NOT NULL,
    cancel_reason varchar(512)
);
CREATE INDEX ix_booking_student ON bookings (student_id, created_at);
CREATE INDEX ix_booking_teacher ON bookings (teacher_id, created_at);
-- 同一时段仅允许一个活跃预约（已取消/已关闭的让出时段）
CREATE UNIQUE INDEX ux_booking_active_slot ON bookings (slot_id)
    WHERE status IN ('REQUESTED', 'CONFIRMED', 'IN_PROGRESS');

-- ----------------------------------------------------------------------------
-- classroom 课堂协同域
-- ----------------------------------------------------------------------------
CREATE TABLE classroom_sessions (
    id                uuid PRIMARY KEY,
    created_at        timestamptz NOT NULL,
    updated_at        timestamptz NOT NULL,
    booking_id        uuid        NOT NULL,
    meeting_provider  varchar(32) NOT NULL,
    meeting_url       varchar(512) NOT NULL,
    join_credential   varchar(256),
    status            varchar(16) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'LIVE', 'ENDED')),
    started_at        timestamptz,
    ended_at          timestamptz,
    CONSTRAINT ux_session_booking UNIQUE (booking_id)
);

CREATE TABLE preparation_tasks (
    id            uuid PRIMARY KEY,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,
    booking_id    uuid        NOT NULL,
    title         varchar(64) NOT NULL,
    sort_index    integer     NOT NULL,
    completed_at  timestamptz
);
CREATE INDEX ix_prep_task_booking ON preparation_tasks (booking_id, sort_index);

-- ----------------------------------------------------------------------------
-- order 订单中心域
-- ----------------------------------------------------------------------------
CREATE TABLE orders (
    id                       uuid PRIMARY KEY,
    created_at               timestamptz NOT NULL,
    updated_at               timestamptz NOT NULL,
    order_no                 varchar(32) NOT NULL,
    booking_id               uuid        NOT NULL,
    payer_parent_id          uuid        NOT NULL,
    student_id               uuid        NOT NULL,
    teacher_id               uuid        NOT NULL,
    order_type               varchar(24) NOT NULL CHECK (order_type IN ('TRIAL_LESSON', 'FORMAL_LESSONS')),
    amount_cents             bigint      NOT NULL CHECK (amount_cents > 0),
    status                   varchar(24) NOT NULL DEFAULT 'PENDING_PAYMENT'
                             CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED', 'CLOSED')),
    reconciliation_snapshot  text,
    paid_at                  timestamptz,
    CONSTRAINT ux_orders_order_no UNIQUE (order_no)
);
CREATE INDEX ix_orders_booking ON orders (booking_id);
CREATE INDEX ix_orders_payer ON orders (payer_parent_id, created_at);
CREATE INDEX ix_orders_status ON orders (status);

-- ----------------------------------------------------------------------------
-- payment 支付网关域
-- ----------------------------------------------------------------------------
CREATE TABLE payment_attempts (
    id                    uuid PRIMARY KEY,
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    order_id              uuid        NOT NULL,
    channel               varchar(16) NOT NULL CHECK (channel IN ('MOCK', 'WECHAT', 'ALIPAY')),
    channel_ref           varchar(128),
    amount_cents          bigint      NOT NULL,
    status                varchar(16) NOT NULL DEFAULT 'INITIATED' CHECK (status IN ('INITIATED', 'SUCCEEDED', 'FAILED')),
    callback_payload_hash varchar(64)
);
CREATE INDEX ix_payment_order ON payment_attempts (order_id);
CREATE UNIQUE INDEX ux_payment_channel_ref ON payment_attempts (channel, channel_ref) WHERE channel_ref IS NOT NULL;

-- ----------------------------------------------------------------------------
-- refund 退款仲裁域
-- ----------------------------------------------------------------------------
CREATE TABLE refund_cases (
    id                    uuid PRIMARY KEY,
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    order_id              uuid        NOT NULL,
    requested_by          uuid        NOT NULL,
    reason                varchar(512) NOT NULL,
    computed_amount_cents bigint      NOT NULL DEFAULT 0,
    affected_lessons      integer,
    status                varchar(16) NOT NULL DEFAULT 'REQUESTED'
                          CHECK (status IN ('REQUESTED', 'EVALUATED', 'APPROVED', 'REJECTED', 'EXECUTED')),
    arbiter_id            uuid,
    decision_note         varchar(512)
);
CREATE INDEX ix_refund_order ON refund_cases (order_id);
CREATE INDEX ix_refund_status ON refund_cases (status);

-- ----------------------------------------------------------------------------
-- settlement 资金结算域
-- ----------------------------------------------------------------------------
CREATE TABLE settlement_entries (
    id                  uuid PRIMARY KEY,
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    teacher_id          uuid        NOT NULL,
    booking_id          uuid        NOT NULL,
    order_id            uuid        NOT NULL,
    gross_cents         bigint      NOT NULL,
    platform_fee_cents  bigint      NOT NULL CHECK (platform_fee_cents >= 0),
    net_cents           bigint      NOT NULL,
    status              varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SETTLED', 'PAID_OUT')),
    settled_at          timestamptz,
    CONSTRAINT ux_settlement_booking UNIQUE (booking_id)
);
CREATE INDEX ix_settlement_teacher ON settlement_entries (teacher_id, status);

-- ----------------------------------------------------------------------------
-- messaging 站内信令域
-- ----------------------------------------------------------------------------
CREATE TABLE conversations (
    id              uuid PRIMARY KEY,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    student_id      uuid        NOT NULL,
    teacher_id      uuid        NOT NULL,
    subject         varchar(64),
    last_message_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_conversation_student ON conversations (student_id, last_message_at);
CREATE INDEX ix_conversation_teacher ON conversations (teacher_id, last_message_at);

CREATE TABLE messages (
    id              uuid PRIMARY KEY,
    created_at      timestamptz NOT NULL,
    updated_at      timestamptz NOT NULL,
    conversation_id uuid        NOT NULL,
    sender_id       uuid,
    sender_role     varchar(16) NOT NULL CHECK (sender_role IN ('STUDENT', 'PARENT', 'TEACHER', 'ADMIN', 'SYSTEM')),
    body            text        NOT NULL,
    sent_at         timestamptz NOT NULL DEFAULT now(),
    read_at         timestamptz
);
CREATE INDEX ix_message_conversation ON messages (conversation_id, sent_at);

CREATE TABLE system_notices (
    id           uuid PRIMARY KEY,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    recipient_id uuid        NOT NULL,
    notice_type  varchar(32) NOT NULL,
    title        varchar(128) NOT NULL,
    body         text,
    read_at      timestamptz
);
CREATE INDEX ix_notice_recipient ON system_notices (recipient_id, created_at);

-- ----------------------------------------------------------------------------
-- review 评价反馈域
-- ----------------------------------------------------------------------------
CREATE TABLE reviews (
    id           uuid PRIMARY KEY,
    created_at   timestamptz NOT NULL,
    updated_at   timestamptz NOT NULL,
    booking_id   uuid        NOT NULL,
    reviewer_id  uuid        NOT NULL,
    reviewee_id  uuid        NOT NULL,
    direction    varchar(24) NOT NULL CHECK (direction IN ('STUDENT_TO_TEACHER', 'TEACHER_TO_STUDENT')),
    rating       integer     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment      varchar(1024),
    CONSTRAINT ux_review_booking_direction UNIQUE (booking_id, direction)
);
CREATE INDEX ix_review_reviewee ON reviews (reviewee_id);

CREATE TABLE review_tags (
    review_id uuid NOT NULL REFERENCES reviews (id) ON DELETE CASCADE,
    tag       varchar(32) NOT NULL
);

-- ----------------------------------------------------------------------------
-- admin 运营治理域
-- ----------------------------------------------------------------------------
CREATE TABLE platform_configs (
    config_key   varchar(64) PRIMARY KEY,
    config_value varchar(256) NOT NULL,
    description  varchar(256),
    updated_by   varchar(64),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

-- ----------------------------------------------------------------------------
-- shared 内核表：幂等防重 / 审计 / 两阶段确认 / 发件箱
-- ----------------------------------------------------------------------------

-- 幂等防重表（PostgreSQL 唯一索引兜底，防止智能体重试造成重复扣费或重复预约）
CREATE TABLE idempotency_keys (
    id                   uuid PRIMARY KEY,
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    caller_id            uuid        NOT NULL,
    http_method          varchar(8)  NOT NULL,
    request_uri          varchar(512) NOT NULL,
    idempotency_key      varchar(64) NOT NULL,
    request_fingerprint  varchar(64) NOT NULL,
    response_status      integer,
    response_snapshot    text
);
CREATE UNIQUE INDEX ux_idempotency_fingerprint
    ON idempotency_keys (caller_id, http_method, request_uri, idempotency_key);

-- 不可篡改审计日志（应用层只插入 + 数据库触发器拒绝 UPDATE/DELETE）
CREATE TABLE audit_logs (
    id               uuid PRIMARY KEY,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    caller_id        uuid,
    caller_role      varchar(16),
    service_account  varchar(128),
    occurred_at      timestamptz NOT NULL,
    http_method      varchar(8),
    request_uri      varchar(512),
    action_type      varchar(64) NOT NULL,
    payload_hash     varchar(64),
    execution_status varchar(16) NOT NULL,
    detail           text
);
CREATE INDEX ix_audit_caller ON audit_logs (caller_id, occurred_at);
CREATE INDEX ix_audit_action ON audit_logs (action_type, occurred_at);

CREATE OR REPLACE FUNCTION prevent_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs 不可篡改：拒绝 % 操作', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION prevent_audit_mutation();

-- 两阶段执行确认凭证
CREATE TABLE confirmation_grants (
    id               uuid PRIMARY KEY,
    created_at       timestamptz NOT NULL,
    updated_at       timestamptz NOT NULL,
    token_hash       varchar(64) NOT NULL,
    action_type      varchar(64) NOT NULL,
    aggregate_id     uuid        NOT NULL,
    caller_id        uuid        NOT NULL,
    issued_at        timestamptz NOT NULL DEFAULT now(),
    expires_at       timestamptz NOT NULL,
    consumed_at      timestamptz,
    preview_snapshot text,
    CONSTRAINT ux_confirmation_hash UNIQUE (token_hash)
);
CREATE INDEX ix_confirmation_action ON confirmation_grants (action_type, aggregate_id);

-- 事务发件箱（Transactional Outbox，替代 Kafka/RocketMQ）
CREATE TABLE outbox_messages (
    id             uuid PRIMARY KEY,
    created_at     timestamptz NOT NULL,
    updated_at     timestamptz NOT NULL,
    event_type     varchar(64) NOT NULL,
    aggregate_type varchar(32) NOT NULL,
    aggregate_id   uuid        NOT NULL,
    payload        text        NOT NULL,
    occurred_at    timestamptz NOT NULL DEFAULT now(),
    dispatched_at  timestamptz,
    attempts       integer     NOT NULL DEFAULT 0
);
CREATE INDEX ix_outbox_pending ON outbox_messages (dispatched_at, occurred_at);
