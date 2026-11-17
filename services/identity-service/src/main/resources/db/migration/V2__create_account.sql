-- Account table for the identity service (ACC-05, ARC-04, ARC-11).
--
-- Roles: two roles only, administrator and user, stored as the is_admin
-- column (requirements.md, open question 3). No separate role table.
--
-- No foreign key points to another service's tables (ADR-0006).
-- No account is inserted here: the first administrator is created through
-- the setup flow, never as a default account (ACC-01, ACC-02).
--
-- Never edit this file after merge. Change the schema with a new migration
-- (ADR-0007).

CREATE TABLE account (
    -- UUID rather than a sequence: the id travels in tokens and requests to
    -- other services, and must not be guessable or enumerable (PRF-04).
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Sign-in identifier. The application stores it trimmed and lower-case,
    -- so a plain unique constraint is enough to make it case-insensitive.
    email         varchar(254) NOT NULL,

    -- BCrypt hash (R-08), never the plain password (ACC-04). BCrypt output is
    -- 60 characters; the wider column leaves room for a future algorithm.
    password_hash varchar(255) NOT NULL,

    -- Defaults to false, so a forgotten value never grants admin rights.
    is_admin      boolean      NOT NULL DEFAULT false,

    -- A disabled account cannot sign in or renew its token (ACC-10, R-10).
    disabled      boolean      NOT NULL DEFAULT false,

    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT account_email_unique     UNIQUE (email),
    CONSTRAINT account_email_normalized CHECK (email = lower(btrim(email))),
    CONSTRAINT account_email_not_blank  CHECK (email <> ''),
    CONSTRAINT account_password_hash_not_blank CHECK (password_hash <> '')
);

COMMENT ON TABLE account IS 'Sign-in accounts. Role is is_admin (administrator or user).';
COMMENT ON COLUMN account.is_admin IS 'true = administrator, false = user.';
COMMENT ON COLUMN account.disabled IS 'A disabled account cannot sign in or renew its token (ACC-10, R-10).';
