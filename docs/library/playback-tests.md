# Playback tests (TST-04)

The playback business rules are tested against a real PostgreSQL (Testcontainers), so Docker must be running. All commands run from `services/library-service`.

| Rule | Requirement | Test class | What it checks |
|---|---|---|---|
| Range responses | PLY-02, PLY-03 | `StreamEndpointTests`, `RangeRequestTest` | Whole file, closed, open-ended and suffix ranges, `206` with the right `Content-Range`, `416` when the range cannot be satisfied, malformed ranges ignored |
| Profile isolation | PRF-03, PRF-04 | `ProfileDataEndpointTests`, `ProfileIsolationWithSignedTokensTests` | A profile never sees or changes another profile's favourites or progress, even if it names that profile in the request |
| Resume | PLY-08, PLY-09 | `ProfileDataEndpointTests`, `ProgressStateTest` | The saved position is returned for the title, saving again replaces it, and the started and completed states are computed |
| Persistence after a restart | PLY-10, OPS-08 | `PersistenceAfterRestartTests` | Writes through the service, stops it, restarts the PostgreSQL container, starts the service again, and reads back the favourite and the saved positions of two profiles |

Run only the playback tests:

```bash
./mvnw test -Dtest='StreamEndpointTests,RangeRequestTest,ProfileDataEndpointTests,ProfileIsolationWithSignedTokensTests,ProgressStateTest,PersistenceAfterRestartTests'
```

Run only the restart test:

```bash
./mvnw test -Dtest=PersistenceAfterRestartTests
```

The restart test covers the service and the database container. The restart of the whole stack (`docker compose restart`, then `down` and `up` without `-v`) is checked by hand in scenario AS-08 ([acceptance criteria](../product/acceptance-criteria.md)), which also keeps the data volume in place.
