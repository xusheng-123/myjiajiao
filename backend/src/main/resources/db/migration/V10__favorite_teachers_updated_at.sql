-- V10: favorite_teachers 补 updated_at（AggregateRoot @LastModifiedDate 必需列）
ALTER TABLE favorite_teachers ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();
