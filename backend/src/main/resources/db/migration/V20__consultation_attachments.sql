CREATE TABLE consultation_files (
 id uuid PRIMARY KEY,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,
 conversation_id uuid NOT NULL REFERENCES conversations(id),owner_id uuid NOT NULL REFERENCES accounts(id),
 storage_key varchar(512) NOT NULL UNIQUE,filename varchar(180) NOT NULL,content_type varchar(100) NOT NULL,
 size_bytes bigint NOT NULL CHECK(size_bytes BETWEEN 1 AND 10485760),sha256 varchar(64) NOT NULL
);
CREATE INDEX ix_consultation_file ON consultation_files(conversation_id,created_at,id);
CREATE TABLE consultation_storage_quotas(owner_id uuid PRIMARY KEY REFERENCES accounts(id),stored_bytes bigint NOT NULL DEFAULT 0 CHECK(stored_bytes BETWEEN 0 AND 104857600));
ALTER TABLE messages ADD COLUMN attachment_id uuid REFERENCES consultation_files(id);
CREATE UNIQUE INDEX ux_message_attachment ON messages(attachment_id) WHERE attachment_id IS NOT NULL;
