CREATE TABLE learning_assessments (
 id uuid PRIMARY KEY,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,
 booking_id uuid NOT NULL REFERENCES bookings(id),teacher_id uuid NOT NULL REFERENCES accounts(id),student_id uuid NOT NULL REFERENCES accounts(id),
 subject varchar(60) NOT NULL,title varchar(120) NOT NULL,scope varchar(1000) NOT NULL,questions_json text NOT NULL,
 status varchar(16) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','PUBLISHED','ARCHIVED')),
 revision bigint NOT NULL DEFAULT 0 CHECK(revision>=0)
);
CREATE INDEX ix_assessment_student ON learning_assessments(student_id,created_at DESC,id);
CREATE INDEX ix_assessment_teacher ON learning_assessments(teacher_id,student_id,created_at DESC,id);
CREATE TABLE assessment_attempts (
 id uuid PRIMARY KEY,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,
 assessment_id uuid NOT NULL UNIQUE REFERENCES learning_assessments(id),student_id uuid NOT NULL REFERENCES accounts(id),
 answers_json text NOT NULL,correct_count integer NOT NULL CHECK(correct_count>=0),total_count integer NOT NULL CHECK(total_count BETWEEN 1 AND 20),
 teacher_comment varchar(4000),comment_revision bigint NOT NULL DEFAULT 0 CHECK(comment_revision>=0),
 CHECK(correct_count<=total_count)
);
CREATE INDEX ix_attempt_student ON assessment_attempts(student_id,created_at DESC,id);
