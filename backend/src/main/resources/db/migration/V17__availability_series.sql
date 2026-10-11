CREATE TABLE availability_series (
 id uuid PRIMARY KEY,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,
 teacher_id uuid NOT NULL REFERENCES accounts(id),time_zone varchar(80) NOT NULL,
 start_date date NOT NULL,start_time time NOT NULL,duration_minutes integer NOT NULL CHECK(duration_minutes BETWEEN 15 AND 180),
 week_count integer NOT NULL CHECK(week_count BETWEEN 1 AND 12)
);
CREATE INDEX ix_series_teacher ON availability_series(teacher_id,created_at DESC);
ALTER TABLE availability_slots ADD COLUMN series_id uuid REFERENCES availability_series(id);
CREATE INDEX ix_slot_series ON availability_slots(series_id);
