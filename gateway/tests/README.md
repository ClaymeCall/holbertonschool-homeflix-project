# Gateway checks

`check-gateway.sh` tests the gateway's Nginx configuration with real HTTP requests (issue I6, #68; PLY-03, OPS-05, risks R-23 and R-25). It needs `docker`, `curl` and `sha256sum`, and leaves nothing behind.

```bash
gateway/tests/check-gateway.sh
```

It exits with 0 when every check passes, and prints one line per check. It runs in the continuous integration pipeline (job "Gateway").

## What it does

It starts the real `gateway/nginx.conf` in the same Nginx image as `gateway/Dockerfile`, next to a stand-in for the library service, on a private Docker network. The stand-in serves a 3 MB random file, and Nginx implements `Range` for it. So the script tests what the **gateway** does to a ranged answer. The library service's own Range rules are tested in `services/library-service` (`RangeRequestTest` and `StreamEndpointTests`).

## What it checks

| Group | Check |
|---|---|
| Range through the gateway | The whole file: `200`, `Accept-Ranges: bytes`, the right `Content-Length`, the same bytes (compared by SHA-256). |
| | A closed range, a slice in the middle (a seek), an open-ended range and a suffix range: `206`, the right `Content-Range` and `Content-Length`, exactly the right bytes. |
| | A start past the end: `416` with `Content-Range: bytes */SIZE`. |
| | Buffering is off on `/api/library/`, and the answer is not chunked. |
| Paths | `/api/<service>/actuator...` answers a JSON `404`, in any letter case. |
| | `/api/<service>/internal...` answers the same JSON `404`, in any letter case and in the `//`, `/./`, `/../` and `%69` spellings ([ADR-0022](../../docs/adr/0022-internal-service-calls.md)). |
| Poster policy | Every answer carries exactly one `Content-Security-Policy: img-src 'self' https://host ...`, built from `POSTER_ALLOWED_HOSTS` (spaces, capitals and a trailing comma are cleaned). An empty list gives `img-src 'self'`. A wildcard, scheme, port, single-label or injecting entry stops the gateway from starting ([ADR-0018](../../docs/adr/0018-posters-as-allowlisted-external-urls.md)). |
| | `/api`, an unknown service, and the names of qBittorrent and the database answer the same JSON `404`. |
| | The frontend is served at `/`, and a frontend route falls back to the app. |
| A service down | With the library service stopped: `503` and the plain JSON message, for a normal and for a ranged request. |
| | A service that does not exist (identity here): the same `503`. |
| | The `503` message shows no technical detail, and the gateway stays healthy. |

## What it does not check

- **The real library service behind the gateway.** Its stream endpoint needs a signed-in user, and the token check (issue G5, #43) is not built yet. Once it is, add a check that signs in and streams a real title through the gateway.
- **Slow clients and very large files.** Buffering is checked in the configuration, not by timing a transfer.
