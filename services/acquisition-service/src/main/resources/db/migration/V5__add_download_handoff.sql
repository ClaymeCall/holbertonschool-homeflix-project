-- Completed-file checks and handoff to the library (J7; docs/architecture/consistency-rules.md, "Completing"
-- and "Time limit and visible failure").

-- When the file passed the checks and the first handoff call was made: after ACQUISITION_HANDOFF_TIMEOUT
-- without an answer from the library, the job fails with handoff_failed.
ALTER TABLE download_job ADD COLUMN handoff_started_at timestamptz;

-- A completed job always says which file was handed over, and a file is only recorded once it is verified.
ALTER TABLE download_job ADD CONSTRAINT download_job_completed_has_path
    CHECK (state <> 'COMPLETED' OR completed_relative_path IS NOT NULL);
ALTER TABLE download_job ADD CONSTRAINT download_job_handoff_has_path
    CHECK ((handoff_started_at IS NULL) = (completed_relative_path IS NULL));
