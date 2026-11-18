-- Download jobs (ACQ-03, ACQ-04, ACQ-05; docs/architecture/data-model.md, "Download jobs";
-- docs/architecture/consistency-rules.md, "Acquisition handoff"). J5 starts and cancels them, J6 tracks
-- their progress and J7 hands completed files to the library.

CREATE TABLE download_job (
    id                      uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    entry_id                uuid          NOT NULL REFERENCES acquisition_entry (id),
    -- Info hash (v1) given by the torrent client when the torrent is added. Null only if adding it failed.
    client_hash             varchar(40),
    state                   varchar(20)   NOT NULL,
    progress_percent        smallint      NOT NULL DEFAULT 0,
    error_code              varchar(50),
    completed_relative_path varchar(4096),
    -- Account id of the administrator who started it: a plain uuid of the identity service (ADR-0006).
    started_by              uuid          NOT NULL,
    created_at              timestamptz   NOT NULL DEFAULT now(),
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    finished_at             timestamptz,
    -- Null while a notification to the library is pending (consistency rules, "Failing and cancelling").
    library_synced_at       timestamptz,

    CONSTRAINT download_job_state_valid       CHECK (state IN ('QUEUED', 'DOWNLOADING', 'COMPLETED', 'FAILED')),
    CONSTRAINT download_job_progress_range    CHECK (progress_percent BETWEEN 0 AND 100),
    CONSTRAINT download_job_hash_format       CHECK (client_hash IS NULL OR client_hash ~ '^[0-9a-f]{40}$'),
    -- A failed job always says why (cancelled, client_error, handoff_failed, verification_failed).
    CONSTRAINT download_job_failed_has_reason CHECK ((state = 'FAILED') = (error_code IS NOT NULL)),
    CONSTRAINT download_job_finished_when_done CHECK ((state IN ('COMPLETED', 'FAILED')) = (finished_at IS NOT NULL)),
    CONSTRAINT download_job_path_relative     CHECK (completed_relative_path IS NULL
        OR (left(completed_relative_path, 1) <> '/' AND completed_relative_path !~ '(^|/)\.\.(/|$)'))
);

-- At most one active job per entry: a second start while one is queued or downloading is refused.
CREATE UNIQUE INDEX download_job_one_active_per_entry ON download_job (entry_id)
    WHERE state IN ('QUEUED', 'DOWNLOADING');

-- The same entry always gives the same torrent, so a hash is unique among active jobs only: an entry
-- downloaded again after a cancel or a failure has a new job with the same hash.
CREATE UNIQUE INDEX download_job_one_active_per_hash ON download_job (client_hash)
    WHERE state IN ('QUEUED', 'DOWNLOADING');

CREATE INDEX download_job_entry_idx ON download_job (entry_id);

CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER download_job_set_updated_at BEFORE UPDATE ON download_job
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE download_job IS 'One download of a verified catalogue entry. A cancelled job is FAILED with error_code cancelled.';
COMMENT ON COLUMN download_job.started_by IS 'Plain value: an account of the identity service. No foreign key (ADR-0006), no email or name (SEC-06).';
