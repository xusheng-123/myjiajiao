CREATE TABLE support_cases (
 id uuid PRIMARY KEY,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,
 owner_id uuid NOT NULL REFERENCES accounts(id),category varchar(16) NOT NULL,
 title varchar(120) NOT NULL,description varchar(4000) NOT NULL,booking_id uuid REFERENCES bookings(id),
 status varchar(20) NOT NULL DEFAULT 'OPEN',revision bigint NOT NULL DEFAULT 0 CHECK(revision>=0),
 CHECK(category IN ('SCHEDULE','PAYMENT','LEARNING','ACCOUNT','OTHER')),
 CHECK(status IN ('OPEN','IN_PROGRESS','WAITING_USER','RESOLVED','CLOSED'))
);
CREATE INDEX ix_support_owner ON support_cases(owner_id,updated_at DESC,id);
CREATE INDEX ix_support_status ON support_cases(status,updated_at DESC,id);
CREATE TABLE support_replies (
 id uuid PRIMARY KEY,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,
 case_id uuid NOT NULL REFERENCES support_cases(id),author_id uuid NOT NULL REFERENCES accounts(id),
 author_role varchar(16) NOT NULL,body varchar(4000) NOT NULL,kind varchar(16) NOT NULL,
 CHECK(kind IN ('REPLY','STATUS'))
);
CREATE INDEX ix_support_reply ON support_replies(case_id,created_at,id);
CREATE TABLE support_evidence (
 id uuid PRIMARY KEY,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,
 case_id uuid NOT NULL REFERENCES support_cases(id),owner_id uuid NOT NULL REFERENCES accounts(id),
 storage_key varchar(512) NOT NULL UNIQUE,filename varchar(180) NOT NULL,content_type varchar(100) NOT NULL,
 size_bytes bigint NOT NULL CHECK(size_bytes BETWEEN 1 AND 10485760),sha256 varchar(64) NOT NULL,deleted boolean NOT NULL DEFAULT false
);
CREATE INDEX ix_support_evidence ON support_evidence(case_id,created_at,id);
CREATE TABLE support_storage_quotas(owner_id uuid PRIMARY KEY REFERENCES accounts(id),stored_bytes bigint NOT NULL DEFAULT 0 CHECK(stored_bytes BETWEEN 0 AND 104857600));
