# Demo: one service calls another

Shows that two services cooperate over REST on the Compose network (ARC-10, ARC-08, ADR-0008, issue #27).

## What it does

The library service calls the identity service's documented endpoint `GET /ping` with Spring's `RestClient`, and returns both answers:

```
browser/curl  ->  gateway  ->  library-service  ->  identity-service
                  /api/library/demo/identity         /ping
```

## Run it

Start HomeFlix (`docker compose up --build`, see the README), then:

```
curl http://localhost:8080/api/library/demo/identity
```

Expected answer (HTTP 200):

```json
{"caller":"library-service","callee":{"service":"identity-service","status":"ok"}}
```

Change the port if you set `GATEWAY_PORT` in `.env`.

## When the called service is down

Stop it and call again:

```
docker compose stop identity-service
curl -i http://localhost:8080/api/library/demo/identity
```

The answer is HTTP 503 with a plain-language message and no technical details:

```json
{"error":"service_unavailable","message":"This part of HomeFlix is temporarily unavailable. Please try again in a moment."}
```

Start it again with `docker compose start identity-service`.

## Where the code is

| Part | File |
|---|---|
| Endpoint called | `services/identity-service/.../demo/PingController.java` |
| Client, timeouts (connect 2 s, read 5 s) | `services/library-service/.../demo/IdentityClient.java`, `IdentityClientConfig.java` |
| Demo endpoint and 503 handling | `services/library-service/.../demo/DemoController.java` |
| Address of the identity service | `homeflix.identity.base-url` (env `IDENTITY_SERVICE_URL`), default `http://identity-service:8081` |

## Limits

- **Temporary.** The demo endpoint and `/ping` are removed once real inter-service calls exist (identity validation, G5 #43).
- Both endpoints are unauthenticated and return no personal data.
- The shared timeout, retry and error conventions are issue #36 (E10). This demo only sets basic timeouts.
