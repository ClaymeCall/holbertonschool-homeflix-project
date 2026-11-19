-- Viewing profiles (PRF-01, PRF-04), owned by an account of this service.
--
-- A profile stores a name and nothing else (data model). Favourites and
-- viewing progress live in the library service, which keeps the profile id
-- as a plain value with no foreign key (ADR-0006, consistency rules).
--
-- Never edit this file after merge. Change the schema with a new migration
-- (ADR-0007).

CREATE TABLE profile (
    -- UUID: the id travels in the token (profileId claim) and in URLs, and
    -- must not be guessable. Ownership is still checked on every request.
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Deleting an account deletes its profiles (data model).
    account_id  uuid        NOT NULL REFERENCES account (id) ON DELETE CASCADE,

    -- Stored trimmed by the application. At most 5 profiles per account is
    -- checked by the service, which locks the account row first.
    name        varchar(30) NOT NULL,

    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT profile_name_unique    UNIQUE (account_id, name),
    CONSTRAINT profile_name_trimmed   CHECK (name = btrim(name)),
    CONSTRAINT profile_name_not_blank CHECK (name <> '')
);

COMMENT ON TABLE profile IS 'Viewing profiles. At most 5 per account; name unique within the account.';
