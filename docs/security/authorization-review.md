# Server-side authorization and data isolation review

Audit of every operation of every service: forbidden operations are refused even when the interface is bypassed, and no service reads another service's data (issue K4 #87; SEC-02, SEC-10, ACC-06, PLY-04, ACQ-05, AS-07; risks R-06, R-12). The acceptance run (K8, #91) links to this page.

| | |
|---|---|
| Code reviewed | `main` at `c8dd742`, plus the two tests added by this review |
| Stack used for the manual checks | Docker Compose, built from that code, on macOS with Docker Desktop |
| Reviewed by | Thomas Rousseau |
| Date | 2026-11-25 |

## How each service decides

No service trusts the gateway or the interface. Each one reads the caller from the signed session cookie and checks it itself (ADR-0009, ADR-0013):

- **identity** and **library**: every operation calls `AccessGuard.requireAccount`, `requireAdmin` or `requireProfile` first. A `profile` operation takes the profile from the token, never from the request.
- **acquisition**: every controller is `@AdminOnly`, checked by an interceptor before the operation runs.
- **`/internal` operations of the library** (ADR-0022): `ServiceTokenGuard` checks the caller's own secret in `X-Service-Token`. A session cookie is never accepted there, and each secret opens only its own caller's operations. The gateway does not route `/internal` at all.

The answers: `401 unauthenticated` without a valid identity, `403 forbidden` for a user on an administrator operation, `403 profile_not_selected` for profile data without a profile in the token.

## Every endpoint

46 operations in the three contracts (`docs/api/*.yaml`), plus the two endpoints of the service-to-service demo. The table was checked against the code: the endpoints found by scanning each application are exactly these.

Short names used for the tests that prove a refusal:

- **Identity scan**: `AuthenticationAndAuthorizationTests.everyEndpointOutsideTheAnonymousListRefusesAnonymousRequests`. It finds every endpoint by scanning the application and expects `401` without a session, except on the anonymous ones.
- **Library scan** and **Acquisition scan**: `EndpointPermissionTests` in each service, added by this review. Same scan, with real signed tokens, a valid body on every request so the refusal comes from the guard, and every role the contract does not allow (see below). The same scan also sends each endpoint as its allowed caller and expects neither `401` nor `403`, so a guard that refused everyone would fail too (checked by making `requireAdmin` refuse every caller: the six admin endpoints failed).

### Identity service

