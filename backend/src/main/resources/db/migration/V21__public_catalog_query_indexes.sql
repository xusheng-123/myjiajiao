-- Read-projection access paths only; existing rows and trading rules remain unchanged.
CREATE INDEX ix_verification_latest_catalog ON verification_cases(teacher_id,submitted_at DESC,id DESC);
CREATE INDEX ix_reviews_catalog_stats ON reviews(reviewee_id) INCLUDE (rating);
CREATE INDEX ix_slots_catalog_open ON availability_slots(teacher_id,start_at) WHERE status='OPEN';
CREATE INDEX ix_offerings_catalog_price ON subject_offerings(price_per_hour_cents,teacher_id,subject,grade_band) WHERE active=true;
