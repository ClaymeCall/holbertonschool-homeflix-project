# ADR-0021: English-only interface, server messages shown as written

- **Status:** Accepted
- **Date:** 2026-11-17
- **Deciders:** Clement Callejon. Accepted when the pull request that records it is approved by another learner, as the ADR process requires.
- **Related:** ADR-0020 (partly superseded), pull request #133, issue #16, requirements PLY-05 and SEC-04

## Context

The interface language was never recorded. ADR-0020 assumed a French interface with an English API, so the frontend would translate each error code into French. The copyright note was written the same way, with the in-app rights reminder in French. Pull request #133 now states that the interface is English only and rewrites the reminder in English. Keeping a French translation layer would mean a second copy of every message that must stay in sync with the server, for a language the interface no longer uses.

## Decision

We will make the interface **English only**: screens, buttons, labels, validation text, the rights reminder and error messages.

The `message` of the common error body (ADR-0020) is the text the user reads. The frontend shows it as it is and keeps no translation map. Messages are written for users, in plain language, without technical details (SEC-04), as described in the error model. The `error` code stays stable and is what the frontend uses to decide how to present an error.

The frontend writes its own text only where the server cannot: no answer at all (network down, status 0), and an answer that is not the common error body (for example a gateway or proxy page). In every other case it shows the `message` of the body, and for a validation error the message of each field in `fields`. A 5xx other than 503 is an unexpected failure whose body cannot be trusted to follow the format, so the frontend also writes its own text for it. It does not choose text from the HTTP status otherwise, because the server already did, and because the server may answer a status with a more precise reason (for example the sign-in answers 401 `invalid_credentials` for an unknown email, a wrong password and a disabled account alike, so accounts are not revealed).

This supersedes the part of ADR-0020 that says the frontend translates the error code into French. Everything else in ADR-0020 (body shape, timeouts, retries, the 503 rule, the file error codes) stays as decided.

## Alternatives considered

- **French interface with a code-to-text map in the frontend:** the earlier plan. Two places hold each message, and a new error code needs a frontend change before users see anything sensible.
- **French messages from the server:** one place, but it fixes the API itself to French and makes tests and API documentation harder to read.
- **Both languages:** needs a translation mechanism and a way to choose the language, which is out of scope for the MVP.

## Consequences

- Fewer moving parts: adding an error code only needs a clear message on the server, and an unknown code is still readable.
- The frontend keeps the `message` and the `fields` of the error body instead of dropping them, and shows them. A status-to-text map for the cases above only is the one piece of text it still owns.
- The French strings that were written in the frontend pull requests (setup, sign-in, catalogue, player screens) have been rewritten in English (#117, #118, #119). This decision does not change that code.
- The error model and the API contracts show the messages the user sees.
- A second language is not ruled out forever, but it would need its own decision and a translation mechanism.
