# ADR-0018: Posters are external image URLs from an allowed host list

- **Status:** Proposed
- **Date:** 2026-11-17
- **Deciders:** Clement Callejon. Awaiting review by another learner, as required by the ADR process.
- **Related:** issue #6, requirements CAT-05, CAT-10, CAT-11 and SEC-12, ADR-0013, ADR-0017

## Context

Each movie page shows a poster. A downloaded title gets almost no metadata from its torrent file (essentially a name and a size), so posters must come from somewhere else, supplied by an administrator. The brief requires validating remote sources, so that arbitrary URLs cannot give access to internal services, and requires safe handling of user-controlled input. A placeholder poster is already required when a title has none (CAT-05).

## Decision

- An administrator supplies a poster by entering an **image URL**. The library service stores the URL as text.
- Only **`https`** URLs are accepted, and only when the host is in an **allowed host list**. The list is configuration (an environment variable), not code. It starts with a few well-known image hosts (for example `upload.wikimedia.org`, to be confirmed when implemented), and an administrator of the installation can extend it.
- The server **never fetches** the URL. The viewer's browser loads the image directly. This removes the server-side request forgery risk for posters.
- The poster is displayed with a **no-referrer** policy. If the image fails to load, the interface shows the **placeholder poster**.
- The gateway sends a **Content-Security-Policy** that limits `img-src` to HomeFlix itself and the allowed hosts. The same allowed host list feeds both the library service's validation and the gateway's policy, so the two cannot drift apart.

## Alternatives considered

- **The server downloads and stores a copy of the image from the URL:** works offline afterwards, but a server fetching a user-supplied URL is the exact risk the brief warns about, and it needs validation, size limits and image checks.
- **Uploading an image file:** self-contained and private, and works with no internet, but needs file validation, a storage volume and a serving path. It remains a possible later improvement.
- **Allowing any `https` URL:** simpler, but every viewer's browser would contact arbitrary hosts, which leaks viewing habits and allows tracking images.

## Consequences

- Very little code: one text field, one validation rule and one `img-src` policy.
- Posters can disappear if the external site removes them, and they need the viewer's browser to reach the internet. The placeholder covers both cases.
- The viewer's browser contacts the allowed hosts to display posters. Staying on a short allowed list limits this.
- Poster images are third-party artwork. The team must only use posters whose licence allows it for the demonstration, and keep the source, as it does for demo files (ACQ-12).
- The allowed host list must be configurable through `.env` and applied to both the gateway configuration and the library service.
- Changing the gateway's headers has to be coordinated with ADR-0017.
