# Out of scope

What HomeFlix deliberately does **not** do in the MVP, and why. Covers DOC-01 and GEN-04: a coherent, reliable MVP takes priority over a long list of unfinished features. The short version is in [requirements.md](requirements.md#out-of-scope).

Nothing here is rejected for good. Each item moves to the [future extensions](#future-extensions) list, and may only be proposed once the MVP is complete and stable. An extension must answer a documented user need, preserve the existing journeys, and include its own tests and documentation.

## Exclusions and reasons

| Exclusion | Reason | What the MVP does instead |
|---|---|---|
| Universal transcoding and support for more video formats | Transcoding needs heavy CPU, new infrastructure and a large test matrix. The brief does not require it (PLY-01). | One format is supported and documented: MP4, H.264 video, AAC audio ([ADR-0015](../adr/0015-mp4-h264-aac-video-format.md)). Other files are reported as unsupported, with a conversion note. |
| Mobile and TV applications | A second client doubles the interface work for a three-person team. | A responsive web interface (PLY-06, UX-03). |
| Recommendations from viewing history | Needs enough viewing data to be meaningful, and adds a service or a model to maintain. | Recently added, categories, favourites and continue watching (CAT-02, CAT-07). |
| Series, seasons and episodes | Changes the catalogue and playback model (grouping, ordering, next episode). | Every title is a single video file. |
| Parental controls, household invitations and extra profile controls | Needs a richer permission model than the two roles decided for the MVP (administrator and user). | Two roles, and profiles that only separate favourites and progress (PRF-01, PRF-03). |
| Remote Internet access | Exposing the application raises security work (HTTPS, hardening) that cannot be tested properly in two weeks. | Local network only. The requirements for a future exposure, including HTTPS, are documented (SEC-11). |
| Universal network protocol support, and support for every NAS or router | Each protocol and device has its own quirks that cannot all be tested. | The library is a folder mounted into the container. Tested environments and mount prerequisites are documented (LIB-13, OPS-11). |
| A BitTorrent engine of our own | Not required, and far outside the project's goal (ACQ-08). | An existing client, qBittorrent, is driven through its API ([ADR-0014](../adr/0014-qbittorrent-p2p-client.md)). |
| A general-purpose torrent search engine | It would lead to sources that distribute unauthorized works, which the brief forbids (ACQ-13). | A separate catalogue of content the team has verified as redistributable, with source and licence for each entry (ACQ-01, ACQ-02). |
| Mockups before development | The brief's Day 1 schedule lists them, but the team decided to skip them. | The frontend lead designs the interface directly in code, using the visual identity in [ADR-0019](../adr/0019-frontend-styling-approach.md). |
| Uploading a poster image file | Needs file upload handling, storage and image validation. | An administrator enters an image URL on an allowed host (CAT-11, [ADR-0018](../adr/0018-posters-as-allowlisted-external-urls.md)). |
| Other interface languages | The interface is English only. | See [ADR-0021](../adr/0021-english-only-interface.md). |

## Future extensions

Ordered roughly by how well they fit the existing architecture. None is planned or scheduled.

| Extension | Builds on |
|---|---|
| Poster upload | Replaces the allowed-host URL mechanism in the library service. |
| Series, seasons and episodes | Extends the library data model. |
| Recommendations | Uses the viewing progress already stored per profile. |
| Parental controls and invitations | Extends the roles and profiles of the identity service. |
| More video formats and transcoding | Extends the supported-format rules and the streaming endpoint. |
| Internet exposure | Starts from the requirements documented for SEC-11. |
| Mobile or TV client | Reuses the existing gateway and REST APIs. |