| Operation | Permission | Refusal proven by |
|---|---|---|
| `GET /setup/status` | anonymous | Not applicable |
| `POST /setup/admin` | anonymous | `FirstAdministratorSetupTests.setupIsRejectedWhenAnAdministratorAlreadyExists`: refused once an administrator exists |
| `POST /auth/sign-in` | anonymous | `SessionTests.disabledAccountCannotSignIn`, `AuthenticationAndAuthorizationTests.incompleteSignInsAreRejectedWithoutASession` |
| `POST /auth/sign-out` | anonymous | Not applicable |
| `POST /auth/refresh` | account | Identity scan; `SessionTests.renewalIsRefusedFor…` (disabled, deleted, expired, other key) |
| `GET /auth/me` | account | Identity scan; `SessionTests.currentAccountWithoutSessionIsRefused` |
| `DELETE /auth/me` | account | Identity scan; `AccountDeletionTests.anonymousAndCrossSiteRequestsAreRefused`, `disabledAccountCannotDeleteItself` |
| `GET /accounts` | admin | Identity scan; `AccountManagementTests.userRequestsAreForbiddenAndChangeNothing` |
| `POST /accounts` | admin | Same |
| `PUT /accounts/{accountId}/role` | admin | Same, also on the user's own account; `AuthenticationAndAuthorizationTests.userCannotClaimTheAdministratorRole` |
| `POST /accounts/{accountId}/disable` | admin | Identity scan; `AccountManagementTests.userRequestsAreForbiddenAndChangeNothing` |
| `POST /accounts/{accountId}/enable` | admin | Same |
| `DELETE /accounts/{accountId}` | admin | Same; `AccountDeletionTests.userCannotDeleteAnotherAccount` |
| `GET /profiles` | account | Identity scan; `ProfileTests.anotherAccountsProfileIsInvisible` |
| `POST /profiles` | account | Identity scan; `ProfileTests.anonymousRequestsAreRefused` |
| `PUT /profiles/{profileId}` | account | Identity scan; `ProfileIsolationAndDisabledAccountTests.administratorHasNoAccessToAnotherAccountsProfiles` (another account's profile) |
| `DELETE /profiles/{profileId}` | account | Same |
| `POST /profiles/{profileId}/select` | account | Same; `selectingAnotherAccountsProfileLeavesTheSessionUnchanged` |
| `GET /ping` (demo) | anonymous | Not applicable: see [the demo](../architecture/service-to-service-demo.md) |

### Library service

| Operation | Permission | Refusal proven by |
|---|---|---|
| `GET /roots`, `POST /roots`, `DELETE /roots/{rootId}` | admin | Library scan; `RootEndpointsTests.anonymousRequestsAreRefused`, `anOrdinaryUserIsRefused` |
| `POST /scans` | admin | Library scan; `ScanEndpointsTests.onlyAnAdministratorCanScan` |
| `GET /media`, `GET /media/{mediaId}`, `GET /categories`, `GET /genres` | account | Library scan; `CatalogueEndpointsTests.anonymousRequestsAreRefused` |
| `PUT /media/{mediaId}`, `DELETE /media/{mediaId}` | admin | Library scan; `MediaAdminEndpointsTests.anonymousAndOrdinaryUsersCannotEditOrRemove` |
| `GET /media/{mediaId}/stream` | account | Library scan; `StreamEndpointTests.anonymousRequestsAreRefusedEvenForARange` |
| `GET /favourites`, `PUT /favourites/{mediaId}`, `DELETE /favourites/{mediaId}` | profile | Library scan; `ProfileIsolationWithSignedTokensTests` (another profile, another account, a profile named in the request, a token without a profile); `ProfileDataEndpointTests.aProfileNeverSeesOrChangesAnotherProfilesList` |
| `GET /progress`, `GET /progress/{mediaId}`, `PUT /progress/{mediaId}`, `DELETE /progress/{mediaId}` | profile | Same; `ProfileDataEndpointTests.aProfileNeverSeesAnotherProfilesProgress` |
| `PUT /internal/downloads/{entryId}`, `POST …/completed`, `POST …/failed` | service (acquisition) | Library scan; `ProfileDataDeletionTests.theAcquisitionServiceCannotUseTheIdentityServicesSecretEither` (and the reverse) |
| `DELETE /internal/profiles/{profileId}/data` | service (identity) | Library scan; `ProfileDataDeletionTests.onlyTheIdentityServiceCanCall` |
| `GET /demo/identity` (demo) | anonymous | Not applicable: see [the demo](../architecture/service-to-service-demo.md) |

What the library scan checks, on every endpoint of each permission:

| Caller | Endpoints | Expected |
|---|---|---|
| No cookie | all but the demo | `401` |
| Signed-in user with a profile | admin | `403` |
| Administrator, or user without a profile in the token | profile | `403` |
| User or administrator session, no service secret | `/internal` | `401` |
| The other service's secret | `/internal` | `401` |

### Acquisition service

| Operation | Permission | Refusal proven by |
|---|---|---|
| `GET /entries`, `GET /entries/{entryId}` | admin | Acquisition scan; `EntryEndpointsTests.anonymousCallerIsRefused`, `ordinaryUserIsRefused` |
| `GET /downloads`, `POST /downloads`, `GET /downloads/{downloadId}`, `POST /downloads/{downloadId}/cancel` | admin | Acquisition scan; `DownloadEndpointsTests.anonymousCallerCannotStartCancelOrRead` |

The acquisition scan expects `401` without a cookie and with an invalid one, `403` for a signed-in user, and neither for an administrator, on every endpoint. Removing `@AdminOnly` from one controller makes it fail (checked during this review).

## Direct calls to the services (R-12)

The services publish no port to the host (checked in CI, and `docker compose ps`: only the gateway and qBittorrent's peer port). Something on the backend network could still call them directly, without the gateway. They refuse it:

```sh
# A token signed with no key: alg "none", admin true, valid until 2100.
b64url() { base64 | tr -d '=\n' | tr '/+' '_-'; }
forged="$(printf '{"alg":"none","typ":"JWT"}' | b64url).$(printf '{"iss":"homeflix-identity","sub":"00000000-0000-0000-0000-000000000001","admin":true,"exp":4102444800}' | b64url)."
call() { # method url [extra curl arguments]
  method=$1 url=$2; shift 2
  printf '%s %s %s: ' "$method" "$url" "$*"
  docker run --rm --network homeflix_backend --entrypoint curl homeflix-qbittorrent:latest \
    -s -m 5 -o /dev/null -w '%{http_code}\n' -X "$method" "$@" "$url"
}
profile=00000000-0000-0000-0000-000000000002
for url in http://identity-service:8081/auth/me http://identity-service:8081/accounts http://identity-service:8081/profiles \
    http://library-service:8082/media http://library-service:8082/roots http://library-service:8082/favourites \
    http://acquisition-service:8083/entries http://acquisition-service:8083/downloads; do
  call GET "$url"
  call GET "$url" -b "homeflix_token=$forged"
done
call DELETE "http://library-service:8082/internal/profiles/$profile/data"
call DELETE "http://library-service:8082/internal/profiles/$profile/data" -H "X-Service-Token: guessed-secret"
# Through the gateway:
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE "http://localhost:8080/api/library/internal/profiles/$profile/data"
```

The qBittorrent image is used only because it has `curl`. Result on 2026-11-25, run again with this script on 2026-11-26 with the same answers:

| Direct call on the backend network | No session | Forged token (`alg: none`, `admin: true`) |
|---|---|---|
| identity `GET /auth/me`, `/accounts`, `/profiles` | 401 | 401 |
| library `GET /media`, `/roots`, `/favourites` | 401 | 401 |
| acquisition `GET /entries`, `/downloads` | 401 | 401 |
| library `DELETE /internal/profiles/{id}/data` | 401 | 401 with a guessed `X-Service-Token` |

Through the gateway, `DELETE /api/library/internal/profiles/{id}/data` answers `404`: `/internal` is not routed (ADR-0022).

## Each service's database user (ADR-0006)

`db/init/01-create-databases.sh` gives each service its own user, owner of its own database only, and revokes access from `PUBLIC`. Each user tried each database over TCP with its own password:

```sh
docker compose exec -T postgres sh -c '
for u in identity library acquisition; do
  case $u in identity) p=$IDENTITY_DB_PASSWORD;; library) p=$LIBRARY_DB_PASSWORD;; acquisition) p=$ACQUISITION_DB_PASSWORD;; esac
  for d in identity library acquisition; do
    printf "%s -> %s: " homeflix_$u homeflix_$d
    PGPASSWORD=$p psql -h 127.0.0.1 -U homeflix_$u -d homeflix_$d -tAc "select current_user" 2>&1 | head -n 1
  done
done'
```

The passwords come from the container's environment and are not printed. Result on 2026-11-25:

| User | `homeflix_identity` | `homeflix_library` | `homeflix_acquisition` |
|---|---|---|---|
| `homeflix_identity` | connects | `permission denied for database` | `permission denied for database` |
| `homeflix_library` | `permission denied for database` | connects | `permission denied for database` |
| `homeflix_acquisition` | `permission denied for database` | `permission denied for database` | connects |

The database privileges agree: each `homeflix_*` database grants `CTc` (connect, temporary, create) to its own user only.

In the code, each service has one data source, on its own database. No service's code names another service's database, and the library schema has no foreign key to another service's data (`MediaSchemaMigrationTests.noForeignKeyPointsToAnotherServiceOrToAProfile`). Data owned by another service is reached only through its API (ADR-0008, ADR-0022).

## Findings

| # | Finding | Decision |
|---|---|---|
| 1 | Every operation calls the guard its contract requires, and refuses every other caller. No missing check was found. | Nothing to fix. Two scan tests added, so a new endpoint without its guard fails the build. |
| 2 | A disabled account keeps access to the library and acquisition services until its token expires, at most 15 minutes. The identity service refuses it at once, sign-in and renewal included. AS-07.6 and ACC-10, as written, do not allow this window (finding F1 of the [first AI agent report](../team/ai-agent-reports/001.md)). | Known limit, accepted (R-10, ADR-0009). The wording of AS-07.6 and ACC-10 is for K11 (#94) to settle. |
| 3 | The demo endpoints `GET /api/identity/ping` and `GET /api/library/demo/identity` are anonymous and not in the contracts (finding F9 of the agent report). They return no user data. | Documented here. Removing them or adding them to the contracts is for K11 (#94). |
