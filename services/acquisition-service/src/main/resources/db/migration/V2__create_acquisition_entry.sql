-- The verified acquisition catalogue (ACQ-01, ACQ-02, R-15, R-19; docs/architecture/data-model.md).
-- Entries are added only by a migration like this one, reviewed in a pull request. No endpoint writes
-- this table, so a user can never submit a URL or a magnet link (ACQ-09, ACQ-10).
-- Download jobs get their own table with the downloads (J5, J6).

CREATE TABLE acquisition_entry (
    id                  uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    title               varchar(500)  NOT NULL,
    source_name         varchar(200)  NOT NULL,
    source_url          varchar(2048) NOT NULL,
    licence             varchar(200)  NOT NULL,
    licence_url         varchar(2048) NOT NULL,
    attribution         varchar(1000),
    download_source     varchar(4096) NOT NULL,
    expected_size_bytes bigint        NOT NULL,
    expected_format     varchar(10)   NOT NULL,
    created_at          timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT acquisition_entry_title_not_blank       CHECK (btrim(title) <> ''),
    CONSTRAINT acquisition_entry_source_name_not_blank CHECK (btrim(source_name) <> ''),
    CONSTRAINT acquisition_entry_licence_not_blank     CHECK (btrim(licence) <> ''),
    CONSTRAINT acquisition_entry_attribution_not_blank CHECK (attribution IS NULL OR btrim(attribution) <> ''),
    -- The source and the licence must be pages anyone can check (ACQ-02).
    CONSTRAINT acquisition_entry_source_url_https      CHECK (source_url LIKE 'https://%'),
    CONSTRAINT acquisition_entry_licence_url_https     CHECK (licence_url LIKE 'https://%'),
    -- A magnet link with a v1 info hash, or an https address of a .torrent file. Nothing else.
    CONSTRAINT acquisition_entry_download_source_valid CHECK (
        download_source ~ '^magnet:\?xt=urn:btih:[0-9a-fA-F]{40}(&|$)'
        OR download_source ~ '^https://[^[:space:]]+\.torrent$'),
    -- Checked against the completed file (R-19, J7).
    CONSTRAINT acquisition_entry_size_positive         CHECK (expected_size_bytes > 0),
    -- The only supported format (ADR-0015).
    CONSTRAINT acquisition_entry_format_valid          CHECK (expected_format = 'MP4'),
    CONSTRAINT acquisition_entry_download_source_unique UNIQUE (download_source)
);

CREATE UNIQUE INDEX acquisition_entry_title_lower_unique ON acquisition_entry (lower(title));

COMMENT ON TABLE acquisition_entry IS 'Verified acquisition catalogue. Written by reviewed migrations only, never by an endpoint (ACQ-09).';
COMMENT ON COLUMN acquisition_entry.download_source IS 'Magnet link or https .torrent address chosen by the team. Never returned by the API, never from a user (R-15).';
COMMENT ON COLUMN acquisition_entry.expected_size_bytes IS 'Size of the completed file, checked before the handoff to the library (R-19).';
