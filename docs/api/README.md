# API contracts

Hand-written OpenAPI contracts, written **before** the implementation (ADR-0010, ARC-06, issue #15). They are the reference that the code and the frontend are checked against.

| File | Content |
|---|---|
| [`common.yaml`](common.yaml) | Error body, session security scheme and responses shared by every service. |
| [`identity.yaml`](identity.yaml) | First-run setup, sessions, account administration and viewing profiles. |
| [`library.yaml`](library.yaml) | Folders, scans, catalogue, streaming with Range, favourites and progress. |
| [`acquisition.yaml`](acquisition.yaml) | The verified acquisition catalogue. Downloads and the handoff to the library are added with their issues (J5 to J7). |

Acquisition downloads and the handoff to the library, and the administration of catalogue metadata, are written in later pull requests, when their issues start.

## Conventions

- **OpenAPI 3.1.** Paths are those of the service. The gateway serves them under `/api/<service>` and strips the prefix (ADR-0017), which is each file's `servers` URL.
- **JSON** with `camelCase` property names. Enumerations in bodies are `UPPER_SNAKE` (`AVAILABLE`, `STARTED`); query parameter values are lower-case (`sort=recent`). Ids are UUIDs and timestamps are RFC 3339.
- **The interface is English only** ([ADR-0021](../adr/0021-english-only-interface.md)): a `message` is the text the user reads, in plain language and without technical details.
- **Errors** use `{ "error", "message", "fields"? }` ([ADR-0020](../adr/0020-common-error-format-and-outage-behavior.md), [error model](../architecture/error-model.md)). Every operation lists the errors it can return, and `503 service_unavailable` is possible everywhere.
- **Identity comes from the token, never from a parameter or path.** The account is the `sub` claim, the administrator flag is `admin`, and the active profile is `profileId` (R-06, PRF-04). A resource of another account answers `404`, the same as one that does not exist.
- **No file path is ever sent by a client**, except the administrator naming a folder to authorize (`POST /roots`). Media is addressed by id (LIB-08).
- **Lists** are plain arrays with `limit` (default 50, maximum 100) and `offset`.

## Session and token

The session is a signed token (RS256 JWT, [ADR-0009](../adr/0009-jwt-rs256-httponly-cookie.md)) in the `homeflix_token` cookie, `HttpOnly` and `SameSite=Strict`. It lasts about 15 minutes and is renewed with `POST /auth/refresh`; renewal fails for a disabled or deleted account, and there is no revocation list (R-10).

| Claim | Meaning |
|---|---|
| `sub` | Account id. |
| `admin` | `true` for an administrator. |
| `profileId` | Active profile id. Absent until `POST /profiles/{profileId}/select`, which re-issues the token with it. |
| `exp` | Expiry. |

Every service verifies the token itself with the identity service's public key. How the public key is distributed is decided in the identity design (issue #14).

## Permissions

`x-permission` on each operation uses four levels. The server enforces them (SEC-02); the interface only reflects them.

| Level | Who |
|---|---|
| `anonymous` | Anyone. |
| `account` | A signed-in account. |
| `profile` | A signed-in account with an active profile. Without one the answer is `403 profile_not_selected`. |
| `admin` | An administrator. A non-administrator gets `403 forbidden`. |

### Identity service

| Operation | Permission |
|---|---|
| `GET /setup/status` | anonymous |
| `POST /setup/admin` | anonymous (only while no administrator exists) |
| `POST /auth/sign-in` | anonymous |
| `POST /auth/sign-out` | anonymous |
| `POST /auth/refresh`, `GET /auth/me` | account |
| `GET /profiles`, `POST /profiles` | account |
| `DELETE /profiles/{profileId}`, `POST /profiles/{profileId}/select` | account (own profiles only) |

### Library service

| Operation | Permission |
|---|---|
| `GET /roots`, `POST /roots`, `DELETE /roots/{rootId}` | admin |
| `POST /scans` | admin |
| `GET /media`, `GET /media/{mediaId}`, `GET /categories`, `GET /genres` | account |
| `GET /media/{mediaId}/stream` | account |
| `GET /favourites`, `PUT` and `DELETE /favourites/{mediaId}` | profile |
| `GET /progress`, `GET`, `PUT` and `DELETE /progress/{mediaId}` | profile |

### Acquisition service

| Operation | Permission |
|---|---|
| `GET /entries`, `GET /entries/{entryId}` | admin |

Every acquisition operation is administrator only (ACQ-05). Entries are added by a reviewed migration, never through the API, and no operation accepts a URL, a magnet link or a torrent file (ACQ-09, ACQ-10, R-15).

## New error codes

These codes are introduced by the contracts and must be added to the [error model](../architecture/error-model.md). Their messages are the ones in the examples: the interface shows the server's `message` as written (ADR-0021).

| Code | Status | Where |
|---|---|---|
| `invalid_credentials` | 401 | Sign-in: unknown email, wrong password or disabled account (one answer, so accounts are not revealed). |
| `profile_limit_reached` | 409 | Creating a sixth profile. |
| `profile_name_taken` | 409 | A profile name already used in the account. |
| `profile_not_selected` | 403 | A profile operation without an active profile. |
| `root_already_added` | 409 | Authorizing a folder twice. |
| `root_overlaps` | 409 | Authorizing a folder that contains, or lies inside, an authorized folder. |
| `scan_in_progress` | 409 | A scan is already running. |
| `media_not_available` | 409 | Streaming a title that is still downloading. |
| `range_not_satisfiable` | 416 | A byte range outside the file, or several ranges. |

## Streaming and Range

`GET /media/{mediaId}/stream` accepts one byte range. Without `Range` it answers `200` with the whole file, with a valid range `206` and `Content-Range`, and with an invalid range `416` and `Content-Range: bytes */<size>`. Several ranges in one request are refused with `416`. This is a deliberate simplification: browsers' `<video>` elements ask for one range at a time.

## Changes this contract asks of existing pull requests

The contract follows the identity service's implementation (#125) where the frontend (#117) disagreed.

- **Frontend, identity** (`frontend/src/api/identity.ts`): `GET /api/identity/setup/status` returning `{setupRequired}`; `POST /api/identity/setup/admin` with `{email, password}`; sign-in with `email`; `POST /auth/sign-in` and `/auth/sign-out` (not `login` and `logout`); an account is `{id, email, admin, profileId}` with `admin` a boolean, not a role string.
- **Frontend, library** (`frontend/src/api/library.ts`): a title has `genres: string[]`, not `genre: string | null`, because a title can have several genres (data model).
- **Identity service (#125):** nothing changes for setup. Sign-in, refresh, profiles and selection are still to build (G3, G9).

## Open points

- **Removing a root** keeps its titles as `UNAVAILABLE`, so the migration for `media_file` must not delete rows when a root is removed (H4, issue #53).
- **Scan duration:** the scan runs inside the request, so the gateway's read timeout for `/api/library/scans` must be raised above the default when it is implemented.
- **Not yet specified:** administration of accounts (G8), catalogue metadata editing and removal (H8), acquisition downloads (J5, J6), handoff and deletion between services (issue #13).
