# Audit of file paths, mounts, permissions, remote URLs and internal network

The file and network boundaries of HomeFlix, checked as built (issue K6 #89; SEC-09, LIB-08, LIB-11, ACQ-06, ACQ-10; risks R-01 to R-04, R-17, R-18, R-20). The acceptance run (K8, #91) links to this page. Authorization is in the [authorization review](authorization-review.md) (K4), and the rest of the security review in the [security review](security-review.md) (K5).

| | |
|---|---|
| Code reviewed | `main` at `c8dd742`, plus the tests added by K4 and K5 |
| Stack used for the live checks | Docker Compose, built from that code, on macOS with Docker Desktop |
| Reviewed by | Thomas Rousseau |
| Date | 2026-11-25 |

## Summary

| Check | Result |
|---|---|
| Path traversal and symbolic link tests (H13) | Pass |
| Library folder mounted read-only, write refused | Pass |
| Downloads folder read-only in the library and acquisition services, writable only by the torrent client | Pass |
| No poster or download URL fetched by a server | Pass |
| qBittorrent's web interface unreachable from the host and the gateway, only its peer port published | Pass (26 checks of `check-isolation.sh`) |

No defect was found.

## Paths and symbolic links (H13; R-01, R-02, R-04)

Clients send media ids, never file paths. Before opening a file, the library resolves its real path, links included, and checks that it is inside its own root (`MediaPathGuard`, see [file access](../library/file-access.md)). A root can only be added inside a mounted path (`RootValidator`). The completed-file checks of the acquisition service apply the same rule to the downloads folder.

Tests, all passing in the full suites of 2026-11-26 (library 370 tests, acquisition 175 tests, no failure, the same counts as CI):

| What is refused | Tests |
|---|---|
| `..` that leaves the root, absolute path outside the root, sibling folder whose name starts like the root | `MediaPathGuardTest`: `parentSequencesThatLeaveTheRootAreRefused`, `absolutePathOutsideTheRootIsRefused`, `siblingDirectoryWithTheRootNameAsPrefixIsRefused` |
| A link, or a linked folder, that points outside the root (one that stays inside is allowed) | `MediaPathGuardTest`: `symbolicLinkPointingOutsideTheRootIsRefused`, `symbolicLinkedDirectoryPointingOutsideTheRootIsRefused`, `symbolicLinkPointingInsideTheRootIsAllowed` |
| A stored path that escapes its folder, at playback, without saying what is there | `StreamEndpointTests`: `aFilePathThatEscapesItsFolderIsDeniedAndNothingIsRevealed`, `aRelativePathWithDotDotCannotLeaveItsFolder` |
| A root outside the mounted paths, a `..` that leaves them, a link that leads out of them | `RootValidatorTest`: `refusesAFolderOutsideTheMountedPaths`, `refusesADotDotThatLeavesTheMountedPath`, `refusesALinkThatLeadsOutsideTheMountedPaths`, `refusesASiblingThatOnlySharesATextPrefix`, `doesNotSayWhetherAPathOutsideTheMountedPathsExists` |
| Links met during a scan | `ScanEndpointsTests.linksAreNotFollowed` |
| A stored path that is absolute or has a `..` segment | `MediaSchemaMigrationTests`: `relativePathWithADotDotSegmentIsRejected`, `relativePathCannotBeAbsoluteOrBlank`, `rootPathWithADotDotSegmentIsRejected` |
| A completed download outside the downloads folder, or a link | library `DownloadEndpointsTests.aLinkLeadingOutsideTheDownloadsFolderIsRefused`; acquisition `DownloadedFileVerifierTest`: `aPathOutsideTheDownloadsFolderIsRefused`, `aSymbolicLinkIsRefused`, `aSubfolderThatIsALinkOutsideIsRefused` |

## Mounts and write permissions (R-03, R-20; LIB-11, ACQ-06)

In `compose.yaml`, the library folder is mounted `:ro` in the library service, and the downloads folder `:ro` in the library and acquisition services. Only qBittorrent mounts the downloads folder writable. The gateway and the identity service mount neither.

Checked in the running containers: the mount options in `/proc/mounts`, then an attempt to create a file.

```sh
docker compose exec -T library-service touch /media/library/.k6-write-test
docker compose exec -T library-service touch /media/downloads/.k6-write-test
docker compose exec -T acquisition-service touch /downloads/.k6-write-test
```

| Container | Folder | Mount | Write attempt |
|---|---|---|---|
| library-service | `/media/library` | `ro` | `touch: cannot touch '/media/library/.k6-write-test': Read-only file system` |
| library-service | `/media/downloads` | `ro` | `Read-only file system` |
| acquisition-service | `/downloads` | `ro` | `Read-only file system` |
| qbittorrent | `/downloads` | `rw` | Writable by `qbittorrent-nox`, which runs as uid 501, the owner of the folder |
| gateway, identity-service | none | | |

No file was created on the host. So HomeFlix cannot modify or delete the source media: removing a title or an account only removes database rows (LIB-12, SEC-07), and a completed download that fails the checks is removed through qBittorrent, the only writer.

## Remote URLs (R-17; ACQ-10)

No server fetches a URL that comes from a user or from stored data:

- **Posters**: stored as an `https` link on an allowed host, shown by the browser. The library never fetches them (ADR-0018, `CatalogueService`).
- **Downloads**: there is no URL field. An administrator picks an entry of the verified catalogue, which is written by migrations only. Its source must be a magnet link with an info hash, a name and trackers only: the database refuses web seeds and HTTP sources (`xs`, `ws`, `as`), `file:` paths and `.torrent` URLs (`AcquisitionEntryMigrationTests.theDownloadSourceIsAMagnetLinkWithANameAndTrackersOnly`).
- **Outgoing HTTP calls** of the services, all to fixed internal hosts from the configuration:

| Service | Calls | Target |
|---|---|---|
| identity | `LibraryClient` (profile data deletion) | `LIBRARY_SERVICE_URL`, default `http://library-service:8082` |
| library | `ServiceClientFactory` (service-to-service demo) | `IDENTITY_SERVICE_URL`, default `http://identity-service:8081` |
| acquisition | `LibraryClient` (handoff) | `LIBRARY_SERVICE_URL`, default `http://library-service:8082` |
| acquisition | `QbittorrentClient` | `QBITTORRENT_URL`, `http://qbittorrent:8080` in `compose.yaml` |

## Internal network and qBittorrent (R-18, R-20)

`qbittorrent/check-isolation.sh .env`, run on the local stack on 2026-11-25: **all 26 checks passed**. Among them:

- only the gateway and qBittorrent publish ports, and qBittorrent only its peer port 6881 (TCP and UDP), never its web port 8080;
- qBittorrent is only on the `p2p` network, with the acquisition service; the gateway is not on it, and a container on the backend network cannot reach it;
- the gateway has no route to it, and neither its page nor its API answers through the gateway;
- its API refuses a request without a session, the old default login and a wrong password; the login from `.env` works;
- every other container mounts the downloads folder read-only, and only qBittorrent mounts the incomplete downloads volume;
- its settings are applied: seeding limits, peer port, UPnP off, login required from every address.

CI runs the same script on every pull request.

The gateway checks (`gateway/tests/check-gateway.sh`, 90 checks, all passed on 2026-11-25) also show that `/actuator` and unknown `/api` paths answer a JSON `404`. `/internal` paths are not routed either ([authorization review](authorization-review.md#direct-calls-to-the-services-r-12)).

## Findings

None. Every boundary in the "Done when" list of K6 holds as built.
