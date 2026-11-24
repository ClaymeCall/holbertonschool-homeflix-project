-- A completed file that cannot be read (J7; docs/architecture/consistency-rules.md, "Time limit and visible
-- failure"). Set at the first status check that cannot read it. If it still cannot be read after
-- ACQUISITION_HANDOFF_TIMEOUT, the job fails with file_unreadable and the file is kept.
ALTER TABLE download_job ADD COLUMN unreadable_since timestamptz;
