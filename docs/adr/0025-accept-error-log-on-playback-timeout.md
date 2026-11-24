# ADR-0025: Accept an `ERROR` log line when a viewer's browser stops reading a video stream

- **Status:** Proposed
- **Date:** 2026-11-24
- **Deciders:** Clement Callejon. Awaiting review by another learner, as required by the ADR process.
- **Related:** issue J10 (#83), pull request #178, [p2p-run.md](../acquisition/p2p-run.md), ADR-0020

## Context

During the recorded real transfer (J10), playback of the downloaded file worked. When the browser stopped reading the stream, the library service logged a `SocketTimeoutException` through `ApiExceptionHandler` as `ERROR` ("Unexpected error").

`StreamController` already treats a viewer who leaves as normal: `isClientGone` logs broken pipes, connection resets and Tomcat client aborts at `debug`. It does not recognise `SocketTimeoutException`, which falls through to the catch-all handler.

The user sees nothing wrong, and playback and the response are not affected. The only effect is a misleading `ERROR` line in the logs. The project is close to its end and has a lot of work left.

## Decision

We will not fix it now. We accept the `ERROR` line as known log noise.

## Alternatives considered

- **Treat `SocketTimeoutException` as "client gone" in `StreamController.isClientGone`:** about two lines plus a test, and the best fix if we change it. Not done now because the effect is only a log line. It is not confirmed that the exception is thrown inside the `try` block of `send`, so a reproduction would be needed first.
- **A dedicated `@ExceptionHandler(SocketTimeoutException.class)` in `ApiExceptionHandler` logging at `debug`:** also small, but it applies to every endpoint, which is broader than the problem.

## Consequences

- The library service log can show `ERROR` lines for a normal playback event. Whoever reads the logs must know that an `ERROR` "Unexpected error" with a `SocketTimeoutException` on the stream endpoint is not a failure.
- If the log noise starts to hide real errors, or if monitoring or alerting is added on `ERROR`, we should apply the first alternative.
