# Identity design: session and token

How a user signs in, how the session is carried and renewed, and how it ends. Covers ACC-03, ACC-06, ACC-10 (token side), and risks R-09 and R-10. The decisions are in [ADR-0009](../adr/0009-jwt-rs256-httponly-cookie.md) and in the risk register (2026-11-17). The implementation is issue #41 (G3).

This document is the session part of the identity and permissions design (issue #14). Permissions per role, the active profile and how the other services receive the public key are written there and in G5.

## Endpoints

Contract: [`docs/api/identity.yaml`](../api/identity.yaml), tag *Session*. Public paths are under `/api/identity` (ADR-0017).

| Operation | Permission | Result |
|---|---|---|
| `POST /auth/sign-in` | anonymous | `200` with the account and a new session cookie, or `401 invalid_credentials` |
| `POST /auth/sign-out` | anonymous | `204`, cookie cleared. Also works with an expired session. |
| `POST /auth/refresh` | account | `200` with a fresh cookie, or `401 unauthenticated` and the cookie cleared |
| `GET /auth/me` | account | `200` with the account, or `401 unauthenticated` |

A failed sign-in gives the same answer for an unknown email, a wrong password and a disabled account. When the email is unknown, the password is still checked against a dummy BCrypt hash, so the answer takes about the same time and timing does not reveal which accounts exist.

## Token

A JWT signed with **RS256** by the identity service.

| Item | Value |
|---|---|
| Header | `alg: RS256`, `typ: JWT`. Any other algorithm, including `none`, is refused. |
| `iss` | `homeflix-identity` |
| `sub` | Account id (UUID). |
| `admin` | `true` for an administrator, `false` for a user. Read from the database at sign-in and at each renewal, so a role change applies at the next renewal. |
| `profileId` | Active profile id. Absent until a profile is selected (G9). Kept by renewal. |
| `iat`, `exp` | Issue time and expiry: **15 minutes** (`homeflix.session.token-lifetime`). |
| `jti` | Random id, unique per token. |

The token never contains the email, the password or its hash.

## Cookie

```
homeflix_token=<jwt>; Path=/; Max-Age=900; HttpOnly; SameSite=Strict
```

- `HttpOnly`: scripts cannot read the token, so an XSS cannot steal it.
- `SameSite=Strict`: the browser does not send it on requests started from another site.
- `Path=/`: sent to every service through the gateway, including video streaming, which cannot send an `Authorization` header (ADR-0009).
- `Max-Age` equals the token lifetime, so the browser drops the cookie when the token expires.
- `Secure` is added with `homeflix.session.cookie-secure=true` once HomeFlix is served over HTTPS (issue L8). Over plain HTTP on the home network it would stop the cookie from being sent.

## Renewal and disabled accounts (R-10)

- There is **no revocation list**, so the other services stay stateless: a token is valid until its `exp`.
- The interface renews the token with `POST /auth/refresh` **before** it expires. An expired token cannot be renewed: the user signs in again.
- Renewal reads the account again. A **disabled or deleted** account is refused, so its access ends at most 15 minutes after it was disabled.
- `GET /auth/me` also reads the account, so the identity service itself refuses a disabled account at once.

## Signing key

- The RSA private key comes from the environment variable `JWT_PRIVATE_KEY` (OPS-03), never from the repository. `.env.example` gives the command to generate it: PKCS#8, 2048 bits, one line of base64. PEM is accepted too.
- The service **refuses to start** if the key is missing, invalid, in PKCS#1 format or shorter than 2048 bits. The error message never contains the key.
- The public key is derived from the private key. Only the public key will be given to the other services (G5).
- Changing the key signs everyone out.
- Tests generate a new key for each run, so no private key is ever committed (R-11).

## CSRF review (R-09)

`SameSite=Strict` is only a mitigation (ADR-0009, consequences). Every state-changing endpoint of the identity service was reviewed:

| Endpoint | Uses the cookie | Risk | Protection |
|---|---|---|---|
| `POST /setup/admin` | No | Another site makes the browser create the first administrator. | JSON body only, refused cross-site, and the operation is closed once an administrator exists. |
| `POST /auth/sign-in` | No | Login CSRF: another site signs the user into the attacker's account. | JSON body only (an HTML form cannot send JSON, a cross-origin `fetch` with JSON needs CORS, which is not enabled), refused cross-site. |
| `POST /auth/sign-out` | No | Another site signs the user out. Nuisance only. | Refused cross-site. |
| `POST /auth/refresh` | Yes | Another site renews the session. Gives the attacker nothing: the new cookie stays in the user's browser. | `SameSite=Strict` (no cookie on cross-site requests), refused cross-site. |

Protections applied to every service request:

1. **`SameSite=Strict` cookie**: not sent on cross-site requests.
2. **JSON-only bodies**: endpoints with a body accept `application/json` only. A form post answers `415`.
3. **No CORS**: no `Access-Control-Allow-Origin` header is sent, so a script on another site cannot send JSON or read answers.
4. **Fetch Metadata check** (`CrossSiteRequestFilter`): a `POST`, `PUT`, `PATCH` or `DELETE` whose `Sec-Fetch-Site` header is `cross-site` or `same-site` gets `403 forbidden`. HomeFlix pages are served from the same origin through the gateway, so they always send `same-origin`. Requests without the header (older browsers, scripts, tests) are covered by points 1 to 3.

The library and acquisition services must apply the same rules to their state-changing endpoints (G5).
