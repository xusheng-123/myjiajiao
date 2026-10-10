-- One free trial per student/teacher; preserve all historical bookings unchanged.
CREATE TABLE trial_entitlements (
    id uuid PRIMARY KEY,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    student_id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    consumed boolean NOT NULL DEFAULT false,
    current_booking_id uuid REFERENCES bookings(id),
    CONSTRAINT ux_trial_pair UNIQUE(student_id,teacher_id)
);

WITH history AS (
    SELECT student_id,teacher_id,
           bool_or(status IN ('IN_PROGRESS','COMPLETED','CLOSED')) AS consumed
    FROM bookings WHERE booking_type='TRIAL' GROUP BY student_id,teacher_id
), active AS (
    SELECT DISTINCT ON(student_id,teacher_id) student_id,teacher_id,id
    FROM bookings WHERE booking_type='TRIAL' AND status IN ('REQUESTED','CONFIRMED')
    ORDER BY student_id,teacher_id,created_at DESC,id
)
INSERT INTO trial_entitlements(id,created_at,updated_at,student_id,teacher_id,consumed,current_booking_id)
SELECT gen_random_uuid(),now(),now(),h.student_id,h.teacher_id,h.consumed,a.id
FROM history h LEFT JOIN active a USING(student_id,teacher_id);
