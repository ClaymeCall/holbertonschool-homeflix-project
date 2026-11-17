-- Library service tables: authorized folders, files, catalogue entries, genres,
-- favourites and viewing progress (LIB-09, ARC-04, CAT-01, CAT-10).
-- Design: docs/architecture/data-model.md.
--
-- A title is split in two so a re-index never overwrites an administrator's edits:
--   media_file: owned by the indexer (where the file is, whether it is there);
--   media_item: the catalogue entry (title, description, poster, duration, genres).
--
-- No foreign key points to another service's tables (ADR-0006). profile_id and
-- acquisition_entry_id are plain uuid values.
--
-- Never edit this file after merge. Change the schema with a new migration (ADR-0007).

-- Authorized media folders, set by an administrator (LIB-01, LIB-08).
CREATE TABLE media_root (
    id         uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    path       varchar(4096) NOT NULL,
    created_at timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT media_root_path_unique    UNIQUE (path),
    CONSTRAINT media_root_path_not_blank CHECK (btrim(path) <> '')
);

-- Files found by the indexer. The key (root_id, relative_path) is what makes
-- scans idempotent (LIB-05): a repeated scan updates last_seen_at, not adds a row.
CREATE TABLE media_file (
    id            uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    root_id       uuid          NOT NULL REFERENCES media_root (id) ON DELETE CASCADE,
    -- Path inside the root, never absolute.
    relative_path varchar(4096) NOT NULL,
    size_bytes    bigint        NOT NULL,
    last_seen_at  timestamptz   NOT NULL DEFAULT now(),
    -- Set when a scan no longer finds the file. The row stays (LIB-07).
    missing_since timestamptz,

    CONSTRAINT media_file_path_unique       UNIQUE (root_id, relative_path),
    CONSTRAINT media_file_path_relative     CHECK (btrim(relative_path) <> '' AND left(relative_path, 1) <> '/'),
    CONSTRAINT media_file_size_not_negative CHECK (size_bytes >= 0)
);

-- The catalogue entry. Only title is required, so a title with no description,
-- poster, genre or duration is still representable (CAT-10).
CREATE TABLE media_item (
    id                   uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Null while the title is downloading, or after its file was removed.
    -- ON DELETE SET NULL keeps the entry, its favourites and its progress when a
    -- root is removed (LIB-07). The service marks the item UNAVAILABLE first.
    media_file_id        uuid          REFERENCES media_file (id) ON DELETE SET NULL,
    -- The acquisition catalogue entry this title is downloaded from. A plain value:
    -- no foreign key across services. Unique, so a repeated notification does not
    -- create a second item.
    acquisition_entry_id uuid,
    title                varchar(500)  NOT NULL,
    description          text,
    -- https only (ADR-0018). The host is checked against the allowed list by the
    -- application when the URL is saved. The server never fetches it.
    poster_url           varchar(2048),
    duration_seconds     integer,
    -- AVAILABLE: the file is present. UNAVAILABLE: deleted or inaccessible, the entry
    -- keeps its information. DOWNLOADING: no file yet. Written by the indexer and by
    -- download notifications, not by an administrator.
    availability         varchar(20)   NOT NULL,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_at           timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT media_item_file_unique       UNIQUE (media_file_id),
    CONSTRAINT media_item_acquisition_unique UNIQUE (acquisition_entry_id),
    CONSTRAINT media_item_title_not_blank   CHECK (btrim(title) <> ''),
    CONSTRAINT media_item_poster_https      CHECK (poster_url IS NULL OR poster_url LIKE 'https://%'),
    CONSTRAINT media_item_duration_positive CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    CONSTRAINT media_item_availability_valid CHECK (availability IN ('AVAILABLE', 'UNAVAILABLE', 'DOWNLOADING')),
    -- An available title has a file; a downloading one does not have one yet.
    CONSTRAINT media_item_available_has_file CHECK (availability <> 'AVAILABLE' OR media_file_id IS NOT NULL),
    CONSTRAINT media_item_downloading_no_file CHECK (availability <> 'DOWNLOADING' OR media_file_id IS NULL)
);

CREATE INDEX media_item_created_at_idx ON media_item (created_at DESC);
CREATE INDEX media_item_title_lower_idx ON media_item (lower(title));
CREATE INDEX media_item_availability_idx ON media_item (availability);

-- Controlled list of genres (CAT-02, CAT-04). Case-insensitive, so Drama and drama
-- are one genre.
CREATE TABLE genre (
    id   uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    name varchar(50) NOT NULL,

    CONSTRAINT genre_name_trimmed CHECK (name = btrim(name) AND name <> '')
);

CREATE UNIQUE INDEX genre_name_lower_unique ON genre (lower(name));

-- A title can have several genres.
CREATE TABLE media_item_genre (
    media_item_id uuid NOT NULL REFERENCES media_item (id) ON DELETE CASCADE,
    genre_id      uuid NOT NULL REFERENCES genre (id) ON DELETE CASCADE,

    PRIMARY KEY (media_item_id, genre_id)
);

CREATE INDEX media_item_genre_genre_idx ON media_item_genre (genre_id);

-- "My List", per profile (CAT-08, PRF-03). profile_id belongs to the identity service
-- and is a plain value: the library cannot tell whether the profile still exists.
-- Removing a profile's rows is a consistency rule between services (issue #13).
CREATE TABLE favourite (
    profile_id    uuid        NOT NULL,
    media_item_id uuid        NOT NULL REFERENCES media_item (id) ON DELETE CASCADE,
    added_at      timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (profile_id, media_item_id)
);

CREATE INDEX favourite_media_item_idx ON favourite (media_item_id);

-- Viewing progress, one row per profile and title (PLY-07 to PLY-09). Started and
-- completed are computed from the row, not stored, so no flag can disagree with the
-- position. Reset deletes the row.
CREATE TABLE progress (
    profile_id       uuid        NOT NULL,
    media_item_id    uuid        NOT NULL REFERENCES media_item (id) ON DELETE CASCADE,
    position_seconds integer     NOT NULL,
    duration_seconds integer     NOT NULL,
    updated_at       timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (profile_id, media_item_id),
    CONSTRAINT progress_position_valid CHECK (position_seconds >= 0 AND position_seconds <= duration_seconds),
    CONSTRAINT progress_duration_positive CHECK (duration_seconds > 0)
);

CREATE INDEX progress_media_item_idx ON progress (media_item_id);
-- "Continue watching": a profile's most recently watched titles.
CREATE INDEX progress_profile_updated_idx ON progress (profile_id, updated_at DESC);

COMMENT ON TABLE media_item IS 'Catalogue entry. Catalogue fields are edited by administrators; availability is written by the indexer and download notifications.';
COMMENT ON COLUMN media_item.availability IS 'AVAILABLE, UNAVAILABLE or DOWNLOADING (CAT-01).';
COMMENT ON COLUMN favourite.profile_id IS 'Plain value: a profile of the identity service. No foreign key (ADR-0006).';
COMMENT ON COLUMN progress.profile_id IS 'Plain value: a profile of the identity service. No foreign key (ADR-0006).';
COMMENT ON COLUMN media_item.acquisition_entry_id IS 'Plain value: an entry of the acquisition service. No foreign key (ADR-0006).';
