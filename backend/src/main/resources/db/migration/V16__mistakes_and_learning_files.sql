CREATE TABLE mistake_notes (
 id uuid PRIMARY KEY,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,
 student_id uuid NOT NULL REFERENCES accounts(id),creator_id uuid NOT NULL REFERENCES accounts(id),
 subject varchar(32) NOT NULL,title varchar(120) NOT NULL,question_text varchar(4000) NOT NULL,
 error_reason varchar(2000) NOT NULL,knowledge_tags varchar(300) NOT NULL,
 status varchar(16) NOT NULL CHECK(status IN('UNMASTERED','REVIEWING','MASTERED')),
 review_date date,revision bigint NOT NULL DEFAULT 0 CHECK(revision>=0),deleted boolean NOT NULL DEFAULT false,
 task_id uuid REFERENCES learning_tasks(id)
);
CREATE INDEX ix_mistake_student ON mistake_notes(student_id,deleted,created_at DESC);
CREATE TABLE learning_files (
 id uuid PRIMARY KEY,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,
 owner_id uuid NOT NULL REFERENCES accounts(id),student_id uuid NOT NULL REFERENCES accounts(id),
 teacher_id uuid NOT NULL REFERENCES accounts(id),booking_id uuid REFERENCES bookings(id),
 task_id uuid REFERENCES learning_tasks(id),question_id uuid REFERENCES questions(id),
 category varchar(24) NOT NULL CHECK(category IN('MATERIAL','TASK_MATERIAL','TASK_SUBMIT','QUESTION')),
 storage_key varchar(255) NOT NULL UNIQUE,original_filename varchar(180) NOT NULL,
 content_type varchar(120) NOT NULL,size_bytes bigint NOT NULL CHECK(size_bytes>0 AND size_bytes<=20971520),
 sha256 varchar(64) NOT NULL,deleted boolean NOT NULL DEFAULT false,
 CONSTRAINT ck_learning_file_context CHECK (
  (category='MATERIAL' AND booking_id IS NOT NULL AND task_id IS NULL AND question_id IS NULL) OR
  (category IN('TASK_MATERIAL','TASK_SUBMIT') AND task_id IS NOT NULL AND booking_id IS NULL AND question_id IS NULL) OR
  (category='QUESTION' AND question_id IS NOT NULL AND booking_id IS NULL AND task_id IS NULL))
);
CREATE INDEX ix_lfile_student ON learning_files(student_id,deleted,created_at DESC);
CREATE INDEX ix_lfile_teacher ON learning_files(teacher_id,deleted,created_at DESC);
CREATE INDEX ix_lfile_task ON learning_files(task_id,deleted);
CREATE INDEX ix_lfile_question ON learning_files(question_id,deleted);
CREATE TABLE learning_storage_quotas (
 owner_id uuid PRIMARY KEY REFERENCES accounts(id),stored_bytes bigint NOT NULL DEFAULT 0 CHECK(stored_bytes>=0)
);
