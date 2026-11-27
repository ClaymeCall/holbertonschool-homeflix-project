# Security review: validation, injection, XSS, CSRF, secrets and exposed errors

Review of the whole project for the risks of issue K5 (#88; SEC-08, SEC-01, SEC-03, SEC-04, SEC-05; risks R-09, R-11, R-33; OWASP Top 10). The acceptance run (K8, #91) links to this page. Authorization and data isolation are in the [authorization review](authorization-review.md) (K4).

| | |
|---|---|
| Code reviewed | `main` at `c8dd742`, plus the tests added by K4 and by this review |
| Stack used for the live checks | Docker Compose, built from that code, on macOS with Docker Desktop |
| Reviewed by | Thomas Rousseau |
| Date | 2026-11-25 |

## Summary

| Area | Result |
|---|---|
| Input validation | Every endpoint validates its body, path and query before using them. Bad input gives `400` with the field named. |
| SQL injection | Every query uses bound parameters. No user input is ever concatenated into SQL. |
| XSS | No raw HTML anywhere in the frontend. Links and images built from stored data only accept `https`. A Content-Security-Policy restricts images. |
| CSRF | `SameSite=Strict` session cookie, JSON-only bodies, and a filter in each service that refuses state-changing requests from another site. Now tested in all three services. |
| Secrets | No secret in the repository or its history (gitleaks). `.env` and `secrets/` are ignored. |
| Exposed errors | Every error answer is the common JSON format, without stack traces, class names, paths or SQL. |

No defect that needs a code fix was found. Two points are recorded under Findings.

## Input validation

Each service validates what it receives before the operation runs. Request bodies use Bean Validation (`@Valid` with `@NotBlank`, `@Size`, `@Email`, `@NotNull`…); the other inputs are checked in code. Path identifiers are typed `UUID`, so a malformed one never reaches the code. A body that is not JSON, or not of the expected types, is refused with `400 invalid_request`, and a form-encoded body with `415`.

| Service | Input | Validation | Test |
|---|---|---|---|
| identity | Setup, sign-in, new account: e-mail and password | `@NotBlank`, `@Email`, `@Size(max = 254)`; password length within BCrypt's 72-byte limit | `FirstAdministratorSetupTests`, `SessionTests.passwordOverTheBcryptLimitIsRejectedAsInvalidCredentials`, `AccountManagementTests` |
| identity | Profile name | `@NotBlank`, `@Size(max)` | `ProfileTests` |
| identity | Role change | `@NotNull` boolean | `AccountManagementTests.roleRequestWithoutTheRoleIsRejected` |
| identity | `GET /accounts` `limit`, `offset` | 1 to 100, not negative, numbers | `AccountManagementTests` (0, 101, -1, `abc`) |
| library | Root folder | `@NotBlank`, then inside a mounted path after following links, no `..`, a readable folder | `RootValidatorTest`, `RootEndpointsTests` |
| library | Title edit | `@NotBlank` title, `@Size` title and description, duration not negative, poster `https` on an allowed host | `MediaAdminEndpointsTests.badValuesAreRefusedWithTheFieldNamedAndNothingIsSaved`, `PosterPolicyTest` |
| library | `GET /media` `query`, `genre`, `sort`, `limit`, `offset` | Sort from a fixed list, paging bounds, numbers | `CatalogueEndpointsTests.badParametersAreRefusedWithTheFieldNamed` |
| library | Progress position and duration | Present, whole numbers, position within duration | `ProfileDataEndpointTests.aBadPositionOrDurationIsRefusedAndNamesTheField` |
| library | Progress list filter and paging | State from a fixed list, paging bounds | `ProfileDataEndpointTests.aBadFilterOrPageIsRefused` |
| library | `Range` header of the stream | One range only, within the file | `RangeRequestTest`, `StreamEndpointTests` |
| library | `/internal` title, relative path, profile id | `@NotBlank`, `@Size`; the path must stay inside the downloads folder; a malformed id is refused | `DownloadEndpointsTests`, `ProfileDataDeletionTests.aMalformedIdIsRefusedForTheSecretBeforeItIsRejectedAsUnknown` |
| acquisition | Start a download | `@NotNull` catalogue entry id. There is no URL or magnet field: only a verified entry can be downloaded (ACQ-02) | `DownloadEndpointsTests` |

Unknown JSON fields are ignored, and no request record carries a field the caller must not set: a user cannot give itself a role or a profile through a body (`AuthenticationAndAuthorizationTests.userCannotClaimTheAdministratorRole`, `ProfileIsolationWithSignedTokensTests.profileNamedInTheRequestInsteadOfTheTokenIsIgnored`).

## SQL injection

The services use `JdbcTemplate` with `?` placeholders for every value. Where a query is assembled, only constants are joined: the column lists (`COLUMNS`), and in the catalogue search (`CatalogueRepository`) fixed `and …` clauses and an `order by` picked by a `switch` over the sort enum. The search text and the genre are bound parameters (`strpos(lower(m.title), lower(?))`). No ORM query language or native query string is built from input.

Checked by searching `services/*/src/main` for SQL built with `+`, `String.format` or `StringBuilder`.

## Cross-site scripting (R-33)

- **No raw HTML.** The frontend has no `dangerouslySetInnerHTML`, `innerHTML`, `document.write`, `eval` or `new Function`. React escapes every text it renders, including titles and descriptions that come from file names, torrent names or administrators.
- **Links and images from stored data.** Three places use a stored URL. All of them only accept `https`:
  - posters (`<img src>`): `PosterPolicy` accepts only `https` on `POSTER_ALLOWED_HOSTS` (ADR-0018);
  - the source and licence links of a catalogue entry (`DownloadsPage`): the database refuses anything but `https` (`AcquisitionEntryMigrationTests.sourceAndLicencePagesMustBeHttps`), and the links open with `rel="noopener noreferrer"`.
- **Session cookie.** `HttpOnly`, so a script cannot read it.
- **Headers on every answer of the gateway:** `Content-Security-Policy: img-src 'self' https://upload.wikimedia.org` (built at start from `POSTER_ALLOWED_HOSTS`), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: same-origin`. Checked on a page and on an API answer of the running stack, and by `gateway/tests/check-gateway.sh` in CI.

## Cross-site request forgery (R-09)

ADR-0009 asks for this review of state-changing endpoints. Three layers:

1. **`SameSite=Strict`** on the session cookie: the browser does not send it with a request started by another site.
2. **JSON-only bodies.** Every body is read as `application/json`. A plain HTML form cannot send that, and a form-encoded body is refused with `415`.
3. **`CrossSiteRequestFilter`** in each service: a `POST`, `PUT` or `DELETE` that the browser marks `Sec-Fetch-Site: cross-site` or `same-site` is refused with `403 forbidden` before anything runs. HomeFlix pages are on one origin and send `same-origin`.

No `GET` operation changes anything (checked in every controller), so the safe methods need no protection.

Tests:

- identity: `SessionTests.crossSiteStateChangingRequestIsRefused`, `AccountManagementTests.crossSiteRequestIsRefused`, `AccountDeletionTests.anonymousAndCrossSiteRequestsAreRefused`, `AuthenticationAndAuthorizationTests.crossSiteAdministratorRequestIsRefused`.
- library and acquisition: **`CrossSiteRequestTests`, added by this review.** The filter was in both services but untested there. The test finds every state-changing endpoint by scanning the application (13 in the library, 2 in acquisition) and checks that a `cross-site` or `same-site` request is refused with `403 forbidden` even with a valid administrator session, while `same-origin` and `none` are let through to the guard.

Live check through the gateway, without a session:

| Request | `Sec-Fetch-Site: cross-site` | `same-origin` |
|---|---|---|
| identity `POST /profiles` | 403 | 400 (empty body) |
| identity `POST /auth/sign-out` | 403 | 204 |
| library `POST /scans` | 403 | 401 |
| library `PUT /favourites/{id}` | 403 | 401 |
| library `DELETE /media/{id}` | 403 | 401 |
| acquisition `POST /downloads` | 403 | 401 |
| acquisition `POST /downloads/{id}/cancel` | 403 | 401 |

## Secrets (R-11)

- `.env` and `secrets/` are in `.gitignore`. `.env.example` holds names and generation commands, no values.
- The only key files in the repository are `test-jwt-public.pem` in the library and acquisition tests: public keys whose private half was discarded, so no valid token can be made with them.
- GitHub secret scanning and push protection are enabled on the repository (R-11).
- Settings and request objects never print a secret (`PasswordAndSecretsTests`, `SettingsTests.secretsAreNeverPrinted`).
- **Scan of the whole history** with gitleaks: see below.

Command, run on 2026-11-25 on every branch of the local clone (values are redacted in the report):

```sh
docker run --rm -v "$PWD:/repo:ro" zricethezav/gitleaks:v8.21.2 git /repo --redact --no-banner
```

Result: 201 commits scanned (merge commits have no diff of their own), 6 findings, all false positives:

| Rule | File | What it is |
|---|---|---|
| `private-key` (×5) | identity `SessionKeys.java`, `session/TestKeys.java`, `session/SessionKeysTests.java` | The PEM header and footer strings `-----BEGIN PRIVATE KEY-----` in code. `SessionKeys` strips them from the configured key; the tests wrap a key generated during the test, or the dummy body `abc`. No key is written in the repository. |
| `generic-api-key` (×1) | identity `PasswordAndSecretsTests.java` | The test password constant `Sup3r-Secret-Passphrase`, used against a test database. |

On macOS with the repository in iCloud Drive, files of `.git` that iCloud has offloaded make the scan very slow (9 minutes here). Scanning a fresh clone outside iCloud avoids it.

## Exposed errors (SEC-04)

Every service answers errors in the common format `{"error": "...", "message": "..."}` (ADR-0020). An unexpected error is `500 internal_error` with a generic message; a dependency or database outage is `503 service_unavailable` (`ApiExceptionHandlerTest` in each service: `anUnexpectedErrorIs500WithoutTechnicalDetails`, `aDatabaseThatIsDownOrTooSlowIs503WithoutDetails`, `malformedJsonGivesInvalidRequestWithoutDetails`). The gateway never shows the services' operational endpoints (`/actuator`).

Live check through the gateway:

| Request | Answer |
|---|---|
| Sign-in with malformed JSON | `400 invalid_request` "The request body is missing or is not valid JSON." |
| Sign-in with wrong field types | `400 invalid_request`, same message |
| Sign-in form-encoded | `415 request_rejected` "This request cannot be processed." |
| `DELETE /api/identity/setup/status` (wrong method) | `405 request_rejected` |
| `GET /api/library/media/not-a-uuid` | `404 not_found` "This address does not exist." |
| `GET /api/library/nowhere`, `GET /api/unknown/x` | `404 not_found` |
| `GET /api/acquisition/actuator/env` | `404 not_found` |
| `GET /api/library/media?limit=abc` | `400 validation_failed`, field `limit`: "Enter a whole number." |

None of them contains a stack trace, a class name, a file path or SQL.

## Findings

| # | Finding | Decision |
|---|---|---|
| 1 | The cross-site filter of the library and acquisition services had no test (only identity's had one). | Fixed: `CrossSiteRequestTests` in both services. |
| 2 | In the library and acquisition services, a PostgreSQL constraint error can carry the failing row's values in the exception and the logs. Answers to clients are not affected: they get the generic `500` message. | Already tracked by #158 (`logServerErrorDetail=false`, done in identity by #148). |
| 3 | The Content-Security-Policy only restricts images (`img-src`). A fuller policy (`default-src 'self'`, `script-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'`) would add a second line of defence against XSS. | Not needed for R-33, which asks for image hosts only, and there is no raw HTML to exploit. Recommended for a later version, with a check of every page. |
| 4 | A query parameter of the wrong type (`GET /media?limit=abc`) is refused with `400` before the guard answers `401`, because Spring converts parameters before the method runs. | No data is exposed. Left as is. |
