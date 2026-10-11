-- Read-projection access paths only; existing rows and trading rules remain unchanged.
-- Enables BitmapOr over every keyword-prefilter branch of the public catalog search
-- (school/subject/display_name/grade_band), so selective keywords drive from trigram
-- indexes instead of a full offering scan.
CREATE INDEX ix_accounts_display_name_trgm ON accounts USING gin (display_name gin_trgm_ops);
CREATE INDEX ix_offerings_grade_band_trgm ON subject_offerings USING gin (grade_band gin_trgm_ops);
