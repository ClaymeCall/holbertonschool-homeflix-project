# Error model and outage behavior

How HomeFlix reports errors and behaves when a service is down or slow. Covers ARC-08, SEC-04 and PLY-05, and risks R-23 and R-24. The decisions are in [ADR-0020](../adr/0020-common-error-format-and-outage-behavior.md). The implementation is issue #36.

## Error format

The gateway and every service answer errors with the same JSON body, and a matching HTTP status:

```json
{
  "error": "validation_failed",
  "message": "Some fields are invalid.",
  "fields": { "email": "Enter a valid email address." }
}
```

| Field | Rule |
|---|---|
| `error` | Required. Stable snake_case code from the catalogue below. Never changes once published. |
| `message` | Required. One short plain-language sentence in English. Safe to show. |
| `fields` | Only for validation errors. Maps a field name to its message. |

A body **never** contains a stack trace, a class or exception name, a file path, a SQL fragment, a host name or a port (SEC-04). Details go to the server log only.

## Who writes the text the user reads

The interface is English only ([ADR-0021](../adr/0021-english-only-interface.md)), so the server's `message` **is** the text the user reads. The interface shows it as it is, with no translation map. The `error` code stays stable for logic, for tests and for choosing how to present the error (for example a retry button for `service_unavailable`). See [Writing messages](#writing-messages).

## Error codes

### Common to every service and the gateway

| Code | Status | Message | When |
|---|---|---|---|
| `validation_failed` | 400 | Some fields are invalid. | Input broke a rule; `fields` says which. |
| `invalid_request` | 400 | The request body is missing or is not valid JSON. | Unreadable body. |
| `unauthenticated` | 401 | Please sign in. | No valid session. *(behavior to confirm in #14)* |
| `forbidden` | 403 | You do not have permission to do this. | Signed in, but the role does not allow it. *(to confirm in #14)* |
| `not_found` | 404 | This address does not exist. | Unknown address or resource. |
| `request_rejected` | 4xx | This request cannot be processed. | Framework rejection (wrong method, unsupported media type). Keeps its status. |
| `internal_error` | 500 | Something went wrong. Please try again. | Unexpected failure. |
| `service_unavailable` | 503 | This part of HomeFlix is temporarily unavailable. Please try again in a moment. | A service is down, too slow, or answered with an error. |

### Identity service

| Code | Status | Message | When |
|---|---|---|---|
| `setup_already_completed` | 409 | HomeFlix is already set up. Sign in instead. | First-run setup after an administrator exists. |

### Library service

| Code | Status | Message | When |
|---|---|---|---|
| `file_not_found` | 404 | The file for this title could not be found. It may have been moved or deleted from the library. | The media file is missing from disk. |
| `access_denied` | 403 | You do not have access to this video. | The real path is outside the authorized folders (see [file access rules](../library/file-access.md)). |
| `directory_not_found` | 400 | This folder does not exist. | Administrator adds a root that does not exist. |
| `directory_not_readable` | 400 | HomeFlix cannot read this folder. | The root exists but is not readable. |
| `not_a_directory` | 400 | This path is a file, not a folder. | The root is not a directory. |
| `directory_outside_library` | 400 | This folder is not inside the folders shared with HomeFlix. | The root is outside the mounted paths (R-02). |

`file_not_found` and `access_denied` have different messages, so the user can tell a missing file from a permission problem (PLY-05), but neither ever says where the file is. `format_not_supported` is **not** an HTTP error. It is the outcome reported for a video found during indexing in an unsupported format (see [media identification rules](../library/media-identification.md)), so such a file never gets a play endpoint.

## Timeouts and retries

| Setting | Value |
|---|---|
| Connect timeout (service to service) | 2 s |
| Read timeout (service to service) | 5 s |
| Gateway connect timeout | 2 s (`proxy_connect_timeout`) |
| Retry | Only GET, once, after about 200 ms |
| Retried when | Connection failure, or a 502, 503 or 504 answer |
| Never retried | POST, PUT, PATCH, DELETE, and any 4xx answer |

Worst case for a retried GET: two attempts of up to 2 s to connect and 5 s to read, plus the pause, about 14 s. Values are configuration, so they can be tuned.

Video streaming is not an inter-service call: the browser streams from the library service through the gateway, so these timeouts do not apply to it.

## When a service is down or slow

A timeout, a refused connection, or a 5xx from the called service all become the same answer to the caller: **503 `service_unavailable`**. The user cannot act on the difference, and the real cause is in the log.

| Situation | What the user sees | Rest of HomeFlix |
|---|---|---|
| Identity service down | Sign-in and account pages show the service-unavailable message | What the other services do without identity depends on the identity design (issue #14) |
| Library service down | Catalogue and player show the service-unavailable message, with a retry button | Sign-in still works |
| Acquisition service down | Download screens show the service-unavailable message | Catalogue and playback are not affected |
| Database down | The owning service answers 503 | The other services keep working |

The gateway answers its own 503 in the same format when a service does not answer (ADR-0017). It already matches this format: `{"error":"service_unavailable","message":"This part of HomeFlix is temporarily unavailable. Please try again in a moment."}`.

## Writing messages

A message is written for the person using HomeFlix, in English (PLY-05, SEC-04):

- One or two short sentences in plain language. Say what happened and, when there is one, what to try.
- Never include a path, a host name, a class name, an SQL fragment or a stack trace. Details go to the log.
- Do not reveal whether something exists when that would help an attacker, for example a failed sign-in gives one answer whether the email is unknown, the password is wrong or the account is disabled.
- Same code, same message everywhere, so tests and the interface can rely on it. Change the table above first, then the code.

## Tests

Issue #36 covers a slow dependency (timeout gives 503), a failing one (one retry for GET, none for POST, then 503), and the shape of the body without technical details (R-24, TST-05).
