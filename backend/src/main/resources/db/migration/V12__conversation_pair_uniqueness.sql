-- Preserve all messages while consolidating historical duplicate participant pairs.
WITH ranked AS (
    SELECT id, first_value(id) OVER (PARTITION BY student_id, teacher_id ORDER BY last_message_at DESC, id) AS keeper
    FROM conversations
)
UPDATE messages m SET conversation_id = r.keeper
FROM ranked r WHERE m.conversation_id = r.id AND r.id <> r.keeper;

WITH ranked AS (
    SELECT id, row_number() OVER (PARTITION BY student_id, teacher_id ORDER BY last_message_at DESC, id) AS position
    FROM conversations
)
DELETE FROM conversations c USING ranked r WHERE c.id = r.id AND r.position > 1;

CREATE UNIQUE INDEX ux_conversation_pair ON conversations (student_id, teacher_id);
CREATE INDEX ix_message_unread ON messages (conversation_id, sender_id) WHERE read_at IS NULL;
